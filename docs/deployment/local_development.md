# BaoSys 本地开发指南

内部子系统使用 [同域共享 Manager 登录](shared_login.md)，无需网关 OAuth2 Client 会话。账户通过统一 permissions 分配多个系统，外部 OAuth2 能力保留。启动需提供 JWK 文件及认证中心配置；Vite 5173 开发时须相应设置 MANAGER_PUBLIC_ORIGIN。数据库初始化说明见 [升级指南](oauth2_upgrade.md)。

## 1. 目标

本文说明如何在本地启动当前完整链路：

```text
PostgreSQL
  → baosys-manager-service :9090
  → baosys-manager-web :8080
  → baosys-manager-ui :5173
```

环境变量完整列表参见 [环境变量说明](environment_variables.md)。

## 2. 环境要求

| 工具 | 要求 |
|---|---|
| JDK | 17 |
| Maven | 3.9+ |
| Node.js | 20.19+，工程打包配置使用 22.4.0 |
| npm | 10+ |
| PostgreSQL | 推荐 17 |
| Docker | 运行 Testcontainers 集成测试时必须 |

检查版本：

```shell
java -version
mvn -version
node --version
npm --version
docker version
```

Windows PowerShell 如果禁止执行 `npm.ps1`，使用：

```powershell
npm.cmd --version
```

后续 npm 命令同样可以替换为 `npm.cmd`。

## 3. 准备 PostgreSQL

### 3.1 使用 Docker

PowerShell：

```powershell
docker run --name baosys-postgres `
  -e POSTGRES_DB=app_db `
  -e POSTGRES_USER=postgres `
  -e POSTGRES_PASSWORD=postgres123456 `
  -p 5432:5432 `
  -d postgres:17-alpine
```

Bash：

```shell
docker run --name baosys-postgres \
  -e POSTGRES_DB=app_db \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres123456 \
  -p 5432:5432 \
  -d postgres:17-alpine
