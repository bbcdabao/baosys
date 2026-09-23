# OceanAcoustic → BaoSys 增量合并方案

日期：2026-09-23。状态：合并已完成并通过下述验证；数据库按首个版本初始化。

## 1. 结论

有新增内容，建议按功能拆分后合并，不能整体覆盖五个目录。

本次核心增量是 Manager 的账号启停、授权变更审计、会话撤销、刷新令牌有效期校验与并发控制，以及对应 UI。Common 的生产代码仅有注释差异，Common API 已一致；Manager Web 只有新增 Dockerfile 和一处空白差异。源项目新增内容中有大量 Simulators 专属功能，即使位于 Manager 目录内，也不建议纳入此次通用能力迁移。

建议默认执行边界：合并通用 Manager 能力；保留 BaoSys 现有命名和权限目录，数据库采用首版独立 schema；不引入 Simulators 用户引用、业务转交、角色和 HTTP 客户端。容器化作为独立可选批次。以下基线统计保留为合并依据；执行状态以文末记录为准。

## 2. 对比范围与基线

| 源目录 | 目标目录 |
|---|---|
| oceanacoustic-common | baosys-common |
| oceanacoustic-common-api | baosys-common-api |
| oceanacoustic-manager | baosys-manager |
| oceanacoustic-manager-ui | baosys-manager-ui |
| oceanacoustic-manager-web | baosys-manager-web |

- 源仓库：`../now/oceanacoustic`，分支 `bao`，HEAD `1006e6f1f53b34d3f3f6f3b9dbcbe58666793d49`。
- 目标仓库：`.`，分支 `main`，HEAD `be75e1690bf026258634d676dd53ad648e0d1192`。
- 检查开始时两个工作区均干净。未拉取远端，结论针对以上本地版本，不保证覆盖远端尚未同步的提交。
- 仅枚举指定五组目录的 Git 跟踪文件；未分析 `oceanacoustic-simulators*` 目录。根 POM 仅作为构建依赖背景查看，不计入模块统计。
- 比较时将路径和文本中的 `oceanacoustic`、`Ocean Acoustic`、`OceanAcoustic`、`OA_CSRF_TOKEN` 分别规范化为 BaoSys 对应标识，统一 CRLF/LF。二进制 favicon 另用 SHA-256 核实一致。
- “源侧新增”表示目标对应路径不存在，不等于所有文件都是最近一次提交新增。“差异”包含注释、品牌和空白，不等于全部需要迁移。
- BaoSys 已有 2026-07-09 的设计和实施计划；其 JWT 校验、会话清理、SPA 测试等内容已在当前目标代码中存在。本方案以当前实物差异为准，不重复执行旧计划，也不依赖旧计划中的本机路径或执行工具。

### 文件统计

| 目标模块 | 源侧文件数 | 规范化一致 | 同路径差异 | 源侧新增 | 目标独有 |
|---|---:|---:|---:|---:|---:|
| baosys-common | 29 | 26 | 3 | 0 | 1 |
| baosys-common-api | 3 | 3 | 0 | 0 | 1 |
| baosys-manager | 79 | 35 | 24 | 20 | 3 |
| baosys-manager-ui | 38 | 30 | 8 | 0 | 2 |
| baosys-manager-web | 12 | 10 | 1 | 1 | 0 |
| 合计 | 161 | 104 | 36 | 21 | 7 |

其中 `LoginView.vue` 的差异仅为大写品牌 `OCEAN ACOUSTIC` / `BAOSYS`，`SpaWebConfiguration.java` 的差异仅为空白；两者均不需要合并。

## 3. 建议合并的功能

### 3.1 账号启停：后端与 UI 配套迁移

源侧新增 `user_info.enabled`，与原有响应中的 `status`（是否在线）分别表达两个概念。

