# Manager UI 路由

公共目录、命名、权限、错误处理、国际化、主题及验证规则见 [前端公共开发规范](../docs/frontend-conventions.md)。本文件补充项目特有约定。

前端使用 Vue Router Hash 模式，部署基址仍为 `/baosys-manager/`：

- 登录：`/baosys-manager/#/login`
- 退出：`/baosys-manager/#/sign-out`
- 用户管理：`/baosys-manager/#/users-management`
- 可访问系统：`/baosys-manager/#/authorized-systems`

路由查询参数位于 Hash 内，例如 `#/login?redirect_url=<编码后的完整返回地址>`。
`VITE_MANAGER_UI_BASE_URL` 仍应配置为不含 Hash、以 `/` 结尾的应用基址。

应用启动时会将旧 History 地址转换为 Hash 地址，并保留查询参数及锚点，
以兼容历史登录/退出地址及旧书签。服务端新生成的入口已统一使用 Hash 地址。
这些旧地址仍需要 Web 服务现有的 SPA 回退支持；新 Hash 地址只请求应用入口。
为了继续支持旧书签和历史外部链接，保留现有 Web 服务的 SPA 回退。

验证：`npm run format:check`、`npm run build`、`npm run test:browser`。
格式化：`npm run format`（会修改文件）。
