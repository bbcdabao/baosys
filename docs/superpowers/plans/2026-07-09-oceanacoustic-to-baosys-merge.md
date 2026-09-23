# OceanAcoustic to BaoSys Merge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 OceanAcoustic 中五个对应模块的最新 Common/Manager 行为、测试和文档合并到 BaoSys，同时保留 BaoSys 命名及目标侧独有文件。

**Architecture:** 以 BaoSys 为基线，按 Common、Manager Service、Manager UI、Manager Web、根构建和文档六个边界执行语义迁移。每批先迁移或补充源侧测试，再迁移实现，并通过模块级测试确认；共享配置和文档最后合并，以避免引入 Simulators 依赖。

**Tech Stack:** Java 17、Spring Boot 3.5.7、Spring Cloud 2025.0.1、Maven、MyBatis Plus、PostgreSQL、Vue 3、TypeScript、Vite、npm。

## Global Constraints

- 只迁移 `oceanacoustic-common`、`oceanacoustic-common-api`、`oceanacoustic-manager`、`oceanacoustic-manager-ui`、`oceanacoustic-manager-web`。
- 所有 Maven artifactId、Java 包、API 路径、Cookie 名和展示名称继续使用 BaoSys 标识。
- 排除所有 Simulators 专属代码、模块、权限布局和文档。
- 保留目标侧独有的 `package-info.java`、LICENSE 和 `baosys-manager-ui/README.md`。
- 不迁移 `target`、`out`、`node_modules`、`dist` 或其他构建产物。
- 不删除 BaoSys 文件，除非迁移后的同一功能明确要求替代；本计划不包含目标独有文件删除。

---

### Task 1: 建立基线和差异清单

**Files:**
- Reference: `docs/superpowers/specs/2026-07-09-oceanacoustic-to-baosys-merge-design.md`
- Reference: `pom.xml`
- Reference: `baosys-manager-ui/package.json`

**Interfaces:**
- Consumes: 当前 BaoSys `main` 分支和本设计提交。
- Produces: 可复现的后端、前端基线结果和迁移文件边界。

- [ ] **Step 1: 确认工作区只包含已提交的设计和计划**

Run:

```powershell
git -c safe.directory=C:/bao/mygithub/baosys status --short --branch
```

Expected: 除本计划文件外无未提交代码或文档修改。

- [ ] **Step 2: 运行后端基线单元测试**

Run:

```powershell
mvn -Dskip.frontend=true test
```

Expected: Maven Reactor 成功；如环境缺少缓存依赖，记录明确的依赖解析错误，不把它误判为代码失败。

- [ ] **Step 3: 运行前端基线构建**

Run:

```powershell
npm run build
```

Working directory: `baosys-manager-ui`

Expected: `vue-tsc --noEmit` 和 Vite build 成功。

- [ ] **Step 4: 提交实施计划**

```powershell
git add docs/superpowers/plans/2026-07-09-oceanacoustic-to-baosys-merge.md
git commit -m "docs: add baosys merge implementation plan"
```

### Task 2: 合并 Common 安全模块

**Files:**
- Create: `baosys-common/src/test/java/baosys/common/security/config/JwtDecoderConfigurationTest.java`
- Modify: `baosys-common/src/main/java/baosys/common/autoconfigure/PasswordAutoConfiguration.java`
- Modify: `baosys-common/src/main/java/baosys/common/security/annotation/EnableAuthenticationCheck.java`
- Modify: `baosys-common/src/main/java/baosys/common/security/annotation/EnableUltraViresCheck.java`
- Modify: `baosys-common/src/main/java/baosys/common/security/config/AuthenticationCheckConfiguration.java`
- Modify: `baosys-common/src/main/java/baosys/common/security/config/JwtDecoderConfiguration.java`
- Modify: `baosys-common/src/main/java/baosys/common/security/config/JwtEncoderConfiguration.java`
- Modify: `baosys-common/src/main/java/baosys/common/security/config/SecurityProperties.java`
- Modify: `baosys-common/src/main/java/baosys/common/security/config/UltraViresCheckConfiguration.java`
- Modify: `baosys-common/src/main/java/baosys/common/security/gateway/CookieTokenToAuthorizationFilter.java`
- Modify: `baosys-common/src/main/java/baosys/common/security/gateway/GatewayCsrfFilter.java`
- Modify: `baosys-common/src/main/java/baosys/common/security/gateway/GatewaySecurityConfig.java`
- Modify: `baosys-common/src/main/java/baosys/common/security/gateway/GatewaySecurityErrorHandler.java`
- Modify: `baosys-common/src/main/java/baosys/common/security/gateway/PermitAllPathMatcher.java`
- Modify: `baosys-common/src/main/java/baosys/common/security/jwt/JwtKeyLoader.java`
- Modify: `baosys-common/src/main/java/baosys/common/security/jwt/JwtTokenService.java`
- Modify: `baosys-common/src/main/java/baosys/common/security/jwt/JwtTokenServiceImpl.java`
- Modify: `baosys-common/src/main/java/baosys/common/security/password/BCryptPasswordHasher.java`
- Modify: `baosys-common/src/main/java/baosys/common/security/password/PasswordHasher.java`
- Modify: `baosys-common/src/main/java/baosys/common/security/password/PasswordProperties.java`
- Modify: `baosys-common/src/main/java/baosys/common/security/resource/JwtPermissionConverter.java`
- Modify: `baosys-common/src/main/java/baosys/common/security/resource/ResourceServerSecurityConfig.java`
- Modify: `baosys-common/src/test/java/baosys/common/security/jwt/JwtTokenServiceTest.java`

