# OceanAcoustic 对应模块合并到 BaoSys 设计

## 目标

将 `oceanacoustic` 中以下五组模块的有效修改迁移到 `baosys`：

| 源模块 | 目标模块 |
|---|---|
| `oceanacoustic-common` | `baosys-common` |
| `oceanacoustic-common-api` | `baosys-common-api` |
| `oceanacoustic-manager` | `baosys-manager` |
| `oceanacoustic-manager-ui` | `baosys-manager-ui` |
| `oceanacoustic-manager-web` | `baosys-manager-web` |

同时整理并合并相关 Markdown 文档。迁移后的项目继续使用 BaoSys 的模块名、包名、接口路径、Cookie 名和展示名称。

## 范围

迁移范围包括：

- 五组映射模块中的 Java、TypeScript、Vue、配置、SQL、测试和构建文件。
- 根 `pom.xml` 中支持上述模块变更所需的构建配置。
- 根 README、Common/Manager README，以及架构、部署和 Manager 设计文档中的相关修改。
- 源侧新增的 JWT 校验测试、登录会话清理 SQL和集成测试。

明确排除：

- `oceanacoustic-simulators`、`oceanacoustic-simulators-ui` 和
  `oceanacoustic-simulators-web`。
- 仅为 Simulators 服务、网关、前端或权限布局增加的共享文档内容。
- 构建产物、依赖目录和源仓库的临时输出。

## 合并策略

采用逐文件语义合并，不整体覆盖目标目录：

1. 对应文件先将源侧项目标识映射为 BaoSys 标识，再比较实际行为差异。
2. 将源侧新增文件复制到映射后的 BaoSys 路径。
3. 保留目标侧独有且仍有价值的文件，包括 `package-info.java`、LICENSE 和
   `baosys-manager-ui/README.md`。
4. 对根 POM 和共享文档进行人工语义合并，只吸收 Common/Manager 所需内容。
5. 不删除目标文件，除非文件已被源侧功能明确替代；本次已知差异不要求删除目标独有文件。

## 标识映射

至少执行并复查以下映射：

- `oceanacoustic` → `baosys`
- `szs.oceanacoustic` → `szs.baosys`
- `Ocean Acoustic` / `OceanAcoustic` → `BaoSys`
- 模块目录、Maven artifactId、Java 包路径和 API 路径使用对应 BaoSys 名称
- OceanAcoustic 专属 Cookie 名改为 BaoSys 现有 Cookie 名

迁移完成后，目标仓库的生产代码、测试、配置和相关文档中不得残留
`oceanacoustic` 标识。

## 主要功能变更

预期吸收的改动包括：

- Common 安全模块的 JWT issuer、子系统类型和网关安全校验增强。
- 安全配置说明、异常处理、权限转换和相关单元测试更新。
- Manager 的认证、用户、登录会话、默认管理员和持久化逻辑更新。
- 登录会话数据库清理函数、`pg_cron` 示例脚本及对应集成测试。
- Manager UI 的权限、认证会话、请求处理、路由、登录和用户管理页面更新。
- Manager Web 的路由、安全、SPA 宿主、测试资源和集成测试更新。
- 根 Maven 构建的测试排除规则与应用 Jar 输出配置；仅配置 BaoSys 已存在的
  Manager 可启动模块，不引入 Simulators 模块。

## 文档合并

文档以 BaoSys 当前架构为基线：

- 保留 BaoSys 的模块结构、端口、路径和环境变量名称。
- 合并与 JWT、权限、Refresh Token、会话清理、测试方式和部署配置一致的说明。
- 文档必须反映迁移后代码的真实行为。
- 删除或改写依赖 Simulators 才成立的描述，不新增 Simulators 设计文档。
- 保留目标侧 UI README；其内容如与迁移后的实现冲突，则同步修订。

## 冲突与错误处理

- 当源、目标行为冲突时，以源侧最新的 Common/Manager 行为为准，但继续使用
  BaoSys 命名和目标侧模块边界。
- 当源侧改动依赖被排除的 Simulators 内容时，只迁移可独立用于 Manager 的部分；
  无法独立工作的改动不进入目标。
- 若文档与代码冲突，以验证后的代码行为为准并修正文档。
- 若现有目标测试揭示 BaoSys 特有约束，优先保留该约束并调整迁移实现。

## 验证

完成标准：

1. 对五组模块重新执行规范化差异检查，确认源侧 Common/Manager 行为已迁移。
2. 扫描目标仓库，确认没有误引入 `oceanacoustic` 标识和 Simulators 模块引用。
3. 运行 Maven 单元测试和适用的集成测试。
4. 运行 Manager UI 的测试、类型检查或构建脚本，以仓库已有脚本为准。
5. 检查根 Maven 构建，确认模块依赖和应用 Jar 输出配置有效。
6. 审阅 Git 差异，确认目标独有文件未被误删、文档与代码一致。

