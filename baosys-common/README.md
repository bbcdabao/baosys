# Common 模块说明

`baosys-common` 是平台共享的安全实现模块，为认证服务、WebFlux 网关和 Servlet 业务服务提供密码摘要、JWT 签发与校验、Cookie 转发和权限转换能力。本模块是普通 Jar，不独立启动，也不访问数据库。

[返回文档目录](../docs/README.md) · [模块架构规范](../docs/microservices_module_architecture_standard.md) · [部署环境变量](../docs/deployment/environment_variables.md)

## 1. 与 Common API 的关系

| 模块 | Maven 坐标 | 职责 | 典型使用方 |
|---|---|---|---|
| `baosys-common-api` | `baosys:baosys-common-api` | `ApiResponse<T>`、`PageResponse<T>` 等公共响应契约 | 各业务 API Jar |
| `baosys-common` | `baosys:baosys-common` | Spring Security、JWT、密码处理及配置装配 | 认证服务、网关、业务实现服务 |

依赖方向为 `baosys-common → baosys-common-api`。`baosys.common.api` 包实际位于独立的 Common API 模块；业务契约只需要公共响应时，应直接依赖 Common API。

Common 不负责用户查询、登录接口、Refresh Token 存储、账号启停、权限目录、授权审计或 Cookie 签发。这些业务由 Manager 实现，见 [认证与会话设计](../docs/modules/manager/auth_and_session.md)。

## 2. 代码结构

所有实现位于 [`src/main/java/baosys/common`](src/main/java/baosys/common)。

| 包 | 核心类型 | 作用 |
|---|---|---|
| `autoconfigure` | `PasswordAutoConfiguration` | 按开关注册密码处理器 |
| `security.annotation` | `EnableAuthenticationCheck`、`EnableUltraViresCheck` | 网关认证和 Servlet 资源服务授权的接入入口 |
| `security.config` | `SecurityProperties`、`JwtDecoderConfiguration`、`JwtEncoderConfiguration` | 安全属性、JWT Bean 和组合配置 |
| `security.password` | `PasswordHasher`、`BCryptPasswordHasher`、`PasswordProperties` | BCrypt 摘要与密码校验 |
| `security.jwt` | `JwtTokenService`、`JwtTokenServiceImpl`、`JwtKeyLoader` | 签发访问令牌、解析 RSA 密钥 |
| `security.gateway` | `GatewaySecurityConfig`、`CookieTokenToAuthorizationFilter`、`GatewayCsrfFilter` | 入口认证、Cookie 转 Bearer、可选 CSRF 校验 |
| `security.resource` | `ResourceServerSecurityConfig`、`JwtPermissionConverter` | 无状态 Servlet 安全链和方法权限转换 |

`package-info.java` 保存相应包的职责说明；本文集中维护模块级接入和使用说明。

## 3. 依赖与启用条件

平台内的实现模块添加：

```xml
<dependency>
  <groupId>baosys</groupId>
  <artifactId>baosys-common</artifactId>
</dependency>
```

版本由根 POM 管理，当前为 `1.0.0`；平台外项目需显式声明版本并提供匹配的 Spring Boot / Spring Cloud 依赖管理。当前基线为 Java 17、Spring Boot 3.5.7、Spring Cloud 2025.0.1，实际依赖见 [pom.xml](pom.xml)。

Common 中 Gateway 和 Servlet API 依赖为 `optional`。使用方仍需引入自身 Web 技术栈：Servlet 服务使用 `spring-boot-starter-web`，网关使用 `spring-cloud-starter-gateway-server-webflux`。

自动配置登记在 [AutoConfiguration.imports](src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports)：

| 入口 | 启用条件 | 主要 Bean / 能力 |
|---|---|---|
| `PasswordAutoConfiguration` | `common.password.enabled=true` 且 BCrypt 类可用 | `PasswordHasher` |
| `JwtDecoderConfiguration` | 配置 `security.jwt.public-key` 且 JWT 类可用 | `JwtDecoder`；响应式依赖可用时另提供 `ReactiveJwtDecoder` |
| `JwtEncoderConfiguration` | 配置 `security.jwt.private-key` 且 JWT 类可用；创建默认编码器还需要公钥 | `JwtEncoder`、`JwtTokenService` |
| `@EnableAuthenticationCheck` | 网关应用显式声明 | 网关安全链、Cookie 转发；配置开关开启时增加 CSRF Filter |
| `@EnableUltraViresCheck` | Servlet 应用显式声明 | 资源服务安全链、JWT 权限转换、`@EnableMethodSecurity` |

引入依赖本身不会启用 BaoSys 的两条安全过滤链，需要选用对应注解。Spring Boot 自身的安全自动配置仍可能生效。

