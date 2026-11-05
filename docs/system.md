# Manager 系统设计

用户、权限、会话和审计由 Manager Service 统一维护。Pingmoniter 已完成工程迁入，HTTP 接口已统一平台响应与版本前缀，并通过平台 JWT 接入 Manager 登录。

## 拨测工程边界

`baosys-pingmoniter` 聚合 API、Core、Service、Robot API 和 Robot；`baosys-pingmoniter-web` 提供独立网关及 UI 静态资源。API 包含请求/响应 DTO、Bean Validation 和 HTTP 接口契约，复用 common-api，不依赖 Core 或 Service。映射、参数及校验声明由 Controller 实现继承。Service 将协调模型转换成 API 数据，保持原 JSON 字段。Robot API 目前提供插件接口，选主、调度、模板加载和内置插件都在 Robot 应用。

Service 与 Robot 通过 ZooKeeper 交换任务、注册信息和结果；Service 通过原 SSE 协议推送变化。Java 包名、插件类名和 ZooKeeper 节点格式保持原样，避免现有任务中的插件 ID 失效。默认 namespace 为 `baosys-pingmoniter`；接入旧数据时需显式配置原 namespace。

隔离联调已覆盖完整 HTTP 拨测链路：Manager 授权账号创建任务并分配探针组，Robot 调度执行并将结果写回 ZooKeeper，Service 通过 REST 和 SSE 提供成功、失败及恢复结果。取消分配会在异步调度收敛后停止执行；结果保留到显式删除，删除变化通过 SSE 通知。可复现命令与验证边界见 [启动与部署](development.md)。

Core 中的旧 JWT 实现、独立管理员配置、旧登录接口及 ptToken 校验均已移除。拨测 Web 使用 common-security-gateway 读取平台 Access Cookie 并转发 Bearer；Service 使用 common-security-resource 再次验签，Controller 同时检查 CAT 和具体操作权限。Robot 保持 ZooKeeper 协作，不持有浏览器认证凭据。

拨测 UI 已统一为与 Manager 相同版本的 Vue 3、TypeScript、Vite、Pinia、Vue Router、Vue I18n 和字节跳动 Arco Design Vue；组件、图标、表格、表单、确认弹窗及布局均使用 Arco。移除 Element Plus、旧图标库和旧主题状态，保留 ECharts/Smoothie 业务图表并适配 Arco 色值。语言与亮/暗主题使用同源的 `baosys-locale`、`baosys-theme` 存储键，与 Manager 约定一致。拨测不再提供密码表单，旧 /#/login 仅跳转 Manager 的 /login，并携带经过 Manager 校验的返回地址。

## 拨测共享登录与会话

拨测通过同源 Web 代理读取 Manager 的 /v1/users/me，不代理 Manager 的管理接口或密码登录。401 经隐藏的 Manager /auth-bridge 协调页刷新一次后重试；403 作为权限不足，不转登录。协调页内复用 Manager 的 Web Locks、刷新版本和广播机制，所有端口的刷新与退出在 Manager 来源串行执行，令牌只保存在 HttpOnly Cookie 中。消息校验父窗口、精确来源和同主机/协议，仅暴露会话操作结果。协调页仅允许配置白名单嵌入，登录及其他页面继续拒绝嵌入。

会话变更会通知拨测页面清理或重新加载，回到页面时也重新校验身份。SSE 出错后经同权限读取接口检查会话并重连，403 停止重连；服务端事件流到 Access Token 到期时停止发送，浏览器再刷新连接。离线 JWT 的既有撤销延迟仍适用：复制的访问令牌在到期前可能有效，退出撤销刷新令牌而非即时失效所有已签发 Access Token。

CAT 权限按模板（plug:list/delete）、任务（task:list/create/delete/assign）、结果（result:list/delete）、探针（robot:list）、配置（config:read/update）划分，完整编码和中英文名称以根系统目录为准。CAT 入口不隐含任何操作权限；新增权限不会自动授予旧账号。页面路由、菜单及修改按钮同步控制，后端仍执行独立校验。

## 拨测接口规范

浏览器业务地址为 `/api/baosys-pingmoniter/v1/**`，Web 去掉 `/api` 后转发至 Service；Service context-path 是 `/baosys-pingmoniter`，Controller 契约使用 `/v1`。资源名保留 `plugs`、`tasks`、`robot`、`result`、`sysconfig`，现有集合查询仍返回数组，尚未引入分页。旧认证路径不再提供服务。

