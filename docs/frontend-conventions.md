# 前端公共开发规范

适用于 `baosys-manager-ui` 及后续接入的平台应用。以 Manager 的工程组织为基准，统一新增和修改代码的约定；图表、实时订阅及认证角色差异按本文保留。公共规则在此维护，各项目 README 记录部署入口和业务特性。

## 目录与职责

| 目录 | 职责与示例 |
| --- | --- |
| `src/api` | 按业务封装接口，如 `user.ts`、`task.ts`；不弹窗、不控制路由 |
| `src/constants` | API 前缀等稳定常量；不存放部署凭据 |
| `src/types` | 按业务定义请求、响应及公共分页类型，保持后端 JSON 字段一致 |
| `src/views/<业务>` | 路由页面，如 `user/UserManagementView.vue`、`task/TaskManagementView.vue` |
| `src/layouts` | 页头、侧栏、菜单、页面容器，如 `AdminLayout.vue` |
| `src/components/<业务>` | 可复用展示或交互组件；公共组件归入 `common` |
| `src/composables` | Vue 响应式复用逻辑，使用 `useXxx.ts` 命名 |
| `src/store` | Pinia 跨组件共享状态；页面临时表单状态优先留在页面 |
| `src/router` | 路由、页面权限与导航守卫 |
| `src/i18n` | 国际化初始化及 `locales/zh-CN.ts`、`locales/en-US.ts` |
| `src/utils` | 职责单一的工具，如 `loginRedirect.ts`；不恢复杂项工具大文件 |
| `baosys-common-ui/src/styles/main.css` | 唯一的自定义全局样式入口，统一基础排版与布局尺寸 |
| `tests` | Playwright 交互、认证和回归测试 |

新增业务接口遵循“页面/Store → API 模块 → 请求层”的调用关系。API 文件按业务拆分，不将所有接口堆入 `api/index.ts`；如保留索引文件，仅做导出。仅在确有使用者时建立目录和抽象。

## 命名与类型

- Vue 页面为 PascalCase + `View.vue`，布局为 `XxxLayout.vue`；组件文件和引用标签使用 PascalCase，如 `LanguageSelect`、`ResultTrendChart`。
- 业务 TypeScript 文件使用 camelCase，如 `loginSession.ts`、`systemConfig.ts`；新文件不使用含义模糊的 `-sub` 后缀。
- API 函数按动作命名：`listTasks`、`createTask`、`deleteTask`、`assignTaskRobotGroups`。参数和返回值对应真实契约，无返回数据使用 `Promise<void>`。
- 保持 TypeScript 严格模式；优先明确类型或 `unknown`，不要用 `any` 绕过接口约束。类型导入使用 `import type`，业务引用使用 `@/` 别名。
- 通用入口优先具名导出。不要仅为了命名一致而改变已有路由名称、URL、响应字段或公共行为；迁移时同步更新引用，并保留必要的旧入口兼容。
- 新增 Store 优先 Setup Store。现有 Options Store 可保留，不为写法一致重写实时状态逻辑。

## 路由、菜单与权限

Manager SPA使用 Hash 模式。应用基址不含 `#` 且以 `/` 结尾；业务路径放在 Hash 中：

```text
/baosys-manager/#/login?redirect_url=<完整返回地址的编码值>
/baosys-manager/#/sign-out
/baosys-manager/#/authorized-systems
```

前端使用 `URLSearchParams` 编码嵌套返回地址，再写入 Hash；不要把 SPA 参数放在 `#` 前。保留返回地址的查询和片段，不能用 `split('?')[1]` 截断包含查询的嵌套地址。OAuth2 协议端点和回调仍使用协议规定的地址，不改成 Hash。

| 路由元信息 | 含义 |
| --- | --- |
| `public` | 公开页，跳过认证恢复 |
| `titleKey` | 页面标题对应的翻译键 |
| `menuTitleKey` | 可选的上级菜单/面包屑翻译键，Manager 使用 |
| `requiredPermission` | 页面访问所需的权限编码 |

业务页同时要求系统入口权限（Manager 为 `MGR`）和页面权限。操作按钮另检查对应操作权限。新菜单和守卫共用路由权限定义，避免新增路径到权限的平行映射。前端权限只控制展示和导航，接口授权由服务端独立执行。

Manager 无管理权限时展示已授权系统页；该页要求登录，但不要求 `MGR`。不要将权限不足当作登录失效反复重定向。

Manager 保留旧 History 地址到 Hash 的转换，以及 Web 层 SPA 回退，支持旧书签。组件文件重命名不代表 URL 也要变更。

## 认证 Store 与角色边界

公共接口为 `currentUser`、`isAuthenticated`、`restoreSession()`、`clearSession()`。用户快照不落盘；并发恢复共用请求，清理会话后旧响应不得写回身份。需要重新检查身份时，先清理再恢复。

Manager 承担共享登录及退出流程，加载当前用户和系统目录，保留 `logout()`，使用认证请求及协调器完成刷新。

每次认证请求使用相应网关的 CSRF 契约，遵守 `enabled: false` 的关闭模式。网络或服务错误不等同于退出。跨端口可共享符合域规则的 Cookie，但不能假定共享 localStorage、BroadcastChannel 或 Web Locks；回到页面时保留身份复核。

## 错误提示

