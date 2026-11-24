# 公共基础设施

[English](shared-infrastructure.en.md)

基础实现、日志和运维资源放在 `baosys-common`；WebFlux、SPA 和网关安全放在 `baosys-common-gateway`；MVC 和资源服务安全放在 `baosys-common-resource`。HTTP 协议模型仍在 `baosys-common-api`，security-core 和 password 保持独立。
前端实现放在 `baosys-common-ui`（npm 包 `@baosys/common-ui`），由Manager UI 的本地依赖引用。

## SPA 托管

Manager Web使用公共 `SpaWebConfiguration`，配置如下：

```yaml
common:
  spa:
    enabled: true
    base-path: /baosys-manager
    resource-location: classpath:/static/
```

组件默认不启用；前缀必须是非根绝对路径，不带尾斜杠。
资源位置支持 `classpath:/` 和 `file:/`，目录末尾自动补 `/`，入口是该目录的 `index.html`。
同时支持带前缀访问和反向代理剥离前缀后的根路径访问。无尾斜杠的入口以 308 跳转并保留原始查询参数。
GET/HEAD 且接受 HTML 的客户端路由可以回退到入口；API、Actuator、OAuth2、静态文件等路径不回退。
修改托管前缀时，仍需同步 Vite base、Router base、反向代理和登录回跳地址。

## 日志

Manager Service 和 Manager Web的 `logback-spring.xml` 只包含公共片段 `baosys/logging/logback-common.xml`。
默认 INFO、控制台和 UTF-8 文件、MDC traceId、每日/10MB 滚动、7 天/1GB 归档限制保持不变。
仍使用 `logging.file.*`、`logging.level.*` 和 `logging.logback.rollingpolicy.*` 覆盖。
新增 logger 或其他应用专属配置可放在入口文件的 include 之后；公共策略只修改公共片段。

## 前端请求与认证

`@baosys/common-ui/authCoordinator` 统一账号版本、页内队列、Web Locks、跨标签页通知和刷新合并。
`@baosys/common-ui/request` 的 `createRequests` 统一 CSRF 获取、账号版本检查、最多一次 401 恢复重试、
响应拆包与 `RequestError` 转换。业务响应类型统一为 `ApiResponse<T>`，原 `ApiResult` 已移除。

每个应用传入自己的 `csrfPath` 和 `recoverSession` 回调：Manager 使用 browser-auth 刷新，
Manager 通过 `authPrefix` 排除认证端点的自动重试；
登录、注销使用独立认证请求通道，仍受 CSRF 和认证操作协调保护。凭据不写入浏览器存储。

公共包使用 TypeScript 源码导出，没有单独发布步骤。Manager UI 的 `.npmrc` 固定 `install-links=true`，
将本地包打包安装到各自 `node_modules`，Axios 由应用提供。修改公共包后在Manager UI 目录重新执行 `npm ci`，
避免继续使用旧安装副本。修改依赖清单时更新对应 `package-lock.json`，不要手改锁文件。
Vite 显式预构建公共入口；浏览器测试在每个应用内部串行运行，避免共享开发服务器的依赖发现重载干扰认证用例。

## 构建

根 `pom.xml` 集中管理 Node `v22.4.0`、npm `10.8.1`、frontend-maven-plugin `1.15.1` 和插件执行配置。
Manager Web 模块只指定 `ui.path` 并启用插件；均执行 `npm ci --no-audit --no-fund` 和 `npm run build`。
UI 与公共 npm 包不加入 Maven Reactor，构建时需要保留仓库内的相对目录布局。
`-Dskip.frontend=true` 可跳过前端安装和构建，发布构建不要使用此开关。

## 运维

Manager Service 和 Manager Web通过 `spring.config.import=classpath:baosys/operations.yml` 使用统一基线：

导入配置优先于同一 YAML 文档中的本地值；应用专属覆盖放在后续 `---` 文档、profile 或环境变量中。
Manager Web 的额外 gateway 端点采用后续文档覆盖。

- Web 服务正常关闭使用 graceful shutdown；`SHUTDOWN_TIMEOUT` 默认 `20s`，控制 Spring 每个停机阶段的等待。
- 暴露 `health,info,metrics`，健康详情默认隐藏，启用 liveness/readiness，指标带 application 标签。
- Web 网关探针路径为 `/actuator/health/liveness` 和 `/actuator/health/readiness`。
  Servlet 服务路径前面加自己的 context-path，例如 `/baosys-manager/actuator/health/readiness`。
- 健康检查和 info 允许匿名访问；其余 `/actuator/**` 不校验系统管理权限，未公开端点仍要求登录，网关支持显式 Bearer 认证。
  Manager Web 额外保留只读 gateway 端点。不要把 metrics 加入 permit-all-paths。
- 探针默认反映 Spring 应用可用性，不代表所有外部依赖和业务任务均正常。

线程池自己的 `shutdown-timeout-seconds` 与 Spring 停机阶段等待是不同配置，前者仍可能先等待、
中断后再等待一次。强制杀进程不能保证执行关闭回调。

## 存储 JSON 兼容性

HTTP（包括 SSE）使用 Spring 管理的 ObjectMapper，遵循应用的 Jackson 配置。
公共 `StorageJson.legacy()` 为接入应用提供兼容旧 Jackson 默认行为的存储序列化能力。
存储 mapper 在创建后不再修改、不对外暴露，可被多线程共享；不再为每个线程创建 mapper。

存储配置刻意保持旧 `new ObjectMapper()` 行为：字段名、类注解、枚举、null、数值和日期规则不变，
未知字段仍报错，不自动注册 HTTP 的日期模块或命名策略。现有 UTF-8 转换不变，Properties 等非 JSON 节点格式不变。
平台不包含业务存储服务，也不执行数据迁移。

`StorageJsonCompatibilityTest` 使用按旧格式固定的样本验证拨测结果、主节点、探针组、系统配置（含 Redis）、
空字段、泛型列表、UTF-8、未知字段、枚举和并发访问；验证旧数据可读、新写入可由旧 mapper 读取及输出一致。
这些是代码契约样本，未读取生产数据。未来如改变存储格式，必须增加版本与迁移策略，并用脱敏历史样本验证。

## 验证

根目录运行 `mvn verify -Pintegration-tests`：包含前端安装/构建、默认后端测试、Manager 网关及 PostgreSQL 集成测试。
数据库集成测试需要 Docker，本机 HTTP 测试需要回环端口。各 UI 目录运行 `npm run test:browser`，
浏览器默认使用 Edge，可用 `PLAYWRIGHT_CHANNEL` 切换。
测试覆盖公共 SPA 配置、自定义资源目录、路由排除、序列化兼容、日志/运维配置导入和管理端点权限。
