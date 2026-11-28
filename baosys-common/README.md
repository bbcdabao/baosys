# baosys-common

跨业务应用复用的基础实现，Maven 坐标为 `baosys:baosys-common`。协议模型仍在 `baosys-common-api`；
基础模块保留 ApiErrors、TraceIds、受管执行器、StorageJson、密码编码自动配置、日志和运维资源，仅依赖 spring-security-crypto，不依赖 Servlet、MVC、WebFlux、Reactor 或 Spring Security 核心。
Web 适配按应用角色归属：

| 模块 | 实现 |
|---|---|
| baosys-common-gateway | ReactiveApiExceptionHandler/Responses/SecurityHandler、TraceWebFilter、Reactive 自动配置、SpaWebConfiguration/Properties，以及网关安全 |
| baosys-common-resource | ApiExceptionHandler、ServletApiSecurityHandler、TraceIdFilter、TraceAutoConfiguration、MvcAsyncAutoConfiguration，以及 MVC 资源服务安全 |

两者依赖基础 common 和 security-core，互不依赖。common-api、security-core 保持独立。
以下记录跨模块共享的行为契约；Java 包名和配置属性保持兼容，业务 Advice 仅处理业务专有异常。

## 异常协议

`ApiErrors` 定义通用错误码，`ApiExceptionHandler` 是 Servlet Advice 基类。
保留 HTTP 状态和 Allow 等框架响应头，统一返回 code/message/data/traceId/timestamp。
业务模块继承基类并添加业务异常处理，不重复定义兜底处理器。

| HTTP | code |
|---|---|
| 400 | VALIDATION_ERROR |
| 401 | UNAUTHORIZED |
| 403 | FORBIDDEN |
| 404 | NOT_FOUND |
| 405 | METHOD_NOT_ALLOWED |
| 409 | CONFLICT |
| 415 | UNSUPPORTED_MEDIA_TYPE |
| 429 | TOO_MANY_REQUESTS |
| 503 | SERVICE_UNAVAILABLE |
| 其他 5xx | INTERNAL_ERROR |

其他 4xx 使用 REQUEST_ERROR。已有业务专有错误码（如 USER_409_001）保留。
Manager 原通用 AUTH_400_001 / REQUEST_400_001 改为 VALIDATION_ERROR，SYS_500_001 改为 INTERNAL_ERROR。
客户端若依赖这些旧通用码，需要同步更新；API 字段不变。

ServletApiSecurityHandler 和 ReactiveApiSecurityHandler 由资源服务/网关安全链显式接入，
保留 Bearer 的 WWW-Authenticate / invalid_token / insufficient_scope 语义。
CSRF 403 和浏览器 WebSession 续期失败不伪造 Bearer challenge。
标准 OAuth2/OIDC 端点继续使用框架协议，不套业务响应。
网关 /api/** 的未处理异常由 ReactiveApiExceptionHandler 处理；静态页面不受影响。
SSE 等响应提交后只记录错误，不尝试追加 JSON。

## 请求追踪

- Servlet：TraceIdFilter 在安全链之前执行，使用请求属性在异步/错误分派间保持 ID，并恢复原 MDC。
- WebFlux：TraceWebFilter 在安全链之前执行，将同一 X-Trace-Id 写入下游请求和响应；
  Reactor Context 通过 Micrometer accessor 和 Reactor 自动传播与 MDC 对接。
- 输入 ID 限制为 1～128 个 ASCII 字母、数字、点、下划线或连字符；缺失或非法时生成 32 位 UUID。
- ID 仅用于日志关联，不是可信身份。无上下文的 TraceIds.current() 返回新 ID，但不写入 MDC。
- ManagedExecutor、ManagedSchedulers 和 MVC Callable 在提交时捕获 MDC，执行结束（包括异常）后恢复工作线程上下文。
  普通手工创建的线程、外部库线程和任意自建线程池不会自动获得请求上下文。
- TraceAutoConfiguration / ReactiveTraceAutoConfiguration 按 Web 类型注册，可通过同类型 Bean 覆盖过滤器。

MVC 异步执行器使用 common.mvc.async-executor.*，默认 core-pool-size=8、max-pool-size=32、
queue-capacity=256、keep-alive-seconds=30、shutdown-timeout-seconds=20。
请求超时仍使用 spring.mvc.async.request-timeout。可提供名为 mvcAsyncExecutor 的 Executor Bean 覆盖默认执行器。

## 执行器与后台任务

ManagedExecutor 是 Spring 管理的有界 ExecutorService；队列满或关闭后抛 RejectedExecutionException，
HTTP 入口映射为 503，调用方可明确感知失败。不会静默丢任务或在调用线程运行长驻后台循环。
关闭先等待 shutdown-timeout-seconds，超时则中断运行任务并取消队列中的 Future，
再等待相同时长；仍未退出的任务记录 WARN。Java 无法强制终止忽略中断的任务。

ManagedSchedulers 创建 Spring 调度器，传播 MDC，关闭时取消周期/延迟任务并有界等待。
ManagedBackgroundTask 将阻塞后台服务的启动和停止回调接入 Spring 生命周期。
使用方必须将这些对象注册为 Bean，不在业务代码自行创建静态线程池。

业务日志统一使用 SLF4J，异常传 Throwable，禁止 printStackTrace 和 System.out。

## 密码编码

设置 `common.password.enabled=true` 后注册唯一的 Spring `PasswordEncoder`，`common.password.strength` 为 BCrypt cost（4～31，默认 10）。
新密码以 `{bcrypt}` 前缀写入；校验兼容历史无前缀 BCrypt 摘要及 Spring 标准前缀（如 `{noop}`）。
账户密码写入、登录校验和 OAuth2 客户端密钥共用同一个 Bean；应用提供自己的 `PasswordEncoder` 时自动退让。

## 测试

根目录执行：

```powershell
mvn test '-Dskip.frontend=true' '-Dmaven.antrun.skip=true'
mvn -pl baosys-manager-web -am verify -Pintegration-tests '-Dskip.frontend=true' '-Dmaven.antrun.skip=true'
mvn -pl baosys-manager/baosys-manager-service -am test-compile failsafe:integration-test failsafe:verify -Pintegration-tests '-Dskip.frontend=true' '-Dmaven.antrun.skip=true'
```

第一条覆盖公共组件和资源服务单元测试；第二条覆盖 Manager 网关集成测试。
第三条通过 Testcontainers PostgreSQL 覆盖 Manager 数据库、认证和会话生命周期，需要 Docker。
本机 HTTP 测试需要允许回环端口。


公共 SPA、Logback 片段、运维配置和 StorageJson 的接入规则见 [公共基础设施](../docs/shared-infrastructure.md)。
