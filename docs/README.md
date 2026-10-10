# BaoSys 文档目录

- [Maven 依赖边界](architecture/dependency-boundaries.md)：直接依赖、公共库与应用 starter 的职责及审计方法。

- [新增子系统接入指南](subsystem-onboarding.md)：根目录配置、双语名称、权限目录和部署覆盖。

本文是仓库文档入口。当前实现说明按主题维护；迁移计划保留为历史资料，不作为部署操作指南。

## 建议阅读顺序

1. [项目概览](../README.md)：模块组成、快速开始和当前边界。
2. [本地开发指南](deployment/local_development.md)：数据库、管理员初始化、启动与测试。
3. [系统总体架构](architecture/system_architecture.md)：依赖、请求链路与数据所有权。
4. [Common 模块说明](../baosys-common/README.md)：公共安全能力、接入注解、配置默认值和使用示例。
5. [微服务模块架构规范](microservices_module_architecture_standard.md)：新增模块及代码组织规则。

## 当前实现文档

| 主题 | 文档 | 内容 |
|---|---|---|
| 部署配置 | [环境变量说明](deployment/environment_variables.md) | 按进程列出变量、应用默认值及一致性要求 |
| 公共模块 | [Common 模块说明](../baosys-common/README.md) | Common / Common API 边界、JWT、密码、网关及资源服务 |
| Manager 入口 | [Manager README](../baosys-manager/README.md) | 模块职责与构建入口 |
| Manager 设计 | [模块设计](modules/manager/manager_module_design.md) | 接口、应用服务、事务和业务约束 |
| 认证 | [认证与会话](modules/manager/auth_and_session.md) | 登录、刷新、Cookie、CSRF 和会话撤销 |
| 授权 | [权限模型](modules/manager/permission_model.md) | 权限目录、授权矩阵和变更规则 |
| 数据 | [数据库设计](modules/manager/database_design.md) | 三张业务表、Flyway、审计和会话清理 |
| 前端 | [Manager UI README](../baosys-manager-ui/README.md) | 开发启动、代理和构建 |

## 设计与历史资料

- [Common 与 JWT 拆分设计](superpowers/plans/2026-10-09-common-jwt-refactor.md)
- [迁移设计](superpowers/specs/2026-07-09-oceanacoustic-to-baosys-merge-design.md)
- [首轮迁移计划](superpowers/plans/2026-07-09-oceanacoustic-to-baosys-merge.md)
- [增量迁移计划](superpowers/plans/2026-09-23-oceanacoustic-incremental-merge.md)

上述资料包含当时的目标、候选文件和执行记录，可能提到当前不存在的文件或功能。当前行为以实现代码及上方主题文档为准。

## 文档模板

[模板目录](../templates/README.md) 包含开发任务、模块、接口、数据库、前端、权限、测试、评审和变更模板。

## 维护约定

- README 提供模块入口，详细规则放在对应主题文档，通过相对链接连接。
- 区分模块配置类默认值与应用 YAML 的覆盖值，例如 Common JWT 有效期为 7200 秒，Manager 使用 1800 秒。
- 功能实现后更新正文，删除相互矛盾的待办，不在末尾重复追加同一规则。
- 命令明确执行目录；示例路径沿用当前 `baosys.*` 包名和 `baosys` Maven groupId。
- 代码、配置、迁移或测试入口变更时，同步更新对应文档和导航。
