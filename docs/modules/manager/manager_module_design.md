# Manager 模块设计

Manager 提供账户管理、共享浏览器登录、OAuth2/OIDC 授权服务器、权限目录、登录会话查询与撤销，以及账户授权审计。网关路由和 Vue 静态资源由独立的 Manager Web 承担。

相关文档：[认证与会话](auth_and_session.md)、[权限模型](permission_model.md)、[数据库设计](database_design.md)、[系统架构](../../architecture/system_architecture.md)。

## 1. 模块和目录

```text
baosys-manager/
├── baosys-manager-api/
│   └── baosys.manager.api
│       ├── UserApi
│       ├── LoginSessionApi
│       ├── SystemCatalogApi
│       ├── BrowserAuthApi
│       └── dto
└── baosys-manager-service/
    └── baosys.manager
        ├── ManagerApplication
        ├── app
        │   ├── controllers
        │   ├── services/impl
        │   ├── assembler
        │   ├── config/web
        │   └── error
        └── infra
            ├── database/entity
            ├── database/mapper
            ├── database/converter
            ├── security
            └── config
```

API Jar 依赖 common-api，spring-web 和 jakarta.validation-api 为 optional。它包含公开接口和 DTO，不包含 Controller、Entity 或 FeignClient。

Service 直接依赖 manager-api、common-api、common-password、common-security-core 和 common-security-resource，并装配 Spring Authorization Server、Spring Session JDBC、MyBatis Plus、Flyway 和 PostgreSQL。当前没有 baosys-common 或 security-issuer 模块。

## 2. 调用与职责

Controller 调用应用服务，应用服务直接编排 Mapper、Assembler 和基础设施，不额外建立 Domain/Agent 层。UserController、LoginSessionController、SystemCatalogController 和 SharedLoginController 均实现 API Jar 接口。BrowserAuthApi 定义浏览器登录、刷新和退出契约，BrowserLoginRequest/BrowserLoginResponse 定义请求与响应；Cookie 设置和登录编排保留在 Service。

| 组件 | 主要职责 |
|---|---|
| UserAppServiceImpl | 账户 CRUD、登录 ID 解析、当前用户、在线状态、用户客户端下线、审计 |
| LoginSessionAppServiceImpl | 跨用户有效登录会话分页 |
| SystemCatalog | 启动加载目录、写入权限校验、有效授权过滤 |
| BrowserAuthAppService / BrowserLoginTransaction | 内部登录用例、排序账户锁、刷新与撤销事务 |
| AccountAuthenticator / InternalLoginAuthenticator | 事务外密码校验 / 已验签内部登录身份转换 |
| AccountAuthorizationService | 委托官方 JDBC 授权存储，同步 user_login，查询令牌时锁定账户并复查 |
| AuthorizationServerConfiguration | OAuth2/OIDC 端点、账号认证、公共令牌签发器装配 |
| AccountTokenSubjectResolver | 两条签发流程共用的账户状态、登录记录和权限查询 |
| AccountTokenGenerator | 将 OAuth2 令牌上下文适配到公共 TokenIssuer |
| TokenIssuer（common-security-core） | 统一业务 Access JWT 声明与签名、随机 Refresh Token 生成 |
| SigningKeysConfiguration | 外部持久化 JWK 加载与 JwtEncoder |
| DefaultAdminInitializer | 可选默认管理员创建与初始化审计 |

业务 DTO 不暴露数据库 Entity。事务通常位于应用服务或安全服务；官方 token/revoke 的 POST 请求额外由 AuthorizationTransactionFilter 包裹事务。

## 3. HTTP API

下表路径省略浏览器统一前缀 `/api/baosys-manager`。网关 StripPrefix=1 后发送到 Service，其 context-path 默认是 `/baosys-manager`。

| 方法 | 路径 | 说明 | Service 方法权限 |
|---|---|---|---|
| GET | /v1/users/me | 当前用户 | 已认证，并解析有效登录记录 |
| GET | /v1/users | 用户分页 | MGR:user:list |
| POST | /v1/users | 创建用户 | MGR:user:add |
| PUT | /v1/users | 更新用户 | MGR:user:update |
| DELETE | /v1/users/{id} | 删除用户 | MGR:user:delete |
| GET | /v1/users/{id}/login-clients | 用户的有效登录客户端 | MGR:user:session:list |
| DELETE | /v1/users/{id}/login-clients/{loginId} | 指定客户端下线 | MGR:user:session:kick |
| DELETE | /v1/users/{id}/login-clients | 全部客户端下线 | MGR:user:session:kick |
| GET | /v1/login-sessions | 跨用户有效会话分页 | MGR:user:session:list |
| GET | /v1/systems | 全部系统及可分配权限目录 | 已认证 |
| POST | /v1/browser-auth/login | 登录并设置共享 Cookie | 公开认证入口，校验来源和凭据 |
| POST | /v1/browser-auth/refresh | 使用 Refresh Cookie 轮换 | 公开入口，校验来源和刷新凭据 |
| POST | /v1/browser-auth/logout | 撤销当前内部授权、清 Cookie | 公开入口，校验来源 |