- `UserCreateRequest`、`UserUpdateRequest` 增加可空 `Boolean enabled`；创建时未传值默认启用，更新时未传值保留原值，以兼容旧 HTTP 调用方。
- `UserResponse`、`UserInfoEntity`、`UserAssembler`、前端 `UserSummary` 和表单同步增加启停字段。
- 登录和刷新都检查用户是否启用；停用后拒绝重新登录和刷新。
- 用户管理页增加账号状态列、开关和中英文提示，继续保留独立的在线状态列。
- Java record 构造参数发生变化，必须检查现有测试和所有 Java 调用点；HTTP 字段兼容不等于 Java 源码兼容。

主要文件：Manager API 的三个 User DTO；服务层 `UserAppServiceImpl`、`AuthAppServiceImpl`、`UserAssembler`、`UserInfoEntity`；UI 的 `types/user.ts`、`UserManagementView.vue`、中英文语言包、组件声明。

### 3.2 授权变更审计与会话撤销

- 新增 `PermissionChangeAuditEntity`、`PermissionChangeAuditMapper` 和数据库审计表。
- 创建、删除用户以及权限/启停状态变化时记录操作者、目标用户、前后权限、前后启停状态、traceId 和时间；默认管理员初始化也写审计。
- `UserApi` → `UserController` → `UserAppService` 传递认证 Principal 中的操作者 ID，不从请求体接收操作者身份。
- 权限、启停状态、密码或系统类型变化时删除目标用户的 Refresh Session；无相关变化时保留会话。
- 删除账号前清理会话，保留审计记录。审计表不增加会随账号删除级联清理的外键。
- 保留 BaoSys 的六个 Manager 权限及既有空的其他系统权限目录，不复制源侧六个 Simulators 角色权限。

源侧 `UserAppServiceImpl` 混有业务引用预检/转交，必须逐段移植通用部分，同时移除对 `UserBusinessReferenceGateway` 的构造依赖，不能通过空实现假装完成引用检查。

### 3.3 刷新令牌过期校验与并发控制

当前 BaoSys 刷新查询只按 token hash 查找；源侧查询增加 `expires_at > now`，并在事务中锁定用户、再锁定有效会话并复核，防止已轮换的旧 token 被重复使用。

建议配套迁移：

- `UserInfoMapper.findByIdForUpdate`、`findByUserNameAndSystemTypeForUpdate`。
- `UserLoginMapper.findActiveByRefreshTokenHash`、`findActiveByRefreshTokenHashForUpdate`。
- 登录、刷新、用户修改/删除保持一致的用户锁顺序和事务范围。
- 刷新令牌过期、禁用账号、并发轮换的测试。

注意：此次使既有 `expires_at` 真正参与刷新资格校验。经实施时复核，源侧刷新只轮换摘要而不更新 expires_at，因此期限从登录时计算，刷新不延长。原有按 `updated_at` 空闲一天清理会话的数据库函数已存在，可继续保留，不能用定时清理替代请求时的过期校验。

### 3.4 认证默认配置

建议吸收以下默认值，保留现有环境变量覆盖能力：

| 配置 | BaoSys 当前默认 | 源侧默认 / 建议 |
|---|---|---|
| Access Token 有效期 | 7200 秒 | 1800 秒 |
| Access Cookie Max-Age | 7200 秒 | 1800 秒 |
| 默认管理员初始化 | 开启 | 关闭 |
| 默认管理员密码 | 配置内固定默认值 | 无默认值，启用初始化时外部提供 |

需要同时改 `AuthProperties`、`application.yml`、相关测试与部署说明。测试 profile 可继续显式启用测试管理员。

撤销 Refresh Session 不会立即使已签发 JWT 失效；采用新默认值后，新签发 Access Token 的剩余有效时间最多约 30 分钟，受显式配置影响。上线前已经签发的 7200 秒 token 不会因修改默认值而缩短寿命。UI 应说明“刷新会话已撤销”，避免承诺所有请求立即失效。

### 3.5 其他通用改进

- `GlobalExceptionHandler` 增加缺少必填 query 参数的统一 400 响应，错误码 `REQUEST_400_001`。原触发场景来自新增引用 API，但异常映射本身可独立合并；补充适用于现有 API 的验证。
- Common 中两个安全配置类可以修正说明：`system-type` 可用于网关和资源服务。无需重写 JWT 实现，也不改变当前认证中心配置。
- `JwtPermissionConverterTest` 的新增用例使用 Simulators 角色；可改成 Manager 多权限列表，验证无前缀和原值转换，不迁入业务角色常量。

