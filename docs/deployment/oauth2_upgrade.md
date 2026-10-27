# OAuth2 与多子系统账户部署指南

## 数据库初始化

当前只保留 V1__init_manager.sql，一次创建最终的账户、登录记录、权限审计、OAuth2 授权和 Spring Session 表，以及清理函数和授权撤销触发器。用户名全局唯一，权限与权限审计使用 TEXT；不再创建旧 system_type、refresh_token_hash 字段，也不再执行旧权限转换和会话清空步骤。

该脚本仅用于空数据库或新的 schema。已执行旧版 V1–V5 的数据库不能直接使用新的迁移历史，Flyway 校验会出现校验和不匹配或缺失迁移；不要通过 repair 或删除历史表直接绕过。需要保留数据时，应先备份并制定独立的数据导入方案，旧权限须明确映射到当前契约。开发环境可使用新的空 schema 初始化。

当前系统入口使用纯大写缩写：MGR、SIM、SMS；接口权限为 <系统缩写>:<资源>:<操作>，例如 MGR:user:list。部署时同步更新外部 baosys-systems.yml、网关路由权限、接口鉴权及前端引用。

内部浏览器登录使用 [同域共享 Manager 登录](shared_login.md)，外部 OAuth2 Authorization Server 能力保留。

## 密钥与客户端配置

在仓库外生成持久化 RSA JWKSet JSON，设置 JWT_JWK_SET_LOCATION=file:/绝对路径/signing-jwks.json。文件至少包含一个完整私有 RSA JWK（含 n/e/d/p/q/dp/dq/qi），alg=RS256、use=sig、唯一 kid，RSA 至少 2048 位。工具示例：

```powershell
node scripts/generate-signing-key.cjs D:/baosys/keys/signing-jwks.json
$env:JWT_JWK_SET_LOCATION = 'file:D:/baosys/keys/signing-jwks.json'
```

也可仅使用 JDK 17 的 JShell 生成同格式的密钥文件（二选一）：

```powershell
jshell '-R-Dsigning.key.output=D:/baosys/keys/signing-jwks.json' scripts/generate-signing-key.jsh
```

JShell 通过 JVM 系统属性接收输出路径；两个脚本均生成 3072 位 RSA 私钥，使用相同的 JWK 字段和 SHA-256 kid 算法，并拒绝覆盖已有文件。

工具拒绝覆盖已有文件；不要每次启动重新生成密钥，不得使用仓库测试密钥上线。只保留一个活动私钥，轮换期旧密钥以公钥形式留在文件中。标准 NimbusJwtEncoder 选择活动私钥，SAS JWKS 仅发布公钥；旧公钥至少保留至已签发令牌过期并覆盖时钟容差。kid 直接取 JWK，不再配置独立 key-id。

设置网关 OAUTH2_CLIENT_SECRET 为高熵随机密钥，认证中心 OAUTH2_CLIENT_SECRET_HASH 为其 Spring PasswordEncoder 编码值（推荐 {bcrypt} 前缀）。二者须匹配；生产不要使用测试中的 {noop}。客户端注册使用标准 spring.security.oauth2.authorizationserver.client.baosys-web，网关使用 spring.security.oauth2.client.registration.baosys。

JWT_ISSUER 默认 http://localhost:9090/baosys-manager，生产应设置为浏览器及网关均可访问的 HTTPS 认证中心地址。JWT_JWK_SET_URI 默认该服务 /oauth2/jwks；OAUTH2_REDIRECT_URI 默认 http://localhost:8080/login/oauth2/code/baosys，认证中心白名单与网关回调必须完全一致。反向代理部署应正确配置 Spring 的 forwarded headers，不信任任意外部伪造代理头。

本地 Vite 开发时两端 OAUTH2_REDIRECT_URI 均设为 http://localhost:5173/login/oauth2/code/baosys；Vite 已代理 OAuth2、回调、logout、csrf 和 API 路径。统一使用 localhost，避免不同主机名造成会话 Cookie 不一致。

令牌期限使用标准 token.access-token-time-to-live（默认 30m）、refresh-token-time-to-live（30d）、reuse-refresh-tokens（false）。官方刷新轮换会重新计算刷新有效期；user_login 到期时间同步更新。会话 Cookie、超时及 HTTPS 属性用 Spring Boot server.servlet.session / server.reactive.session / spring.session 标准配置。

已删除 JWT_PRIVATE_KEY、JWT_PUBLIC_KEY、JWT_KEY_ID、AUTH_CLIENT_ID、AUTH_COOKIE_*、AUTH_ACCESS_TOKEN_COOKIE_*、AUTH_REFRESH_TOKEN_COOKIE_*、REFRESH_TOKEN_EXPIRE_DAYS、ACCESS_TOKEN_EXPIRE_SECONDS 及 security.jwt.*。清理部署平台中的旧值。

## 授权与验证

账户仍以 permissions 数组同时配置多个 MGR、SIM 等系统入口及各系统细粒度权限；目录编码全局唯一，接口权限必须伴随所属系统入口。网关用路由 hasAuthority、后端用 @PreAuthorize；共享 /users/me、/systems 只要求登录。每个子系统使用自己的 API 网关，Manager Web 不再预留其他子系统的路由。

标准资源服务器配置仍为 spring.security.oauth2.resourceserver.jwt 下的 issuer-uri、jwk-set-uri、jws-algorithms=[RS256]、audiences=[def]。离线 public-key-location 与 jwk-set-uri 不能同时启用。项目补充 at+jwt 及业务声明验证。详见 [认证与会话](../modules/manager/auth_and_session.md)。

CSRF 使用 Spring Cookie 仓库与 XOR 掩码，前端从同源 /csrf 获取；只对显式 Bearer API 豁免，浏览器退出不能绕过。安全错误采用标准 401/403，不要求业务 JSON 包装。

运行验证：

```powershell
mvn '-Dskip.frontend=true' -Pintegration-tests clean verify
cd baosys-manager-ui
npm ci
npm run build
```

集成测试使用 Docker 隔离 PostgreSQL，不修改开发库。skip.frontend 仅跳过前端工具下载；发布前须确保 dist 对应最新代码。

历史验证（2026-10-15）：干净构建通过，48 项单元测试、32 项集成测试，失败/错误/跳过均为 0；前端构建通过。当时覆盖授权码 + PKCE、JWKS、公私钥轮换选择、刷新轮换与并发、账户撤销、网关自动续期与退出、CSRF 和多系统权限。

2026-10-19 调整：内部登录已改成 [同域共享 Token Cookie](shared_login.md)，不依赖各网关 OAuth2 Session，也不启用 PKCE。外部 OAuth2 能力保留，baosys-web 的 require-proof-key 当前为 false；未来外部系统需要 PKCE 时按客户端单独配置。上述历史测试结果不代表本次调整后的验证结果。

官方参考：[Authorization Server 核心组件](https://docs.spring.io/spring-authorization-server/reference/core-model-components.html)、[PKCE 与 BFF](https://docs.spring.io/spring-authorization-server/reference/guides/how-to-pkce.html)。依赖版本跟随当前 Spring Boot BOM，未单独强行升级框架版本。