普通 HTTP 响应使用 common-api 的 `ApiResponse`，包含 `code/message/data/traceId/timestamp`，成功码 `SUCCESS`；删除旧重复响应类型。参数错误返回 400/VALIDATION_ERROR，资源缺失 404/NOT_FOUND，冲突 409/CONFLICT，协调连接不可用 503/SERVICE_UNAVAILABLE，未知异常 500/INTERNAL_ERROR。请求方法和内容类型错误保留 405、415。内部异常只写日志；服务响应携带 X-Trace-Id，并将有效传入追踪标识回显到响应体。网关认证失败使用平台公共安全模块，前端依据 HTTP 401/403 处理。

SSE 只迁移地址前缀，保留 text/event-stream、事件名和 data 结构，不包装成普通 JSON 响应；已提交的事件流异常不会再改写为 JSON。前端所有请求与 SSE 地址同步调整，路径参数按 URL 编码。非法任务/分组名称、缺少必填字段和非正超时会在写入前被拒绝；配置 Cron 使用与 Robot 一致的 Spring CronExpression 解析后才更新。

## 请求与模块边界

```text
开发：浏览器 :5173 → Vite 代理 → Manager Web :8080
打包：浏览器 :8080 → Manager Web（同时提供 Vue 静态资源）
                                  ↓
                    Manager Service :9090 → PostgreSQL
```

网关将 `/api/baosys-manager/**` 去掉 `/api` 后转发；服务 context-path 是 `/baosys-manager`。例如外部 `/api/baosys-manager/v1/users` 对应服务内 `/v1/users` Controller。

Manager API 只提供接口和 DTO；Service 中 `app` 负责 Controller、业务编排与事务，`infra` 负责数据库和认证实现。网关不访问业务数据库。公共安全能力按 core、resource、gateway 和 password 分开依赖。

## 登录与会话

1. 前端写请求先同源请求 `GET /csrf`，按响应的 `headerName` 携带 `token`。
2. 登录提交 JSON 到 `POST /api/baosys-manager/v1/browser-auth/login`。服务校验账号、启停状态和返回地址，创建授权及登录记录。
3. 响应通过 HttpOnly Cookie 保存 `BAOSYS_ACCESS_TOKEN` 和 `BAOSYS_REFRESH_TOKEN`；JSON 返回跳转地址，Token 不写入前端存储。
4. 网关读取 Access Cookie 或显式 Bearer 头，校验 JWT 后转发 Bearer；资源服务再次校验 JWT。显式 Authorization 头优先。
5. API 返回 401 时，前端最多刷新一次并重试，并发请求共用刷新；失败转登录。403 表示权限不足。

访问令牌采用 RS256，`aud=def`，业务权限来自 `permissions`，`sub` 为登录记录 ID。签名私钥仅由 Manager Service 加载，公钥通过服务的 `/baosys-manager/oauth2/jwks` 发布。网关通过配置的 JWKS 地址读取公钥。

Cookie 为 host-only、SameSite=Lax；Access 路径为 `/`，Refresh 路径为 `/api/baosys-manager/v1/browser-auth`。登录、刷新和退出均经过 CSRF 校验，Service 还要求写请求 Origin 精确匹配 `MANAGER_PUBLIC_ORIGIN`。

默认访问令牌有效期 30 分钟、刷新期限 30 天，取自应用中 `baosys-web` 客户端的 token 配置。刷新轮换 Refresh Token，保留首次登录的最终到期时间。退出或撤销会话会删除授权和登录记录；用户名、密码、权限、启停变化及删除账号也会撤销关联授权。离线验签不会实时查询撤销状态，已复制的 Access Token 仍可能有效至过期。

服务保留标准 OAuth2 Authorization Server 能力；当前 Manager 页面使用上述账号密码登录流程，网关不需要 OAuth2 Client 密钥。跨端口返回地址白名单用于拨测登录回跳，Manager Service/Web 均须配置相同 SHARED_LOGIN_ALLOWED_ORIGINS。

## 权限

[baosys-systems.yml](../baosys-systems.yml) 是系统与权限目录，启动时加载；外部文件覆盖后需重启。当前 Manager 入口权限为 `MGR`，接口权限如下：