“公开入口”不代表免 CSRF：通过网关的浏览器写请求还要携带 /api/baosys-manager/csrf 返回的 token。共享登录请求均使用 application/json。OAuth2/OIDC 的协议端点由框架提供，使用自己的协议响应，详见 [认证与会话](auth_and_session.md)。

默认网关对 /users/me、/systems 仅要求登录，其余管理 API 额外要求 MGR 入口。该规则由有序的 spring.security.gateway.access-rules 声明。

用户分页支持 name 模糊查询、systemType 入口权限筛选、page（默认 0）和 size（默认 20，1～200）。未知 systemType 返回 USER_400_002。会话分页支持 userId（至少 1）或 userName，其中 userId 优先，分页规则相同。

## 4. 请求和响应字段

| DTO | 当前字段 |
|---|---|
| UserCreateRequest | name、pass、permissions、enabled |
| BrowserLoginRequest | username、password、redirectUrl |
| BrowserLoginResponse | redirectUrl |
| UserUpdateRequest | id、name、可选 pass、permissions、enabled |
| UserResponse | id、name、permissions、enabled、status |
| CurrentUserResponse | userId、username、displayName、permissions |
| LoginClientResponse | id、loginClient、loginAt |
| LoginSessionResponse | id、userId、userName、permissions、loginClient、loginAt、expiresAt |
| SystemDefinition | systemType、name（语言映射）、permissions（code/name 列表） |

账户响应不含 systemType/systemTypes；客户端根据 permissions 提取系统入口。CurrentUserResponse.displayName 当前与用户名相同。LoginClientResponse 的 id 是登录记录 ID，并不是 OAuth2 client_id；client_id 虽然存在于数据库，但上述会话 DTO 不返回它。

UserResponse.status 表示是否存在未过期登录记录，enabled 表示账户启停。当前用户权限按目录过滤，用户/会话列表保留数据库权限，便于识别已失效的历史编码。

共享登录请求为 username（非空，最多 128）、password（非空，最多 256）、redirectUrl（可选，最多 8192）；成功返回 data.redirectUrl，令牌通过 HttpOnly Cookie 下发。

## 5. 核心业务规则

1. 账户名称写入前 trim，最长 128，全局唯一；数据库唯一约束保障并发写入，重复键异常映射为 409。
2. 创建密码非空，长度 8～128；更新密码为 null、空串或纯空白时不修改，有效新密码长度仍为 8～128。
3. permissions 必须提供，空集合合法；未知权限或缺少所属入口的接口权限被拒绝。
4. 创建时 enabled 省略默认为 true，更新时省略保留原值；禁用账户不能登录或刷新。
5. 用户名、密码、启停或权限变化会撤销相关授权和认证中心 Session；删除账户也清理登录记录。
6. 创建、删除及权限/启停变化写审计，与用户写操作同事务；单独用户名/密码修改不写权限变更审计。
7. 创建、修改、删除的 operatorUserId 由 Controller 将 JWT sub（登录记录 ID）解析成账户 ID，不接受请求体指定操作者。
8. 单客户端下线必须匹配用户 ID、登录记录 ID 且未过期；全部下线删除该用户全部登录记录。
9. 只读列表中的有效会话条件是 expires_at > 当前时间；在线状态按整页批量查询。
10. 撤销立即阻止刷新，但仅离线验证 JWT 的接口仍可在 Access Token 到期前接受旧令牌；默认 Access 期限为 30 分钟。

内部共享登录和外部 OAuth2 刷新的期限及错误语义不同，见 [认证与会话](auth_and_session.md)。服务端当前没有“最后一个管理员保护”、角色授权或数据权限。

## 6. 事务边界

| 操作 | 实现方式 |
|---|---|
| 用户、当前身份、会话查询 | 应用服务 @Transactional(readOnly=true) |
| 用户新增/更新/删除、客户端下线 | 应用服务 @Transactional |
| 内部登录完成/刷新/退出 | BrowserLoginTransaction @Transactional；密码校验在事务外 |
| 授权保存/删除/令牌查找 | AccountAuthorizationService 的事务方法 |
| 官方 POST /oauth2/token、/oauth2/revoke | AuthorizationTransactionFilter 包裹事务 |
| 默认管理员初始化 | 初始化器 @Transactional |

