# 启动与部署

当前系统尚未部署，按空库首次初始化。以下命令默认从仓库根目录执行；环境变量示例使用 PowerShell，每个服务在自己的终端设置。

## 环境与构建

需要 JDK 17、Maven 3.9+、Node.js 20.19+ 和 PostgreSQL 17。数据库集成测试需要 Docker。

```powershell
docker run --name baosys-postgres -e POSTGRES_DB=app_db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres123456 -p 5432:5432 -d postgres:17-alpine
mvn install -DskipTests "-Dskip.frontend=true"
npm --prefix baosys-manager-ui install
```

已有 PostgreSQL 时直接创建 `app_db` 并配置连接参数，无需启动容器。Windows 若阻止 `npm.ps1`，用 `npm.cmd` 替代。

## 一键启动打包产物

执行 `mvn install` 后，Linux 使用 `bash startup.sh`（Bash 4.3+），Windows 使用 `.\startup.bat`。脚本按自身位置定位 `out`，在该目录运行全部 `*.jar`，日志直接输出到当前终端；按 Ctrl+C 停止本次启动的全部 Java 进程。任一 JAR 退出时，脚本也会停止其余进程。

首次运行若缺少 `out/signing-jwks.json`，脚本使用 PATH 中 JDK 17+ 的 `jshell` 调用 `scripts/generate-signing-key.jsh` 生成，后续启动复用。脚本设置 `JWT_JWK_SET_LOCATION` 指向该文件，并设置 `DEFAULT_ADMIN_ENABLED=true`、`DEFAULT_ADMIN_USERNAME=admin`、`DEFAULT_ADMIN_PASSWORD=LocalAdmin123!`。数据库等其他配置沿用当前环境变量及应用默认值。

注意：当前 Maven 构建会清空 `out`，重新构建后其中的密钥也会被删除，下次启动将生成新密钥。该一键脚本用于本地开发；需要跨构建保留密钥时，使用下方的独立密钥与手动启动方式。

## 首次准备签名密钥

Manager 必须加载持久化的私有 JWKSet：RSA 至少 2048 位，每个 key 有唯一 `kid`、`use=sig`、`alg=RS256`，其中恰好一个包含私钥。下面用 Node.js 在用户目录生成本地开发密钥；只执行一次，重启复用同一文件。

```powershell
node --input-type=module -e "import {generateKeyPairSync,randomUUID} from 'node:crypto'; import {mkdirSync,writeFileSync} from 'node:fs'; import {homedir} from 'node:os'; import {join} from 'node:path'; const dir=join(homedir(),'.baosys'); mkdirSync(dir,{recursive:true}); const {privateKey}=generateKeyPairSync('rsa',{modulusLength:2048}); const jwk={...privateKey.export({format:'jwk'}),kid:randomUUID(),use:'sig',alg:'RS256'}; writeFileSync(join(dir,'signing-jwks.json'),JSON.stringify({keys:[jwk]}),{flag:'wx',mode:0o600});"
```

该文件包含私钥，不提交仓库。正式部署使用受限访问的独立密钥文件。

## 启动三个进程

Manager Service 终端：

```powershell
$env:JWT_JWK_SET_LOCATION = ([System.Uri](Join-Path $HOME '.baosys/signing-jwks.json')).AbsoluteUri
$env:MANAGER_PUBLIC_ORIGIN = "http://localhost:5173"
$env:DEFAULT_ADMIN_ENABLED = "true"
$env:DEFAULT_ADMIN_USERNAME = "admin"
$env:DEFAULT_ADMIN_PASSWORD = "LocalAdmin123!"
mvn -f baosys-manager/baosys-manager-service/pom.xml spring-boot:run
```

客户端密钥默认使用 `{noop}local-dev-client-secret`，本地开发无需设置 `OAUTH2_CLIENT_SECRET_HASH`。该默认值和示例密码仅用于本地开发。正式部署通过 `OAUTH2_CLIENT_SECRET_HASH` 将客户端密钥覆盖为带 `{bcrypt}` 前缀的编码值，并使用独立管理员密码。当前页面登录仍复用 `baosys-web` 注册配置；网关不需要客户端密钥。

