# 环境变量配置说明

**简体中文** | [English](environment-variables.en.md)

本文集中说明 BaoSys 当前使用的环境变量。变量主要分为三类：

- Java Service/Web 启动变量，由 Spring `application.yml` 或共享配置读取。
- 系统目录变量，用于拼接子系统 UI 地址，在 Service 启动时加载。
- Node/Vite 和 Playwright 构建或测试变量，只在前端构建、开发或测试时读取。

环境变量可以覆盖示例中的默认值，但不会改变下列依赖关系：

- `JWT_ISSUER` 必须在 Manager Service 和 Manager Web 中保持一致。
- `MANAGER_PUBLIC_ORIGIN` 必须与 MGR 系统目录的 `ui-base-url` 来源一致。
- `JWT_JWK_SET_LOCATION` 和 `JWT_JWK_SET_URI` 是不同变量：前者由认证中心加载私钥 JWKSet，后者由验签端读取公开 JWKS。
- 修改系统目录后需要重启 Manager Service；修改前端构建变量后需要重新执行前端构建。

## 1. Manager Service

配置来源：[application.yml](../baosys-manager/baosys-manager-service/src/main/resources/application.yml)。

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `MANAGER_PORT` | `9090` | Manager Service HTTP 端口 |
| `MANAGER_CONTEXT_PATH` | `/baosys-manager` | Service 上下文路径 |
| `DB_URL` | `jdbc:postgresql://localhost:5432/app_db` | PostgreSQL JDBC 地址 |
| `DB_USERNAME` | `postgres` | 数据库用户名 |
| `DB_PASSWORD` | `postgres123456` | 数据库密码，仅适合本地开发；生产环境必须覆盖 |
| `MANAGER_DB_SCHEMA` | `manager` | Hikari 和 Flyway 使用的数据库 schema |
| `JWT_ISSUER` | `http://localhost:9090/baosys-manager` | JWT issuer，Service、Web 和资源接入方必须一致 |
| `JWT_JWK_SET_LOCATION` | 无 | 私钥 JWKSet 的资源 URI，正式启动必须提供；支持文件、classpath 等 Spring 资源 URI |
| `JWT_JWK_SET_URI` | `http://localhost:9090/baosys-manager/oauth2/jwks` | Manager 自身资源验签读取的 JWKS 地址 |
| `OAUTH2_CLIENT_SECRET_HASH` | `{noop}local-dev-client-secret` | 内置 `baosys-web` OAuth 客户端密钥摘要；生产环境必须覆盖为编码密钥 |
| `OAUTH2_REDIRECT_URI` | `http://localhost:8080/login/oauth2/code/baosys` | OAuth 客户端回调地址 |
| `DEFAULT_ADMIN_ENABLED` | `false` | 是否在启动时初始化默认管理员 |
| `DEFAULT_ADMIN_USERNAME` | `admin` | 默认管理员用户名；启用初始化时必须为非空值 |
| `DEFAULT_ADMIN_PASSWORD` | 空 | 默认管理员密码；启用初始化时至少 12 字符 |
| `MANAGER_PUBLIC_ORIGIN` | `http://localhost:8080` | 浏览器可访问的 Manager 来源，不包含路径、尾斜杠、查询或片段 |
| `BAOSYS_SYSTEMS_CONFIG` | `classpath:baosys-systems.yml` | 系统目录配置 URI，可指向外部文件 |

生产环境至少应覆盖数据库密码、签名 JWK、OAuth 客户端密钥和公开来源。不要在提交的配置、日志、镜像层或前端代码中保存私钥和生产密码。

## 2. Manager Web

配置来源：[application.yml](../baosys-manager-web/src/main/resources/application.yml)。

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `GATEWAY_PORT` | `8080` | Manager Web 网关端口 |
| `CSRF_ENABLED` | `false` | 是否启用网关浏览器 CSRF 校验 |
| `MANAGER_SERVICE_URL` | `http://localhost:9090` | Manager Service 上游地址；容器部署时使用可达网络地址 |
| `JWT_ISSUER` | `http://localhost:9090/baosys-manager` | 网关验签 issuer，必须与 Service 一致 |
| `JWT_JWK_SET_URI` | `http://localhost:9090/baosys-manager/oauth2/jwks` | 网关读取公开 JWKS 的地址 |

网关固定使用以下名称：

- CSRF 路径：`/api/baosys-manager/csrf`
- CSRF Cookie：`BAOSYS-MGR-XSRF-TOKEN`
- Access Token Cookie：`BAOSYS_ACCESS_TOKEN`
- Refresh Token Cookie：`BAOSYS_REFRESH_TOKEN`

正式 HTTPS 部署开启 CSRF 后，还应按实际域名、代理和 TLS 配置 Cookie 与代理头；当前 Cookie 实现尚未提供环境变量控制的 `Secure` 属性。

## 3. 系统目录