```

后续启动和停止：

```shell
docker start baosys-postgres
docker stop baosys-postgres
```

### 3.2 使用本机 PostgreSQL

创建数据库：

```sql
CREATE DATABASE app_db;
```

默认连接参数：

```text
jdbc:postgresql://localhost:5432/app_db
postgres
postgres123456
```

如本地参数不同，通过 `DB_URL`、`DB_USERNAME` 和 `DB_PASSWORD` 覆盖。

启动 Manager Service 时，Flyway 从 `V1__init_manager.sql` 创建账户、登录、审计、OAuth2 授权和 Spring Session 表，以及会话清理函数和授权撤销触发器。默认使用独立的 `manager` schema，可通过 `MANAGER_DB_SCHEMA` 覆盖。

## 4. 首次构建

在仓库根目录执行：

```shell
mvn install -DskipTests "-Dskip.frontend=true"
```

该命令：

- 编译并安装 Common、Manager API 等内部依赖。
- 跳过测试。
- 跳过 Manager Web 中的 Node 安装和前端构建。

首次执行需要 Maven 能访问依赖仓库。

前端安装：

```shell
cd baosys-manager-ui
npm install --no-audit --no-fund
cd ..
```

## 5. 启动后端服务

建议为每个进程单独打开终端。

### 5.1 终端一：Manager Service

首次需要创建管理员时，先设置以下变量（PowerShell 示例，密码仅用于本地开发）：

```powershell
$env:DEFAULT_ADMIN_ENABLED="true"
$env:DEFAULT_ADMIN_USERNAME="local-admin"
$env:DEFAULT_ADMIN_PASSWORD="LocalAdmin123!"
```

默认不创建管理员，密码至少 12 位；初始化器不会覆盖已有账户。环境变量只作用于当前终端及其启动的进程。

```shell
mvn -f baosys-manager/baosys-manager-service/pom.xml spring-boot:run
```

预期：

```text
Port:         9090
Context path: /baosys-manager
Application:  baosys-manager
```

检查健康状态：

```text
http://localhost:9090/baosys-manager/actuator/health
```

### 5.2 终端二：Manager Web

```shell
mvn -f baosys-manager-web/pom.xml spring-boot:run "-Dskip.frontend=true"
```

预期：

```text
Port:        8080
Application: baosys-manager-web
Downstream:  http://127.0.0.1:9090
```

检查网关健康状态：

```text
http://localhost:8080/actuator/health
```

## 6. 启动前端

终端三：

```shell
cd baosys-manager-ui
npm run dev
```

开发地址：

```text
http://localhost:5173
```

Vite 把 `/api/**` 代理到：

```text
http://localhost:8080
```

因此浏览器始终通过网关访问 Manager Service。

## 7. 启动顺序

推荐：

```text
1. PostgreSQL
2. baosys-manager-service
3. baosys-manager-web
4. baosys-manager-ui
```

Manager Web 可以先启动，但在 Manager Service 可用前，请求会返回下游连接错误。

## 8. 常用开发命令

### 8.1 后端编译

```shell
mvn compile "-Dskip.frontend=true"
```

### 8.2 默认后端测试

```shell
mvn test "-Dskip.frontend=true"
```

根 POM 的 Surefire 默认排除所有 `*IntegrationTest`，因此该命令只运行非集成测试，不需要 Docker。

### 8.3 只测试 Common

```shell
mvn -pl baosys-common -am test
```

### 8.4 只测试 Manager

```shell
mvn -pl baosys-manager/baosys-manager-service -am test
```

### 8.5 只测试网关

```shell
mvn -pl baosys-manager-web -am test "-Dskip.frontend=true"
```

### 8.6 前端检查与构建

```shell
cd baosys-manager-ui
npm run build
```

该脚本先运行 `vue-tsc --noEmit`，再生成 `dist`。

### 8.7 后端集成测试

在仓库根目录启用已配置的 `integration-tests` profile，由 Failsafe 执行集成测试并在 `verify` 检查结果：

```shell
mvn -Pintegration-tests verify "-Dskip.frontend=true"
```

只运行 Manager 及其依赖的测试（需要 Docker，Testcontainers 会启动独立 PostgreSQL）：

```shell
mvn -Pintegration-tests -pl :baosys-manager-service -am verify "-Dskip.frontend=true"
```

只运行网关及其依赖的测试（无需数据库或 Docker）：

```shell
mvn -Pintegration-tests -pl :baosys-manager-web -am verify "-Dskip.frontend=true"
```

## 9. 生产形式的本地打包

完整打包：

```shell
mvn clean package
```

Manager Web 会：

1. 下载工程指定的 Node/npm。
2. 执行 `npm install --no-audit --no-fund`。
3. 执行 `npm run build`。
4. 把 UI `dist` 复制到 `target/classes/static`。
5. 生成可启动 Jar。

产物：

```text
baosys-manager/baosys-manager-service/target/baosys-manager.jar
baosys-manager-web/target/baosys-manager-web.jar
```

执行 `mvn verify` 后，可启动应用 Jar 还会复制到仓库根目录的 `out` 目录。

启动：

```shell
java -jar baosys-manager/baosys-manager-service/target/baosys-manager.jar
java -jar baosys-manager-web/target/baosys-manager-web.jar
```

此时直接访问：

```text
http://localhost:8080
```

Manager Web 会托管已经打包的 Vue 页面。

## 10. 本地验证清单

```text
[ ] PostgreSQL 5432 可连接
[ ] Manager Service 健康检查为 UP
[ ] Manager Web 健康检查为 UP
[ ] http://localhost:5173 可打开登录页
[ ] 管理员可以登录
[ ] 用户列表可以加载
[ ] 可以新增、修改和删除测试用户
[ ] 登录会话页面可以查询
[ ] npm run build 通过
[ ] Maven 单元测试通过
[ ] Docker 可用时数据库集成测试通过
```

## 11. 常见问题

### 11.1 数据库连接失败

检查：

```text
PostgreSQL 是否启动
数据库 app_db 是否存在
DB_URL / DB_USERNAME / DB_PASSWORD 是否属于同一终端
5432 是否被其他进程占用
```

### 11.2 Testcontainers 找不到 Docker

典型错误：

```text
Could not find a valid Docker environment
```

处理：

1. 启动 Docker Desktop。
2. 确认 `docker version` 可连接服务端。
3. 再运行 Maven 测试。

### 11.3 npm.ps1 被 PowerShell 拒绝

使用：

```powershell
npm.cmd ci
npm.cmd run dev
npm.cmd run build
```

不需要为了本项目修改系统执行策略。

### 11.4 网关返回下游连接错误

确认 Manager Service 已启动，并检查：

```text
MANAGER_SERVICE_URL=http://127.0.0.1:9090
```

### 11.5 登录后接口返回 401

检查：

- Manager Web 和 Manager Service 的 JWT issuer、公钥是否一致。
- 浏览器是否收到共享 `BAOSYS_ACCESS_TOKEN` HttpOnly Cookie；Manager Origin 是否与浏览器地址一致。详见 [共享登录配置](shared_login.md)。
- 请求是否经过 8080 网关，而不是直接访问错误端口。
- Access Token 是否已过期。

### 11.6 前端页面刷新后 404

开发模式应访问 Vite 5173；打包模式应访问 Manager Web 8080。Manager Web 的 SPA 回退只处理接受 HTML 且不是 `/api`、`/actuator` 或静态资源的路径。

### 11.7 修改内部模块后启动找不到依赖

先在根目录重新安装：

```shell
mvn install -DskipTests "-Dskip.frontend=true"
```

然后重启对应模块。

## 12. 结束开发

停止三个应用进程后，停止数据库容器：

```shell
docker stop baosys-postgres
```

保留容器会保留数据库卷层中的开发数据；需要全新数据时，可以删除并重新创建容器。

## 13. 容器构建

在完成 Maven verify 后，从仓库根目录执行：

```powershell
docker build -t baosys-manager:1.0.0 baosys-manager/baosys-manager-service
docker build -t baosys-manager-web:1.0.0 baosys-manager-web
```

可用 --build-arg BASE_IMAGE=<镜像地址> 覆盖 Java 17 运行时镜像。容器分别暴露 9090/8080；通过环境变量传入数据库与路由配置。
