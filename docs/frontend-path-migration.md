# 子系统前端基础路径与共享登录改造方案

状态：待实施。本文描述目标方案，不代表当前代码已经支持。

## 1. 改造目标

为每个子系统提供独立的前端基础路径，与现有 API 前缀配套，使本地按端口部署和生产环境通过 Nginx 按路径分流使用同一套应用约定。

| 系统 | 前端基础路径 | API 前缀 |
| --- | --- | --- |
| Manager（MGR） | `/baosys-manager/` | `/api/baosys-manager/` |
| 其他子系统 | `/baosys-xxxx/` | `/api/baosys-xxxx/` |

Manager 提供所有子系统共用的登录页 `/baosys-manager/login`。子系统身份和权限仍由 `MGR`、`CAT` 等系统编码决定，端口和 URL 路径只负责访问与部署。

本次不改变业务 API 前缀，不在 API 外再套前端路径，不改变数据库中的默认子系统编码和权限编码。

## 2. 对外地址约定

以前端已适配基础路径为前提，部署地址可以按环境配置：

| 用途 | 本地独立端口 | 生产统一入口 |
| --- | --- | --- |
| Manager 前端 | `http://localhost:8080/baosys-manager/` | `https://example.com/baosys-manager/` |
| XXXX 前端 | `http://localhost:8081/baosys-xxxx/` | `https://example.com/baosys-xxxx/` |
| 共享登录页 | `http://localhost:8080/baosys-manager/login` | `https://example.com/baosys-manager/login` |
| Manager API | `http://localhost:8080/api/baosys-manager/...` | `https://example.com/api/baosys-manager/...` |
| XXXX API | `http://localhost:8081/api/baosys-xxxx/...` | `https://example.com/api/baosys-xxxx/...` |

统一使用带尾斜杠的前端基址；访问 `/baosys-manager` 时规范化到 `/baosys-manager/`，保留查询参数。前端页面深层地址如 `/baosys-manager/users-management` 刷新后仍应正常显示。

Vite 开发时，Manager 的公开入口可以配置为 `http://localhost:5173/baosys-manager/`，并由 Vite 代理 API 和 CSRF 请求到 Web 网关。

生产 Nginx 对前端和 API 的转发约定：

| 外部路径 | 目标 | 路径处理 |
| --- | --- | --- |
| `/baosys-manager/…` | Manager Web | 去掉前端前缀后交给现有静态资源宿主 |
| `/baosys-xxxx/…` | XXXX Web | 同上 |
| `/api/baosys-manager/…` | Manager Web | 保留完整 API 路径 |
| `/api/baosys-xxxx/…` | XXXX Web | 保留完整 API 路径 |

本地没有 Nginx 时，Web 静态资源宿主也必须识别前端前缀并映射到静态资源和 SPA 入口。该映射只处理前端资源，不应全局剥离 API 或认证请求路径。生产代理和本地宿主的行为必须分别验证。

## 3. 系统目录与环境变量

继续使用 `baosys-systems.yml` 作为子系统入口及回跳范围的唯一目录。在 `baosys` 下增加 `ui-base-url-pre`，从环境变量 `BASE_URL` 读取公共地址前缀；各子系统通过“公共前缀 + 子系统地址后缀”的字符串拼接，得到浏览器实际可访问的完整地址。

目标配置示意（省略现有名称和权限，不应在实施时删除）：

```yaml
baosys:
  ui-base-url-pre: ${BASE_URL:http://localhost:}
  systems:
    - system-type: MGR
      ui-base-url: "${baosys.ui-base-url-pre}${MANAGER_UI_URL_SUFFIX:8080/baosys-manager/}"
      home-path: /users-management
    - system-type: CAT
      ui-base-url: "${baosys.ui-base-url-pre}${CAT_UI_URL_SUFFIX:8081/baosys-xxxx/}"
      home-path: /
```

`baosys-xxxx` 是示例名称，实际接入 CAT 时应换成该系统确定的路径。

