# 认证与会话

内部浏览器使用同域共享 HttpOnly Token Cookie，Manager 提供统一账号密码登录页；外部 OAuth2 协议继续由 Spring Authorization Server 提供。账户 CRUD、多子系统权限、登录记录和审计由 Manager 维护。部署及子系统接入见 [同域共享登录](../../deployment/shared_login.md)。

## 登录和退出

1. 子系统 API 返回 401 时，前端整页跳转 Manager `/login?redirect_url=...`，403 不触发重新登录。
2. Manager Vue 表单向 `/api/baosys-manager/v1/browser-auth/login` 提交账号密码和返回地址；后端校验同主机、同协议及允许端口。
3. 登录成功设置共享 `BAOSYS_ACCESS_TOKEN` / `BAOSYS_REFRESH_TOKEN` HttpOnly Cookie，返回校验后的原地址；无参数进入用户中心。内部不使用 PKCE 或独立网关 Session。
4. 各网关从共享 Cookie 校验 Access JWT、执行目标系统权限检查，并向后端转发 Bearer。显式 Authorization 头优先。
5. Manager 在 API 401 时尝试一次统一刷新接口；刷新令牌轮换，失败转登录。退出撤销授权并清除 Cookie。
6. 写请求先 GET `/csrf` 获取掩码令牌；认证接口还校验精确的 Manager Origin。登录成功替换此浏览器旧内部授权，各端口后续请求使用新账户凭证。

认证中心还提供标准 token、jwks、revoke、introspect 和 OIDC 元数据端点。旧 `/v1/auth/*` 已删除。外部系统不得继续调用旧接口。

## JWT 与授权

Access JWT 使用 RS256、typ=at+jwt、JWK 文件中的 kid；iss 为认证中心 URL；sub 为 user_login.id；aud=def。内部登录由 SharedLoginService 按同一契约生成 claims 并调用 Spring JwtEncoder 签名，client_id 使用内部注册记录 baosys-web。外部 OAuth2 令牌由官方生成器及业务 customizer 签发。

业务权限仅通过 permissions 表达，来自账户有效授权；Access Token 不再签发 scope，也不校验它与 permissions 的关系。没有 system_type/systemType。OIDC 请求使用 openid/profile；ID Token 的 sub 为稳定账户 ID，与业务 Access Token 的登录记录 ID 不同。这是兼容现有账户模型的明确扩展，并非 SAS 默认声明内容；客户端不得把 Access JWT 的 sub 当成 OIDC 用户身份。

网关按路由使用 hasAuthority 检查 MGR 等系统入口，后端验证 JWT 并用 @PreAuthorize 检查具体 API 权限，不再统一检查子系统入口。网关的 spring.security.gateway.access-rules 中，permission 未配置、空字符串或仅含空白字符时只要求登录，适用于全局共享接口；有值时检查对应系统入口权限。规则按顺序首次匹配，共享路径须放在子系统通配规则之前。目录中接口权限全局唯一。JWT 校验继续组合 Spring 标准验证器，业务校验只补充权限契约等要求。

## 持久化和撤销

官方 JdbcOAuth2AuthorizationService 存储内部共享登录与外部 OAuth2 的授权状态及令牌；内部使用独立 grant 类型，登录记录复用现有生命周期。Spring Session JDBC 仅用于外部 OAuth2 认证中心登录会话。配置驱动 RegisteredClient 使用 Spring Boot 自动装配，配置 map key 作为稳定记录 ID，不得随意更名。

user_login 提供数字登录 ID、账户、客户端、登录时间和到期时间，authorization_id 关联授权；authentication_session_id 仅在外部认证中心存在登录会话时填写。它不存储自建刷新摘要。业务适配器维护该投影，令牌序列化由官方 JDBC 服务处理；内部刷新由 SharedLoginService 轮换，外部 OAuth2 刷新由官方组件处理。

修改用户名、密码、启停状态、权限或删除账户会撤销相关授权及认证中心会话。踢下线和空闲会话清理通过数据库触发器同步删除对应授权，防止遗留刷新令牌继续签发。令牌端点事务与账户行锁串行化刷新/禁用/踢下线，重复使用旧刷新令牌返回 invalid_grant。

内部刷新每次旋转随机令牌，保持初次登录时基于 TokenSettings 计算的绝对到期时间；外部 OAuth2 刷新遵循官方 TokenSettings，reuse-refresh-tokens=false 时新 Refresh Token 获得新的有效期。user_login 同步各自到期时间。空闲超过一天的清理函数保留，需要部署环境定期调用。

撤销刷新授权不会让已发出的 JWT 在所有离线验签服务立即失效：依赖 user_login 的身份查询立即失效，其余 API 最长仍可接受到 Access Token 过期。jti 不是黑名单，也不意味着防重放。

内部网关使用共享 JWT Cookie，不依赖本机 WebSession。授权记录跨重启保留；数据库包含令牌，须限制读取权限及保护备份。撤销立即阻止刷新；离线 JWT 校验仍受 Access Token 剩余有效期限制。

## 配置

参见 [共享登录配置](../../deployment/shared_login.md)。客户端注册、issuer 和期限使用 spring.security.oauth2.authorizationserver；验签使用 spring.security.oauth2.resourceserver.jwt；共享入口、允许返回的 Origin 和 Cookie 安全设置使用 baosys.shared-login。内部网关不再需要 spring.security.oauth2.client；外部 OAuth2 适配器按需启用。