**Interfaces:**
- Consumes: `SecurityProperties.Jwt.issuer`、JWT 公私钥和 Spring Security JWT API。
- Produces: `SecurityProperties.systemType`；解码器对 `iss` 和可选 `systemType` claim 的联合校验；网关和资源服务统一的认证错误行为。

- [ ] **Step 1: 迁移 JWT 解码器测试**

将源测试映射为 `baosys` 包，保留以下断言：

```java
assertThat(decoder.decode(managerToken).getClaimAsString("systemType"))
        .isEqualTo("MANAGER");
assertThatThrownBy(() -> decoder.decode(otherSystemToken))
        .isInstanceOf(JwtValidationException.class);
```

- [ ] **Step 2: 运行新测试确认当前实现不满足子系统校验**

Run:

```powershell
mvn -pl baosys-common -am -Dtest=JwtDecoderConfigurationTest test
```

Expected: FAIL，失败点为缺少 `systemType` 属性或错误系统 token 未被拒绝。

- [ ] **Step 3: 迁移 Common 实现**

逐文件采用源侧行为并执行以下命名变换：

```text
oceanacoustic -> baosys
oceanacoustic -> baosys
Ocean Acoustic / OceanAcoustic -> BaoSys
OA_CSRF_TOKEN -> BAOSYS_CSRF_TOKEN
```

保留 `baosys-common/src/main/java/baosys/common/security/package-info.java`。核心联合校验必须为：

```java
return new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefault(),
        issuerValidator,
        systemTypeValidator);
```

- [ ] **Step 4: 运行 Common 测试**

Run:

```powershell
mvn -pl baosys-common -am test
```

Expected: `baosys-common-api` 和 `baosys-common` 测试全部通过。

- [ ] **Step 5: 提交 Common 迁移**

```powershell
git add baosys-common baosys-common-api
git commit -m "feat: merge common security updates"
```

### Task 3: 合并 Manager API、服务和会话清理

**Files:**
- Create: `baosys-manager/baosys-manager-service/src/main/resources/db/scheduler/pg_cron_user_login_cleanup.sql`
- Create: `baosys-manager/baosys-manager-service/src/test/java/baosys/manager/infra/database/UserLoginCleanupFunctionIntegrationTest.java`
- Modify: `baosys-manager/baosys-manager-api/src/main/java/baosys/manager/api/AuthApi.java`
- Modify: `baosys-manager/baosys-manager-api/src/main/java/baosys/manager/api/LoginSessionApi.java`
- Modify: `baosys-manager/baosys-manager-api/src/main/java/baosys/manager/api/UserApi.java`
- Modify: `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/config/DefaultAdminInitializer.java`
- Modify: `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/config/web/TraceIdFilter.java`
- Modify: `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/controllers/AuthController.java`
- Modify: `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/error/GlobalExceptionHandler.java`
- Modify: `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/model/PermissionCode.java`
- Modify: `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/services/impl/AuthAppServiceImpl.java`
- Modify: `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/services/impl/UserAppServiceImpl.java`
- Modify: `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/infra/database/converter/PermissionPersistenceConverter.java`
- Modify: `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/infra/database/entity/UserLoginEntity.java`
- Modify: `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/infra/database/mapper/UserLoginMapper.java`
- Modify: `baosys-manager/baosys-manager-service/src/main/java/baosys/manager/infra/security/RefreshTokenProvider.java`
- Modify: `baosys-manager/baosys-manager-service/src/main/resources/application.yml`
- Modify: `baosys-manager/baosys-manager-service/src/main/resources/db/migration/V20260621_001__create_user_auth_tables.sql`
- Modify: `baosys-manager/baosys-manager-service/src/test/java/baosys/manager/app/controllers/UserControllerIntegrationTest.java`
- Modify: `baosys-manager/baosys-manager-service/src/test/java/baosys/manager/app/services/impl/AuthAppServiceImplTest.java`
- Modify: `baosys-manager/baosys-manager-service/src/test/java/baosys/manager/app/services/impl/UserAppServiceImplTest.java`