## 4. 本次不合并的内容

| 内容 | 原因与处理 |
|---|---|
| `UserReferenceApi`、`UserReferenceResponse`、对应 Assembler/Controller/Service/实现 | 实际实现硬编码 SIMULATORS 和其角色白名单，并非现成通用用户查询接口；整组排除 |
| `UserBusinessReferenceResponse`、`UserBusinessTransferRequest`、`UserBusinessReferenceGateway` | 服务于试验负责人、任务参与人预检和转交；排除 |
| Manager 内 `infra/simulators/*` 及对应测试 | 依赖被排除的业务服务；排除 |
| `business-references`、`business-transfer` API，用户删除/平台切换中的试验场分支 | 与上列依赖绑定；不迁入，保持 BaoSys 现有业务边界 |
| UI 生命周期抽屉、转交目标、试验引用详情及对应语言包/API/types | 没有目标业务后端；排除，避免留下不可用入口 |
| `PermissionCode` 和前端权限目录中的 Simulators 角色 | 不扩展当前业务范围；保留 BaoSys 权限目录 |
| `PermissionCodeTest`、`PermissionCatalogMigrationIntegrationTest`、`UserReferenceControllerIntegrationTest` 的源侧新增文件 | 新用例针对 Simulators 白名单或引用能力；不原样迁移。通用数据库结构验证另补测试 |
| 新 SQL 中针对 SIMULATORS 的权限清洗 UPDATE | 会修改目标用户权限，不属于通用启停/审计建表；删除该段后再迁移 |
| `LoginView.vue` 品牌差异、Web SPA 空白差异 | 保留目标版本 |

BaoSys 原本就存在 `SystemType.SIMULATION`、`SystemType.SIMULATORS` 及空权限目录，此次不删除这些既有预留值。验收应检查“未新增 Simulators 业务依赖”，不能以全仓库出现该词即判失败。

## 5. 首版数据库（按用户后续指示修订）

用户明确要求将当前作为首个版本，不做旧库兼容。移除旧日期版本脚本，统一为 V1__init_manager.sql，一次创建用户（含 enabled）、会话、授权审计及清理函数。采用 MANAGER_DB_SCHEMA（默认 manager）和 flyway_schema_history_manager。无需 baseline-on-migrate 或 repair；未操作任何实际部署数据库。排除 Simulators 权限清洗。清理函数固定初始化时的 search_path，供 schema 限定的调度调用使用。

## 6. 容器与根构建

- 源侧新增两个 Dockerfile，可作为独立可选合并批次，不作为通用功能合并的前置条件。
- 当前 Dockerfile 绑定源侧内网镜像仓库；需要改成 BaoSys 部署环境可访问的基础镜像，并映射工作目录和 JAR 名称。
- 目标服务 POM 的 `finalName` 是 `baosys-manager`，所以服务产物为 `baosys-manager.jar`，不是 `baosys-manager-service.jar`；Web 为 `baosys-manager-web.jar`。构建上下文应为各自模块目录。
- 五组模块的 POM、UI `package.json`、锁文件规范化后一致，不需要以本次合并为由升级框架或依赖。
- 源根 POM 有 `integration-tests`/Failsafe profile，而 BaoSys 没有。建议只提取该 profile 作为测试支撑；不引入源根 POM 中任何 Simulators module、依赖或产物规则。

## 7. 实施顺序与验收

以下均为后续实施步骤，本次没有执行。

