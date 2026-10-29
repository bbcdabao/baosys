# 认证与会话

Manager 同时提供内部浏览器共享 Cookie 登录和外部 OAuth2/OIDC 授权码流程。两者共用账户、JWK 签名密钥、官方 JDBC 授权存储及登录记录，但浏览器交互与刷新期限不同。

内部登录、刷新和退出的逐步调用过程见 [BrowserAuthApi 接口实现时序详解](browser_auth_sequence.md)，其中包含三个接口各自的时序图、事务与失败分支说明。

## 1. 内部共享登录

Vue 登录页是 Manager Web 的 `/login?redirect_url=...`。页面参数使用 `redirect_url`，提交给后端的 JSON 字段是 `redirectUrl`。

三个共享认证接口由 manager-api 中的 `BrowserAuthApi` 声明，登录请求和响应分别使用 `BrowserLoginRequest`、`BrowserLoginResponse`。SharedLoginController 实现契约，负责设置 Cookie 和调用认证服务。

| 方法 | 浏览器路径 | 请求与结果 |
|---|---|---|
| POST | `/api/baosys-manager/v1/browser-auth/login` | JSON：username、password、可选 redirectUrl；响应 data.redirectUrl，令牌只写 Cookie |
| POST | `/api/baosys-manager/v1/browser-auth/refresh` | JSON 请求，使用 Refresh Cookie 轮换令牌 |
| POST | `/api/baosys-manager/v1/browser-auth/logout` | JSON 请求，撤销当前内部授权并清除两个 Cookie |
| GET | `/csrf` | 网关返回掩码 CSRF token 和 headerName |

登录校验用户名密码及 enabled；用户名先 trim。成功时撤销该浏览器旧 Refresh Cookie 对应的内部授权，再创建新授权及登录记录。授权类型为 `urn:baosys:grant:shared-login`，使用已注册的 `baosys-web` 客户端设置。

返回地址由 `SharedLoginProperties` 校验：必须同主机、同协议，来源为 manager-origin 或 allowed-origins 中的值；拒绝用户信息、登录/API/授权路径等不适合的目标。未传地址时默认返回 `{manager-origin}/users-management`。

| Cookie | Path | 属性 |
|---|---|---|
| BAOSYS_ACCESS_TOKEN | `/` | HttpOnly、SameSite=Lax |
| BAOSYS_REFRESH_TOKEN | `/api/baosys-manager/v1/browser-auth` | HttpOnly、SameSite=Lax |

不设置 Domain，因此 Cookie 在同一主机的不同端口共享，不跨子域共享。Secure 由配置控制；manager-origin 为 HTTPS 时要求 secure-cookie=true。响应使用 Cache-Control: no-store。

网关优先读取显式 Authorization 头，否则读取 Access Cookie；无效的显式头不会回退到 Cookie。公开的共享认证端点不依赖旧 Access JWT，避免过期 JWT 阻止刷新。

浏览器写请求先取得 `/csrf` 的 token 并放入指定请求头。网关负责 CSRF 校验；直连 Service 的共享认证写请求仍须具有与 manager-origin 精确相同的 Origin。Service 该链本身为无状态且关闭 CSRF。网关仅对受保护 API 中显式 Bearer 请求豁免 CSRF，公开登录/刷新/退出不能据此豁免。

Manager UI 对非认证接口的 401 合并并发刷新、每个请求最多重试一次；失败后跳转登录。认证接口自身的 401 不递归刷新，403 不触发刷新。其他子系统跳转 Manager 的登录入口是接入约定，仓库当前只实现 Manager UI。

## 2. 外部 OAuth2/OIDC

`AuthorizationServerConfiguration` 启用 Spring Authorization Server 的 OAuth2 和 OIDC 端点，包含授权、token、jwks、revoke、introspect 及发现端点；路径基于 Manager Service 的 context-path。授权码登录使用认证中心自己的表单登录与 Spring Session JDBC。

配置驱动的 RegisteredClient 由 Spring Boot 装配，默认客户端支持 authorization_code、refresh_token 和 openid/profile；配置 map key 作为记录 ID，已持久化授权依赖这个 ID。没有数据库 registered-client 表。

网关保留 `BrowserOAuth2Configuration` 外部客户端适配，仅在 `baosys.browser-oauth2.enabled=true` 时启用，并需要配置 Spring OAuth2 客户端注册。当前 Manager Web 默认没有启用该开关或提供 `spring.security.oauth2.client` 注册，因此内部登录不走网关授权码回调或 WebSession。

## 3. JWT 契约

内部 `SharedLoginService` 与外部 OAuth2 customizer 签发同一业务 Access Token 契约：

| 项目 | 当前含义 |
|---|---|
| alg / typ / kid | RS256 / at+jwt / 外部 JWK 的非空 key ID |
| iss | AuthorizationServerSettings 中的 issuer |
| sub | `user_login.id`，不是账户 ID |
| aud | 包含 `def` |
| client_id | 注册客户端标识，内部为 `baosys-web` |
| iat、exp、jti | 签发时间、到期时间、令牌 ID |
| permissions | 当前目录过滤后的权限字符串列表，空列表合法 |