管理员默认不自动创建；启用时密码至少 12 位，仅在用户名不存在时创建，不覆盖已有密码或权限。首次创建完成后关闭初始化开关。Flyway 自动执行 V1，无需手动逐个执行 SQL。默认管理员的默认登录子系统为 MGR。

按仓库未部署首版约定，本次默认子系统字段合并到 V1：`user_info.default_system_type` 非空且禁止空白，无数据库默认值，账户写入必须显式选择目录中的系统。已执行旧 V1 的开发数据库需要保留数据后另行迁移，或使用新的隔离 schema 初始化；不要直接覆盖历史校验和或删除已有用户。正式部署库必须通过递增 Flyway 迁移增加字段、明确补齐每个已有账户的默认系统，再施加非空约束。

Manager Web 终端：

```powershell
mvn -f baosys-manager-web/pom.xml spring-boot:run "-Dskip.frontend=true"
```

前端终端：

```powershell
npm --prefix baosys-manager-ui run dev
```

浏览器访问 `http://localhost:5173`。Vite 将 `/api` 和 `/csrf` 代理到 8080。Service 健康检查为 `http://localhost:9090/baosys-manager/actuator/health`，网关为 `http://localhost:8080/actuator/health`。

## 配置速查

实际配置见 [Service application.yml](../baosys-manager/baosys-manager-service/src/main/resources/application.yml) 和 [Web application.yml](../baosys-manager-web/src/main/resources/application.yml)。

| 变量 | 进程 | 默认值或要求 |
|---|---|---|
| `DB_URL` | Service | `jdbc:postgresql://localhost:5432/app_db` |
| `DB_USERNAME / DB_PASSWORD` | Service | `postgres / postgres123456`，本地默认值 |
| `MANAGER_DB_SCHEMA` | Service | `manager`，用户需有建 schema 权限 |
| `MANAGER_PORT / MANAGER_CONTEXT_PATH` | Service | `9090 / /baosys-manager` |
| `JWT_JWK_SET_LOCATION` | Service | 必填，私有 JWKSet 文件资源 URI |
| `OAUTH2_CLIENT_SECRET_HASH` | Service | 默认 `{noop}local-dev-client-secret`；正式部署覆盖为独立的编码密钥 |
| `DEFAULT_ADMIN_ENABLED` | Service | `false` |
| `DEFAULT_ADMIN_USERNAME / DEFAULT_ADMIN_PASSWORD` | Service | `admin / 空` |
| `MANAGER_PUBLIC_ORIGIN` | Service | `http://localhost:8080`；Vite 开发改为 5173，不带路径或尾斜杠 |
| `SHARED_LOGIN_SECURE_COOKIE` | Service | `false`；HTTPS 必须设为 `true` |
| `CAT_UI_BASE_URL` | Service（系统目录） | `http://localhost:8081/`；必须与 Manager 同主机、同协议，以斜杠结尾 |
| `BAOSYS_SYSTEMS_CONFIG` | Service | `classpath:baosys-systems.yml`；可改为完整外部目录文件 URI |
| `GATEWAY_PORT` | Web | `8080` |
| `MANAGER_SERVICE_URL` | Web | `http://127.0.0.1:9090` |
| `JWT_ISSUER` | 两者 | 默认均为 `http://localhost:9090/baosys-manager`，本地无需设置；覆盖时两者保持一致 |
| `JWT_JWK_SET_URI` | 两者 | Service 默认 localhost:9090、Web 默认 127.0.0.1:9090 的 `/baosys-manager/oauth2/jwks` |