- 请求层解析业务错误、保留调用者需要的状态并抛出，不直接弹 UI 消息。请求错误须保留 HTTP `status`。
- 页面加载、用户操作及路由守卫负责提示。一次请求失败只由一个明确的 UI 边界处理，避免请求层与页面重复弹窗。
- Manager 现有页面 `try/catch` 可以保留，错误提示由页面边界负责。
- 页面初始化、异步事件及 watcher 必须处理拒绝；只有真正成功后才显示成功提示或执行后续导航。
- 后台身份检查、SSE 重连不持续弹窗；认证失效交给既有登录跳转。批量操作可逐项报告失败，但不能用成功提示覆盖失败结果。
- 前端新增提示应走翻译键；后端业务错误消息按当前接口契约展示。不要把正常取消当作异常提示。

## 国际化

入口为 `src/i18n/index.ts`，具名导出 `i18n`。使用 `zh-CN`、`en-US` 和 `baosys-locale`。翻译键按 `common`、`app`、`menu` 和业务域分组，例如 `common.confirm`、`task.name`；新增和删除键需同步两种语言及调用处。

公共语言偏好优先于浏览器语言。Vue I18n、Arco ConfigProvider 和 HTML `lang` 同步更新。

路由标题也使用翻译键；不要保存翻译后的字符串作为持久化标识。Canvas 图表标题、轴标签等应响应语言切换，避免重建业务订阅。

## 全局样式、主题与实时组件

- 基础布局和排版放在 `@baosys/common-ui/main.css` 及其导入的公共样式中；应用不再维护自己的 `main.css`。业务样式放在组件的 `scoped` 样式内。避免全局覆盖所有 `.arco-select`、`.arco-table` 等组件。
- 直接使用 Arco 的 `--color-*`、`--primary-*`、`--success-*`、`--danger-*` 等变量，不引入旧式 `--element-*` / `--cardbody-*` 别名或固定明暗色。
- 主题由 `useTheme()` 管理，使用 `baosys-theme` 和 `body[arco-theme='dark']`。用户设置优先，否则跟随初始化时的系统偏好。
- Canvas 需要实际颜色值，应读取当前主题。成功、失败、主线等颜色保持业务语义。
- 图表布局、统计计算、刷新周期、事件类型属于业务能力，不能为了统一外观而随意更改。主题切换更新颜色，不清空已有数据、不重新建立订阅。
- 卸载组件时关闭订阅、清理计时器及 ResizeObserver、销毁图表实例；移动端允许表格内部横向滚动，避免整页溢出。

## 格式化与验证

前端固定使用 Prettier `3.6.2`。`.prettierrc.json` 和 `.prettierignore` 保持一致；修改格式规则时同步更新使用方。当前配置为两空格、单引号、无分号、无尾逗号、100 列、箭头参数括号、LF 换行及 Vue 多属性换行。

依赖、锁文件、自动生成的组件声明和构建/测试产物不做手工格式化。保留许可证头；格式化不代替类型检查或业务测试。

在任一前端目录执行：

```sh
npm ci
npm run format
npm run format:check
npm run build
npm run test:browser
```

`format` 会修改文件；`format:check` 只检查排版；`build` 先执行 `vue-tsc --noEmit` 再构建。没有单独的 ESLint 检查，不要将格式检查称为 lint 或功能验证。

从仓库根目录运行 Manager 的检查：

```sh
npm --prefix baosys-manager-ui run format:check
npm --prefix baosys-manager-ui run build
npm --prefix baosys-manager-ui run test:browser
```

Node 版本遵循 `package.json` 的 engines。浏览器测试默认使用 Edge，Manager 使用 5178 端口。其他浏览器配置见 [启动与部署](development.md)。

涉及认证、权限、Hash 跳转、语言、主题或公共布局时，应运行 Manager 前端回归。保留对真实交互和请求载荷的检查，图表改动补充主题、实时更新及离页清理验证。浏览器测试使用模拟 API，不替代服务端集成测试和实际部署验收。

## 已有实现差异与维护原则

以下是目前的实现差异，不应被文档误写为已经完全统一：

- Manager 的 `useSystemCatalog.ts` 仍在 composable 内定义目录类型并请求接口；后续修改该模块时可按 API/types 分层拆分，本轮规范化不改变其行为。
- Manager 通过公共 `RoutedAdminLayout` 从路由 `meta.menu` 与 `requiredPermission` 生成菜单；应用提供权限判断，路由守卫与后端鉴权仍独立执行。
- Manager 尚未对中英文键结构使用自动校验，修改语言文件时需同时核对两种语言。
- 现存业务类型名称、Store 文件名及历史路由可保留；不要为了规范一次性改动已对外使用的契约。

项目入口与业务细节见 [Manager README](../baosys-manager-ui/README.md)。接口、安全与跨模块规则继续遵循 [开发约定](conventions.md)。

## 公共 UI 接入

布局、菜单、面包屑使用 `@baosys/common-ui/layout`；页面容器、卡片、筛选栏和详情抽屉使用 `@baosys/common-ui/components`。语言初始化与切换使用 `@baosys/common-ui/i18n`，主题与全屏偏好使用 `@baosys/common-ui/preferences`。已授权系统展示和入口 URL 校验复用 `@baosys/common-ui/pages`。业务页面负责数据、操作和权限，不覆盖公共布局样式。

公共尺寸集中配置于 `baosys-common-ui/src/styles/main.css`，业务表格样式留在 Manager 的 `ManagementTable.vue`。接口及使用示例见 [公共 UI 文档](../baosys-common-ui/README.md)。修改公共包后先在 Manager UI 运行 `npm ci`，再执行构建与浏览器回归，确保使用最新本地依赖。