目录来源：[baosys-systems.yml](../baosys-systems.yml)。

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `BASE_URL` | `http://localhost:` | 系统 UI 地址的协议和主机前缀 |
| `MANAGER_UI_URL_SUFFIX` | `8080/baosys-manager/` | 拼接后的 MGR UI 相对地址后缀；Vite 开发环境使用 `5173/baosys-manager/` |

目录中的可选扩展变量遵循与 `MANAGER_UI_URL_SUFFIX` 相同的拼接方式，例如未来接入 CAT 时可使用 `CAT_UI_URL_SUFFIX=/baosys-xxxx/`，但该变量仅在对应目录项实际读取该占位符后才生效。当前仓库只登记 MGR，因此不存在实际生效的 CAT 环境变量。

目录生成的完整 UI 地址必须满足：

- MGR 的来源与 `MANAGER_PUBLIC_ORIGIN` 同主机、同协议和同端口。
- 所有子系统 UI 范围不能重叠。
- `home-path` 必须以 `/` 开头且位于自身 UI 范围内。
- 修改目录后重启 Service。

## 4. 前端构建与测试

配置来源：[.env.production](../baosys-manager-ui/.env.production)、[vite.config.mts](../baosys-manager-ui/vite.config.mts)、[playwright.config.js](../baosys-manager-ui/playwright.config.js)。

| 环境变量 | 默认值或位置 | 说明 |
|---|---|---|
| `VITE_MANAGER_UI_BASE_URL` | `.env.production` 中为 `http://localhost:8080/baosys-manager/` | Manager 完整 UI 基址；新子系统构建时必须覆盖为部署地址 |
| `PLAYWRIGHT_CHANNEL` | `msedge` | 浏览器测试使用的 Playwright channel |

只有以 `VITE_` 开头的变量会由 Vite 暴露给前端代码。不要在此类变量中放私钥、数据库密码或 JWT 密钥。

## 5. 日志、停机和可选 Nacos

配置来源：[operations.yml](../baosys-common/src/main/resources/baosys/operations.yml)。

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `SHUTDOWN_TIMEOUT` | `20s` | 每个 Spring graceful-shutdown 阶段等待时间 |
| `LOGGING_FILE_PATH` 或 `LOG_PATH` | 工作目录等日志配置默认值 | 日志文件目录 |
| `LOGGING_FILE_NAME` | 各应用独立文件名 | 完整日志文件路径 |
| `LOGGING_LEVEL_ROOT` | `INFO` | 根日志级别 |
| `LOGGING_LOGBACK_ROLLINGPOLICY_MAX_FILE_SIZE` | `10MB` | 单个日志滚动大小 |
| `LOGGING_LOGBACK_ROLLINGPOLICY_MAX_HISTORY` | `7` | 保留天数 |
| `LOGGING_LOGBACK_ROLLINGPOLICY_TOTAL_SIZE_CAP` | `1GB` | 日志归档总量上限 |

以下 Nacos 变量目前仅作为注释示例保留；启用前需要在 Maven 和应用配置中解除相关依赖与注册：

| 环境变量 | 预期用途 |
|---|---|
| `NACOS_SERVER_ADDR` | Nacos 服务地址 |
| `NACOS_NAMESPACE` | 命名空间 |
| `NACOS_GROUP` | 分组 |
| `NACOS_USERNAME` | 用户名 |
| `NACOS_PASSWORD` | 密码 |

## 6. 本地启动脚本设置的变量

一键脚本是本地开发入口，不是生产部署模板：

| 环境变量 | 脚本行为 |
|---|---|
| `JWT_JWK_SET_LOCATION` | 指向 `out/signing-jwks.json` |
| `DEFAULT_ADMIN_ENABLED` | 固定设为 `true` |
| `DEFAULT_ADMIN_USERNAME` | 固定设为 `admin` |
| `DEFAULT_ADMIN_PASSWORD` | 固定设为 `LocalAdmin123!` |

脚本生成的密钥保存在 `out/signing-jwks.json`，执行 Maven clean 或删除 `out` 后会丢失或在下次启动时重新生成。生产部署不得使用脚本中的固定管理员和示例密钥。

## 7. 推荐的本地与生产检查

本地启动至少确认：

```text
DB_URL
DB_USERNAME
DB_PASSWORD
JWT_JWK_SET_LOCATION
MANAGER_PUBLIC_ORIGIN
MANAGER_UI_URL_SUFFIX
```

生产启动至少确认：

```text
DB_URL
DB_USERNAME
DB_PASSWORD
MANAGER_DB_SCHEMA
JWT_ISSUER
JWT_JWK_SET_LOCATION
JWT_JWK_SET_URI
OAUTH2_CLIENT_SECRET_HASH
DEFAULT_ADMIN_PASSWORD
MANAGER_PUBLIC_ORIGIN
MANAGER_SERVICE_URL
MANAGER_UI_URL_SUFFIX
CSRF_ENABLED
```

当实例位于 HTTPS 反向代理后，必须同时核对外部 Origin、系统目录、OAuth redirect URI、Issuer、JWKS 可达性和代理转发头。配置不一致时通常表现为登录 403、刷新 401、Cookie 不共享或签名验签失败。
