# BaoSys 微服务模块架构规范

> 本规范以当前 BaoSys 工程为基线，用于约束新微服务、公共模块、网关宿主和服务调用代码。
> 依赖版本以根目录 `pom.xml` 为唯一事实来源；当前工程采用 Java 17、Spring Boot 3.5.x 和 Spring Cloud 2025.0.x。

## 1. 目标与适用范围

本规范解决以下问题：

1. 统一微服务模块、包和类的命名方式。
2. 分离对外契约与内部实现，避免调用方依赖服务实现。
3. 统一 HTTP 路径、响应、异常、分页和追踪约定。
4. 根据业务复杂度选择简单分层或领域分层，避免机械套用 DDD。
5. 明确公共模块、业务服务、网关和前端的依赖边界。
6. 为人工开发、代码审查和 AI 生成新模块提供可执行清单。

规范中的关键词：

- **必须**：新代码应遵守，偏离时需要在模块设计中说明原因。
- **建议**：默认采用；有明确收益时可以调整。
- **禁止**：会破坏模块边界，不应采用。

## 2. 平台模块全景

当前工程包含四类模块：

```text
baosys
├── baosys-common-api                 # 平台公共接口协议
├── baosys-common                     # 兼容基础入口，无安全装配
├── baosys-manager
│   ├── baosys-manager-api            # manager 对外契约
│   └── baosys-manager-service        # manager 可启动服务
├── baosys-manager-web                # API 网关与 SPA 宿主
└── baosys-manager-ui                 # Vue 管理端，独立 Node 工程
```

各模块职责：

| 模块类型 | 是否可启动 | 主要内容 |
|---|---:|---|
| `baosys-common-api` | 否 | `ApiResponse`、`PageResponse` 等平台公共协议 |
| `baosys-common` | 否 | 兼容基础入口，无安全装配 |
| `baosys-common-security-*` | 否 | core / issuer / resource / gateway 分离的安全能力 |
| `baosys-common-password` | 否 | 密码编码自动配置 |
| `xxx-api` | 否 | 当前服务的 HTTP 接口、请求/响应 DTO、公开枚举 |
| `xxx-service` | 是 | Controller、应用服务、业务规则和基础设施实现 |
| `xxx-web` | 是 | 网关路由、统一入口、SPA 静态资源宿主 |
| `xxx-ui` | 否 | 前端页面、状态管理和 API 适配 |

## 3. 总体依赖规则

### 3.1 Maven 模块依赖

```text
xxx-service
    ├── xxx-api
    │     └── baosys-common-api
    └── baosys-common-security-resource

yyy-service（调用方）
    └── xxx-api
          └── baosys-common-api

xxx-web
    └── baosys-common-security-gateway
          └── baosys-common-security-core
                └── baosys-common-api
```

必须遵守：

1. `xxx-service` 必须依赖自己的 `xxx-api`。
2. `xxx-api` 可以依赖 `baosys-common-api`，禁止依赖 `baosys-common`。
3. 调用方只依赖被调用方的 `xxx-api`，禁止依赖其 `xxx-service`。
4. `baosys-common-api` 禁止反向依赖任何业务模块。
5. `baosys-common` 禁止依赖具体业务服务。
6. 不同 `xxx-service` 之间禁止通过实现 Jar 共享 Entity、Mapper 或 Service。

### 3.2 运行时调用关系

```text
Browser
   │
   ▼
baosys-manager-ui
   │  /api/{service}/v1/**
   ▼
baosys-manager-web
   │  Gateway 路由
   ▼
xxx-service
   │
   ▼
Database / Cache / MQ / Other Services
```

网关负责入口路由和跨服务公共能力；业务判断必须留在业务服务中。

## 4. 业务微服务模块结构

每个独立微服务必须采用契约 Jar 与实现 Jar 分离的结构：

```text
xxx
├── xxx-api
└── xxx-service
```

推荐的完整目录：