内部不写 scope，外部 customizer 显式移除框架预填的 scope；没有 system_type/systemType。权限转换仅使用 permissions。OIDC 的 openid/profile 是协议 scope；ID Token 的 sub 保持稳定账户 ID，不等于业务 Access Token 的 sub。

公共 `JwtVerification` 组合 Spring 标准校验器与平台契约校验，检查 issuer、audience、时效、at+jwt 类型、必需声明、非空 kid 和字符串列表 permissions；算法及签名由 decoder 校验。它不逐个查询目录或数据库。

签名密钥文件必须包含且仅包含一个私有 RSA 签名密钥，可附带旧公钥；所有 key 必须具有唯一非空 kid、至少 2048 位、use=sig、alg=RS256。当前没有权限长度预算或签发后 JWT 字符数上限检查。

## 4. 授权存储和刷新

`AccountAuthorizationService` 委托 `JdbcOAuth2AuthorizationService` 保存令牌并维护 `user_login` 投影。投影保存账户、客户端、授权 ID、登录描述、到期时间，以及可选的认证中心 Session ID；不保存自建刷新摘要。

| 行为 | 内部共享登录 | 外部 OAuth2 |
|---|---|---|
| 刷新入口 | browser-auth/refresh | 官方 token 端点 |
| 刷新实现 | SharedLoginService 生成新随机 Refresh Token | 官方 token generator/provider |
| 刷新期限 | 保留初次登录的绝对到期时间 | 按 TokenSettings，新令牌可获得新的有效期 |
| Access 到期 | 不晚于当前 Refresh 到期时间 | 按官方 TokenSettings |
| 默认期限 | Access 30m、Refresh 30d | Access 30m、Refresh 30d，reuse-refresh-tokens=false |

授权查询时锁定账户并重新读取令牌，避免在等待锁期间继续使用已轮换或删除的授权。共享服务事务覆盖登录/刷新/撤销；官方 POST `/oauth2/token` 和 `/oauth2/revoke` 由 `AuthorizationTransactionFilter` 包裹事务。

旧 Refresh Token 轮换后不可再用。内部无效刷新返回业务 401（AUTH_401_001）；外部 token 端点按 OAuth2 返回 invalid_grant，不能把两类错误格式混用。

## 5. 撤销与有效性边界

用户名、密码、权限、启停变化或删除账户会通过数据库触发器清理相关授权和认证中心 Session。单客户端下线、全部下线、空闲清理删除 `user_login` 时，触发器同步删除对应授权和关联 Session；删除授权也通过外键级联删除登录记录。

内部退出仅撤销当前 Refresh Cookie 对应的内部授权并清除 Cookie。外部认证中心 Session 和网关 OAuth2 WebSession 属于另一条登录流程，不能将内部退出描述为“清除网关本地会话”。

`cleanup_inactive_user_login_sessions()` 删除 updated_at 超过一天未变更的记录，需要部署方调度调用。普通业务 API 请求不会更新此时间，授权保存/刷新会更新。

撤销立即阻止后续刷新；`/users/me` 以及需要解析登录记录的操作也随记录删除失效。其他仅离线校验 JWT 的接口仍可能接受已签发令牌直到过期，jti 不是黑名单或防重放机制。

## 6. 关键配置

配置文件见 [Manager application.yml](../../../baosys-manager/baosys-manager-service/src/main/resources/application.yml) 和 [Manager Web application.yml](../../../baosys-manager-web/src/main/resources/application.yml)。

| 配置 | 用途 |
|---|---|
| spring.security.oauth2.authorizationserver | issuer、客户端、令牌期限 |
| spring.security.oauth2.resourceserver.jwt | 验签 issuer、JWKS、算法与受众 |
| baosys.authorization.jwk-set-location | 持久化 JWK 文件，环境变量 JWT_JWK_SET_LOCATION |
| baosys.shared-login.manager-origin | Manager 公开来源，默认 http://localhost:8080 |
| baosys.shared-login.allowed-origins | 允许登录后返回的同主机、同协议来源列表 |
| baosys.shared-login.secure-cookie | Secure Cookie 开关，默认 false |
| spring.security.gateway.access-rules | 有序路径授权规则 |
| spring.security.gateway.permit-all-paths | 公开入口路径 |

部署必须显式统一 JWT_ISSUER：授权服务器和网关默认 `baosys-manager`，Service 资源服务器默认 `http://localhost:9090/baosys-manager`，两者当前不一致。还需提供 OAUTH2_CLIENT_SECRET_HASH 和签名 JWK，并使 JWKS URL 对验签服务可达。

详细表结构见 [数据库设计](database_design.md)，授权规则见 [权限模型](permission_model.md)。
