# BaoSys Manager

内部子系统通过 [同域共享 Manager 登录](../docs/deployment/shared_login.md) 使用统一 HttpOnly Token Cookie，支持同主机不同端口及登录后返回原页面。账户通过统一 permissions 分配多个系统；外部 OAuth2 Authorization Server 能力保留。启动需提供 JWK 文件及认证中心配置；数据库初始化说明见 [升级指南](../docs/deployment/oauth2_upgrade.md)。

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
    ├── baosys-manager-api → baosys-common-api
    └── baosys-common → baosys-common-api
```

- `api` 包含 `UserApi`、`LoginSessionApi`、请求/响应 DTO 和公开枚举，不包含 Feign、数据库或业务实现。
- `app` 负责 Controller 适配、事务与业务流程编排。
- `infra` 封装 MyBatis Plus、刷新令牌生成等技术细节，由 `app` 直接调用。

用户列表同时返回账号启停状态 enabled 和在线状态 status。刷新要求会话存在、expires_at 晚于当前时间且账号已启用。事务中先锁用户再锁会话并复核旧摘要，每次刷新轮换摘要、更新 updated_at，但不延长登录时确定的 expires_at。空闲超过一天的会话仍由数据库任务清理。

默认管理员初始化关闭；首次使用时显式设置 DEFAULT_ADMIN_ENABLED=true 和至少 12 位的 DEFAULT_ADMIN_PASSWORD。初始化会记录授权审计。

Access Token 和 Refresh Token 均通过 `HttpOnly` Cookie 返回，Refresh Token
不会出现在响应体或浏览器存储中。Cookie 持久时间可通过
`AUTH_ACCESS_TOKEN_COOKIE_MAX_AGE_SECONDS`（默认 1800）和
`AUTH_REFRESH_TOKEN_COOKIE_MAX_AGE_SECONDS`（默认 2592000）配置。配置为
`0` 时不输出 `Max-Age`，Cookie 在当前浏览器会话中有效；HTTP 协议中的
`Max-Age=0` 表示立即删除 Cookie。

启动服务前确认 `DB_URL`、`DB_USERNAME` 和 `DB_PASSWORD` 与本地数据库一致；未设置时使用应用 YAML 的开发默认值。

验证与打包（在仓库根目录执行）：

```shell
mvn -pl baosys-manager/baosys-manager-service -am test
mvn -pl baosys-manager/baosys-manager-service -am -DskipTests package
```

常规 `mvn test` 不执行 `*IntegrationTest`，运行默认测试不需要启动 Docker。数据库集成测试需要 Docker，在仓库根目录执行：

```shell
mvn -Pintegration-tests -pl :baosys-manager-service -am verify "-Dskip.frontend=true"
```

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

账号创建、删除、权限和启停变化记录 permission_change_audit；权限、启停、密码或平台变化撤销刷新会话。已签发 JWT 在到期前仍可能有效，默认期限为 1800 秒。

数据库按首版初始化：V1__init_manager.sql 一次建立用户、会话、审计表和清理函数。默认 schema 为 manager（MANAGER_DB_SCHEMA），历史表为 flyway_schema_history_manager。

详细说明：

- [Manager 模块设计](../docs/modules/manager/manager_module_design.md)
- [认证与会话](../docs/modules/manager/auth_and_session.md)
- [权限模型](../docs/modules/manager/permission_model.md)
- [数据库设计](../docs/modules/manager/database_design.md)
- [Common 模块说明](../baosys-common/README.md)
- [本地开发指南](../docs/deployment/local_development.md)