```text
xxx
├── xxx-api
│   ├── src/main/java/baosys/xxx
│   │   └── api
│   │       ├── XxxApi.java
│   │       ├── dto
│   │       │   ├── XxxCreateRequest.java
│   │       │   ├── XxxUpdateRequest.java
│   │       │   └── XxxResponse.java
│   │       └── enums
│   │           └── XxxType.java
│   └── pom.xml
│
└── xxx-service
    ├── src/main/java/baosys/xxx
    │   ├── XxxApplication.java
    │   ├── app
    │   │   ├── controllers
    │   │   ├── services
    │   │   │   └── impl
    │   │   ├── assembler
    │   │   ├── config
    │   │   ├── error
    │   │   └── model
    │   ├── domain                     # 复杂业务可选
    │   │   ├── model
    │   │   ├── agent
    │   │   ├── service
    │   │   └── event
    │   └── infra
    │       ├── database
    │       │   ├── entity
    │       │   ├── mapper
    │       │   └── converter
    │       ├── agentimpl              # 使用 domain.agent 时创建
    │       ├── client
    │       ├── cache
    │       ├── mq
    │       ├── security
    │       └── config
    ├── src/main/resources
    │   ├── application.yml
    │   └── db/migration
    ├── src/test
    └── pom.xml
```

目录只在存在实际代码时创建，禁止预先生成大量空包。

## 5. 按复杂度选择分层

### 5.1 简单业务模式

适用于账户管理、基础资料、字典等以 CRUD 和流程编排为主的服务。当前 `baosys-manager-service` 采用该模式。

```text
Controller
    ↓
App Service
    ↓
Mapper / Client / Cache
```

允许：

- App Service 直接依赖 `infra.database.mapper`。
- API Request/Response 直接作为简单用例的输入和输出。
- 使用独立 Assembler 或 Converter 隔离重复转换逻辑。

即使使用简单模式，也必须保证 Controller 不直接访问 Mapper。

### 5.2 领域业务模式

适用于状态机、复杂规则、多聚合协作、算法编排等业务。

```text
Controller
    ↓
App Service
    ↓
Domain Model / Domain Service
    ↓
Domain Agent（接口）
    ↑
Infra AgentImpl（实现）
```

必须遵守：

1. `domain` 不依赖 Controller、API DTO 或 App Service。
2. `domain` 不依赖 MyBatis、JPA、Redis、Feign、HTTP 或 MQ SDK。
3. 外部能力通过 `domain.agent` 中的业务接口表达。
4. `infra.agentimpl` 负责技术实现与对象转换。
5. Domain 对象表达业务概念，不与数据库 Entity 混用。

不要仅为目录“看起来完整”而引入 Domain 和 Agent。业务复杂度出现后再升级，升级时必须保持依赖方向不变。

## 6. `xxx-api` 契约 Jar 规范

### 6.1 允许包含的内容

```text
api interface
request / response DTO
跨服务公开枚举
契约级常量
Jakarta Validation 注解
必要的 Jackson 注解
```

示例：

```java
package baosys.sample.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import baosys.common.api.ApiResponse;
import baosys.sample.api.dto.SampleCreateRequest;
import baosys.sample.api.dto.SampleResponse;

public interface SampleApi {

    String SERVICE_NAME = "baosys-sample";
    String PREFIX = "/v1/samples";

    @GetMapping("/{id}")
    ApiResponse<SampleResponse> getById(@PathVariable("id") long id);

    @PostMapping
    ApiResponse<SampleResponse> create(
            @Valid @RequestBody SampleCreateRequest request);
}
```

接口约束：

1. `SERVICE_NAME` 与 `spring.application.name` 保持一致。
2. `PREFIX` 只包含服务内部版本化路径，例如 `/v1/users`。
3. API 接口类上不写 `@RequestMapping`，由 Controller 绑定 `PREFIX`。
4. 参数注解必须写在 API 接口方法上，Controller 实现不重复声明。
5. 参数名需要显式稳定时，使用 `@PathVariable("id")`、`@RequestParam(name = "page")`。
6. API 方法不包含默认业务实现。
7. API 接口不绑定 Feign、网关或具体服务发现方案。

### 6.2 DTO 规范

对外 DTO 建议使用不可变 `record`：

```java
public record SampleCreateRequest(
        @NotBlank String name,
        @NotNull SampleType type) {
}

public record SampleResponse(
        long id,
        String name,
        SampleType type) {
}
```

命名统一使用：

| 用途 | 命名 |
|---|---|
| 新增请求 | `XxxCreateRequest` |
| 修改请求 | `XxxUpdateRequest` |
| 查询条件 | `XxxQueryRequest` |
| 对外响应 | `XxxResponse` |
| 分页响应 | `PageResponse<XxxResponse>` |
| 公开枚举 | 业务名称，例如 `SystemType` |

新契约不再混用 `DTO`、`VO`、`BO` 表示 HTTP 响应。字段名应表达真实语义，避免 `data1`、`status`、`pass` 等含义不完整的名称。