| 批次 | 工作 | 通过条件 |
|---|---|---|
| 0 | 从确认后的 BaoSys HEAD 建立 `codex/oceanacoustic-incremental-merge` 分支，复查状态和基线差异 | 不覆盖未提交工作；记录实施时的新 HEAD |
| 1 | 首版 SQL、enabled DTO/实体/Assembler、审计实体/Mapper | 空库首版建库通过，无业务权限清洗 |
| 2 | Controller/Service 操作者传递、启停、审计、会话撤销、登录/刷新行锁 | 后端单元与集成测试通过；无 Simulators 构造依赖 |
| 3 | UI 启停字段、状态列、表单、提示及必要组件声明 | 类型检查/生产构建通过；真实接口联调通过 |
| 4 | token/admin 默认配置、异常映射、Common 注释/通用测试、测试 profile | 新默认值和兼容行为有测试证据 |
| 5 | 更新 Manager README、认证/权限/数据库和环境变量文档 | 文档准确说明启停、审计、刷新过期及 JWT 撤权延迟 |
| 可选 | Dockerfile 适配 | 两个目标镜像构建、启动与健康检查通过 |

批次 1—4 是同一发布的配套工作，不应把只完成 enabled 数据模型或只完成 UI 的中间状态单独上线。所有改动逐文件语义合并，保留目标独有文件。

### 必须验证的行为

1. 旧客户端不传 enabled：创建默认启用、更新不改变状态；API 返回中 enabled 和 status 分别正确。
2. 禁用用户不能登录或刷新；改权限/禁用/改密码/改系统类型撤销刷新会话；仅改名称或相同权限不误踢会话。
3. 创建、授权变更、启停、删除和管理员初始化记录正确操作者、目标、traceId 和前后值；业务失败时审计与业务修改一起回滚。
4. 过期刷新令牌被拒绝；同一旧令牌并发刷新至多一次成功；禁用与刷新并发不产生新的可用刷新会话。除 Mockito 测试外，补真实 PostgreSQL 并发测试确认锁行为。
5. 删除用户会清理会话但保留审计；既有 Manager 权限控制和未授权响应仍正确。
6. UI 新建/编辑/停用/重新启用、列表两种状态、中文和英文提示正常；没有引用转交入口或访问 Simulators 的请求。
7. 首版数据库初始化、默认管理员关闭与显式启用、token/Cookie 期限、原 SPA 路由和网关鉴权回归通过。
8. 测试数据显式设置 enabled；改造源测试中的 SIMULATORS 夹具为 MANAGER 合法权限，保留目标原有回归用例。不要整份覆盖混合测试。

### 建议执行命令

在 BaoSys 根目录，常规单元测试：

```powershell
mvn test "-Dskip.frontend=true"
```

提取并验证 Failsafe `integration-tests` profile 后，在 Docker/PostgreSQL 测试环境可用时执行：

```powershell
mvn -Pintegration-tests -pl :baosys-manager-service -am verify "-Dskip.frontend=true"
```

在 `baosys-manager-ui` 执行：

```powershell
npm ci
npm run build
```

当前 UI 只有 dev/build/preview 脚本，没有 `npm test` 或 E2E 脚本；不能声称运行了不存在的测试。UI 另按上述场景做联调验收。

在 BaoSys 根目录执行包含前端资源的完整产物验证：

```powershell
mvn verify
git diff --check
git status --short
```

确认 Web JAR 包含前端资源，`out` 中是 `baosys-manager.jar` 和 `baosys-manager-web.jar`。无 Docker、依赖解析失败等应记录为环境阻塞，不应当作测试通过。

## 8. 本次检查状态

已完成指定五组目录的跟踪文件清单、标识规范化对比、增量功能与依赖拆分、目标独有文件检查、POM/配置和数据库迁移静态审阅。此处为初次分析基线；后续实施已修改代码，验证结果另记。没有提交 Git。

下附逐文件差异清单；路径统一写成目标 BaoSys 路径，替换回 oceanacoustic 即可定位源侧文件。没有列出的 104 个规范化一致文件无需重复迁移。

## 附录 A：逐文件差异与处置

“新增”不表示建议整文件复制；以下处置优先于机械同步。

### baosys-common