**Interfaces:**
- Consumes: Common JWT/密码服务、Manager API DTO、`user_info` 和 `user_login` 表。
- Produces: 与源侧一致的登录、刷新、用户管理和会话清理行为；保留 `/baosys-manager` API 前缀。

- [ ] **Step 1: 先迁移 Manager 单元及集成测试**

将三个既有测试和新增清理测试映射为 BaoSys 包与路径。清理测试必须验证：

```sql
select cleanup_inactive_user_login_sessions()
```

执行后删除 `updated_at < current_timestamp - interval '1 day'` 的会话，并保留最近续租会话。

- [ ] **Step 2: 运行 Manager 单元测试确认行为差异**

Run:

```powershell
mvn -pl baosys-manager -am test
```

Expected: 至少一个迁移测试失败，指向刷新资格、默认管理员、权限或用户在线状态的旧行为。

- [ ] **Step 3: 迁移 API 和服务实现**

按源侧文件逐项合并，统一执行：

```text
oceanacoustic -> baosys
oceanacoustic-manager -> baosys-manager
/api/oceanacoustic-manager -> /api/baosys-manager
/oceanacoustic-manager -> /baosys-manager
```

保留三个目标侧 `package-info.java`。`PermissionCode` 只保留 Manager 当前使用的权限，不引入 Simulators 布局权限。

- [ ] **Step 4: 迁移数据库清理实现**

迁移 Flyway 中的清理函数和独立 `pg_cron` 示例。函数删除条件保持：

```sql
updated_at < CURRENT_TIMESTAMP - INTERVAL '1 day'
```

独立调度脚本只负责注册每日清理，不假定所有部署环境已安装 `pg_cron`。

- [ ] **Step 5: 运行 Manager 测试**

Run:

```powershell
mvn -pl baosys-manager -am test
```

Expected: 所有非 `*IntegrationTest` 测试通过。

- [ ] **Step 6: 在 Docker 可用时运行新增数据库集成测试**

Run:

```powershell
mvn -pl baosys-manager/baosys-manager-service -Dtest=UserLoginCleanupFunctionIntegrationTest test
```

Expected: Docker 可用时 PASS；Docker 不可用时记录 Testcontainers 环境错误。

- [ ] **Step 7: 提交 Manager 后端迁移**

```powershell
git add baosys-manager
git commit -m "feat: merge manager service updates"
```

### Task 4: 合并 Manager UI

**Files:**
- Modify: `baosys-manager-ui/index.html`
- Modify: `baosys-manager-ui/package-lock.json`
- Modify: `baosys-manager-ui/src/constants/permissions.ts`
- Modify: `baosys-manager-ui/src/i18n/locales/en-US.ts`
- Modify: `baosys-manager-ui/src/i18n/locales/zh-CN.ts`
- Modify: `baosys-manager-ui/src/router/index.ts`
- Modify: `baosys-manager-ui/src/store/auth.ts`
- Modify: `baosys-manager-ui/src/utils/authSession.ts`
- Modify: `baosys-manager-ui/src/utils/request.ts`
- Modify: `baosys-manager-ui/src/views/login/LoginView.vue`
- Modify: `baosys-manager-ui/src/views/user/LoginManagementView.vue`
- Modify: `baosys-manager-ui/src/views/user/UserManagementView.vue`
- Preserve: `baosys-manager-ui/LICENSE`
- Preserve: `baosys-manager-ui/README.md`

**Interfaces:**
- Consumes: Manager API 的 BaoSys 路径、JWT `systemType` 和 `permissions` claims、BaoSys CSRF Cookie。
- Produces: 与后端新认证/权限行为一致的登录态、请求重试、路由守卫和用户管理界面。