以下对象禁止放入 `xxx-api`：

- Controller 和 Spring Boot 启动类。
- App Service、Domain Service 及其实现。
- Entity、PO、DO、Mapper、Repository。
- FeignClient、Redis、MQ、数据库相关代码。
- 内部 Command、Context、临时流程对象。
- 仅服务内部使用的异常和配置类。

### 6.3 API 模块依赖

API Jar 必须保持轻量。参考当前 `baosys-manager-api`：

```xml
<dependencies>
  <dependency>
    <groupId>baosys</groupId>
    <artifactId>baosys-common-api</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-web</artifactId>
    <optional>true</optional>
  </dependency>
  <dependency>
    <groupId>jakarta.validation</groupId>
    <artifactId>jakarta.validation-api</artifactId>
    <optional>true</optional>
  </dependency>
</dependencies>
```

禁止在 API Jar 中引入完整 Web Starter、数据库 Starter、OpenFeign Starter 或业务实现模块。

## 7. `xxx-service` 实现 Jar 规范

### 7.1 启动类

启动类放在服务根包，保证组件扫描覆盖当前服务：

```java
package baosys.sample;

@SpringBootApplication
@MapperScan("baosys.sample.infra.database.mapper")
public class SampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }
}
```

服务需要平台公共能力时，通过对应 `baosys-common-security-*` / `baosys-common-password` 模块提供的自动配置或启用注解接入，禁止复制公共实现。

### 7.2 Controller

Controller 必须实现 API Jar 中的接口：

```java
@RestController
@RequestMapping(SampleApi.PREFIX)
public class SampleController implements SampleApi {

    private final SampleAppService sampleAppService;

    public SampleController(SampleAppService sampleAppService) {
        this.sampleAppService = sampleAppService;
    }

    @Override
    public ApiResponse<SampleResponse> getById(long id) {
        return ApiResponse.success(
                sampleAppService.getById(id),
                TraceIds.current());
    }
}
```

Controller 只负责：

- HTTP 参数适配和校验触发。
- 调用一个明确的应用服务用例。
- 包装统一响应。
- 声明接口级授权规则。

Controller 禁止：

- 直接访问 Mapper、Repository、Redis 或远程 Client。
- 编写事务和复杂业务分支。
- 返回 Entity。
- 捕获所有异常并自行拼接错误响应。

### 7.3 App Service

应用服务负责一次完整用例的事务边界和流程编排：

```java
public interface SampleAppService {

    SampleResponse getById(long id);

    SampleResponse create(SampleCreateRequest request);
}

@Service
public class SampleAppServiceImpl implements SampleAppService {

    @Override
    @Transactional(readOnly = true)
    public SampleResponse getById(long id) {
        // 查询与组装
    }

    @Override
    @Transactional
    public SampleResponse create(SampleCreateRequest request) {
        // 校验、持久化与组装
    }
}
```

事务约定：

1. 写操作使用 `@Transactional`。
2. 读操作使用 `@Transactional(readOnly = true)`。
3. 事务边界放在 App Service，不放在 Controller。
4. 远程调用与长耗时任务需要评估事务占用，不应无条件包在数据库事务中。

简单用例可以直接使用 API DTO；复杂用例建议定义内部 `XxxCommand`、`XxxQuery` 和 `XxxResult`，避免 HTTP 契约侵入领域规则。

### 7.4 Assembler、Model 与 Error

- `app.assembler`：Entity、Domain Model 与 Response 之间的转换。
- `app.model`：当前服务内部使用的应用模型、权限目录等。
- `app.error`：业务异常、统一异常处理器和服务内部错误码映射。
- `app.config`：当前服务自己的配置属性和 Bean 装配。

`ApiResponse` 只在 Controller 和统一异常处理器中创建，App Service 不返回 `ApiResponse`。

### 7.5 Infra

基础设施代码按技术能力组织：

| 包 | 内容 |
|---|---|
| `infra.database.entity` | 数据库映射实体 |
| `infra.database.mapper` | MyBatis Mapper 或 Repository |
| `infra.database.converter` | 持久化格式转换 |
| `infra.client` | 其他服务或第三方客户端 |
| `infra.cache` | 缓存读写 |
| `infra.mq` | 消息发布与消费适配 |
| `infra.security` | 当前服务特有的安全基础设施 |
| `infra.config` | 数据库、分页等技术配置 |

Entity 只反映持久化结构，不作为 HTTP Response，也不跨服务传输。

## 8. 公共模块规范

