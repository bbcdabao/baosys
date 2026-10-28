# BaoSys

BaoSys 当前只有 **Manager 管理系统**，处于首版开发阶段，尚未部署。其他业务系统尚未开发；公共模块和系统目录保留扩展能力。

Manager 已实现账号登录、用户增删改查、账号启停、权限分配、登录会话查询与撤销、授权变更审计。前端支持中英文和主题切换。系统配置页仍是界面骨架，尚未实现角色、组织机构、多租户或业务数据权限。

## 工程组成

| 模块 | 职责 |
|---|---|
| `baosys-common-api` | 统一响应与分页协议 |
| `baosys-common-security-core` | 安全配置、JWT 和权限基础能力 |
| `baosys-common-security-resource` | Servlet 服务的认证与接口授权 |
| `baosys-common-security-gateway` | 网关认证、入口授权和 CSRF |
| `baosys-common-password` | 密码编码与校验 |
| `baosys-manager/baosys-manager-api` | Manager 接口契约和 DTO |
| `baosys-manager/baosys-manager-service` | 业务、认证中心和数据库访问，默认 9090 |
| `baosys-manager-web` | API 网关与前端静态资源宿主，默认 8080 |
| `baosys-manager-ui` | Vue 管理端，开发服务器默认 5173 |

技术栈为 Java 17、Spring Boot、Spring Cloud Gateway、MyBatis Plus、PostgreSQL、Flyway，以及 Vue 3、TypeScript、Vite、Pinia、Arco Design。具体版本以 [pom.xml](pom.xml) 和 [package.json](baosys-manager-ui/package.json) 为准。

## 如何阅读

- [启动与部署](docs/development.md)：本地启动、必填配置、测试和打包。
- [系统设计](docs/system.md)：请求链路、认证权限、接口和数据库。
- [开发约定](docs/conventions.md)：分层、依赖、接口、数据库与文档维护规则。

首次启动使用空数据库或新 schema，由唯一的 `V1__init_manager.sql` 创建完整结构。需要准备签名密钥并显式启用管理员初始化，具体命令见启动文档。

当前 [系统目录](baosys-systems.yml) 中除 `MGR` 外还有空权限的预留项；目录登记不代表对应系统已经开发。本仓库运行链路仅包含 Manager。