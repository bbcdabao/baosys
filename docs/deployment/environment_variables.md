# BaoSys 环境变量说明

子系统目录通过 `BAOSYS_SYSTEMS_CONFIG` 指定，默认 `classpath:baosys-systems.yml`（构建时取工程根目录文件）。外部文件示例：`file:/config/baosys-systems.yml`；需提供完整目录并重启 manager。详见[子系统接入指南](../subsystem-onboarding.md)。

## 1. 配置来源与优先级

当前应用主要通过 `application.yml` 中的 `${ENV:default}` 读取环境变量。

常用优先级从高到低：

```text
命令行参数
环境变量
外部 application.yml
Jar 内 application.yml 默认值
```

本文只描述当前代码已经使用或已经预留的配置。不同进程需要分别设置自己的环境变量。

Common 配置类的完整属性及默认值见 [Common 模块说明](../../baosys-common/README.md#5-配置默认值)。本文表格列出的是应用 YAML 的实际默认值，例如 Manager 的 Access Token 有效期为 1800 秒，签发属性默认值同样为 1800 秒。

## 2. Manager Service

配置文件：

```text
baosys-manager/baosys-manager-service/src/main/resources/application.yml
```

### 2.1 服务配置

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `MANAGER_PORT` | `9090` | Manager Service 端口 |
| `MANAGER_CONTEXT_PATH` | `/baosys-manager` | Servlet context-path |

修改 context-path 时，必须同步检查网关路由转发结果。

### 2.2 数据库

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/app_db` | JDBC URL |
| `DB_USERNAME` | `postgres` | 数据库用户 |
| `DB_PASSWORD` | `postgres123456` | 数据库密码 |
| `MANAGER_DB_SCHEMA` | `manager` | Hikari 默认 schema 与 Flyway 管理的 schema |

示例：

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/app_db"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="postgres123456"
```

默认使用 `manager` schema，可通过 `MANAGER_DB_SCHEMA` 覆盖。Flyway 历史表为 `flyway_schema_history_manager`；首个迁移为 `V1__init_manager.sql`，数据库用户需要创建 schema 的权限。

### 2.3 管理员初始化

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `DEFAULT_ADMIN_ENABLED` | `false` | 是否在启动时幂等创建管理员 |
| `DEFAULT_ADMIN_USERNAME` | `admin` | 默认管理员用户名 |
| `DEFAULT_ADMIN_PASSWORD` | 空 | 默认管理员密码，启用时至少 12 位 |

初始化器只在指定 Manager 用户不存在时创建，不会覆盖已有密码或权限。

默认关闭管理员初始化；首次需要创建管理员时显式启用并设置至少 12 位密码。

### 2.4 OAuth2 与会话

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| JWT_ISSUER | http://localhost:9090/baosys-manager | 标准 issuer，须为可访问 URL |
| JWT_JWK_SET_URI | http://localhost:9090/baosys-manager/oauth2/jwks | 资源服务器 JWKS 地址 |
| JWT_JWK_SET_LOCATION | 必填 | 认证中心私有 JWKSet 文件资源位置 |
| OAUTH2_CLIENT_SECRET | 必填 | 网关保管的随机客户端密钥 |
| OAUTH2_CLIENT_SECRET_HASH | 必填 | 认证中心的对应 {bcrypt} 编码值 |
| OAUTH2_REDIRECT_URI | http://localhost:8080/login/oauth2/code/baosys | 两端匹配的回调 URI |

内部令牌期限沿用 spring.security.oauth2.authorizationserver.client.baosys-web.token 属性；共享 Cookie 通过 MANAGER_PUBLIC_ORIGIN、SHARED_LOGIN_ALLOWED_ORIGINS、SHARED_LOGIN_SECURE_COOKIE 配置部署规则，前端子系统使用 VITE_MANAGER_ORIGIN 指定统一入口。CSRF 由 Spring 管理，前端同源 GET /csrf 后提交掩码。详见 [共享登录配置](shared_login.md)。

## 3. Manager Web

配置文件：

```text
baosys-manager-web/src/main/resources/application.yml
```

### 3.1 服务与路由

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `GATEWAY_PORT` | `8080` | 网关和 SPA 宿主端口 |
| `MANAGER_SERVICE_URL` | `http://127.0.0.1:9090` | Manager 下游地址 |

每个子系统使用自己的 API 网关；Manager Web 仅配置 Manager 服务的转发和入口权限规则。

### 3.2 JWT 和 CSRF

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `JWT_ISSUER` | `http://localhost:9090/baosys-manager` | 必须与签发端一致 |
| `JWT_JWK_SET_URI` | `http://127.0.0.1:9090/baosys-manager/oauth2/jwks` | 固定可信 manager 公钥接口；更改端口/context-path 时同步修改 |

网关与资源服务通过 Spring/Nimbus 原生 JWKS 支持获取并缓存公钥；未知 kid 可触发重新获取，不再维持旧自建缓存和失败退避约定。配置标准 issuer-uri、jwk-set-uri、jws-algorithms=[RS256]、audiences=[def]。本地离线公钥模式使用标准 public-key-location，并清空 jwk-set-uri，不支持本地优先远程回退。

Manager Web 不持有私钥。spring.security.gateway.access-rules 按路径声明访问要求：permission 有值时检查系统入口权限，未配置、空字符串或仅含空白字符时只要求登录。规则按顺序首次匹配，共享路径应放在子系统通配规则之前。后端资源服务验证 JWT，并通过 @PreAuthorize 校验具体 API 权限。账户权限数组中需包含相应入口编码。公钥端点为 /baosys-manager/oauth2/jwks，经网关为 /api/baosys-manager/oauth2/jwks。

## 4. 前端

不再配置 VITE_CSRF_COOKIE_NAME。写请求前通过 GET /csrf 获取标准 Token，按响应 headerName 提交；无需读取 Cookie 或本地存储。网关始终启用 CSRF，XSRF-TOKEN 为框架管理的 HttpOnly Cookie。

当前 API 前缀不是环境变量，固定在：

```text
baosys-manager-ui/src/constants/api.ts
MANAGER_API_PREFIX = /api/baosys-manager
```

## 5. Nacos 预留配置

Manager Web 的 Nacos 依赖和配置当前均处于注释状态。以下变量只有启用 Nacos 后才生效：

| 环境变量 | 默认值 |
|---|---|
| `NACOS_SERVER_ADDR` | `127.0.0.1:8848` |
| `NACOS_NAMESPACE` | 空 |
| `NACOS_GROUP` | `DEFAULT_GROUP` |
| `NACOS_USERNAME` | `nacos` |
| `NACOS_PASSWORD` | `nacos` |

启用时还需要：

1. 放开 `spring-cloud-starter-alibaba-nacos-discovery` 依赖。
2. 放开 `spring.cloud.nacos.discovery` 配置。
3. 把下游 URL 改为 `lb://baosys-manager` 等服务名。

## 6. 测试配置

测试使用：

```text
baosys-manager-service/src/test/resources/application-test.yml
baosys-manager-web/src/test/resources/application-test.yml
```

Manager 数据库集成测试通过 `DynamicPropertySource` 使用 Testcontainers 动态覆盖：

```text
spring.datasource.url
spring.datasource.username
spring.datasource.password
spring.datasource.driver-class-name
```

因此运行测试时不使用开发数据库。

测试配置还会降低 BCrypt strength，以缩短测试时间。

## 7. PowerShell 配置示例

### 7.1 Manager Service 终端

```powershell
$env:JWT_JWK_SET_LOCATION = "file:D:/baosys/keys/signing-jwks.json"
# 另设置两端对应的 OAUTH2_CLIENT_SECRET / OAUTH2_CLIENT_SECRET_HASH
$env:MANAGER_PORT="9090"
$env:DB_URL="jdbc:postgresql://localhost:5432/app_db"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="postgres123456"
$env:DEFAULT_ADMIN_ENABLED="true"
$env:DEFAULT_ADMIN_USERNAME="admin"
$env:DEFAULT_ADMIN_PASSWORD="Admin123!test"

mvn -f baosys-manager/baosys-manager-service/pom.xml spring-boot:run
```

### 7.2 Manager Web 终端

```powershell
$env:GATEWAY_PORT="8080"
$env:MANAGER_SERVICE_URL="http://127.0.0.1:9090"

mvn -f baosys-manager-web/pom.xml spring-boot:run "-Dskip.frontend=true"
```

关闭终端后，这些临时变量不会写入系统级环境。

## 8. Bash 配置示例

```shell
export JWT_JWK_SET_LOCATION="file:/config/keys/signing-jwks.json"
# 另设置两端对应的 OAuth2 客户端密钥
export DB_URL='jdbc:postgresql://localhost:5432/app_db'
export DB_USERNAME='postgres'
export DB_PASSWORD='postgres123456'
export DEFAULT_ADMIN_ENABLED='true'
export DEFAULT_ADMIN_USERNAME='admin'
export DEFAULT_ADMIN_PASSWORD='Admin123!test'

mvn -f baosys-manager/baosys-manager-service/pom.xml spring-boot:run
```

## 9. 配置一致性检查

启动完整系统前检查：

```text
[ ] Manager Web 的 MANAGER_SERVICE_URL 指向 Manager Service
[ ] Manager Web 与 Manager Service 的 JWT_ISSUER 一致
[ ] 两个服务的 JWKS 地址正确，JWK kid 与发布公钥一致
[ ] AUTH_REFRESH_TOKEN_COOKIE_PATH 与外部认证路径一致
[ ] DB_URL 指向已创建且可访问的 PostgreSQL 数据库
```

## 10. 新增环境变量规则

新增配置时：

1. 在对应 `@ConfigurationProperties` 中定义类型和默认值。
2. 在 `application.yml` 使用 `${ENV:default}` 显式映射。
3. 在本文补充变量、默认值、所属进程和影响。
4. 测试环境需要差异时更新 `application-test.yml`。
5. 多个进程必须一致的配置，在本文加入一致性检查。
6. 不要让前端直接读取只属于后端的配置。