- [ ] **Step 1: 迁移 UI 源文件**

以源侧十二个差异文件为行为基线，执行：

```text
oceanacoustic -> baosys
Ocean Acoustic / OceanAcoustic -> BaoSys
OA_CSRF_TOKEN -> BAOSYS_CSRF_TOKEN
```

`permissions.ts` 不引入 Simulators 权限；`package.json` 保持 BaoSys 名称和当前依赖版本。

- [ ] **Step 2: 运行类型检查和生产构建**

Run:

```powershell
npm run build
```

Working directory: `baosys-manager-ui`

Expected: `vue-tsc --noEmit` 和 Vite build 成功。

- [ ] **Step 3: 检查 UI 标识**

Run:

```powershell
rg -n -i "oceanacoustic|ocean acoustic|simulators" baosys-manager-ui
```

Expected: 生产源码、配置和文档中无匹配；依赖锁文件中的第三方文本例外需逐条说明。

- [ ] **Step 4: 提交 UI 迁移**

```powershell
git add baosys-manager-ui
git commit -m "feat: merge manager ui updates"
```

### Task 5: 合并 Manager Web

**Files:**
- Create: `baosys-manager-web/src/test/resources/static/assets/test.css`
- Create: `baosys-manager-web/src/test/resources/static/index.html`
- Modify: `baosys-manager-web/pom.xml`
- Modify: `baosys-manager-web/src/main/java/baosys/managerweb/SpaWebConfiguration.java`
- Modify: `baosys-manager-web/src/main/resources/application.yml`
- Modify: `baosys-manager-web/src/test/java/baosys/managerweb/ManagerWebApplicationTest.java`
- Modify: `baosys-manager-web/src/test/java/baosys/managerweb/security/GatewaySecurityIntegrationTest.java`
- Modify: `baosys-manager-web/src/test/java/baosys/managerweb/SpaWebConfigurationTest.java`
- Modify: `baosys-manager-web/src/test/resources/application-test.yml`
- Preserve: `baosys-manager-web/src/main/java/baosys/managerweb/package-info.java`

**Interfaces:**
- Consumes: `baosys-common` 网关安全配置、Manager Service `/baosys-manager/**` 路径、Manager UI 静态资源。
- Produces: `/api/baosys-manager/**` 路由、安全过滤和 SPA fallback。

- [ ] **Step 1: 先迁移 Web 测试和静态测试资源**

测试继续使用 BaoSys 路由，并覆盖：

```text
未认证 API 请求被拒绝
公开登录/刷新端点可访问
不存在的前端路由回退到 index.html
带扩展名的不存在资源不回退
```

- [ ] **Step 2: 运行 Web 测试确认当前实现差异**

Run:

```powershell
mvn -pl baosys-manager-web -am -Dskip.frontend=true test
```

Expected: 新迁移的 SPA 或网关安全断言在旧实现上至少一项失败。

- [ ] **Step 3: 迁移 Web 实现与配置**

按源侧行为映射包名、模块名和路径。`pom.xml` 只吸收源侧 `npm install --no-audit --no-fund` 变更，继续使用 `baosys-manager-ui` 目录和 `baosys-manager-web` finalName。

- [ ] **Step 4: 运行 Web 测试**

Run:

```powershell
mvn -pl baosys-manager-web -am -Dskip.frontend=true test
```

Expected: Web 及其依赖模块测试全部通过。

- [ ] **Step 5: 提交 Web 迁移**

```powershell
git add baosys-manager-web
git commit -m "feat: merge manager web updates"
```

### Task 6: 合并根 Maven 构建配置

**Files:**
- Modify: `pom.xml`

**Interfaces:**
- Consumes: BaoSys 四个 Maven 模块。
- Produces: 默认单元测试边界和 `out` 目录中的 Manager 应用 Jar。

- [ ] **Step 1: 合并测试与输出属性**

在根 POM 增加：

```xml
<maven-antrun-plugin.version>3.1.0</maven-antrun-plugin.version>
<output.directory>${maven.multiModuleProjectDirectory}/out</output.directory>
```

- [ ] **Step 2: 合并 Surefire 集成测试排除**

增加：

```xml
<plugin>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <excludes>
      <exclude>**/*IntegrationTest.java</exclude>
    </excludes>
  </configuration>
</plugin>
```

- [ ] **Step 3: 合并应用 Jar 复制逻辑**

使用 `maven-antrun-plugin` 在 `verify` 阶段复制以下两个 artifact：