| 权限 | 用途 |
|---|---|
| `MGR:user:list` | 查询用户 |
| `MGR:user:add` | 新增用户 |
| `MGR:user:update` | 修改账号、权限和启停状态 |
| `MGR:user:delete` | 删除用户 |
| `MGR:user:session:list` | 查询登录会话 |
| `MGR:user:session:kick` | 撤销登录会话 |

账号直接保存权限集合，尚无角色模型。入口权限和接口权限分别授予；只有 `MGR` 不会自动得到全部接口权限。提交示例为 `["MGR", "MGR:user:list"]`。服务拒绝未知权限及缺少所属入口的接口权限，去重排序后存入 TEXT 列。

用户创建、修改、当前用户、用户列表和登录会话响应统一使用扁平 `permissions`，不重复传输 `systemTypes`。前端从权限集合派生系统标签和编辑分组；系统目录中的 `systemType` 及用户查询的 `systemType` 筛选参数仍保留。当前用户返回目录内有效权限，管理列表保留账号存储权限以便识别和清理失效授权；会话列表中的权限同样表示账号当前存储权限，不是签发时的 Token 快照。

网关按路径检查 `MGR`，Service 用 `@PreAuthorize` 检查接口权限。`/v1/users/me` 和 `/v1/systems` 仅要求登录，网关规则须放在通配规则之前。JWT 权限按原值映射，不以 `scope` 或 `SCOPE_` 作为业务授权依据。

系统大写缩写和 `MGR:user:list` 形式是命名约定；不要把约定误写为完整的运行时格式校验。目录保留全局唯一性、权限登记和入口依赖检查。新增目录权限不会自动授予已有账号。

## 主要接口

下表路径均相对于外部前缀 `/api/baosys-manager`；请求与响应字段以 [API 契约](../baosys-manager/baosys-manager-api/src/main/java/baosys/manager/api/module/contract) 为准，请求模型位于 `module/payloads`，应答模型位于 `module/responses`。

| 方法与路径 | 行为 |
|---|---|
| `POST /v1/browser-auth/login` | 登录 |
| `POST /v1/browser-auth/refresh` | 刷新 |
| `POST /v1/browser-auth/logout` | 退出 |
| `GET /v1/users/me` | 当前用户 |
| `GET /v1/systems` | 系统与权限目录 |
| `GET /v1/users` | 用户分页查询 |
| `POST /v1/users` | 创建用户 |
| `PUT /v1/users` | 修改用户 |
| `DELETE /v1/users/{id}` | 删除用户 |
| `GET /v1/login-sessions` | 会话分页查询 |
| `GET /v1/users/{id}/login-clients` | 用户会话 |
| `DELETE /v1/users/{id}/login-clients/{loginId}` | 撤销指定会话 |
| `DELETE /v1/users/{id}/login-clients` | 撤销用户全部会话 |

统一响应包含 `code、message、data、traceId、timestamp`；成功码为 `SUCCESS`。分页包含 `items、total、page、size、totalPages`，页码从 0 开始，每页最多 200 条。

## 数据库

[V1 初始化脚本](../baosys-manager/baosys-manager-service/src/main/resources/db/migration/V1__init_manager.sql) 一次创建七张表、索引、外键、清理函数及撤销触发器：

| 表 | 内容 |
|---|---|
| `user_info` | 全局唯一用户名、BCrypt 密码、启停状态、权限和审计字段 |
| `user_login` | 账号、客户端、授权关联、认证会话和到期时间 |
| `permission_change_audit` | 操作者、目标、变更前后权限与启停状态、traceId 和时间 |
| `oauth2_authorization` | 授权与令牌 |
| `oauth2_authorization_consent` | OAuth2 授权确认 |
| `spring_session` | 认证中心会话 |
| `spring_session_attributes` | 认证中心会话属性 |

默认 schema 为 `manager`，Flyway 历史表为 `flyway_schema_history_manager`。无旧 `system_type` 和 `refresh_token_hash` 字段，也无需 V2～V4 升级脚本。

审计记录不设用户外键，删除账号后仍保留历史。在线状态按是否存在未过期登录记录计算，不是用户表字段。清理函数仅删除 `expires_at` 已到期的登录记录，先按账户 ID 顺序加锁、跳过忙碌账户，每次最多处理 100 个账户，并触发授权撤销；Flyway 只创建函数，不负责定时调度，部署配置见 [启动与部署](development.md)。
