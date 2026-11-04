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

登录校验用户名密码及 enabled；用户名先 trim。密码校验由 AccountAuthenticator 在事务外完成；BrowserLoginTransaction 按新旧账户 ID 升序加锁，复查用户名、密码摘要和启用状态，再撤销仍匹配旧 Refresh Cookie 的内部授权并创建新授权及登录记录。失败时整个替换事务回滚。授权类型为 `urn:baosys:grant:shared-login`，使用已注册的 `baosys-web` 客户端设置。

普通返回地址由 `SharedLoginProperties` 校验：必须同主机、同协议，来源为 manager-origin 或 allowed-origins 中的值；拒绝用户信息、登录/API/授权路径等不适合的目标。`SharedLoginNavigation` 仅对配置的 issuer + authorization endpoint 提供精确例外，以恢复 OAuth 授权请求；该地址也必须与登录页同主机、同协议，才能共享 Cookie，不允许片段或用户信息。未传地址时默认返回 `{manager-origin}/users-management`。

| Cookie | Path | 属性 |
|---|---|---|
| BAOSYS_ACCESS_TOKEN | `/` | HttpOnly、SameSite=Lax |
| BAOSYS_REFRESH_TOKEN | `/api/baosys-manager/v1/browser-auth` | HttpOnly、SameSite=Lax |

不设置 Domain，因此 Cookie 在同一主机的不同端口共享，不跨子域共享。Secure 由配置控制；manager-origin 为 HTTPS 时要求 secure-cookie=true。响应使用 Cache-Control: no-store。

网关优先读取显式 Authorization 头，否则读取 Access Cookie；无效的显式头不会回退到 Cookie。公开的共享认证端点不依赖旧 Access JWT，避免过期 JWT 阻止刷新。

浏览器写请求先取得 `/csrf` 的 token 并放入指定请求头。网关负责 CSRF 校验；直连 Service 的共享认证写请求仍须具有与 manager-origin 精确相同的 Origin。Service 该链本身为无状态且关闭 CSRF。网关仅对受保护 API 中显式 Bearer 请求豁免 CSRF，公开登录/刷新/退出不能据此豁免。

Manager UI 对非认证接口的 401 合并并发刷新，每个请求最多重试一次。authCoordinator 使用同源 Web Locks 串行协调登录、刷新和退出，并以非敏感 accountEpoch/refreshRevision 标记识别其他标签页已经完成的刷新；消息通知不携带凭据。旧账户请求在账户切换后不自动重放，其迟到响应也不会覆盖新账户状态。

刷新明确返回 401 才通知登录失效；网络错误、5xx 和锁等待超时保留登录状态。退出成功后才清理前端状态和通知其他标签页。认证接口自身不递归刷新，403 不触发刷新。无 Web Locks 或存储不可用时降级为页内协调；跨端口不共享协调锁。其他子系统继续跳转 Manager 管理登录。

## 2. 外部 OAuth2/OIDC

`AuthorizationServerConfiguration` 启用 Spring Authorization Server 的 OAuth2 和 OIDC 端点，包含授权、token、jwks、revoke、introspect 及发现端点；路径基于 Manager Service 的 context-path。未登录的浏览器进入同一张 Vue 登录页，不再生成 Spring 默认表单。页面通过 `/browser-auth/login` 调用 `BrowserAuthAppService`，登录成功携带新的内部 Access Cookie 返回授权端点，继续原授权码/PKCE 请求。

授权端点通过 `SharedLoginAuthorizationFilter` 识别内部登录：优先验证网关转发的 Bearer Token，无 Authorization 头时读取共享 Access Cookie。过滤器复用框架的 `BearerTokenAuthenticationFilter` 和 `JwtAuthenticationProvider` 完成验签、认证上下文管理及错误响应；`InternalLoginAuthenticator` 负责检查登录记录未过期、账户启用、授权属于内部 shared-login，并将 JWT 的登录记录 ID 转换为账户 ID。有效内部登录无需再次输入密码；该身份不另存 Spring Session，每次仍检查原登录记录。无效令牌不回退到 Cookie：HTML 浏览器请求跳转到共用 Vue 登录页，非 HTML 请求仍返回 401；没有认证状态也跳转到同一页面。跳转通过 redirect_url 保留原 client_id、redirect_uri、state、nonce 和 PKCE 参数，登录后的恢复地址只允许配置中的授权端点。第三方 OAuth Access Token 不作为认证中心登录凭据，避免提升其授权范围。token/revoke 等协议端点不经过此转换，仍执行原客户端认证。

PKCE 按客户端配置，默认不强制；携带 PKCE 时仍由框架校验 verifier。内部始终使用用户名密码登录，外部授权码流程按接入需要使用。

配置驱动的 RegisteredClient 由 Spring Boot 装配，默认客户端支持 authorization_code、refresh_token 和 openid/profile；配置 map key 作为记录 ID，已持久化授权依赖这个 ID。没有数据库 registered-client 表。