```text
baosys-manager-service
baosys-manager-web
```

不得加入任何 Simulators artifact。

- [ ] **Step 4: 验证 Maven 模型**

Run:

```powershell
mvn -Dskip.frontend=true -DskipTests validate
```

Expected: Reactor 只包含 BaoSys 现有 Maven 模块并成功完成。

- [ ] **Step 5: 提交根构建配置**

```powershell
git add pom.xml
git commit -m "build: align baosys reactor configuration"
```

### Task 7: 语义合并 Markdown 文档

**Files:**
- Modify: `README.md`
- Modify: `baosys-common/README.md`
- Modify: `baosys-manager/README.md`
- Modify: `baosys-manager-ui/README.md` only if implementation makes an existing statement inaccurate
- Modify: `docs/architecture/system_architecture.md`
- Modify: `docs/deployment/environment_variables.md`
- Modify: `docs/deployment/local_development.md`
- Modify: `docs/microservices_module_architecture_standard.md`
- Modify: `docs/modules/manager/auth_and_session.md`
- Modify: `docs/modules/manager/database_design.md`
- Modify: `docs/modules/manager/manager_module_design.md`
- Modify: `docs/modules/manager/permission_model.md`

**Interfaces:**
- Consumes: Tasks 2–6 验证后的实际代码、配置、路径和测试行为。
- Produces: 仅描述 BaoSys Common/Manager 当前事实的文档集合。

- [ ] **Step 1: 合并 Common 和 Manager 模块 README**

文档必须准确说明：

```text
BAOSYS_CSRF_TOKEN
默认管理员默认关闭
常规 Maven 测试排除 *IntegrationTest
登录会话按 updated_at 空闲一天后由数据库任务清理
```

- [ ] **Step 2: 合并 Manager 专题文档**

从源文档吸收 JWT `systemType`、Refresh Token、会话清理、权限和测试边界的说明。只保留 `MANAGER` 系统类型和 BaoSys API 路径。

- [ ] **Step 3: 整理架构、部署和根 README**

保留 BaoSys 当前单一 Manager 闭环；删除源文档中所有 Simulators 模块、链路、环境变量、布局权限和启动方式。

- [ ] **Step 4: 扫描文档残留**

Run:

```powershell
rg -n -i "oceanacoustic|ocean acoustic|simulators" -g "*.md" .
```

Expected: 无 OceanAcoustic 残留；除设计/实施计划中说明排除范围的文字外，无 Simulators 产品文档。

- [ ] **Step 5: 提交文档迁移**

```powershell
git add README.md baosys-common/README.md baosys-manager/README.md baosys-manager-ui/README.md docs
git commit -m "docs: align baosys common and manager documentation"
```

### Task 8: 全量验证和最终审阅

**Files:**
- Verify: 所有本计划修改的文件

**Interfaces:**
- Consumes: Tasks 2–7 的提交。
- Produces: 可构建、无错误命名残留、文档一致的 BaoSys 工作树。

- [ ] **Step 1: 扫描项目标识和排除模块**

Run:

```powershell
rg -n -i "oceanacoustic|ocean acoustic" --glob "!docs/superpowers/**" .
rg -n "baosys-simulators|baosys\.simulators" --glob "!docs/superpowers/**" .
```

Expected: 两次扫描均无匹配。

- [ ] **Step 2: 运行全量后端单元测试**

Run:

```powershell
mvn -Dskip.frontend=true test
```

Expected: Reactor BUILD SUCCESS，默认排除 `*IntegrationTest`。

- [ ] **Step 3: 运行前端生产构建**

Run:

```powershell
npm run build
```

Working directory: `baosys-manager-ui`

Expected: 类型检查和 Vite build 成功。

- [ ] **Step 4: 运行 Maven 打包验证**

Run:

```powershell
mvn -Dskip.frontend=true -DskipTests verify
```

Expected: BUILD SUCCESS，`out` 包含 `baosys-manager-service.jar` 和 `baosys-manager-web.jar`。

- [ ] **Step 5: 检查补丁质量**

Run:

```powershell
git diff HEAD~6 --check
git status --short --branch
```

Expected: 无空白错误；工作树干净；分支只领先远端本次设计、计划和迁移提交。

- [ ] **Step 6: 对照设计完成验收**

逐项确认：

```text
五组模块全部覆盖
目标独有文件保留
无 Simulators 产品内容
无 OceanAcoustic 标识
代码、配置和 Markdown 描述一致
```

