# 开发约定

当前维护 Manager，并进行 Pingmoniter 拨测子系统整合。新增能力优先在对应业务模块内完成，不预建空模块或业务目录。新迁入模块统一继承根 POM；已有 Java、Spring 和前端依赖版本以 BaoSys 为准。

文档注释统一使用多行格式：`/**` 和 `*/` 各占一行，正文每行以 ` * ` 开头；即使只有一句说明，也不写成单行 `/** ... */`。

## 分层与依赖

- `manager-api` 放接口契约、请求/响应 DTO 和校验注解，不放 Entity、Mapper、Service 实现或完整 starter。
- `manager-service` 依赖自己的 API。Controller 做输入适配，App Service 编排业务与事务，Infra 实现持久化和安全能力；Controller 不直接调用 Mapper。
- 公共协议放 `common-api`，安全与密码实现按现有模块归属；不恢复一个包揽所有能力的 Common 模块。
- 网关负责路由、认证和入口权限，不承载业务 CRUD 或直接访问数据库。
- 源码直接使用的库由本模块 POM 显式声明。根 POM 聚合模块、管理版本，应用模块选择 starter，公共库仅依赖所需框架库。
- 不依赖其他业务服务的实现 Jar，不对外暴露数据库实体，不为简单业务强行增加领域层。

业务 API 模块统一在 Java 基础包下使用以下目录和包名，目录层级与 package 声明保持一致：

| 目录 | 职责 | Manager 包名示例 |
| --- | --- | --- |
| `module/payloads` | 请求模型及其参数校验注解 | `baosys.manager.api.module.payloads` |
| `module/responses` | 应答模型及应答使用的嵌套结构 | `baosys.manager.api.module.responses` |
| `module/contract` | 接口契约，声明 HTTP 映射、参数和返回类型 | `baosys.manager.api.module.contract` |

请求与应答模型分开存放，不再混放到 `dto` 包；接口契约引用这两类模型，Controller 在 Service 模块实现契约。通用响应包装 `ApiResponse`、`PageResponse` 继续由 `common-api` 提供。调整包名时同步更新调用方和测试引用，不改变 HTTP 路径和 JSON 字段。

## 接口与权限

统一使用 `ApiResponse` 和 `PageResponse`。分页从 0 开始，限制每页最多 200 条；错误由统一异常处理返回稳定错误码和 traceId，不向客户端暴露堆栈。

业务接口及浏览器共享登录接口的参数、映射和请求/响应 DTO 写在 API 契约，Controller 实现契约并声明授权。Cookie 设置和认证流程实现保留在 Service；框架提供的 OAuth2/OIDC 端点由安全配置装配。网关入口路径保持 `/api/baosys-manager`，变更 context-path 时同步修改转发、前端和认证配置。

权限统一登记在根目录 `baosys-systems.yml`，采用 `MGR`/`CAT` 入口和 `<系统>:<资源>:<操作>` 接口命名。增加接口时同步目录、方法鉴权和前端操作控制；前端隐藏按钮不能替代后端鉴权。新权限不会自动提升已有账号。

请求与响应字段以代码契约为准，文档只解释重要行为和约束，避免重复抄写每个 DTO。

## 数据库与配置

当前 V1 是未部署首版的完整初始化脚本。正式部署后，变更必须使用递增 Flyway 迁移；新增字段明确默认值、可空性及已有数据处理，保留数据库唯一约束和必要外键。

业务变更与审计在同一事务中完成。时间使用 UTC 语义，数据库时间类型为 TIMESTAMPTZ。账号、权限、会话变化需验证撤销行为，不能只验证更新成功。

配置优先复用已有属性；新增环境变量在应用 YAML 中明确映射，并补充 [配置速查](development.md)。私钥、正式密码和客户端密钥不写入仓库或前端。

## 验证与文档

按影响范围验证：普通逻辑跑单元测试，数据库、迁移、事务或认证流程变更跑相关集成测试；前端变更执行类型检查与构建。命令集中在 [启动与部署](development.md)，数据库集成测试使用 Testcontainers，避免依赖个人开发库。

依赖变更额外检查 `mvn dependency:analyze "-Dskip.frontend=true"`。字节码分析不能识别所有自动配置和 SPI 使用，不凭 unused 报告删除 starter、驱动或运行时组件。

每次变更说明四项即可：解决什么问题、影响哪些行为、如何验证、是否需要配置或数据调整。无需为每个功能复制一组设计模板。

文档固定为根 README 和本目录三份主题文档。更新已有正文，删除过时描述；只写已实现能力，预留配置明确注明，不保留重复升级指南或历史实施计划。修改后检查相对链接和命令路径。
