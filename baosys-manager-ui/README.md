# baosys-manager-ui

BaoSys 管理平台前端，当前包含登录、用户管理、登录会话和系统配置页面骨架。

```bash
npm install
npm run dev
```

本地开发时，请先启动：

- `baosys-manager`：`9090`
- `baosys-manager-web`：`8080`

当前开发配置默认启用管理员初始化，需要覆盖时为后端配置：

```text
DEFAULT_ADMIN_ENABLED=true
DEFAULT_ADMIN_USERNAME=admin
DEFAULT_ADMIN_PASSWORD=<至少 12 个字符>
```
