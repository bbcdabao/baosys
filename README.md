# BaoSys

BaoSys 是一个面向多业务系统的前后端分离基础工程。当前仓库已经实现公共 API 协议、安全公共模块、Manager 管理服务、API 网关与 Vue 管理端，后续业务服务按 `xxx-api + xxx-service` 模式扩展。

## 当前能力

- 用户登录、刷新令牌、退出登录。
- 用户新增、修改、删除和分页查询。
- 当前用户信息查询。
- 登录会话查询、单客户端下线和全部客户端下线。
- 基于权限编码的接口授权和前端操作控制。
- JWT 签发与校验、HttpOnly Cookie 和可选 CSRF 校验。
- PostgreSQL、Flyway 和 MyBatis Plus。
- Spring Cloud Gateway 路由与 Vue SPA 宿主。
- 中英文界面、主题切换和响应式管理端布局。

## 技术基线

| 领域 | 技术 |
|---|---|
| Java | Java 17 |
| 后端 | Spring Boot 3.5.7 |
| 微服务基础 | Spring Cloud 2025.0.1 |
| 网关 | Spring Cloud Gateway WebFlux |
| 数据访问 | MyBatis Plus 3.5.16 |
| 数据库 | PostgreSQL + Flyway |
| 测试 | JUnit 5、Mockito、Testcontainers 2.x |
| 前端 | Vue 3、TypeScript、Vite、Pinia |
| UI | Arco Design Vue |

依赖版本以根目录 [`pom.xml`](pom.xml) 和 [`package.json`](baosys-manager-ui/package.json) 为准。

## 工程结构

```text
baosys
├── baosys-common-api
│   └── 平台统一响应和分页协议
├── baosys-common
│   └── 密码、JWT、网关和资源服务公共能力
├── baosys-manager
│   ├── baosys-manager-api
│   └── baosys-manager-service
├── baosys-manager-web
│   └── API 网关与 SPA 静态资源宿主
├── baosys-manager-ui
│   └── Vue 管理端
├── docs
│   └── 架构、模块和部署文档
└── templates
    └── 设计、接口、数据库和测试模板
```

模块依赖和新服务创建规则参见 [`docs/microservices_module_architecture_standard.md`](docs/microservices_module_architecture_standard.md)。

## 请求链路

```text
Browser
  → baosys-manager-ui
  → baosys-manager-web :8080
  → baosys-manager-service :9090
  → PostgreSQL :5432
```

Manager 用户接口的路径转换示例：

```text
外部：/api/baosys-manager/v1/users
网关：StripPrefix=1
下游：/baosys-manager/v1/users
服务 context-path：/baosys-manager
Controller：/v1/users
```

## 快速开始

### 1. 准备环境

- JDK 17。
- Maven 3.9+。
- Node.js 20.19+，推荐使用工程构建配置中的 Node 22。
- PostgreSQL 17，或可运行 PostgreSQL 容器的 Docker。
- 运行后端集成测试时必须启动 Docker。

### 2. 启动 PostgreSQL

本地默认连接：

```text
URL:      jdbc:postgresql://localhost:5432/app_db
Username: postgres
Password: postgres123456
```

可以使用本地 PostgreSQL，也可以启动临时容器：

```shell
docker run --name baosys-postgres \
  -e POSTGRES_DB=app_db \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres123456 \
  -p 5432:5432 \
  -d postgres:17-alpine
```

PowerShell 请将命令写成一行，或使用反引号替代反斜杠续行。

### 3. 构建后端依赖

在仓库根目录执行：

```shell
mvn install -DskipTests "-Dskip.frontend=true"
```

### 4. 启动 Manager 服务

```shell
mvn -f baosys-manager/baosys-manager-service/pom.xml spring-boot:run
```

服务地址：

```text
http://localhost:9090/baosys-manager
```

### 5. 启动网关

```shell
mvn -f baosys-manager-web/pom.xml spring-boot:run "-Dskip.frontend=true"
```

网关地址：

```text
http://localhost:8080
```

### 6. 启动前端开发服务器

```shell
cd baosys-manager-ui
npm install
npm run dev
```

浏览器访问：

```text
http://localhost:5173
```

完整启动说明、环境覆盖和排错方法参见 [`docs/deployment/local_development.md`](docs/deployment/local_development.md)。

## 构建与测试

后端完整测试：

```shell
mvn test "-Dskip.frontend=true"
```

根 POM 默认排除使用 Testcontainers 的 `*IntegrationTest`，因此该命令只运行
非集成测试，不需要 Docker。数据库集成测试需要通过专用测试配置执行，并要求
本机 Docker 可用。

前端类型检查与生产构建：

```shell
cd baosys-manager-ui
npm run build
```

包含前端资源的完整打包：

```shell
mvn clean package
```

主要产物：

```text
baosys-manager/baosys-manager-service/target/baosys-manager.jar
baosys-manager-web/target/baosys-manager-web.jar
```

执行 `mvn verify` 后，这两个可启动 Jar 还会复制到仓库根目录的 `out` 目录。

## 文档导航

### 核心文档

- [系统总体架构](docs/architecture/system_architecture.md)
- [微服务模块架构规范](docs/microservices_module_architecture_standard.md)
- [Manager 模块设计](docs/modules/manager/manager_module_design.md)
- [认证与会话设计](docs/modules/manager/auth_and_session.md)
- [权限模型](docs/modules/manager/permission_model.md)
- [Manager 数据库设计](docs/modules/manager/database_design.md)
- [本地开发指南](docs/deployment/local_development.md)
- [环境变量说明](docs/deployment/environment_variables.md)

### 补充资料

- [文档模板](templates)

## 当前边界

当前版本定位为基础工程和 Manager 第一阶段：

- 权限直接配置到用户，尚未实现角色模型。
- `SIMULATION` 和 `SIMULATORS` 已预留系统类型，尚未配置权限目录。
- 网关中已预留 `baosys-plant` 路由，但仓库内暂未包含 Plant 服务。
- 系统配置页面目前以界面骨架为主，尚未形成后端配置管理闭环。
- 已具备账号授权与启停审计；暂无组织机构、多租户、记录级数据权限、通用业务审计和动态菜单管理。

新增功能前应先明确归属服务，再按模块架构规范创建契约、实现、迁移和测试。

## 首版数据库与管理员

Flyway 从 `V1__init_manager.sql` 初始化完整模型，默认使用独立的 `manager` schema（可用 `MANAGER_DB_SCHEMA` 覆盖）。首次创建管理员需在启动 Manager 前显式设置 `DEFAULT_ADMIN_ENABLED=true` 和至少 12 位的 `DEFAULT_ADMIN_PASSWORD`；默认关闭自动初始化。

数据库集成测试需 Docker，可在仓库根目录执行 `mvn -Pintegration-tests -pl :baosys-manager-service -am verify "-Dskip.frontend=true"`。
