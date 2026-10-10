# Maven 依赖边界

## 声明原则

- 源码直接使用的类型，在本模块 POM 中声明其所属库，不能依赖另一个业务模块“顺便带入”。这也适用于公共库和测试代码。
- 根 POM 只聚合模块和管理版本，不通过父 POM 的 `dependencies` 给全部模块注入依赖。
- 公共库声明实际使用的框架库；可启动应用负责选择 Web、Security、数据库等 starter。第三方库自身的正常传递依赖仍由 Maven 解析，不逐层添加 exclusions，也不统一标记 optional。
- `test` 用于测试工具；`runtime` 用于无需源码编译引用的运行时功能；`provided` 用于由宿主提供的 Servlet API。不能仅凭静态分析把框架运行必需的库改为 test。
- 已由 Spring Boot / Spring Cloud BOM 管理的版本不在子模块重复填写。Nimbus、MyBatis 等未被这些 BOM 管理的直接依赖，在根 POM 管理并沿用整改前版本。

## 各模块职责

| 模块 | 依赖边界 |
| --- | --- |
| common-api | 纯公共协议，不依赖安全模块或应用框架 |
| security-core | 安全属性、密钥和 JWT 基础能力；不依赖 common-api 或 Web starter |
| security-issuer | 依赖 core 和签发所需库，不引入 Servlet / Gateway 启动环境 |
| security-resource | 直接依赖 common-api、core、Servlet 安全所需库；Servlet API 为 provided，不绑定嵌入式 Tomcat |
| security-gateway | 直接依赖 common-api、core、Gateway / WebFlux / Reactor 所需库；应用负责 Gateway starter |
| common-password | 密码接口、BCrypt 和自动配置所需库；不依赖 JWT 或 common-api |
| manager-api | 接口协议及自身使用的 common-api、Web 注解、Validation 注解，保留注解库的 optional 约定 |
| manager-service | 显式依赖其使用的 common-api、core、issuer、resource、password、manager-api；选择 Servlet、数据库和认证运行环境 |
| manager-web | 选择 Gateway、认证和 Actuator 运行环境；负载均衡、Caffeine 保留为 runtime，支持现有 lb:// 配置 |

公共库的测试依赖按使用情况声明 JUnit、AssertJ、Mockito、Boot Test，JUnit engine 保留用于执行测试。应用的测试仍使用 Boot 测试环境，同时直接声明源码使用的测试库。未使用的 Testcontainers JUnit 扩展和 manager-web 的 Security Test 已移除。

## 验证

在根目录执行（PowerShell 下保留参数引号）：

```powershell
mvn -B clean verify dependency:analyze -Pintegration-tests '-Dskip.frontend=true'
```

`dependency:analyze` 不应出现 `Used undeclared dependencies found`。它按字节码分析，不能独自决定是否删除依赖：

- starter、JUnit engine、数据库驱动、Flyway 方言等通过自动配置、反射或 SPI 使用，可能被标为 unused，必须保留。
- Spring Core / Beans、JWT、Reactor Netty 等可能被标为“仅测试直接引用”，但实际参与类型继承、框架运行或可执行 Jar 启动，不能缩成 test 作用域。
- 整改必须同时通过干净编译、单元测试、数据库/网关集成测试和应用打包。

若只修改 Java 依赖，可以跳过前端重建；发布时仍按原有前端构建流程生成静态资源。