这里使用普通字符串拼接，不使用 URL 相对路径解析，不自动增删冒号或斜杠。默认前缀为 `http://localhost:`，与默认后缀拼接后，分别得到 `http://localhost:8080/baosys-manager/` 和 `http://localhost:8081/baosys-xxxx/`。生产环境不带端口时，将前缀改为 `https://example.com`，后缀改为以 `/` 开头的子系统路径。YAML 中的 `http://` 不需要写成 `http\://`。

配置职责：

| 配置 | 含义 | 生效方式 |
| --- | --- | --- |
| `BASE_URL`（拟新增） | 公共地址前缀，默认 `http://localhost:` | 映射到 `baosys.ui-base-url-pre`，系统目录启动时解析 |
| `MANAGER_PUBLIC_ORIGIN`（现有） | Manager 的协议、主机和端口，不包含路径 | Service 启动时读取，保留用于 Origin 校验 |
| `MANAGER_UI_URL_SUFFIX`（拟新增） | Manager 地址后缀，默认 `8080/baosys-manager/` | 与公共前缀拼成 MGR 完整 UI 基址；登录地址从 MGR 目录项派生 |
| `CAT_UI_URL_SUFFIX`（拟新增） | CAT 地址后缀，默认 `8081/baosys-xxxx/` | 与公共前缀拼成 CAT 完整 UI 基址 |
| `VITE_MANAGER_UI_BASE_URL`（拟新增） | 前端访问共享 Manager 登录入口所用的完整基址 | 前端构建时读取，不能假设容器启动环境会自动覆盖 |
| 前端构建基础路径 | Manager 为 `/baosys-manager/`，其他系统为各自路径 | Vite 构建与 Router 使用同一来源 |

保留纯 Origin 与完整 UI 基址的区别；校验 MGR 基址的 Origin 与 `MANAGER_PUBLIC_ORIGIN` 一致。当前同主机、同协议的共享登录约束继续保留，本地允许不同端口。

`BASE_URL` 是拼接用前缀，不一定是合法的完整 URL，也不等于 Origin；应在拼接后校验各子系统完整 URL。实施时以这套前缀与后缀配置替代此前方案中的 `MANAGER_UI_BASE_URL`，并迁移现有 `CAT_UI_BASE_URL` 部署配置，避免两套配置来源并存。

本地配置示例（也可直接使用 YAML 中的默认值）：

```dotenv
BASE_URL=http://localhost:
MANAGER_PUBLIC_ORIGIN=http://localhost:8080
MANAGER_UI_URL_SUFFIX=8080/baosys-manager/
CAT_UI_URL_SUFFIX=8081/baosys-xxxx/
```

生产配置示例：

```dotenv
BASE_URL=https://example.com
MANAGER_PUBLIC_ORIGIN=https://example.com
MANAGER_UI_URL_SUFFIX=/baosys-manager/
CAT_UI_URL_SUFFIX=/baosys-xxxx/
SHARED_LOGIN_SECURE_COOKIE=true
```

其他子系统前端构建时设置 `VITE_MANAGER_UI_BASE_URL=https://example.com/baosys-manager/`。Manager 自身可从当前页面 Origin 与构建基础路径推导自己的基址。

本次约定各环境保持相同的子系统路径，只变化公开协议、主机和端口。若要求同一个前端构建产物跨不同公开地址复用，应另外提供启动时运行配置加载；仅设置 `VITE_*` 容器环境变量不能实现这一点。

系统目录仍遵循现有加载机制：修改环境变量或外部目录后重启服务，不作为动态热更新配置。

## 4. 首页与地址拼接规则

保留现有 `home-path` 形式，但明确其含义是“相对于子系统 UI 根目录的页面路径”，不是站点根路径。

```text
ui-base-url = https://example.com/baosys-manager/
home-path   = /users-management
结果        = https://example.com/baosys-manager/users-management

home-path   = /
结果        = https://example.com/baosys-manager/
```