### 8.1 `baosys-common-api`

只放平台级、跨服务稳定的协议。目前包括：

```text
baosys.common.api
├── ApiResponse.java
└── PageResponse.java
```

适合继续加入：

- 通用分页查询对象。
- 稳定的平台错误码接口。
- 跨服务通用值对象。

禁止加入：

- 某个业务服务专属 DTO。
- Spring Security、数据库或网关实现。
- 无明确复用场景的工具类。

### 8.2 Common 实现模块

现有能力、启用注解、条件装配和配置示例见 [Common 模块说明](../baosys-common/README.md)。

安全能力按 core、issuer、resource、gateway 分开依赖，密码独立为 common-password；旧 common 仅兼容基础依赖。签发和密码模块登记自动配置，resource/gateway 由原启用注解显式导入，不注册基础 imports。新增可复用自动配置能力时建议采用：

```text
接口或配置属性
    +
默认实现
    +
AutoConfiguration
    +
@ConditionalOnClass / @ConditionalOnProperty / @ConditionalOnMissingBean
```

公共能力必须允许业务服务按需启用和覆盖默认 Bean。自动配置类登记在：

```text
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

只有跨两个及以上服务、且语义稳定的能力才进入对应 Common 实现模块。不要把 Common 变成无法治理的 `utils` 集合。

## 9. HTTP 路径规范

路径分为三层：

```text
外部网关路径：/api/{service-name}/v1/{resources}
服务 context-path：/{service-name}
Controller 前缀：/v1/{resources}
```

当前 manager 示例：

```text
浏览器请求：
/api/baosys-manager/v1/users

网关 StripPrefix=1 后：
/baosys-manager/v1/users

manager-service context-path：
/baosys-manager

UserApi.PREFIX：
/v1/users
```

规则：

1. API 版本放在服务契约中，例如 `/v1/users`。
2. `/api/{service-name}` 是网关入口约定，不写入 `XxxApi.PREFIX`。
3. 资源路径使用复数名词和短横线，例如 `/login-sessions`。
4. 查询过滤使用 `@RequestParam`，资源标识使用 `@PathVariable`。
5. 新版本并行演进时新增 `/v2`，不要直接破坏已有契约。

## 10. 响应、分页、异常与追踪

### 10.1 统一响应

业务 API 当前统一返回：

```java
ApiResponse<T>
```

字段语义：

```text
code       业务响应码，成功固定为 SUCCESS
message    稳定的用户可读消息
data       业务数据
traceId    请求追踪标识
timestamp  UTC 响应时间
```

禁止在不同服务中重新定义另一套通用响应结构。

### 10.2 分页

分页统一使用：

```java
PageResponse<T>(
    List<T> items,
    long total,
    int page,
    int size,
    int totalPages
)
```

当前页码从 `0` 开始；查询接口必须限制最大 `size`，数据库分页组件也应设置相同上限。

### 10.3 异常

服务使用 `@RestControllerAdvice` 统一处理：

- 参数校验错误。
- 请求体解析错误。
- 业务异常。
- 未认证和无权限。
- 未预期异常。

未预期异常必须记录完整日志，对外返回稳定错误码，不泄露内部堆栈。Controller 不重复编写 `try/catch`。

### 10.4 Trace ID

服务从 `X-Trace-Id` 接收或生成追踪标识，并写入：

- 响应头。
- `ApiResponse.traceId`。
- 日志 MDC。

跨服务 HTTP 调用必须继续传递该请求头。

## 11. 数据库与配置规范

### 11.1 数据库迁移

数据库结构必须通过 Flyway 管理：

```text
src/main/resources/db/migration
└── V{version}__{description}.sql
```

当前 Manager 从 `V1__init_manager.sql` 开始，后续使用递增版本，例如 `V2__add_user_lock.sql`。

要求：

1. 已执行的迁移脚本禁止修改，变更通过新版本脚本完成。
2. 唯一性、外键和必要的状态约束同时落实到数据库。
3. 查询条件和关联字段建立合适索引。
4. 时间统一保存为带时区类型，并使用 UTC。
5. 密码、令牌等敏感值只保存摘要或不可逆结果。

### 11.2 Entity 与审计字段

Entity 使用 `*Entity` 命名。审计字段统一采用：

```text
createdAt
updatedAt
createdBy
updatedBy
```

审计填充器属于 `infra.config`。业务需要记录真实操作者时，应从可信认证上下文获取，而不是由客户端传入。

### 11.3 应用配置

配置遵循：

1. 通用默认值写入 `application.yml`。
2. 环境差异通过环境变量或配置中心覆盖。
3. 模块配置使用明确前缀和 `@ConfigurationProperties`。
4. 配置属性较多或有范围约束时使用 `@Validated`。
5. 测试配置放入 `src/test/resources/application-test.yml`。

## 12. 跨服务调用规范

调用方依赖被调用方 `xxx-api`，并在自己的 `infra.client` 中定义客户端：

```java
@FeignClient(
        name = SampleApi.SERVICE_NAME,
        contextId = "sampleClient",
        path = SampleApi.PREFIX)
