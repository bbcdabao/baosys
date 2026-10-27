<!-- 历史方案：当前实现已取消业务 JWT scope 的生成、一致性校验和 SCOPE_ authority 转换；权限仅以 permissions 为准。OAuth2/OIDC 协议使用的 openid/profile 授权范围仍保留。 -->

 > 后续实施已切换为官方 Spring Authorization Server；本文为初始计划，旧 signer、key-id 和 Cookie 方案已被替换。当前方案见 [升级指南](../../deployment/oauth2_upgrade.md)。

# OAuth2 / JWT 鉴权体系规范化修改计划

日期：2026-10-14。状态：代码已实施，验证记录见第 10 节。以下第 1–9 节保留设计依据与实施清单；部署操作需按升级指南在目标环境执行。

## 1. 目标与实施边界

统一认证中心、响应式网关、Servlet 资源服务的访问令牌契约；优先使用 Spring Boot 标准属性、Spring Security 原生编码器、解码器、验证器和授权组件。删除重复实现框架能力的配置与 JWKS 网络缓存代码，只保留项目确实需要的业务扩展。

当前根 POM 使用 Spring Boot 3.5.7，其 BOM 管理 Spring Security 6.5.6。本计划以该版本为基线，实施前通过依赖树确认实际解析版本；不以升级框架作为前提。[Boot 3.5.7 依赖定义](https://github.com/spring-projects/spring-boot/blob/v3.5.7/spring-boot-project/spring-boot-dependencies/build.gradle)

本轮同时改造 JWT、Resource Server 接入及账户多子系统授权模型：删除登录请求的 `systemType`，删除 `user_info.system_type`，将子系统入口权限与接口权限统一存入 `user_permissions`。保留登录、刷新、退出和 Cookie 交互的基本流程，调整对应 API 和前端。标准 OAuth2 授权码、PKCE、客户端注册和 Token Endpoint 不属于本轮；后续需要时单独规划 Spring Authorization Server。

## 2. 契约决定

| 项目 | 本轮约定 |
|---|---|
| `alg` | 固定 `RS256`，私钥签名、公钥验签；这属于签名，不是对载荷加密 |
| `typ` | 签发固定 `at+jwt`，验签端明确校验访问令牌类型 |
| `kid` | 本项目要求非空，例如 `v1-key-2026`；与 JWKS 中对应公钥的 `kid` 一致 |
| `iss` | 固定默认 `auth-server`，签发与验证值一致 |
| `sub` | `user_login.id` 的字符串形式；不是 `user_info.id`，也不是客户端 ID |
| `aud` | 从需求允许的两种方案中选择固定 `def`；接收端验证包含 `def` |
| `client_id` | 保留必填，标识登录客户端而非授权子系统；本轮建议由认证服务配置默认客户端 `baosys-web`，登录会话保存该值，刷新沿用；不再从 LoginRequest/systemType 或权限反推 |
| `exp`、`iat` | 必填，NumericDate 秒级时间戳，满足 `exp > iat` |
| `jti` | 每次签发生成新的 UUID；刷新后重新生成，不能使用登录记录 ID 代替 |
| `scope` | 保留必填，从 permissions 中的子系统入口权限派生，如 `MANAGER SIMULATION`；不是独立存储或网关授权依据；空字符串表示无子系统入口权限 |
| `permissions` | 必填字符串数组，包含全部已授予的子系统入口权限及接口细粒度权限，与数据库规范化后的 user_permissions 对应 |
| `systemType`、`system_type` | 都不再出现在新 JWT 中；仓库当前实际使用的是驼峰 `systemType` |
| `username` | 当前为展示扩展字段；目标令牌删除，展示信息走当前用户接口；实施时同步排查消费方 |

RFC 9068 要求 `aud` 必填，因此选择 `def`；所有服务共享该值时，它不能区分资源服务，本项目以 permissions 中的子系统入口权限进行隔离。`scope` 必填及登录记录作为 `sub` 是项目约定；不把这些选择描述为 RFC 的普遍要求。`jti` 只是令牌标识，单独增加它不能防止 Bearer Token 重放。[RFC 9068](https://www.rfc-editor.org/rfc/rfc9068.html)

本次删除 `systemType` 后，原先“client_id 等于登录 systemType”的约定废止。默认 `baosys-web` 是本计划提出的客户端命名，表示共享登录入口；它不限制账户能访问哪些子系统。若部署存在多个真实客户端，分别配置受信入口并在登录时绑定会话，不能把多个子系统代码拼接成 client_id，也不把终端描述 `loginClient` 当作客户端标识。

目标示例（时间仅作结构演示）：

```json
{
  "alg": "RS256",
  "typ": "at+jwt",
  "kid": "v1-key-2026"
}
```

```json
{
  "iss": "auth-server",
  "sub": "9527",
  "aud": ["def"],
  "client_id": "baosys-web",
  "exp": 1789299600,
  "iat": 1789296000,
  "jti": "b4f2da39-7c11-4822-bc54-20a2f94500a1",
  "scope": "MANAGER SIMULATION",
  "permissions": ["system:MANAGER", "system:SIMULATION", "user:list", "user:update"]
}
```

`aud` 使用 Spring Claims Builder 自然生成的数组形式，语义等同于单值 `def`，无需为了字符串形式自定义序列化。

## 3. 已确认的现状与差异

| 位置 | 当前行为 | 修改方向 |
|---|---|---|
| issuer 模块 `JwtTokenServiceImpl` | `sub=userId`，写入 `username`、`systemType`；没有显式构建目标头和完整新字段 | 显式构建 Header；签发登录 ID、客户端、scope、aud、jti |
| core 模块 `SecurityProperties` | 签发、验签、远程缓存配置混在 `security.jwt.*` | 验签迁至 Boot 原生属性，精简剩余项目属性 |
| `JwtVerification` | 校验时间、issuer、subject 和 `systemType` | 使用标准验证器组合，移除旧子系统 claim 校验 |
| 两种 `Lazy*JwksJwtDecoder`、`JwksSnapshot` | 自建 JWKS 获取、缓存和快照策略 | 改用 Nimbus 原生 JWKS 解码；回归通过后删除 |
| `JwtPermissionConverter` | 仅生成 permissions 权限，覆盖默认 scope 转换 | 合并默认 `SCOPE_` 权限和现有细粒度权限 |
| `AuthAppServiceImpl` | 登录已插入 `user_login`，但签发只接收 user；刷新同样不传 login | 将登录记录传入统一签发流程 |
| `UserController.parseUserId` | 直接把 principal 名称解析为用户 ID | 根据登录 ID 解析真实用户 ID |
| manager-web YAML | 通过 `security.system-type` 和 JWT 的 `systemType` 隔离 | 网关按所保护子系统检查 permissions 中的入口权限 |
| manager-service YAML | 内嵌签名密钥，验签使用自定义属性 | 签发密钥外部注入，验签配置标准化 |
| `user_info` / `UserInfoMapper` | 单 system_type，用户名与系统联合唯一，permissions 仅存接口权限 | 删除系统列；用户名全局唯一；统一存储多系统权限 |
| `SystemCatalog` | 每个系统内部检查 permission code 重复 | 全目录全局唯一校验，建立 code 到所属系统的索引 |
| 用户管理及登录前端 | 登录发送 MANAGER，创建账户单选系统 | 登录移除系统参数，账户编辑改为多系统与分组权限选择 |

## 4. 修改清单

### 4.1 依赖与配置职责

- 检查根 `pom.xml` 及四个 security 模块、两个应用 POM 的依赖树，优先使用 Boot 管理的版本。
- 评估移除根 POM 对 `nimbus-jose-jwt:9.37.4` 的显式版本覆盖，验证与 Spring Security 管理版本兼容；直接使用 Nimbus API 的模块仍声明必要直接依赖。
- 应用按 Servlet / Reactive 技术栈接入 OAuth2 Resource Server starter，公共库保持现有依赖边界，避免引入错误 Web 栈。
- 直接使用 `OAuth2ResourceServerProperties` 读取标准验签属性，不再复制一套 `jwk-set-uri`、算法、issuer 等属性类。
- `SecurityProperties` 仅保留 Cookie、路径、子系统入口权限策略等 Boot 无法表达的配置；签发参数集中到 issuer 模块的小型属性类（建议 `security.jwt.issuer`、有效期、密钥、key-id、published-keys 暂保留以减少迁移）。
- 清理无调用的配置类、导入选择器与注册信息；不要为了清理而重新设计模块体系。

### 4.2 签发与密钥发布

涉及 issuer 模块：`JwtTokenService.java`、`JwtTokenServiceImpl.java`、`JwtEncoderConfiguration.java`、`SigningKeyMaterial.java`。

- 签发接口改为明确接收 `loginId`、`clientId`、规范化 permissions；scope 由其中的子系统入口权限确定性派生，不能由调用者独立传入不一致的值。
- 使用 Spring `JwsHeader` 显式设置 RS256、`at+jwt`、当前签名密钥的 ID；继续使用 `NimbusJwtEncoder`。
- 通过 `JwtClaimsSet` 设置全部目标字段，固定 audience `def`；从数据库读取全部已授予权限，经全局目录校验后生成 permissions 与 scope，不再按单 systemType 过滤。
- 删除 `username`、`systemType`、`system_type` 写入。优先删除通用 `extraClaims` 参数；若还有必要消费方，改为扩展白名单，禁止覆盖所有标准字段、权限字段或注入旧字段。
- 签发端显式配置 `security.jwt.key-id`，Header 与已发布 JWK 使用同一份密钥材料；禁止新旧不同公钥复用相同 kid。
- 保留现有 `/oauth2/jwks` 公钥发布端点及轮换能力；网关通过 JWKS 获取公钥，无需在每个服务的 YAML 中重复维护 kid 到公钥映射。
- 保留 RSA 长度、密钥对匹配、重复 kid 检查；JWKS 只发布公钥参数。
- 旧公钥保留到最后一枚旧 Token 过期并覆盖时钟容差；结合原生缓存行为验证新 key 预发布、切换与移除过程。
- 将主配置中的私钥改为外部注入；测试使用专用密钥。当前仓库内密钥不得作为生产密钥继续使用。

### 4.3 登录、刷新与用户身份

涉及 manager-service：`AuthAppServiceImpl`、`UserController`、`UserAppServiceImpl`、`UserLoginMapper`，及相关 API/测试。

- 登录先插入 `user_login` 并确认 ID 回填，再调用 `createResult(user, login, ...)`，将 `login.id` 写入 `sub`。
- 刷新沿用已锁定登录记录的 ID；轮换 Refresh Token，生成新的 Access Token `jti/iat/exp`。
- 删除 `LoginRequest.systemType` 及其校验；前端登录请求同步移除该参数。凭全局唯一用户名和密码认证，用户能访问哪些系统仅由账户已分配权限决定。
- `UserInfoMapper` 的查找、锁定和重名检查改为按用户名；删除 `findByUserNameAndSystemType*` 等单系统查询方法。
- 登录时读取认证入口配置的客户端 ID（建议 `auth.client-id=baosys-web`），保存到新增 `user_login.client_id`；刷新读取会话值，保持同一客户端身份。该业务配置不伪装成 Spring 原生属性。
- 老会话在发布切换时统一失效；新增列迁移先处理旧记录再加非空约束，不能把原系统类型自动填成客户端 ID。
- 修改 `UserController.parseUserId`：principal 名称仍保留 Spring 默认的 `sub`（登录 ID），在应用服务边界通过 `user_login.id -> user_info_id` 获取操作者，不再直接当用户 ID。
- 当前用户、创建用户、修改用户、删除用户和审计链路使用解析后的真实用户 ID；缺失或失效会话按认证失效处理，不返回 500。
- 明确行为变化：依赖登录记录解析身份的 Manager 接口，在会话删除后可立即拒绝；仅离线验 JWT 的其他接口仍可能接受未过期令牌。不能据此声称全平台退出立即生效。

### 4.4 统一权限模型与多子系统授权

本轮直接支持一个账户拥有多个子系统权限。建议采用扁平、全局唯一的权限编码集合：系统入口编码使用保留前缀 `system:`，例如 `system:MANAGER`；接口权限保留现有 `user:list` 等已全局唯一的编码。系统目录中的 `system-type` 仍作为系统标识保留，删除的是账户上的单系统归属。

示例：`user_permissions` 逻辑集合为 `["system:MANAGER", "system:SIMULATION", "user:list"]`。该账户可进入 MANAGER 与 SIMULATION，但在 Manager 中仅有查看用户权限。没有细粒度权限目录的系统只需入口权限；勾选系统不得自动授予全部接口权限。

- 数据库存储沿用现有 `PermissionPersistenceConverter` 的英文逗号分隔格式，稳定排序、去重；JWT/API 使用字符串数组。建议将 `VARCHAR(1024)` 扩为 `TEXT`，同步调整旧长度校验，并根据代理请求头限制设置、验证权限数和 Token 大小上限。
- 创建/修改账户统一接收 `permissions` 集合，不再接收单值 `systemType`。前端多选子系统后把入口权限与勾选的细粒度权限合并提交；数据库不额外保存一份系统列表。
- 服务端验证每个入口系统存在、每个细粒度 code 存在且其所属系统入口权限同时存在；拒绝未知权限和无入口的孤立接口权限，不能因提交细粒度权限而隐式提升系统权限。
- 允许仅授予系统入口、不给接口权限；无细粒度目录的系统正常支持。空权限集合表示无系统访问权，不自动补默认系统；账户是否禁用仍由 enabled 表达。
- 更新时以提交的完整权限集替换原集合，取消系统必须同时移除该系统接口权限；后端拒绝不一致请求。权限变更统一记审计并撤销该账户 Refresh Session。
- 登录/刷新读取同一权限集合，生成 JWT permissions；从 `system:` 编码提取系统代码生成空格分隔 scope，二者保持一致。已有 Token 的撤权延迟仍遵循第 6 节。
- `JwtPermissionConverter` 使用原生转换器读取 permissions、使用空前缀；可组合原生 scope 转换器保留 `SCOPE_` 映射，但不得允许业务接口 code 使用 `SCOPE_` 前缀，避免转换后的 authority 冲突。
- 网关在完成 JWT 验证后，检查 permissions 对应的 `hasAuthority('system:MANAGER')` 等入口权限，不检查 `user:list` 等接口权限，也不使用 client_id 或 scope 替代入口权限。
- 独立子系统网关检查自己的入口权限；共享网关按路由目标映射入口权限，不能把所有路由统一判为 MANAGER。当前 `/api/baosys-plant/**` 的目标系统归属需要在实施时显式配置，不能从服务名称猜测。
- 后端微服务使用 `@PreAuthorize("hasAuthority('user:list')")` 等检查接口细粒度权限；受保护资源同时要求所属系统入口权限以防直连绕过。没有细粒度权限的接口只要求系统入口及有效身份，公开登录/刷新/JWKS 按明确白名单处理。
- 保持 401（无效令牌）与 403（有效身份缺入口/接口权限）区别；不将登录到某个系统误认为已获得它全部接口权限。

### 4.4.1 权限目录全局唯一

涉及根 `baosys-systems.yml`、`SystemCatalog`、`SystemDefinition`、目录接口及测试配置。

- 将 `SystemCatalog` 当前“每个系统各建一个 code 集合”的校验改为全局集合/映射；不同系统内同名 `permissions[].code` 也必须启动失败，并指出 code 和两个系统。唯一性针对权限编码，双语展示名称可以相同。
- 从系统目录自动生成 `system:<system-type>` 入口权限，无须在每个系统 permissions 列表重复声明；接口权限禁用保留的 `system:` 和 `SCOPE_` 前缀。目录中的接口 code 与生成入口 code 共享无冲突命名规则。
- 建立全局 `code -> systemType` 所属关系与全局合法权限集合，提供统一校验和分组展示；禁止“后出现覆盖前出现”。
- 所有生产、外部覆盖、测试目录执行同样校验；现有接口 code 若已全局唯一可保留，不强制无意义改名。重名时按系统命名空间改名，并同步数据库、注解、前端和测试。
- 对目录删除/改名进行存量授权审计；失效入口及其接口权限不得再签发。失效代码在账户编辑中明确提示，不能自动映射到其他系统的同名权限。

### 4.4.2 数据库迁移与账户唯一性

- 新增 Flyway 迁移，不修改已执行的 V1/V2；盘点依赖 system_type 的约束、SQL、实体、会话列表查询和初始化逻辑。
- 预检查旧 `(user_name, system_type)` 联合唯一模型产生的跨系统同名账户；新登录只提交用户名，因此必须改为 `user_name` 全局唯一，并与现有用户名 trim/大小写规则一致。
- 如存在重名，输出冲突清单并要求按明确业务映射改名或合并后再迁移；不按第一条匹配登录、不静默合并密码与权限。合并需另行处理登录记录、审计和其他用户外键。
- 对每个旧账户把原 system_type 转成入口权限，与原 user_permissions 合并；保留原接口授权，不扩大权限。空接口权限账户也必须迁入原系统入口。
- 若旧目录跨系统重名，先提供 `(旧 system_type, 旧 code) -> 新全局 code` 映射；在仍有 system_type 时迁移，不能删列后失去归属信息。未知旧系统或权限应输出清单阻止盲目迁移。
- 验证迁移后的账户数、用户 ID、权限集合和唯一性后，删除旧联合唯一/系统检查约束及 `user_info.system_type`，建立用户名唯一约束；删除 `UserInfoEntity.systemType`。
- 查询按“包含某个系统入口权限”筛选用户；现有 CSV 存储必须用分隔符切分后的精确成员匹配，禁止模糊 LIKE 导致 MANAGER 与相似代码混淆。保留分页计数一致性。
- 同步调整 `PermissionPersistenceConverter`、`UserInfoMapper`、`UserAssembler`、`UserAppServiceImpl`、`LoginSessionAppServiceImpl`、会话 Mapper/Row、`DefaultAdminInitializer` 及测试数据。
- 默认管理员显式拥有 MANAGER 入口及所需管理接口权限；不得仅因目录新增系统就给现有管理员自动扩权。权限审计记录保存入口和接口权限的完整变更。

### 4.4.3 账户 API 与前端

- `LoginRequest`、`types/auth.ts`、`LoginView.vue` 删除登录 systemType，清除硬编码 `MANAGER`；成功登录后按返回权限展示可进入的子系统。
- `UserCreateRequest/UserUpdateRequest` 删除单 systemType，仅提交完整 permissions；`UserResponse/CurrentUserResponse/LoginSessionResponse` 删除单系统字段，按需返回从入口权限派生的只读 `systemTypes` 列表用于展示。
- 用户列表筛选可保留 systemType 作为“拥有该系统权限”的查询参数，但不再代表账户归属字段；同步 API 文档和 `api/user.ts`。
- `UserManagementView.vue` 改为多选子系统，按系统分组勾选细粒度权限；无接口权限的系统可独立选择；取消系统时清除其接口选择；编辑回显保持全部系统授权。
- `useSystemCatalog.ts` 增加全局权限所属关系与标签查找；更新 `types/user.ts`、`types/loginSession.ts`、`LoginManagementView.vue` 和中英文文案，列表显示多个系统。
- 检查前端路由、菜单、按钮权限及当前用户状态管理：入口看系统权限、按钮看接口权限；前端展示控制不替代后端鉴权。

### 4.5 标准解码与必要扩展

涉及 resource/gateway 模块：`JwtDecoderConfiguration`、`ReactiveJwtDecoderConfiguration`、两种 `Lazy*JwksJwtDecoder`、安全链；core 模块 `JwtVerification`、`JwksSnapshot`。

- 删除自建 JWKS 拉取、刷新、缓存、失败退避及本地优先远程回退策略，使用 `NimbusJwtDecoder` / `NimbusReactiveJwtDecoder` 原生能力。
- 不承诺只改 YAML 即满足全部契约：`typ=at+jwt` 和严格必填 claim 校验需要明确装配。优先在现有配置中保留最薄的 decoder Bean，读取 Boot 标准属性，不增加平行配置体系。
- 显式 decoder Bean 会使 Boot 默认 decoder 自动配置退让；因此必须把所用标准属性（JWKS URI、issuer、算法、audience）接入构建和验证，不能只改属性名而不消费。
- 基于当前版本验证 `validateType(false)` 的可用性，再交给明确的访问令牌类型验证器检查 typ；不能关闭底层类型检查后不补验证。具体方法签名以实际 Servlet / Reactive API 为准。
- 优先使用 `JwtValidators.createAtJwtValidator()` 和内置 validator；其 builder 的固定 clientId 示例不适合多客户端服务，按官方扩展点替换为非空及受信客户端集合校验，避免所有服务硬编码 `MANAGER`。
- 保留签名、RS256、issuer、audience、时间及必填字段验证；补充本项目 kid、scope、permissions 的类型和格式约束。时间容差默认沿用框架，校验不能因组合自定义 validator 丢失。
- 完整回归原生 JWKS 的启动、缓存、未知 kid、轮换和网络失败行为；不能宣称其缓存 TTL、回退行为与现有自建实现完全相同。
- JWKS 为默认唯一部署方案。本地单公钥仅在确有离线部署需求时通过原生 `public-key-location` 单独配置；普通 PEM 不包含 kid 映射，不能宣称等价于 JWKS 轮换。

Spring 提供 Resource Server JWT 和 RFC 9068 验证扩展；默认自动配置并不表达本项目全部业务策略。[Servlet JWT 文档](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html)、[Reactive JWT 文档](https://docs.spring.io/spring-security/reference/6.5/reactive/oauth2/resource-server/jwt.html)、[6.5.6 JwtValidators 源码](https://github.com/spring-projects/spring-security/blob/6.5.6/oauth2/oauth2-jose/src/main/java/org/springframework/security/oauth2/jwt/JwtValidators.java)

### 4.6 安全链与配置精简边界

- 精简 `ResourceServerSecurityConfig`、`GatewaySecurityConfig`，继续使用框架 Bearer 认证与授权流程。
- 保留无法用普通 YAML 表达的公开路径、子系统入口权限授权、方法安全、无状态会话策略和 permissions 转换装配。
- 保留现有 Cookie Token 提取及转发适配；请求头优先级、公开路径处理、CSRF 与前端响应约定纳入回归，避免改造导致登录链路失效。
- 401/403 优先遵循框架语义；如保留统一 JSON 错误体，使用框架扩展点并保留适用的 `WWW-Authenticate` 响应头。
- 检查 `AuthenticationCheckConfiguration`、`UltraViresCheckConfiguration`、两个 ImportSelector 及启用注解，移除只为旧 decoder 管理服务的装配，保留仍承担业务接入职责的部分。

“尽量默认”不等于删除全部安全配置类：自定义 JWT 契约、Cookie 兼容、业务路径授权和 permissions 不会仅靠 Resource Server 默认配置自动产生。只装配现有框架组件，不重复实现它们。

## 5. 配置迁移样例

manager-web、manager-service 以及后续资源服务统一采用以下验签配置（部署时覆盖内部地址）：

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${JWT_JWK_SET_URI:http://127.0.0.1:9090/baosys-manager/oauth2/jwks}
          issuer-uri: ${JWT_ISSUER:auth-server}
          jws-algorithms: [RS256]
          audiences: [def]
```

保留 `auth-server` 的方案使用显式 JWKS URI，issuer 仅用于精确校验，不使用 issuer discovery。它不是可访问的元数据地址，不能只配置该 issuer 然后调用 `withIssuerLocation`。Manager 自身验签访问本服务 JWKS 时，必须放行 `/oauth2/jwks` 并验证不会发生递归认证；Servlet context-path 为 `/baosys-manager`。

签发参数仍属于项目扩展，不能伪装成 Boot 自带 Resource Server 属性：

```yaml
auth:
  client-id: ${AUTH_CLIENT_ID:baosys-web}

security:
  jwt:
    issuer: ${JWT_ISSUER:auth-server}
    key-id: ${JWT_KEY_ID:v1-key-2026}
    private-key: ${JWT_PRIVATE_KEY}
    public-key: ${JWT_PUBLIC_KEY}
    access-token-expire-seconds: ${ACCESS_TOKEN_EXPIRE_SECONDS:1800}
```

| 旧配置 | 目标处理 |
|---|---|
| `security.jwt.jwk-set-uri` | `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` |
| `security.jwt.issuer-uri` / 验签端 `security.jwt.issuer` | `spring.security.oauth2.resourceserver.jwt.issuer-uri` |
| `security.jwt.allowed-algorithms` | `spring.security.oauth2.resourceserver.jwt.jws-algorithms` |
| 验签端 `security.jwt.public-key` | 默认取消，统一 JWKS |
| 验签端 `security.jwt.public-key-location` | 可选离线方案使用 Boot 同名标准属性 |
| `security.jwt.jwks.*` | 删除；仅在实测必要时采用框架公开定制接口 |
| `security.jwt.clock-skew` | 默认使用框架容差；有业务需求时保留最小 validator 参数 |
| `security.system-type` | 改为网关/路由所需的系统入口权限策略，例如 `system:MANAGER`；不再是 decoder claim 等值校验 |
| `permissions-claim-name`、`authority-prefix` | 契约已固定为 permissions / 空前缀，排查使用后删除可变配置 |

签发端仍需要公钥作为 JWK 材料，不随“删除验签端公钥配置”一同删除。

## 6. jti、撤销与黑名单

- 本轮必须实现唯一 jti、刷新更换 jti、验证非空、日志中可关联 jti；日志不输出完整令牌。
- 本轮不把“存在 jti”当成已实现黑名单；当前 logout 删除 Refresh Session，不会自动吊销所有已签发 Access Token。
- 若要实现即时撤销，作为后续独立任务设计共享存储：按 `(iss, jti)` 撤销，TTL 覆盖 `exp` 与验证时钟容差，并在验签成功后查询。
- 踢除整个登录会话需要覆盖该 `sub` 下所有未过期 Token；仅保存最近一个 jti 不够。可设计会话撤销记录或会话到 jti 的索引，并明确多实例一致性和存储不可用策略。
- 有效 Bearer Token 在未撤销时仍可重复使用；若需真正约束重放，另行设计请求级防重放或发送者约束机制。

## 7. 实施顺序与迁移

1. 固化统一权限编码、客户端配置与 JWT 契约；扫描跨系统重名账户、重名权限及无效授权，准备迁移映射和备份。
2. 实现目录全局唯一校验、新增 Flyway 迁移、账户权限读写、用户名全局唯一和会话客户端绑定；在测试数据副本上演练。
3. 改造公共签发、原生 decoder 装配、权限转换器；同步修改登录/刷新、用户身份解析、网关入口权限、后端接口权限和 YAML。
4. 更新账户与会话 API、登录前端、多子系统权限编辑、管理员初始化和审计；完成第 8 节回归后清理旧实现与旧属性。
5. 维护窗口联动发布数据库、签发端、网关、资源服务和前端，旧 Refresh Session 统一失效并要求重新登录；同步文档及环境模板。

旧 Token 的 `sub` 是用户 ID，新 Token 是登录 ID，不能根据数字是否存在来猜测版本。若必须滚动升级，应另写包含数据库扩展/收缩的限时兼容方案；不能让旧 `sub` 进入新登录 ID 查询路径。回滚必须覆盖数据库、API、前端和所有 Token 消费方。删列后新产生的多系统账户无法无损还原为旧单系统模型；保留切换前备份，回滚演练明确如何处理切换后的写入，不能简单反向加列。

## 8. 验证与验收

| 验证项 | 验收条件 |
|---|---|
| 签发契约 | Header 与 Claims 完整；没有旧 systemType/system_type/username；扩展字段不能覆盖契约 |
| 登录身份 | 登录记录 ID 与用户 ID 刻意设置不同，当前用户、修改操作与审计仍指向正确用户 |
| 刷新 | 同一会话 sub 不变；jti 改变；旧 Refresh Token 不能再次使用；scope/permissions 按服务端现状重新计算 |
| 必填与类型 | 缺失/空 sub、client_id、jti、kid；缺失 iat/exp；错误 scope/permissions 类型均拒绝 |
| 密码学与时间 | 篡改、错误公钥、未知 kid、非 RS256、错误 typ/issuer/audience、过期或不合法时间均拒绝 |
| Scope 一致性 | 从 permissions 的系统入口编码派生、去重排序；单/多/空系统正确；不能仅凭 SCOPE_ authority 获得网关入口 |
| 网关授权 | 有本系统入口即可通过粗粒度授权；无入口返回 403；共享网关按目标路由判断；无入口但带接口 code 不能通过 |
| 后端授权 | 入口合法但缺接口权限返回 403；具备入口及所需接口权限才通过；直连同样受保护 |
| 多系统账户 | 一次登录可访问全部已授予系统；未授予的第三系统拒绝；无细粒度目录的系统可仅授予入口；修改后完整回显 |
| 目录唯一性 | 同系统及跨系统重复 code 均启动失败并定位冲突；保留前缀冲突拒绝；展示名称重复允许 |
| 账户约束 | 无 systemType 的登录成功；跨系统同名账户迁移被明确拦截；全局同名创建/修改及并发创建受唯一约束保护 |
| 数据迁移 | 旧入口与接口授权完整保留；重命名映射正确；无静默扩权；精确系统筛选与分页一致；删除旧列后无残留查询 |
| 权限编辑 | 多选/取消系统及各系统接口权限；未知或孤立权限拒绝；审计完整；撤销会话行为保持一致 |
| 客户端绑定 | client_id 来源为配置入口，写入登录记录并在刷新时沿用，不依赖账户系统权限和终端描述 |
| JWKS | 匿名访问公钥端点；无私钥泄露；新旧 key 轮换正常；网络失败时不接受无法验证的 Token |
| 框架装配 | Servlet/Reactive 均只有预期 decoder；标准 YAML 参数真正生效；无重复安全链或错误 Web 栈 |
| 浏览器 | 登录、刷新、退出、Cookie/Authorization 优先级、CSRF 开关及 401/403 前端处理正常 |
| 迁移 | 旧 Token 明确拒绝或按专门兼容方案处理；不发生用户 ID/登录 ID 混淆 |

重点更新现有 `JwtTokenServiceTest`、`SigningKeyMaterialTest`、`JwtPermissionConverterTest`、`JwtDecoderConfigurationTest`、两端 `JwksContractTest/DecoderWiringTest`、`AuthAppServiceImplTest`、`AuthControllerIntegrationTest`、`SystemCatalogTest/SystemCatalogIntegrationTest`、`UserControllerIntegrationTest`、`GatewaySecurityIntegrationTest` 和 `JwksSecurityTest`；增加数据库迁移与多系统账户编辑回归，断言最终契约而非旧缓存内部实现。

实施阶段在仓库根目录运行 `mvn test`，准备好项目测试数据库等前置条件后运行 `mvn -Pintegration-tests verify`。根 POM 的普通测试排除了 `*IntegrationTest`，不能只跑 `mvn test` 就宣称端到端验收完成。实际执行结果见第 10 节。

## 9. 文档同步范围

- `docs/modules/manager/auth_and_session.md`：Token 契约、登录主体、刷新与撤销边界。
- `docs/modules/manager/permission_model.md`：统一权限集合、全局唯一目录、网关与后端分工、派生 scope。
- `docs/modules/manager/database_design.md`：删除账户系统列、用户名全局唯一、权限容量、会话 client_id 与迁移说明。
- `docs/deployment/environment_variables.md`、`local_development.md`：标准配置、密钥注入、测试入口。
- `docs/subsystem-onboarding.md`：新资源服务的 JWKS、系统入口权限、全局唯一接口编码和方法权限接入。
- `baosys-manager-ui/README.md` 及账户 API 文档：无系统参数登录、多系统授权编辑、返回字段与筛选语义。
- `README.md`、`baosys-manager/README.md`、相关 YAML 测试配置、部署环境模板：删除旧 claim/属性示例。
- 更新文档导航，历史重构计划保留历史语义，不覆盖为当前实现说明。

完成标准：一个账户支持多个子系统及各自接口权限；user_permissions 为授权的唯一存储来源；目录权限编码全局唯一；网关检查系统入口权限，后端检查接口权限；签发及消费契约一致，原生框架负责 JWT/JWKS 核心处理；身份、权限、前端和数据迁移测试全部通过。

## 10. 实施记录（2026-10-14）

已完成 JWT Header/Claims、登录与刷新、全局用户名、多子系统权限存储、目录唯一校验、网关入口鉴权、微服务方法权限、账户编辑器与共享用户资料页面的配套改造。新增 V3 迁移及迁移前目录校验，旧权限与系统入口合并，旧会话失效；未修改 V1/V2。

已删除自建 JWKS 快照与两端 Lazy decoder；标准配置直接绑定 OAuth2ResourceServerProperties。保留薄 decoder 配置以启用 at+jwt 和本项目契约校验。实际依赖为 Spring Security 6.5.6，其 builder 方法为 validateType(false)。Nimbus 9.37.4 与该 Security 版本依赖一致，Boot 不直接管理其版本，因此保留根 POM 显式版本。

client_id 默认来自 AUTH_CLIENT_ID=baosys-web，并保存于登录记录。scope 由 system:<type> 权限派生。共享 /v1/users/me 与 /v1/systems 允许所有已认证账户访问，其余 Manager API 要求 MANAGER 入口及相应方法权限。Plant 归属仍由部署方通过 PLANT_SYSTEM_PERMISSION 指定，缺省拒绝访问。

最终验证：68 项单元测试、33 项 PostgreSQL/Docker 与网关集成测试全部通过（无失败、错误或跳过），前端 vue-tsc/Vite 构建通过；测试覆盖迁移保留数据、重复用户名及跨系统旧权限拦截、JWT 验签和 JWKS 刷新、多系统授权、刷新并发与权限变更。未进行浏览器人工验收或部署环境迁移。

本轮实现 jti 生成和校验；共享黑名单、防重放和按 jti 的运维日志关联仍属于后续能力，不声称即时撤销所有 Access Token。

部署需要注入专用 RSA 密钥，并联动升级数据库、签发端、网关、微服务和前端。详细步骤、回滚边界和验证命令见 [升级指南](../../deployment/oauth2_upgrade.md)。

### 后续标准化已完成

继续精简了 JWT 重复校验，使用 JwtTypeValidator / JwtClaimValidator 和 at+jwt 标准验证器组合。删除自建 CSRF Filter、安全错误处理器和路径匹配器；采用 Spring 原生 CSRF、Bearer 错误响应和标准 matcher。前端通过 /csrf 获取掩码 Token，不再缓存 Cookie 值；旧 CSRF 配置和闲置直接依赖已清理。此次干净构建通过 68 项单元测试、37 项集成测试，前端构建通过；详见升级指南的标准能力精简章节。
