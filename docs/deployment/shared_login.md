# 同域共享 Manager 登录

内部子系统使用同一域名或同一 IP、相同协议，可以使用不同端口。所有这些端口必须由可信系统管理：Cookie 不按端口隔离。内部登录不使用 OAuth2 网关会话或 PKCE；外部 OAuth2 Authorization Server 接口保留，未来可为外部注册客户端单独启用 PKCE。

## 配置

Manager Service 配置：

```text
MANAGER_PUBLIC_ORIGIN=http://example.com:8000
SHARED_LOGIN_ALLOWED_ORIGINS=http://example.com:8001,http://example.com:8002
SHARED_LOGIN_SECURE_COOKIE=false
```

生产 HTTPS 设置 `SHARED_LOGIN_SECURE_COOKIE=true`，所有入口统一使用 HTTPS。Origin 不带路径或末尾斜杠。Manager 自身 Origin 自动允许，其他端口必须显式登记。不同域名、子域名、IP/域名混用及 HTTP/HTTPS 混用不属于共享范围。默认 Manager Origin 为 `http://localhost:8080`。

Manager 网关不再需要 OAuth2 Client 的密钥或注册配置。认证中心仍使用 `baosys-web` 注册记录的令牌期限和现有 JWT 签名配置；`OAUTH2_CLIENT_SECRET_HASH` 是外部 OAuth2 配置的一部分，不能把客户端密钥放入前端。无需新增数据库迁移，内部授权复用初始化脚本中的授权表与登录记录。

Vite 直接开发时，设置后端 `MANAGER_PUBLIC_ORIGIN=http://localhost:5173`，因为浏览器提交的 Origin 是 5173；`/api`、`/csrf` 通过 Vite 代理。更改该配置需重启后端。

## 登录流程

1. 子系统 API 无有效凭证时返回 401；前端整页跳到 `MANAGER_PUBLIC_ORIGIN/login?redirect_url=<编码后的当前页面完整 URL>`。403 表示无权限，不重定向。
2. Manager Vue 页面提交账号密码到 `POST /api/baosys-manager/v1/browser-auth/login`。后端校验返回地址，再验证账号，撤销此浏览器旧刷新凭证对应的内部授权并创建新登录。
3. 响应设置 `BAOSYS_ACCESS_TOKEN` 和 `BAOSYS_REFRESH_TOKEN`，均为 HttpOnly、SameSite=Lax、host-only；不设置 Domain。Access Cookie 路径为 `/`，Refresh Cookie 仅发送到 `/api/baosys-manager/v1/browser-auth`。
4. 响应 JSON 只有校验后的 `data.redirectUrl`，没有 Token。前端整页返回该地址，路径、query 和 hash 保留。无地址默认进入 Manager `/users-management`。非法地址返回 400，不签发新凭证。
5. 子系统网关读取共享 Access Cookie，校验 JWT 和系统权限，再把已认证 JWT 转发为 Bearer。显式 Authorization 头优先，错误的头不回退到 Cookie。

子系统前端 401 处理示例（替换为部署配置）：

```ts
let redirecting = false
axiosInstance.interceptors.response.use(response => response, error => {
  if (error.response?.status === 401 && !redirecting) {
    redirecting = true
    const login = new URL('http://example.com:8000/login')
    login.searchParams.set('redirect_url', window.location.href)
    window.location.assign(login.href)
  }
  return Promise.reject(error)
})
```

Manager 前端提供 `src/utils/loginRedirect.ts`；复制到其他子系统时通过 `VITE_MANAGER_ORIGIN` 指定同一 Manager Origin。不要对 Axios 的 API 401 改发 302，302 无法可靠触发顶层页面导航。普通前端路由仍是公开的 SPA 入口，由 API 认证和前端守卫决定是否进入登录页。

## 刷新、退出与 CSRF

- Manager 在 API 401 后最多尝试一次 `POST /api/baosys-manager/v1/browser-auth/refresh`，成功后重试原请求。并发请求共用一次刷新；失败进入登录页。其他子系统使用上面的跳转流程，不直接跨端口调用刷新接口。
- Refresh Token 每次旋转；旧值不能重放；会话保持初次登录时的刷新到期时间，不无限续期。
- 退出调用 `POST /api/baosys-manager/v1/browser-auth/logout`，撤销内部授权并清除两个 Cookie。再次登录覆盖共享 Cookie，所有端口后续请求采用新账户。
- 三个接口均使用 JSON，并在网关执行 Spring CSRF 校验：先同源 GET `/csrf`，提交响应中的 `headerName` / `token`。公开认证接口不会被过期 Access Cookie 拦截，也不能用 Bearer 头绕过 CSRF。
- Manager Service 额外校验写请求的 `Origin` 必须精确等于 `MANAGER_PUBLIC_ORIGIN`，拒绝缺失 Origin 或其他端口的直接调用；网关必须保留 Origin。无需开放跨域 CORS。
- Manager 页面切回可见时重查当前用户；账户变化时重新加载页面。其他子系统应同样重新获取账户及权限，避免继续展示旧账户数据。

撤销授权会立即使刷新凭证失效，并删除对应登录记录；纯离线 JWT 网关不会自动获知数据库撤销，被复制的旧 Access Token 最长仍可使用至过期。需要即时全系统撤销时，应另接在线状态校验或撤销分发。本次保持现有 JWT 校验语义，浏览器正常请求会使用覆盖后的 Cookie。

## 子系统接入和迁移

升级 `baosys-common-security-gateway`，启用 `@EnableAuthenticationCheck`，配置相同 issuer、JWKS、公钥算法和 audience，以及该子系统的访问权限规则。子系统不再需要内部 OAuth2 Client 注册、回调和各自的 SESSION。Manager 网关放行 `/api/baosys-manager/v1/browser-auth/**`，其他子系统不要暴露该路由。

旧 SESSION 不再作为内部认证凭证，部署后用户需要重新登录。旧授权记录按原过期/清理机制处理。可选旧 OAuth2 浏览器网关适配器仅在显式设置 `baosys.browser-oauth2.enabled=true` 并提供完整 OAuth2 Client 注册时加载；内部共享部署不要开启该选项。
