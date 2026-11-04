# Manager 系统设计

当前产品范围只有 Manager 管理系统。用户、权限、会话和审计由 Manager Service 统一维护，其他业务系统尚未开发。

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

服务保留标准 OAuth2 Authorization Server 能力；当前 Manager 页面使用上述账号密码登录流程，网关不需要 OAuth2 Client 密钥。跨端口返回地址校验是预留扩展能力，不代表已有其他系统接入。

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

下表路径均相对于外部前缀 `/api/baosys-manager`；请求与响应字段以 [API 契约](../baosys-manager/baosys-manager-api/src/main/java/baosys/manager/api) 为准。

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