目录文件构建时从根目录打包；修改后重新构建，或用 `BAOSYS_SYSTEMS_CONFIG=file:/config/baosys-systems.yml` 覆盖并重启。保留 `MGR` 及其接口权限；每项必须配置 `ui-base-url` 和 `home-path`。MGR 基址引用 `MANAGER_PUBLIC_ORIGIN`，CAT 基址引用 `CAT_UI_BASE_URL`；回跳白名单直接来自该目录，不再使用 `SHARED_LOGIN_ALLOWED_ORIGINS`。CAT 入口登记不代表部署了 CAT 服务；其他子系统需单独部署并接入共享登录。

## 测试与打包

```powershell
# 默认测试不含 *IntegrationTest，不需要 Docker
mvn test "-Dskip.frontend=true"
# 完整后端测试：Manager 数据库测试使用 Testcontainers 独立数据库
mvn -Pintegration-tests verify "-Dskip.frontend=true"
# 前端类型检查和生产构建
npm --prefix baosys-manager-ui run build
# 登录回跳、默认系统表单、兜底页及同源双标签页认证测试（默认本机 Edge，HTTP 接口使用测试替身）
npm --prefix baosys-manager-ui run test:browser
# 包含前端的完整打包；verify 同时复制应用 Jar 到 out
mvn clean verify
```

产物：

- `baosys-manager/baosys-manager-service/target/baosys-manager.jar`
- `baosys-manager-web/target/baosys-manager-web.jar`

部署时分别运行 `java -jar <应用Jar路径>`，Web Jar 已包含前端资源，无需 Vite。本地打包运行时把 `MANAGER_PUBLIC_ORIGIN` 改回 `http://localhost:8080`；正式环境设为浏览器实际 HTTPS Origin，并启用 Secure Cookie。两个进程配置相同 issuer 和可访问的 JWKS 地址；容器中将数据库、Service 和 JWKS 地址改为实际网络地址。

首次部署只执行 V1；部署后结构变更新增 V2 及后续迁移，不再改已执行的 V1。

## 定时清理与排错

V1 创建 `cleanup_inactive_user_login_sessions()`，但不自动安排执行。需要清理到期会话时，配置数据库调度每五分钟调用 `SELECT manager.cleanup_inactive_user_login_sessions();`（自定义 schema 时相应替换）。使用 pg_cron 可参考 [调度脚本](../baosys-manager/baosys-manager-service/src/main/resources/db/scheduler/pg_cron_user_login_cleanup.sql)，需先安装并启用扩展；调度时间以数据库调度器时区为准。每次最多处理 100 个账户，先锁账户并跳过忙碌账户；根据积压量调整执行频率。函数名和 pg_cron 任务名保留兼容，但不再按一天未更新删除会话。

| 现象 | 检查 |
|---|---|
| Service 启动失败 | 数据库连接、JWK 文件、客户端编码密钥，以及管理员密码是否齐全 |
| 登录或刷新返回 403 | 浏览器 Origin 与 MANAGER_PUBLIC_ORIGIN、CSRF 请求头是否匹配 |
| 接口返回 401 | 两端 issuer、JWKS 可达性、Cookie 和 Token 有效期 |
| 已登录但管理接口返回 403 | 账号是否同时拥有 MGR 和对应接口权限 |
| 网关连接下游失败 | Service 是否启动、MANAGER_SERVICE_URL 是否正确 |
| 集成测试找不到 Docker | 启动 Docker Desktop，确认 docker version 可连接服务端 |
| 修改公共模块后启动仍用旧代码 | 在根目录重新执行 install 后重启服务 |

本地数据库容器用 `docker stop baosys-postgres` 停止、`docker start baosys-postgres` 恢复。

浏览器测试默认使用已安装的 Edge；其他环境可设置 `PLAYWRIGHT_CHANNEL=chromium` 并在 UI 目录执行 `npx playwright install chromium`。测试在本机 5178 端口启动独立 Vite 服务，覆盖真实 Cookie、Web Locks、跨标签页通知和自动重试；不替代后端 PostgreSQL 集成测试。生产协调依赖同源和浏览器能力，无 Web Locks/可用存储时降级为单页协调。
