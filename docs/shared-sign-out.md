# 统一登出流程规范

所有子系统的退出按钮只导航到 Manager 的 `/baosys-manager/#/sign-out`，不在子系统内发送登出请求。Manager 自身的退出按钮也使用此入口。

本文规定内部共享 Cookie 认证的登出流程，现有及新增子系统均须遵循。登录入口仍为 `/baosys-manager/#/login`；`/baosys-manager/#/sign-out` 是登出处理页面，两者不得混用。

## 标准流程

1. 用户在任意子系统或 Manager 点击“退出登录”。发起页面必须先导航到配置的 Manager 完整基址下的 `sign-out` 页面，不得先调用登出接口再跳转，也不得只跳转登录页代替登出。
2. Manager 退出页作为公开路由加载，无须先恢复用户身份。组件挂载后自动执行登出，不要求用户再次点击确认；GET 页面导航本身不执行服务端撤销操作。
3. 退出页通过 Manager 的认证请求层取得同源认证锁，并请求 `GET /api/baosys-manager/csrf`，使用返回的 `headerName` 与 `token` 准备 CSRF 请求头。
4. 退出页向同源 `POST /api/baosys-manager/v1/browser-auth/logout` 提交空 JSON 对象 `{}`，携带浏览器 Cookie。开启网关 CSRF（`CSRF_ENABLED=true`）时由网关统一校验 CSRF，内网 Manager Service 不重复校验请求 Origin；不得为退出流程单独绕过或削弱网关校验。
5. 服务端撤销当前 Refresh Cookie 对应的内部授权；成功响应通过 `Set-Cookie` 将两个登录 Token Cookie 的 `Max-Age` 设为 `0`。JavaScript 不读取或自行删除 HttpOnly Token Cookie。
6. 退出页收到成功响应后，更新认证版本、通知同源标签页并清除前端用户快照，显示完成状态。随后使用路由替换进入 Manager 登录页，避免普通返回操作直接回到刚完成的退出页。当前实现展示完成状态约 1.2 秒，也可点击“前往登录页面”立即进入。

## 请求次数与失败处理

- 每次退出页挂载自动启动一次登出流程；一次流程先获取 CSRF，成功后至多发送一次 POST 登出请求。子系统不得重复发送该请求。
- 页内正在处理或已成功时，不得并发重复执行。刷新或重新打开退出页会启动新的流程，因此“请求一次”不代表跨刷新、跨标签页的全局恰好一次。
- CSRF 获取失败时不得继续发送 POST。任何阶段出现网络错误、403 或服务端错误，均停留在退出页显示“退出尚未完成”及重试入口，不标记清理成功，也不自动进入登录页。
- 只在用户点击重试后重新获取 CSRF 并尝试登出，不自动循环重试。认证接口的失败不得触发业务请求层的令牌刷新。
- Refresh Cookie 缺失或对应内部授权已不存在时，服务端仍返回成功并清除登录 Cookie；退出页面不得依赖当前 Access Token 有效。
- 登录后返回目标可以通过 `redirect_url` 保留，退出页只把它传给登录页；不得把它当作退出成功后的直接跳转地址。

## 页面职责与安全边界

页面展示“结束登录会话、清除登录凭据、返回登录页面”三个状态。前两个步骤由同一次服务端登出响应确认，不模拟独立的服务端进度；失败时显示“尚未确认”。

此流程清理当前浏览器的两个共享登录 Token Cookie，并撤销对应内部授权，不清除所有站点数据，不退出该用户的全部设备，也不撤销外部 OAuth 授权。已被复制且仅通过离线 JWT 校验的 Access Token 仍可能被接受直至到期，不能把 Cookie 清理描述为所有已签发 JWT 立即失效。

需要清除的 Cookie 为 `BAOSYS_ACCESS_TOKEN`（Path=`/`）和 `BAOSYS_REFRESH_TOKEN`（Path=`/api/baosys-manager/v1/browser-auth`）。设置和清除时的名称、域与路径范围必须一致。

## 新子系统（例如 8082）

在所有前端构建环境中配置相同的 Manager 完整基址，例如：

```dotenv
VITE_MANAGER_UI_BASE_URL=http://localhost:8080/baosys-manager/
```

Manager Service 的 `MANAGER_PUBLIC_ORIGIN` 必须对应这个公开来源（本例为 `http://localhost:8080`），系统目录中的 Manager URL 也需与其一致。这些 Vite 配置在构建时生效，修改后需重新构建部署。不要混用 `localhost` 与 `127.0.0.1`。

8081、8082、8083 的退出按钮均导航到上述固定基址下的 `sign-out`；浏览器先完成页面导航，之后 CSRF 与 POST 登出请求都由 Manager 页面在 Manager 来源内发起，不需要跨域 AJAX 或子系统的 CSRF 别名。使用相同主机和协议，不同端口可共享符合路径条件的登录 Cookie。不同域名不在此方案范围内。

可以携带 `redirect_url` 保留登录后返回的子系统地址。退出页只将该参数交给登录页，真正登录后的目标由服务端验证，不直接按任意 URL 跳转。

两套前端的 `.env.production` 默认配置 `http://localhost:8080/baosys-manager/`，与服务端及启动脚本默认值一致，生产构建不再落到当前业务端口的 Manager 代理页面。非 localhost 部署必须通过构建环境覆盖这个基址，并同步服务端公开来源和 MGR 目录；不要只修改其中一项。

开发模式未配置完整基址时仍使用同源 `/baosys-manager/`。若采用统一反向代理或其他公开端口，必须明确配置所有前端和服务端使用同一个公开 Manager 来源。

跨端口页面不共享 BroadcastChannel 或 Web Locks。其他端口已打开的子系统在重新显示或下一次认证请求时发现登录失效；本次改动不改变子系统的业务请求、刷新令牌路由与原有来源约束。多端口完整认证部署仍需单独规划这些路径。

## 接入验收

- 点击子系统退出后，先进入配置的 Manager `sign-out` 页面；子系统来源不发送 POST 登出。
- 页面加载自动获取 Manager CSRF，随后仅发送一次 POST；不需要用户再次确认。
- 成功响应清除两个 Token Cookie，页面展示完成状态并进入 Manager 登录页。
- CSRF 获取失败或 POST 失败时不显示成功；用户可重试，处理中重复点击不产生并发请求。
- 无有效登录状态也能访问退出页并完成幂等清理；新增端口使用同一个配置入口。

相关规范：[认证与会话](modules/manager/auth_and_session.md)、[BrowserAuthApi 接口实现时序](modules/manager/browser_auth_sequence.md)。实现入口：[SignOutView.vue](../baosys-manager-ui/src/views/login/SignOutView.vue)。