前后端必须使用一致的拼接规则：校验 `home-path` 后移除其起始 `/`，再相对于带尾斜杠的 UI 基址解析。禁止直接调用 `base.resolve("/users-management")` 或 `new URL("/users-management", base)`，因为这会丢失子系统前缀。

首页配置禁止外部 URL、`//`、路径穿越及编码绕过，拼接结果仍须位于所属子系统范围内。共享登录页和已授权系统页也统一从 MGR 基址派生。

## 5. 共享登录与回跳

### 5.1 带回跳地址

1. 子系统需要登录时，将当前页面完整 URL 作为 `redirect_url`，通过 URL 参数编码传给 Manager 的 `/baosys-manager/login`。
2. 登录页提交账户信息与回跳地址；认证成功后，由后端校验并决定最终目标。
3. 普通页面回跳必须属于目录中登记的 UI 范围，且用户拥有目标系统入口权限。
4. 合法但无权限的目标进入 `/baosys-manager/authorized-systems`；非法地址继续返回现有业务错误，不能直接跳转。
5. 回跳保留合法查询参数和片段，避免丢失原页面状态。

### 5.2 不带回跳地址

根据当前用户保存的默认子系统编码查找目录项，再使用 `ui-base-url` 与 `home-path` 生成目标。默认子系统不存在或用户没有其入口权限时，进入已授权系统页。

### 5.3 跳转实现

保持当前浏览器登录接口返回 `redirectUrl`、登录页执行 `window.location.replace()` 的方式。本次不要求把该接口改为 HTTP 302。现有后端认证入口的 302 跳转需要同步改为新的共享登录页地址。

现有 OAuth2 授权流程允许的精确授权端点回跳单独保留，不能把授权端点放宽为任意 URL，也不能被普通 UI 范围校验误拒绝。OAuth2 issuer、JWKS 和客户端回调配置逐项核对，只有确实改变其公开访问地址时才同步调整。

## 6. 需要修改的代码范围

| 位置 | 改造内容 |
| --- | --- |
| `baosys-manager-ui/vite.config.mts` | 将构建基础路径设为 `/baosys-manager/`；核对开发访问与代理 |
| `baosys-manager-ui/src/router/index.ts` | Router 使用与 Vite 一致的基础路径；页面路由保持子系统内部表达 |
| `baosys-manager-ui/src/utils/loginRedirect.ts` | 使用 Manager 完整 UI 基址生成登录地址，去除丢弃路径的 `.origin` 用法 |
| `baosys-manager-ui/src/composables/useSystemCatalog.ts` | 修正首页拼接，保证系统选择页与后端默认跳转一致 |
| `baosys-manager-ui/src/utils/request.ts` | API 继续使用 `/api/...`；按下节约定调整 CSRF 地址 |
| `baosys-manager-web/.../SpaWebConfiguration.java` | 支持带前缀的本地静态资源及 SPA 深层路由；保留资源缺失和 API 错误语义 |
| `baosys-systems.yml` | 增加 `baosys.ui-base-url-pre` 并映射 `BASE_URL`；公共前缀与各系统后缀拼成完整 UI 基址 |
| `SharedLoginProperties` | 保留纯 Origin 校验，并与目录中的 MGR 基址校验一致性 |
| `SubsystemNavigation` | 移除 MGR 必须位于站点根目录的限制；按基址生成首页、已授权系统页 |
| `SharedLoginNavigation` | 从 MGR 基址派生登录页，保留精确 OAuth2 回跳逻辑 |
| 各子系统前端与 Web | 采用相同的基础路径、资源映射和登录导航约定 |

保留子系统 URL 范围不重叠的检查。`/baosys-manager/` 与 `/baosys-xxxx/` 可以共存；同一 Origin 下 `/` 与其他子系统前缀仍然冲突。匹配时考虑协议、主机、有效端口和路径边界，不能把 `/baosys-manager-other/` 识别为 Manager。