密码处理器、JWT 编解码器、令牌服务以及安全过滤链等默认 Bean 使用 `@ConditionalOnMissingBean`，可由应用自行提供对应类型的 Bean。`UltraViresCheckConfiguration` 中的 `JwtAuthenticationConverter` 不使用该退让条件，不能把所有 Bean 都当成可直接重复声明。

本模块没有独立的 JSON 自动配置，也不自行提供兜底 `ObjectMapper`；安全响应处理需要使用方提供该 Bean，通常由 Spring Boot Web 应用自动配置。

## 4. 三种接入方式

### 4.1 认证服务：密码校验与令牌签发

需要保护自身业务接口的 Servlet 认证服务，在启动类或配置类上添加 `baosys.common.security.annotation.EnableUltraViresCheck` 注解。下面是应合并到应用配置中的示例片段：

```yaml
common:
  password:
    enabled: true
    strength: 10
security:
  jwt:
    issuer: auth-server
    access-token-expire-seconds: 1800
    public-key: ${JWT_PUBLIC_KEY}
    private-key: ${JWT_PRIVATE_KEY}
  resource:
    permit-all-paths:
      - /v1/auth/login
      - /v1/auth/refresh
      - /v1/auth/logout
      - /actuator/health/**
```

通过构造器注入 `PasswordHasher` 和 `JwtTokenService`，业务方法内使用：

```java
String encoded = passwordHasher.hash(rawPassword);
boolean matched = passwordHasher.matches(rawPassword, encoded);

String accessToken = jwtTokenService.generateAccessToken(
        "42", "demo-user", "MANAGER",
        List.of("user:list"), Map.of("displayName", "演示用户"));
```

片段中的集合使用 `java.util.List` 和 `java.util.Map`。密码摘要每次带有独立 salt，校验必须调用 `matches`。账号启用状态、密码策略、允许的权限以及刷新会话均由认证服务校验和管理。

### 4.2 WebFlux 网关：入口认证与转发

启动类示例：

```java
package baosys.gateway;

import baosys.common.security.annotation.EnableAuthenticationCheck;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableAuthenticationCheck
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

安全配置片段（路由由网关另行配置）：

```yaml
security:
  jwt:
    issuer: auth-server
    public-key: ${JWT_PUBLIC_KEY}
  system-type: MANAGER
  gateway:
    permit-all-paths:
      - /api/baosys-manager/v1/auth/login
      - /api/baosys-manager/v1/auth/refresh
      - /api/baosys-manager/v1/auth/logout
    csrf:
      enabled: false
      cookie-name: BAOSYS_CSRF_TOKEN
