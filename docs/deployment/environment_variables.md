# BaoSys 环境变量说明

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

示例：

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/app_db"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="postgres123456"
```

### 2.3 管理员初始化

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `DEFAULT_ADMIN_ENABLED` | `true` | 是否在启动时幂等创建管理员 |
| `DEFAULT_ADMIN_USERNAME` | `admin` | 默认管理员用户名 |
| `DEFAULT_ADMIN_PASSWORD` | `Admin123!test` | 默认管理员密码，启用时至少 12 位 |

初始化器只在指定 Manager 用户不存在时创建，不会覆盖已有密码或权限。

当前运行配置默认开启管理员初始化；生产环境必须通过环境变量设置安全密码，
或在完成首次初始化后关闭该功能。

### 2.4 Cookie 与会话

| 环境变量 | 默认值 | 说明 |
|---|---:|---|
| `AUTH_COOKIE_SECURE` | `false` | Cookie 是否仅通过 HTTPS 发送 |
| `AUTH_ACCESS_TOKEN_COOKIE_MAX_AGE_SECONDS` | `7200` | Access Cookie 持久时间 |
| `AUTH_REFRESH_TOKEN_COOKIE_MAX_AGE_SECONDS` | `2592000` | Refresh Cookie 持久时间 |
| `AUTH_REFRESH_TOKEN_COOKIE_PATH` | `/api/baosys-manager/v1/auth` | Refresh Cookie Path |
| `REFRESH_TOKEN_EXPIRE_DAYS` | `30` | 写入 `expiresAt` 的预留绝对有效期 |

Max-Age 配置为 `0` 时，应用写出会话 Cookie。

`AUTH_REFRESH_TOKEN_COOKIE_PATH` 面向浏览器经过网关的外部路径，不是 Manager Service 内部 `/v1/auth`。

当前 Refresh 资格只以 `user_login` 记录是否存在为准；`expiresAt` 尚未参与
Refresh 判定。会话通过 `updatedAt` 表示最近续租时间，超过 1 天未更新的记录由
数据库清理任务删除。

### 2.5 JWT

| 环境变量 | 默认值 | 说明 |
|---|---:|---|
| `SZS_JWT_ISSUER` | `szs-auth-server` | JWT issuer |
| `SZS_ACCESS_TOKEN_EXPIRE_SECONDS` | `7200` | Access Token 有效期 |

当前 RSA 公钥和私钥直接配置在 `application.yml` 的：

```text
szs.security.jwt.public-key
szs.security.jwt.private-key
```

它们当前没有使用 `${SZS_JWT_PUBLIC_KEY}` / `${SZS_JWT_PRIVATE_KEY}` 占位符。需要外部化时，应提供外部 Spring 配置文件或修改 YAML 为显式环境变量占位符，并确保 Manager Web 使用同一公钥。

### 2.6 CSRF

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `GATEWAY_CSRF_ENABLED` | `false` | 是否签发 CSRF Token Cookie |

该变量需要同时设置给 Manager Service 和 Manager Web：

- Manager Service 决定是否签发 CSRF Cookie/Header。
- Manager Web 决定是否执行双提交校验。

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
| `PLANT_SERVICE_URL` | `http://127.0.0.1:9091` | 预留 Plant 下游地址 |

`PLANT_SERVICE_URL` 已出现在网关路由中，但当前仓库没有 Plant 服务。

### 3.2 JWT 和 CSRF

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `SZS_JWT_ISSUER` | `szs-auth-server` | 必须与签发端一致 |
| `SZS_SYSTEM_TYPE` | `MANAGER` | Manager 网关只接受该子系统的 JWT |
| `GATEWAY_CSRF_ENABLED` | `false` | 是否启用网关 CSRF Filter |

网关公钥当前位于：

```text
szs.security.jwt.public-key
```

Manager Web 不需要私钥。

## 4. 前端

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `VITE_CSRF_COOKIE_NAME` | `BAOSYS_CSRF_TOKEN` | 前端读取的 CSRF Cookie 名称 |

Vite 环境变量在前端构建时固化。修改后需要重新启动开发服务器或重新构建。

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
$env:MANAGER_PORT="9090"
$env:DB_URL="jdbc:postgresql://localhost:5432/app_db"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="postgres123456"
$env:DEFAULT_ADMIN_ENABLED="true"
$env:DEFAULT_ADMIN_USERNAME="admin"
$env:DEFAULT_ADMIN_PASSWORD="Admin123!test"
$env:GATEWAY_CSRF_ENABLED="false"

mvn -f baosys-manager/baosys-manager-service/pom.xml spring-boot:run
```

### 7.2 Manager Web 终端

```powershell
$env:GATEWAY_PORT="8080"
$env:MANAGER_SERVICE_URL="http://127.0.0.1:9090"
$env:GATEWAY_CSRF_ENABLED="false"

mvn -f baosys-manager-web/pom.xml spring-boot:run "-Dskip.frontend=true"
```

关闭终端后，这些临时变量不会写入系统级环境。

## 8. Bash 配置示例

```shell
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
[ ] Manager Web 与 Manager Service 的 SZS_JWT_ISSUER 一致
[ ] Manager Web 公钥与 Manager Service 签发私钥匹配
[ ] 两个进程的 GATEWAY_CSRF_ENABLED 一致
[ ] AUTH_REFRESH_TOKEN_COOKIE_PATH 与外部认证路径一致
[ ] VITE_CSRF_COOKIE_NAME 与后端 CSRF Cookie 名称一致
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