普通根相对回跳地址（如 `/baosys-xxxx/tasks`）继续按 Manager 的站点 Origin 解析，然后校验所属系统；不要与 `home-path` 的子系统相对语义混用。跨端口回跳使用完整 URL。

## 7. API、CSRF 与 Cookie

业务 API 和浏览器登录接口保持现有地址：

```text
/api/baosys-manager/...
/api/baosys-manager/v1/browser-auth/login
/api/baosys-manager/v1/browser-auth/refresh
/api/baosys-manager/v1/browser-auth/logout
```

访问令牌 Cookie 保持覆盖所有业务 API 所需的共享范围；刷新令牌 Cookie 继续限制在 `/api/baosys-manager/v1/browser-auth`。不能因增加前端路径，将令牌 Cookie 限制到 `/baosys-manager/`，否则不会随 `/api/...` 请求发送。HTTPS 环境继续启用 Secure Cookie。

当前各网关通过 `/csrf` 提供令牌，统一 Origin 后不能把同一个路径同时分给多个网关。本方案采用各自路径暴露 CSRF：例如 `/api/baosys-manager/csrf`、`/api/baosys-xxxx/csrf`，由各自网关本地处理，不转发到业务 Service。需同步修改公共网关控制器映射、放行规则、前端请求及 Vite 代理。

CSRF Cookie 的名称、路径、令牌生成与校验需验证多网关并存行为，避免其他网关覆盖 Cookie 后导致已取得的令牌失效。按需要为各网关隔离 CSRF Cookie 名称；业务认证令牌仍共享。

本地不同端口的 Cookie 可在同主机及匹配路径下共享，但 localStorage、BroadcastChannel 和 Web Locks 按 Origin 隔离。当前前端的认证协调机制不能跨端口同步；生产同源路径模式则会共享这些机制。两种环境都需要验证切换账户、退出和并发刷新，不应把本地跨端口等同于生产同源行为。

## 8. 实施顺序

1. 确定所有子系统前端前缀、公开基址配置及统一地址拼接规则。
2. 修改系统目录、导航校验和共享登录地址生成，补充导航与认证测试。
3. 修改 Manager 前端基础路径、首页链接和登录导航，适配本地 Web 静态资源宿主。
4. 处理各网关 CSRF 地址与 Cookie 并存问题，并同步开发代理。
5. 其他子系统按相同约定接入，配置 Nginx 路径转发。
6. 完成本地独立端口及统一入口两套验证，再更新正式启动部署文档和环境变量说明。

此次只形成方案文档。实施时需要重新核对代码现状，旧根路径 `/login`、`/users-management` 等是否保留兼容跳转由实际已有链接决定；不能让兼容规则占用其他子系统路径。

## 9. 验收清单

- Manager 和其他子系统的主页、静态资源及深层页面刷新正常；不存在前缀丢失或重复添加。
- 缺失的静态资源和不存在的 API 返回正确错误，不被 SPA 回退伪装成成功页面。
- 本地 8080/8081、Vite 开发及生产统一 Origin 都能访问正确的页面和 API。
- 从其他系统进入共享登录页，成功后恢复完整页面地址；无参数时进入用户默认子系统。
- 无目标系统权限、无可用默认系统时进入已授权系统页，接口权限校验仍然生效。
- 外站、未登记端口、伪造前缀、重叠目录、路径穿越、编码绕过及登录循环目标被拒绝。
- 系统选择页链接与后端计算的默认首页相同；`home-path: /` 正确返回子系统根入口。
- OAuth2 原有授权与回调流程正常，不放宽其精确白名单。
- 两个子系统登录、刷新、退出、切换账户和并发请求正常；CSRF Cookie 不互相破坏。
- 后端执行相关导航、安全及浏览器认证测试；前端执行类型检查、构建和相关浏览器测试，并实测 Nginx 转发链路。

配置调整与应用发布需要配套完成。只更新系统目录或 Nginx，不能替代前端资源、路由和共享登录逻辑的路径适配。本方案不需要数据库结构迁移。