账户更新和签发/刷新使用账户行锁。指定客户端下线当前通过 Mapper 删除登录记录及数据库级联/触发器完成，不应描述为该方法显式锁定账户。

## 7. 响应与异常

业务接口返回 ApiResponse，含 code、message、data、traceId、timestamp；分页 data 使用 PageResponse。业务日志与响应通过 TraceIds.current() 关联。

| 错误码 | HTTP 状态 | 场景 |
|---|---:|---|
| REQUEST_400_001 | 400 | 方法参数校验或缺少查询参数 |
| AUTH_400_001 | 400 | 请求体校验或无法读取 JSON，包括账户请求体 |
| AUTH_400_REDIRECT | 400 | 不允许的登录返回地址 |
| AUTH_401_001 | 401 | 共享登录/刷新凭据无效，或业务登录记录失效 |
| USER_400_001 | 400 | 用户名为空 |
| USER_400_002 | 400 | 权限配置错误或未知系统筛选 |
| USER_400_003 | 400 | 更新密码长度错误 |
| USER_404_001 | 404 | 用户不存在 |
| USER_409_001 | 409 | 用户名重复 |
| LOGIN_404_001 | 404 | 有效登录客户端不存在 |
| SYS_500_001 | 500 | 未预期异常 |

JWT 认证/授权失败由 Spring Security 处理，采用标准 401/403 及适用的 WWW-Authenticate，不保证业务 JSON 结构。CSRF 和 Origin 拒绝为 403；OAuth2 token/revoke 等端点使用协议响应。

## 8. 启动配置

默认 application.name=baosys-manager，端口 9090，context-path=/baosys-manager。配置入口见 [application.yml](../../../baosys-manager/baosys-manager-service/src/main/resources/application.yml)。

必须提供 JWT_JWK_SET_LOCATION；正式部署覆盖 OAUTH2_CLIENT_SECRET_HASH，并保持两端 JWT_ISSUER 一致。默认 issuer 已统一；详细说明见 [认证与会话配置](auth_and_session.md#6-关键配置)。

系统目录由 BAOSYS_SYSTEMS_CONFIG 指定，默认 classpath:baosys-systems.yml。数据库默认使用 app_db/manager，Flyway 执行统一 V1 初始化。

默认管理员初始化关闭；启用 DEFAULT_ADMIN_ENABLED 后必须提供至少 12 字符的 DEFAULT_ADMIN_PASSWORD。若同名用户存在则不修改；新建时授予目录中的 MGR 全部权限并写入审计。

## 9. 测试现状与执行方式

现有测试按实际类名包括：

| 范围 | 代表测试 |
|---|---|
| 账户服务与控制器 | UserAppServiceImplTest、UserControllerTest、UserControllerIntegrationTest |
| 共享登录与授权服务器 | SharedLoginControllerTest、SharedLoginIntegrationTest、AuthorizationServerIntegrationTest、AuthorizationClaimsTest |
| 资源接口安全与目录 | ResourceApiSecurityTest、SystemCatalogTest、SystemCatalogIntegrationTest |
| 密钥和登录配置 | SigningKeysConfigurationTest、SharedLoginPropertiesTest |
| 初始化、清理、生命周期 | ManagerInitializationIntegrationTest、UserLoginCleanupFunctionIntegrationTest、AccountLifecycleIntegrationTest |
| 网关和公共安全 | GatewaySecurityIntegrationTest、JwtVerificationTest、JwtPermissionConverterTest、JwksContractTest |

AccountLifecycleIntegrationTest 覆盖 V1 初始化与删除后审计保留、并发刷新仅一次成功、禁用与刷新竞争后的授权撤销。数据库测试继承 PostgreSqlIntegrationTest，容器镜像为 postgres:17-alpine，并由 Flyway 初始化。

根 Surefire 默认排除 *IntegrationTest，integration-tests profile 由 Failsafe 执行这些测试：

```powershell
mvn -pl :baosys-manager-service -am test '-Dskip.frontend=true'
mvn -Pintegration-tests -pl :baosys-manager-service -am verify '-Dskip.frontend=true'
```

第二条命令需要 Docker/Testcontainers。上述是运行方式及现有覆盖说明，不代表本文更新时重新执行过测试。当前没有 LoginSessionController 独立测试类。

## 10. 当前未实现的能力

没有独立的自助修改密码/管理员重置密码端点，但管理员可通过现有用户更新接口设置密码。角色、组织、租户、数据权限、账户锁定和在线修改系统目录尚未实现；前端系统配置页面不代表已有后端配置 CRUD。