```

网关默认保护 `/api/**`，其余路径允许访问。公开 API 使用外部路径配置，登录、刷新等白名单路径不提取令牌，避免旧 Access Cookie 阻止重新登录。

受保护请求优先使用 `Authorization: Bearer ...`，其次读取 `access_token` Cookie。验证通过后，`CookieTokenToAuthorizationFilter` 为下游补充 Bearer 请求头；已有 Bearer 请求头保持原值。

### 4.3 Servlet 业务服务：JWT 与方法级权限

启动类添加 `@EnableUltraViresCheck`，配置公钥、可信 issuer 和公开路径：

```yaml
security:
  jwt:
    issuer: auth-server
    public-key: ${JWT_PUBLIC_KEY}
  system-type: MANAGER
  resource:
    permit-all-paths:
      - /actuator/health/**
```

资源服务从 Bearer 请求头读取令牌，采用无状态会话策略，除白名单外均要求认证。白名单使用应用内路径，不包含 Servlet context-path；例如 `/baosys-manager/v1/auth/login` 对应 `/v1/auth/login`。

具体业务操作使用 `org.springframework.security.access.prepost.PreAuthorize`：

```java
@PreAuthorize("hasAuthority('user:list')")
```

Common 只把 JWT 权限编码转成 `GrantedAuthority`，不验证编码是否属于业务权限目录，也不查询用户当前权限。

## 5. 配置默认值

以下是 Common 配置类默认值，可能被应用自己的 YAML 覆盖。

| 配置项 | Common 默认值 | 含义 |
|---|---|---|
| `common.password.enabled` | `false` | 是否创建默认密码处理器 |
| `common.password.strength` | `10` | BCrypt cost，构造器允许 4～31 |
| `security.jwt.issuer` | `auth-server` | 签发及验证时使用的 issuer |
| `security.jwt.access-token-expire-seconds` | `7200` | Access Token 有效期；Manager 覆盖为 `1800` |
| `security.jwt.public-key` | 未设置 | X.509 RSA 公钥 |
| `security.jwt.private-key` | 未设置 | PKCS#8 RSA 私钥，仅签发端需要 |
| `security.access-token-cookie-name` | `access_token` | 网关读取的访问令牌 Cookie 名称 |
| `security.permissions-claim-name` | `permissions` | 权限列表 claim 名称 |
| `security.authority-prefix` | 空字符串 | 转换权限时添加的前缀 |
| `security.system-type` | 未设置 | 不设置时跳过子系统校验；设置后与 JWT `systemType` 精确匹配 |
| `security.gateway.permit-all-paths` | 空列表 | 网关公开路径 |
| `security.resource.permit-all-paths` | 空列表 | Servlet 资源服务公开路径 |
| `security.gateway.csrf.enabled` | `false` | 是否启用网关自定义 CSRF Filter |
| `security.gateway.csrf.cookie-name` | `BAOSYS_CSRF_TOKEN` | 双提交 CSRF Cookie 名称 |

示例中的 `JWT_PUBLIC_KEY` / `JWT_PRIVATE_KEY` 是显式 YAML 占位符。当前 Manager 的应用 YAML 直接包含密钥值，并未使用这两个占位符；要采用示例变量，需添加对应映射或提供外部配置。实际应用变量以 [环境变量说明](../docs/deployment/environment_variables.md) 为准。

## 6. JWT 与密钥约定

`JwtTokenService` 接收用户 ID、用户名、系统类型、权限列表和扩展 claim。用户 ID、用户名和系统类型不能为空；系统类型会去除首尾空白，空权限列表参数按空集合处理。

| Claim | 用途 |
|---|---|
| `iss` | 鉴权中心名称，与解码器配置一致 |
| `sub` | 用户 ID，也是认证主体名称 |
| `iat` / `exp` | 签发时间与到期时间 |
| `username` | 展示和日志上下文 |
| `systemType` | 子系统标识 |
| `permissions` | 默认权限列表；名称可配置 |

解码器执行 RSA 签名、标准时间声明、issuer 和可选 systemType 校验。issuer 支持 `auth-server` 这样的普通字符串。扩展 claim 应只使用业务字段，不传入 `iss`、`sub`、`iat`、`exp` 等标准声明；当前实现没有完整的保留字段拒绝机制。

密钥可以是完整 PEM 文本或去掉头尾后的 Base64。公钥格式为 X.509 SubjectPublicKeyInfo，私钥格式为 PKCS#8，不支持 PKCS#1 `RSA PRIVATE KEY`，也不会把 `classpath:` 或文件路径自动读取成密钥内容。

仓库根目录可执行以下命令生成新密钥：

```shell
jshell scripts/generate-jwt-keys.jsh
```

脚本输出标签为 `BAOSYS_JWT_PUBLIC_KEY`、`BAOSYS_JWT_PRIVATE_KEY`，仅表示生成结果；它不会修改配置或设置环境变量。将输出值映射到应用的 `security.jwt.public-key` / `private-key`。网关与资源服务只分发公钥。

## 7. CSRF 与错误响应

启用 CSRF 后，网关针对受保护 API 中使用 Access Cookie 的非 GET、HEAD、OPTIONS 请求，比较 CSRF Cookie 与 `X-CSRF-Token` 请求头。公开路径和显式 Bearer 请求跳过该校验。

过滤顺序是 CSRF Filter（`-90`）先于 Cookie 转 Bearer Filter（`-80`）。CSRF Token 的签发由 Manager `AuthController` 完成；Common 只做校验。当前工程需要在 Manager Service 和 Manager Web 同时开启 `GATEWAY_CSRF_ENABLED`。

公共安全错误处理器使用 Common API 的 `ApiResponse`：未认证为 `AUTH_401_001`，权限不足为 `AUTH_403_001`，CSRF 失败为 `AUTH_403_002`。Spring Security 自身处理的部分 Bearer 解析/验证异常仍可能使用框架默认响应，不能假定所有 401/403 都经过这些处理器。

## 8. 验证与扩展

在仓库根目录执行 Common 测试：

```shell
mvn -pl baosys-common -am test
```

现有测试覆盖自动配置启用、BCrypt、JWT 签发、issuer / systemType 校验和权限转换。网关安全集成测试位于 Manager Web，可单独运行，无需数据库：

```shell
mvn -Pintegration-tests -pl :baosys-manager-web -am verify "-Dskip.frontend=true"
```

Common 没有刷新会话数据库、JWT 撤销列表或权限实时查询。修改账号权限或删除刷新会话不会让已签发 JWT 立即失效。业务规则和相关测试应保留在所属服务。

新增公共能力时，保持“接口 + 默认实现 + 配置属性 + 条件装配”的结构，并明确依赖、启用条件、默认值及覆盖方式；模块边界见 [公共模块规范](../docs/microservices_module_architecture_standard.md#8-公共模块规范)。