网关保留 `BrowserOAuth2Configuration` 外部客户端适配，仅在 `baosys.browser-oauth2.enabled=true` 时启用，并需要配置 Spring OAuth2 客户端注册。当前 Manager Web 默认没有启用该开关或提供 `spring.security.oauth2.client` 注册，因此内部登录不走网关授权码回调或 WebSession。

## 3. JWT 契约

内部应用服务委托 `BrowserLoginTransaction`，与外部 OAuth2 的 `AccountTokenGenerator` 均调用 common-security-core 的 `TokenIssuer` 签发 Access/Refresh Token。`AccountTokenSubjectResolver` 在 Manager 中统一查询账户、登录记录和最新权限；Core 不依赖 Manager 或授权服务器类型。业务 Access Token 契约如下：

| 项目 | 当前含义 |
|---|---|
| alg / typ / kid | RS256 / at+jwt / 外部 JWK 的非空 key ID |
| iss | AuthorizationServerSettings 中的 issuer |
| sub | `user_login.id`，不是账户 ID |
| aud | 包含 `def` |
| client_id | 注册客户端标识，内部为 `baosys-web` |
| iat、exp、jti | 签发时间、到期时间、令牌 ID |
| permissions | 当前目录过滤后的权限字符串列表，空列表合法 |

公共签发器不写 scope；没有 system_type/systemType。权限转换仅使用 permissions。OIDC ID Token 仍由框架 JwtGenerator 签发，openid/profile 是协议 scope；ID Token 的 sub 保持稳定账户 ID，不等于业务 Access Token 的 sub。

公共 `JwtVerification` 组合 Spring 标准校验器与平台契约校验，检查 issuer、audience、时效、at+jwt 类型、必需声明、非空 kid 和字符串列表 permissions；算法及签名由 decoder 校验。它不逐个查询目录或数据库。

签名密钥文件必须包含且仅包含一个私有 RSA 签名密钥，可附带旧公钥；所有 key 必须具有唯一非空 kid、至少 2048 位、use=sig、alg=RS256。当前没有权限长度预算或签发后 JWT 字符数上限检查。

## 4. 授权存储和刷新

`AccountAuthorizationService` 委托 `JdbcOAuth2AuthorizationService` 保存令牌并维护 `user_login` 投影。投影保存账户、客户端、授权 ID、登录描述、到期时间，以及可选的认证中心 Session ID；不保存自建刷新摘要。

| 行为 | 内部共享登录 | 外部 OAuth2 |
|---|---|---|
| 刷新入口 | browser-auth/refresh | 官方 token 端点 |
| 刷新实现 | BrowserAuthAppService 经 BrowserLoginTransaction 调用公共 TokenIssuer | 框架 provider 经 AccountTokenGenerator 调用同一个 TokenIssuer |
| 刷新期限 | 保留初次登录的绝对到期时间 | 按 TokenSettings，新令牌可获得新的有效期 |
| Access 到期 | 不晚于当前 Refresh 到期时间 | 按官方 TokenSettings |
| 默认期限 | Access 30m、Refresh 30d | Access 30m、Refresh 30d，reuse-refresh-tokens=false |

授权查询时锁定账户并重新读取令牌，避免在等待锁期间继续使用已轮换或删除的授权。BrowserLoginTransaction 的事务覆盖登录完成/刷新/撤销，密码校验不持有事务；官方 POST `/oauth2/token` 和 `/oauth2/revoke` 由 `AuthorizationTransactionFilter` 包裹事务。

旧 Refresh Token 轮换后不可再用。内部无效刷新返回业务 401（AUTH_401_001）；外部 token 端点按 OAuth2 返回 invalid_grant，不能把两类错误格式混用。

## 5. 撤销与有效性边界

用户名、密码、权限、启停变化或删除账户会通过数据库触发器清理相关授权和认证中心 Session。单客户端下线、全部下线、空闲清理删除 `user_login` 时，触发器同步删除对应授权和关联 Session；删除授权也通过外键级联删除登录记录。

内部退出仅撤销当前 Refresh Cookie 对应的内部授权并清除 Cookie。外部认证中心 Session 和网关 OAuth2 WebSession 属于另一条登录流程，不能将内部退出描述为“清除网关本地会话”。

`cleanup_inactive_user_login_sessions()` 只删除 expires_at 已到期记录。每次按账户 ID 顺序锁定最多 100 个账户，跳过忙碌账户，锁内复查到期条件并删除；推荐每五分钟调度一次。updated_at 仅表示记录变更时间，不作为空闲超时。一日未访问不会提前撤销仍在绝对有效期内的内部登录。

OAuth 投影有 Refresh Token 时使用其到期时间；尚未兑换的授权按授权码期限，无 Refresh Token 的已兑换授权保留到 Access/ID Token 中较晚的到期时间。清理不代替请求时的有效性校验。

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

两端 JWT_ISSUER 默认统一为 `http://localhost:9090/baosys-manager`；覆盖时保持一致。必须提供签名 JWK，正式部署覆盖 OAUTH2_CLIENT_SECRET_HASH，并使 JWKS URL 对验签服务可达。

详细表结构见 [数据库设计](database_design.md)，授权规则见 [权限模型](permission_model.md)。
