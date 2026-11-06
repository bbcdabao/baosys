# Maven 依赖边界

**简体中文** | [English](dependency-boundaries.en.md)

本文以当前各模块 POM 为准；聚合入口见 [根 POM](../../pom.xml)。

## 1. 模块与依赖方向

根项目使用 Java 17、Spring Boot 3.5.7 和 Spring Cloud 2025.0.1。根 POM 聚合模块并管理版本，不通过自身的 `dependencies` 向所有模块注入依赖。`baosys-manager` 是聚合 POM，其 API 和 Service 的 Maven parent 均直接指向根项目。

下表省略模块名的 `baosys-` 前缀：

| 模块 | 当前直接依赖和职责 |
|---|---|
| common-api | 无第三方依赖，提供 `ApiResponse`、`PageResponse` |
| common-security-core | Spring Boot 配置属性及 Spring Security JWT/authority 基础能力；不依赖 common-api 或 Web starter |
| common-security-resource | core、Servlet 资源服务安全所需库；Servlet API 为 provided；不依赖 common-api |
| common-security-gateway | core、Gateway/WebFlux/Reactor、OAuth2 Client/Resource Server 所需库；不依赖 common-api |
| common-password | Spring 配置与 BCrypt 密码能力；不依赖 JWT 或 common-api |
| manager-api | common-api；spring-web、jakarta.validation-api 为 optional |
| manager-service | manager-api、common-api、core、resource、password；选择 Servlet、授权服务器、资源服务器、数据库和 Session JDBC 运行环境 |
| manager-web | gateway；选择 Gateway、OAuth2 Client/Resource Server、Actuator 运行环境；LoadBalancer 与 Caffeine 为 runtime |

当前没有 `baosys-common` 兼容模块，也没有 `baosys-common-security-issuer` 模块。JWT 签发和授权服务器装配位于 Manager Service 的 `infra/security`。

`baosys-manager-ui` 不加入 Maven Reactor。Manager Web 的 `frontend-maven-plugin` 执行 Node/npm 安装、`npm install --no-audit --no-fund`、`npm run build`，资源插件将 `dist` 复制到 Jar 的静态资源目录。

## 2. 声明原则

- 本模块源码直接引用的类型，应在本模块 POM 中声明所属库，包括测试代码。
- 公共库声明实际使用的框架库；可启动应用选择 starter 和运行环境。不要依赖其他业务实现模块间接带入依赖。
- `test` 用于测试工具，`runtime` 用于无需源码编译引用的运行时功能，`provided` 用于宿主提供的 Servlet API。
- Boot/Cloud BOM 已管理的版本不重复声明；Nimbus、MyBatis 等额外版本由根 POM 管理。
- API Jar 不依赖 Service 实现；Common 不依赖业务模块。
- 第三方库的正常传递依赖交给 Maven 解析，不统一添加 exclusions 或 optional。

Manager Service 的 PostgreSQL 驱动和 Flyway PostgreSQL 方言是 runtime 依赖。Manager Web 保留 OAuth2 Client starter，但默认配置没有客户端注册；内部共享 Cookie 登录不依赖 OAuth2 Client WebSession。

## 3. 验证方式

在根目录执行：

```powershell
mvn -B clean verify dependency:analyze -Pintegration-tests '-Dskip.frontend=true'
```

该命令是依赖调整后的验证入口，不代表本文更新时已执行通过。数据库集成测试需要可用的 Docker/Testcontainers 环境。

`dependency:analyze` 基于字节码，不能单独决定依赖是否删除：

- starter、JUnit engine、数据库驱动、Flyway 方言可能通过自动配置、反射或 SPI 使用。
- 仅在测试源码直接出现的类型，仍可能属于应用启动所必需的运行依赖。
- 依赖整改还应通过编译、单元测试、集成测试和应用打包。

`skip.frontend=true` 跳过前端插件执行；完整发布构建应生成最新的前端静态资源。