public interface SampleClient extends SampleApi {
}
```

FeignClient 禁止放入被调用方 `xxx-api`，原因是：

1. 契约 Jar 不应绑定 OpenFeign。
2. 不同调用方可能需要不同超时、降级和认证配置。
3. 服务提供方不应被迫引入调用端依赖。

调用方应在 `infra.client` 或 `infra.agentimpl` 边界完成：

- `ApiResponse<T>` 拆包。
- 业务错误码转换。
- 超时、重试和远程异常转换。
- Response 到内部模型或 Domain Model 的转换。

App Service 和 Domain 不应到处判断远程 `ApiResponse.code`。

## 13. 网关与前端宿主规范

`baosys-manager-web` 同时承担：

- Spring Cloud Gateway 路由。
- 统一入口的公共过滤能力。
- Vue SPA 静态资源宿主。
- SPA 客户端路由回退。

网关禁止承载具体业务 CRUD 或直接访问业务数据库。

`baosys-manager-ui` 是独立 Node 工程，不加入 Maven `<modules>`。生产构建由 `baosys-manager-web` 的 `frontend-maven-plugin` 执行，并将 `dist` 复制到 Jar 的 `static` 目录。

前端约定：

1. 所有 API 通过统一请求封装访问。
2. API 路径常量集中维护。
3. 页面不直接处理 Axios 底层错误结构。
4. TypeScript 类型与 API Response 保持一致。
5. 权限只用于界面展示控制，最终业务授权由服务端完成。

## 14. 测试规范

每个服务至少覆盖三层测试：

### 14.1 单元测试

适合测试：

- App Service 业务分支。
- Converter、Assembler 和值对象。
- 密码、令牌等公共组件。
- 异常与边界条件。

外部依赖使用 Mock，测试应快速且不启动完整 Spring Context。

### 14.2 Controller 测试

验证：

- 请求参数绑定和校验。
- HTTP 状态与统一响应结构。
- Controller 是否正确调用应用服务。
- 接口授权规则。

### 14.3 集成测试

数据库集成测试使用 Testcontainers 和真实数据库迁移，当前工程采用 Testcontainers 2.x：

```text
org.testcontainers:testcontainers-junit-jupiter
org.testcontainers:testcontainers-postgresql
org.testcontainers.postgresql.PostgreSQLContainer
```

集成测试必须验证：

- Flyway 迁移可以从空库执行。
- 数据库约束和 Mapper 查询正确。
- 登录、刷新、分页等关键流程能够闭环。
- API Jar 注解与 Controller 实现组合后可正常工作。

网关使用 `WebTestClient` 验证认证、路由改写和 SPA 回退。

命名约定：

```text
XxxTest                  # 单元或切片测试
XxxIntegrationTest       # 需要完整上下文或外部容器
```

根 POM 的 Surefire 默认排除 `*IntegrationTest`，常规 `mvn test` 和 `mvn verify` 只运行非集成测试。根 POM 已通过 `integration-tests` profile 配置 Maven Failsafe，在 `integration-test` / `verify` 阶段执行集成测试：

```shell
mvn -Pintegration-tests verify "-Dskip.frontend=true"
```

数据库集成测试需要 Docker；仅执行 Manager Web 的网关集成测试无需数据库。按模块运行的命令见 [本地开发指南](deployment/local_development.md#8-常用开发命令)。

## 15. Maven 与构建规范

根 `pom.xml` 统一管理：

- Java 版本。
- Spring Boot Parent。
- Spring Cloud BOM。
- MyBatis Plus 等公共依赖版本。
- 内部模块版本。
- 编译和源码 Jar 插件。
- 单元测试与集成测试的命名边界。
- 可启动应用 Jar 的统一输出目录。

模块要求：

1. API 和 Common API 使用普通 Jar，不配置 Spring Boot 重打包。
2. 可启动 Service 和 Web 模块配置 `spring-boot-maven-plugin`。
3. 内部依赖版本优先由根 `dependencyManagement` 管理。
4. 测试依赖使用 `test` scope。
5. 运行时数据库驱动使用 `runtime` scope。
6. 注解处理器和可选框架依赖使用 `optional`。

工程稳定后建议在根构建中统一增加：

- Spotless 格式检查。
- JaCoCo 覆盖率报告与门禁。
- Maven Enforcer。
- Git Commit/Build Info。
- ArchUnit 分层依赖测试。

## 16. 命名规范

| 类型 | 命名示例 |
|---|---|
| API 契约 | `UserApi` |
| Controller | `UserController` |
| 应用服务接口 | `UserAppService` |
| 应用服务实现 | `UserAppServiceImpl` |
| HTTP 请求 | `UserCreateRequest` |
| HTTP 响应 | `UserResponse` |
| 内部命令 | `CreateUserCommand` |
| 内部结果 | `LoginResult` |
| 数据库实体 | `UserInfoEntity` |
| Mapper | `UserInfoMapper` |
| 转换器 | `PermissionPersistenceConverter` |
| 组装器 | `UserAssembler` |
| 业务异常 | `BusinessException` 或明确的 `*Exception` |
| 配置属性 | `AuthProperties` |
| 配置类 | `AuthConfiguration` |
| 领域端口 | `XxxAgent` |
| 领域端口实现 | `XxxAgentImpl` |

包名全部小写，使用 `baosys.{service}` 作为根包。

## 17. 新微服务创建清单

创建新服务时按以下顺序执行：

1. 在根工程下创建聚合模块 `baosys-xxx`。
2. 创建 `baosys-xxx-api` 和 `baosys-xxx-service`。
3. 将两个模块登记到对应聚合 `pom.xml`。
4. 在根 `dependencyManagement` 登记 `baosys-xxx-api`。
5. 在 API Jar 定义 `SERVICE_NAME`、版本化 `PREFIX`、Request 和 Response。
6. 在 Service 中创建启动类，并依赖自己的 API Jar。
7. 根据复杂度选择简单模式或领域模式。
8. 创建统一异常处理、配置属性和必要的基础设施。
9. 使用 Flyway 创建数据库结构。
10. 编写 App Service 单元测试、Controller 测试和数据库集成测试。
11. 在网关增加 `/api/baosys-xxx/**` 路由。
12. 验证完整路径、统一响应、追踪标识和服务启动。

交付前检查：

```text
[ ] API Jar 没有业务实现和数据库依赖
[ ] Service 没有被其他业务模块直接依赖
[ ] Controller 实现 API 接口且没有直接访问 Mapper
[ ] 写操作和读操作事务边界正确
[ ] Entity 未作为 HTTP Response
[ ] 外部路径和内部 PREFIX 没有混淆
[ ] 数据库变更全部通过 Flyway
[ ] 关键流程存在自动化测试
[ ] application-test.yml 可独立运行测试
[ ] 新模块已通过根工程构建
```

## 18. 禁止的反模式

```text
禁止 xxx-api 依赖 xxx-service
禁止业务服务依赖另一个服务的实现 Jar
禁止在 API 接口上直接声明 @FeignClient
禁止 Controller 直接调用 Mapper
禁止 Entity 跨服务或直接返回前端
禁止 Domain 依赖 Spring Web、数据库和远程调用技术
禁止在多个服务重复定义 ApiResponse / PageResponse
禁止把网关写成新的业务服务
禁止把所有复用代码无差别放入 common
禁止修改已经执行过的 Flyway 迁移
```

## 19. 当前工程对照

| 规范角色 | 当前实现 |
|---|---|
| 平台公共协议 | `baosys-common-api` |
| 平台公共自动配置 | 对应的 Common 功能模块 |
| 业务契约 Jar | `baosys-manager-api` |
| 业务实现服务 | `baosys-manager-service` |
| 简单业务分层示例 | `UserAppServiceImpl`、`UserInfoMapper` |
| 网关与 SPA 宿主 | `baosys-manager-web` |
| 前端工程 | `baosys-manager-ui` |
| 数据库迁移 | `db/migration/V1__init_manager.sql` |
| 数据库集成测试 | `PostgreSqlIntegrationTest` 及 Controller Integration Test |

新增模块应优先复用这些已经落地的模式；当规范与代码发生长期偏差时，应同步修正规范或通过架构测试固化新的约定。