| 目标相对路径 | 差异类型 | 建议处置 |
|---|---|---|
| `baosys-common/src/main/java/baosys/common/security/config/JwtDecoderConfiguration.java` | 同路径差异 | 仅改通用注释，可选；生产逻辑已一致 |
| `baosys-common/src/main/java/baosys/common/security/config/SecurityProperties.java` | 同路径差异 | 仅改通用注释，可选；不复制业务示例 |
| `baosys-common/src/test/java/baosys/common/security/resource/JwtPermissionConverterTest.java` | 同路径差异 | 改写为 Manager 多权限通用用例 |
| `baosys-common/src/main/java/baosys/common/security/package-info.java` | 目标独有 | 保留，不删除 |

### baosys-common-api

| 目标相对路径 | 差异类型 | 建议处置 |
|---|---|---|
| `baosys-common-api/src/main/java/baosys/common/api/package-info.java` | 目标独有 | 保留，不删除 |

### baosys-manager

| 目标相对路径 | 差异类型 | 建议处置 |
|---|---|---|
| `baosys-manager/README.md` | 同路径差异 | 按最终行为更新，排除试验场章节 |
| `baosys-manager/baosys-manager-api/src/main/java/baosys/manager/api/UserApi.java` | 同路径差异 | 仅取 Principal 操作者传递；排除引用/转交 API |
| `baosys-manager/baosys-manager-api/src/main/java/baosys/manager/api/UserReferenceApi.java` | 源侧新增 | Simulators 专属链路/测试，本次排除 |
| `baosys-manager/baosys-manager-api/src/main/java/baosys/manager/api/dto/UserBusinessReferenceResponse.java` | 源侧新增 | Simulators 专属链路/测试，本次排除 |
| `baosys-manager/baosys-manager-api/src/main/java/baosys/manager/api/dto/UserBusinessTransferRequest.java` | 源侧新增 | Simulators 专属链路/测试，本次排除 |
| `baosys-manager/baosys-manager-api/src/main/java/baosys/manager/api/dto/UserCreateRequest.java` | 同路径差异 | 合并可空 enabled |
| `baosys-manager/baosys-manager-api/src/main/java/baosys/manager/api/dto/UserReferenceResponse.java` | 源侧新增 | Simulators 专属链路/测试，本次排除 |
| `baosys-manager/baosys-manager-api/src/main/java/baosys/manager/api/dto/UserResponse.java` | 同路径差异 | 合并 enabled，保留 status 在线含义 |
| `baosys-manager/baosys-manager-api/src/main/java/baosys/manager/api/dto/UserUpdateRequest.java` | 同路径差异 | 合并可空 enabled |
| `baosys-manager/baosys-manager-service/Dockerfile` | 源侧新增 | 可选，映射 JAR/目录并适配基础镜像 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/assembler/UserAssembler.java` | 同路径差异 | 合并 enabled 输出 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/assembler/UserReferenceAssembler.java` | 源侧新增 | Simulators 专属链路/测试，本次排除 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/config/AuthProperties.java` | 同路径差异 | 合并 Access Cookie 默认 1800 秒 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/config/DefaultAdminInitializer.java` | 同路径差异 | 合并启用字段与初始化审计 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/controllers/UserController.java` | 同路径差异 | 仅取操作者传递 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/controllers/UserReferenceController.java` | 源侧新增 | Simulators 专属链路/测试，本次排除 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/error/GlobalExceptionHandler.java` | 同路径差异 | 合并缺参数 400 映射 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/model/PermissionCode.java` | 同路径差异 | 保留目标版本，差异为角色新增/格式 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/services/UserAppService.java` | 同路径差异 | 仅取操作者参数签名 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/services/UserBusinessReferenceGateway.java` | 源侧新增 | Simulators 专属链路/测试，本次排除 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/services/UserReferenceAppService.java` | 源侧新增 | Simulators 专属链路/测试，本次排除 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/services/impl/AuthAppServiceImpl.java` | 同路径差异 | 合并启停、过期校验与行锁 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/services/impl/UserAppServiceImpl.java` | 同路径差异 | 拆分后合并启停/审计/会话撤销，排除业务引用链 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/services/impl/UserReferenceAppServiceImpl.java` | 源侧新增 | Simulators 专属链路/测试，本次排除 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/infra/database/entity/PermissionChangeAuditEntity.java` | 源侧新增 | 新增通用审计实体 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/infra/database/entity/UserInfoEntity.java` | 同路径差异 | 合并 enabled 字段 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/infra/database/mapper/PermissionChangeAuditMapper.java` | 源侧新增 | 新增审计 Mapper |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/infra/database/mapper/UserInfoMapper.java` | 同路径差异 | 仅取用户行锁查询；排除引用查询 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/infra/database/mapper/UserLoginMapper.java` | 同路径差异 | 合并有效期过滤与会话锁 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/infra/simulators/SimulatorsClientConfiguration.java` | 源侧新增 | Simulators 专属链路/测试，本次排除 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/infra/simulators/SimulatorsClientProperties.java` | 源侧新增 | Simulators 专属链路/测试，本次排除 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/infra/simulators/SimulatorsUserBusinessReferenceClient.java` | 源侧新增 | Simulators 专属链路/测试，本次排除 |
| `baosys-manager/baosys-manager-service/src/main/resources/application.yml` | 同路径差异 | 仅取认证默认值；合并 Manager schema/历史表，排除客户端 |
| `baosys-manager/baosys-manager-service/src/main/resources/db/migration/V20260716_001__add_account_status_and_permission_audit.sql` | 源侧新增 | 通用 DDL 并入 V1 初始迁移；排除权限清洗 |
| `baosys-manager/baosys-manager-service/src/main/resources/db/scheduler/pg_cron_user_login_cleanup.sql` | 同路径差异 | 本次保留 public 版本；schema 改造另议 |
| `baosys-manager/baosys-manager-service/src/test/java/baosys/manager/app/controllers/AuthControllerIntegrationTest.java` | 同路径差异 | 合并 Cookie 时长与刷新过期验证 |
| `baosys-manager/baosys-manager-service/src/test/java/baosys/manager/app/controllers/UserControllerIntegrationTest.java` | 同路径差异 | 改造成 Manager 启停/审计场景，保留原回归 |
| `baosys-manager/baosys-manager-service/src/test/java/baosys/manager/app/controllers/UserControllerTest.java` | 同路径差异 | 新增源用例属转交，排除；另补 CRUD 操作者传递验证 |
| `baosys-manager/baosys-manager-service/src/test/java/baosys/manager/app/controllers/UserReferenceControllerIntegrationTest.java` | 源侧新增 | Simulators 专属链路/测试，本次排除 |
| `baosys-manager/baosys-manager-service/src/test/java/baosys/manager/app/model/PermissionCodeTest.java` | 源侧新增 | Simulators 专属链路/测试，本次排除 |
| `baosys-manager/baosys-manager-service/src/test/java/baosys/manager/app/services/impl/AuthAppServiceImplTest.java` | 同路径差异 | 合并禁用/过期/并发轮换用例 |
| `baosys-manager/baosys-manager-service/src/test/java/baosys/manager/app/services/impl/UserAppServiceImplTest.java` | 同路径差异 | 仅取通用撤权/审计用例，改造夹具并去掉引用依赖 |
| `baosys-manager/baosys-manager-service/src/test/java/baosys/manager/infra/database/PermissionCatalogMigrationIntegrationTest.java` | 源侧新增 | Simulators 专属链路/测试，本次排除 |
| `baosys-manager/baosys-manager-service/src/test/java/baosys/manager/infra/simulators/SimulatorsUserBusinessReferenceClientTest.java` | 源侧新增 | Simulators 专属链路/测试，本次排除 |
| `baosys-manager/baosys-manager-api/src/main/java/baosys/manager/api/package-info.java` | 目标独有 | 保留，不删除 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/services/package-info.java` | 目标独有 | 保留，不删除 |
| `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/infra/database/package-info.java` | 目标独有 | 保留，不删除 |

### baosys-manager-ui

| 目标相对路径 | 差异类型 | 建议处置 |
|---|---|---|
| `baosys-manager-ui/src/api/user.ts` | 同路径差异 | 新增均为引用/转交方法，保留目标版本 |
| `baosys-manager-ui/src/components.d.ts` | 同路径差异 | 按最终 UI 使用组件重新生成/核验 |
| `baosys-manager-ui/src/constants/permissions.ts` | 同路径差异 | 保留目标目录；descriptionKey 仅在实际使用时引入 |
| `baosys-manager-ui/src/i18n/locales/en-US.ts` | 同路径差异 | 仅取启停/会话提示，排除试验角色与生命周期文案 |
| `baosys-manager-ui/src/i18n/locales/zh-CN.ts` | 同路径差异 | 仅取启停/会话提示，排除试验角色与生命周期文案 |
| `baosys-manager-ui/src/types/user.ts` | 同路径差异 | 仅取 enabled 字段，排除业务引用/转交类型 |
| `baosys-manager-ui/src/views/login/LoginView.vue` | 同路径差异 | 保留 BaoSys 品牌，无功能增量 |
| `baosys-manager-ui/src/views/user/UserManagementView.vue` | 同路径差异 | 部分合并：状态列/启停/提示，排除生命周期抽屉 |
| `baosys-manager-ui/LICENSE` | 目标独有 | 保留，不删除 |
| `baosys-manager-ui/README.md` | 目标独有 | 保留，不删除 |

### baosys-manager-web

| 目标相对路径 | 差异类型 | 建议处置 |
|---|---|---|
| `baosys-manager-web/Dockerfile` | 源侧新增 | 可选，映射 JAR/目录并适配基础镜像 |
| `baosys-manager-web/src/main/java/baosys/managerweb/SpaWebConfiguration.java` | 同路径差异 | 仅空白差异，不迁移 |


## 附录 B：实际合并与验证结果（2026-09-23）

用户授权实施，并明确当前作为首版。实际变更已写入 BaoSys 当前工作区，未创建提交，源 OceanAcoustic 仓库未修改。

- 完成账号 enabled、授权审计、操作者传递、刷新会话撤销、过期检查与并发控制；UI 增加账号状态列和编辑开关。
- Token/Cookie 默认 1800 秒，默认管理员关闭，密码由外部显式提供。
- 删除旧日期迁移，改用 V1__init_manager.sql，一次初始化三张业务表和清理函数；采用独立 manager schema 和 flyway_schema_history_manager。未启用 baseline-on-migrate，未运行 repair，未操作实际业务数据库。
- 新增 Manager 服务和 Web Dockerfile，基础镜像可通过 BASE_IMAGE 覆盖；Docker 镜像构建与容器部署本次未执行。
- 不引入 Simulators 角色、用户引用/转交 API、客户端和 UI；保留原有预留系统类型及目标独有文件。
- 复核刷新实现后修正方案表述：expires_at 从登录时确定，刷新只轮换摘要，不延长该绝对期限。

验证记录：

| 验证 | 结果 |
|---|---|
| 全模块 Maven 单元测试及 verify（skip.frontend=true） | 通过，37 个单元/应用测试，无失败 |
| Manager integration-tests profile | 通过，21 个 PostgreSQL 集成测试，无失败 |
| Web integration-tests profile | 通过，3 个网关集成测试，无失败 |
| UI npm run build | vue-tsc 与 Vite 生产构建通过 |
| 应用 JAR 内容检查 | Web 含更新后的 UI；Manager 仅含 V1 初始迁移 |
| git diff --check / 排除依赖扫描 | 通过，无新增 Simulators 业务依赖或源项目包名 |

PostgreSQL 测试通过 Testcontainers 启动临时 PostgreSQL 17，验证了空库初版迁移、账号启停、审计保留、刷新过期、并发刷新仅成功一次、禁用与刷新竞争以及会话清理。集成测试 profile 与默认单元测试分开执行。UI 未进行浏览器人工联调；生产数据库与部署环境未做变更。

产物：out/baosys-manager.jar、out/baosys-manager-web.jar。上述实际结果优先于正文实施前的批次建议与检查基线。
