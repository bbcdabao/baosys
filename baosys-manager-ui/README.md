# baosys-manager-ui

Manager 提供账号密码登录表单，支持同域不同端口子系统的 redirect_url 回跳。Token 使用 HttpOnly Cookie，不存入前端存储。API 401 尝试统一刷新后转登录，403 提示权限不足。部署配置和子系统接入见 [同域共享登录](../docs/deployment/shared_login.md)。

BaoSys 管理平台前端，当前包含登录、用户管理、登录会话和系统配置页面骨架。

写请求（含登录、刷新、退出）先通过同源 `GET /csrf` 获取 Spring 标准 Token，再按返回的 `headerName` 提交；不再读取 CSRF Cookie 或缓存至 sessionStorage。Vite 同时代理 `/api` 和 `/csrf`。安全层错误按 HTTP 401/403 处理，业务错误仍读取 JSON message。

在 `baosys-manager-ui` 目录执行：

```shell
npm install
npm run dev
```

本地开发时，请先启动：

- `baosys-manager-service`：`9090`
- `baosys-manager-web`：`8080`

管理员初始化默认关闭。首次需要创建管理员时，在启动 Manager Service 的终端设置：

```text
DEFAULT_ADMIN_ENABLED=true
DEFAULT_ADMIN_USERNAME=admin
DEFAULT_ADMIN_PASSWORD=<至少 12 个字符>
```

浏览器访问 `http://localhost:5173`，Vite 将 `/api/**` 代理到 `http://localhost:8080`。初始化不会覆盖已存在的管理员。

类型检查与生产构建：

```shell
npm run build
```

生产资源输出到 `dist`，完整 Maven 构建会将其打包进 Manager Web Jar。

相关说明：[本地开发指南](../docs/deployment/local_development.md)、[环境变量](../docs/deployment/environment_variables.md#4-前端)、[权限模型](../docs/modules/manager/permission_model.md)。
