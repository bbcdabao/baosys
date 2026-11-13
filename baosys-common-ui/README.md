# @baosys/common-ui

供 Manager 及后续应用复用的 TypeScript 基础设施：

- `./api`：统一 `ApiResponse<T>`。
- `./authCoordinator`：账号版本、Web Locks、跨标签页通知、认证操作排队。
- `./request`：`createRequests({ csrfPath, authPrefix?, recoverSession })`、`RequestError`。

两个应用通过 `file:../baosys-common-ui` 和 `install-links=true` 安装；Axios 为 peer dependency。
修改本包后在Manager UI 目录运行 `npm ci`，再运行 `npm run build` 与 `npm run test:browser`。
应用保留自己的登录恢复回调、事件总线和 UI 导航。

配置、构建与兼容性说明见 [公共基础设施](../docs/shared-infrastructure.md) / [English](../docs/shared-infrastructure.en.md)。
