# BaoSys Manager

`baosys-manager` 按微服务契约与实现分离：

```text
baosys-manager
├── baosys-manager-api      # HTTP API 契约与公开 DTO
└── baosys-manager-service  # Spring Boot 可启动服务
    └── app / infra
```

依赖方向：

```text
baosys-manager-service
    → baosys-manager-api
    → baosys-common-api
```

- `api` 只包含 `AuthApi`、`UserApi` 和请求/响应 DTO，不包含 Feign、数据库或业务实现。
- `app` 负责 Controller 适配、事务与业务流程编排。
- `infra` 封装 MyBatis Plus、刷新令牌生成等技术细节，由 `app` 直接调用。

用户列表使用分页响应，Refresh Token 具有绝对过期时间。当前开发配置默认启用
管理员初始化，可通过 `DEFAULT_ADMIN_ENABLED`、`DEFAULT_ADMIN_USERNAME` 和至少
12 个字符的 `DEFAULT_ADMIN_PASSWORD` 覆盖；实际值以
`baosys-manager-service/src/main/resources/application.yml` 为准。

Access Token 和 Refresh Token 均通过 `HttpOnly` Cookie 返回，Refresh Token
不会出现在响应体或浏览器存储中。Cookie 持久时间可通过
`AUTH_ACCESS_TOKEN_COOKIE_MAX_AGE_SECONDS`（默认 7200）和
`AUTH_REFRESH_TOKEN_COOKIE_MAX_AGE_SECONDS`（默认 2592000）配置。配置为
`0` 时不输出 `Max-Age`，Cookie 在当前浏览器会话中有效；HTTP 协议中的
`Max-Age=0` 表示立即删除 Cookie。

启动服务前必须配置 `DB_URL`、`DB_USERNAME` 和 `DB_PASSWORD`。

验证与打包（在仓库根目录执行）：

```shell
mvn -pl baosys-manager/baosys-manager-service -am test
mvn -pl baosys-manager/baosys-manager-service -am -DskipTests package
```

集成测试通过 Testcontainers 使用真实 PostgreSQL，运行测试前需要启动 Docker。

如果当前目录是 `baosys-manager`，需要显式指定仓库根 POM，让
`baosys-common-api` 一起进入 Maven Reactor：

```shell
mvn -f ../pom.xml -pl baosys-manager/baosys-manager-service -am test
mvn -f ../pom.xml -pl baosys-manager/baosys-manager-service -am -DskipTests package
```

可启动 Jar 输出为：

```text
baosys-manager/baosys-manager-service/target/baosys-manager.jar
```
