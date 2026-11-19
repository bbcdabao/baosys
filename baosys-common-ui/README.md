# @baosys/common-ui

供 Manager 及后续应用复用的 TypeScript 基础设施及 Vue 布局组件：

- `./api`：统一 `ApiResponse<T>`。
- `./authCoordinator`：账号版本、Web Locks、跨标签页通知、认证操作排队。
- `./request`：`createRequests({ csrfPath, authPrefix?, recoverSession })`、`RequestError`。
- `./layout`：`AppSpaLayout`、`Sidebar`、`Header`、`Breadcrumb` 及其配置类型。
- `./pages`：`AuthorizedSystems` 当前用户已授权子系统及权限页面。
- `./preferences`：公共主题、全屏偏好及安全存储工具。
- `./i18n`：语言初始化、切换及公共界面文案。
- `./components`：页面容器、标题、卡片、筛选栏、详情抽屉及状态组件。
- `./main.css`：全局样式与布局尺寸变量入口。

消费应用通过 `file:../baosys-common-ui` 和 `install-links=true` 安装；Axios 为 peer dependency。
修改本包后在 Manager UI 目录运行 `npm ci`，再运行 `npm run build` 与 `npm run test:browser`。
应用保留自己的登录恢复回调、事件总线和 UI 导航。

## 单页应用布局

```vue
<template>
  <AppSpaLayout>
    <template #sidebar>
      <Sidebar
        app-name="示例应用"
        app-short-name="示"
        :items="menus"
        :selected-keys="[route.path]"
        @navigate="router.push"
      />
    </template>
    <template #header>
      <!-- Header 属性和事件接入示例见 Manager 的 AdminLayout.vue -->
      <Header
        v-bind="headerProps"
        @logout="logout"
      >
        <Breadcrumb :items="breadcrumbs" />
      </Header>
    </template>
    <router-view />
  </AppSpaLayout>
</template>
```

组件从 `@baosys/common-ui/layout` 显式导入，使用 Vue 3 与 Arco Design Vue；
组件自行导入 Arco 组件、图标和对应 CSS，不要求应用配置组件自动导入插件。
依赖版本见 `peerDependencies`，源码 `.vue` 由消费项目的 Vue 构建插件编译。

- `AppSpaLayout`：`sidebar`、`header` 和默认内容插槽；不绑定 Vue Router。
- `Sidebar`：`items: SidebarItem[]` 支持多层 `children`，每项包含唯一 `key`、
  已翻译的 `label` 和可选 Vue `icon` 组件；项目先过滤权限。通过 `selectedKeys`
  指定选中项，`defaultOpenKeys` 指定初始展开分组，`navigate(key)` 交给项目导航。
  支持 `v-model:collapsed`、`isDark` 和 `brand` 插槽（接收 `collapsed`）。
  `expandLabel` / `collapseLabel` 提供展开和收起按钮文案。按钮固定在左上角，
  收起时品牌区域隐藏；菜单独立滚动，底部不再显示 Arco 默认折叠按钮。
- `Header`：传入 `locale`、`localeOptions`、`isDark`、`userName`、`loggingOut`、
  `userActionLabel`（用户图标的提示和无障碍标签）、
  `labels`（`theme`、`fullscreen`、`exitFullscreen`、`logout`）；响应
  `update:locale`、`toggleTheme`、`logout`、`fullscreenError` 事件。
  默认插槽放面包屑，`actions` 插槽可添加工具。全屏状态及事件监听由组件管理。
  工具栏默认允许换行，可用 `wrapActions=false` 禁止换行，`languageLabel` 设置语言选择器的无障碍标签。
  `userClick` 事件用于进入当前用户的已授权子系统页面；窄屏只隐藏用户名，保留图标入口。
- `Breadcrumb`：接收 `items: BreadcrumbItem[]`（唯一 `key`、已翻译的 `label`），
  按传入顺序显示任意层级；当前只展示位置，不自动推断路由或生成链接。

使用底层 AdminLayout 时，项目负责菜单等数据；推荐使用 RoutedAdminLayout，由公共包根据路由生成菜单、选中项、面包屑，并管理语言与主题。项目提供业务路由、权限判断、用户数据与退出回调。
菜单隐藏不替代路由和后端鉴权。消费应用通过 `createBreadcrumbs(subsystem, pages)`
统一生成 `baosys / 子系统名称 / 页面路由层级…`；页面名称来自匹配路由的 `titleKey`，
不将菜单分组当成路由祖先。根节点固定小写 `baosys`。
组件保留原布局的 CSS 类名和响应式尺寸，以兼容既有样式与页面测试。
`AppSpaLayout` 高度跟随浏览器可视工作区，右侧内容独立滚动；它同时加载公共滚动条样式，
通过 Arco 主题变量适配亮色、暗黑模式，覆盖内容、菜单和弹窗中的原生滚动条。
Chrome/Edge/WebKit 默认使用 8px 滚动轨道、两侧各 2px 边框的滑块（可通过 main.css 配置）；悬停仅改变颜色。
左右滚动容器都预留稳定的轨道空间，不随内容是否溢出改变可用宽度。
支持 WebKit 滚动条伪元素时将标准滚动条属性设为 auto，避免覆盖固定像素规则；
Firefox 使用标准属性回退，其原生滚动条宽度由浏览器决定，不承诺固定像素宽度。
布局通过 ResizeObserver 将 Header 实际高度同步给 Sidebar，确保窄屏换行后左右
滚动区域仍上下对齐。菜单仅内部纵向滚动，长标题省略并可悬停查看；展开、收起按钮
和各级菜单图标共用同一垂直中心线。

## 已授权子系统页面

从 `@baosys/common-ui/pages` 导入 `AuthorizedSystems`。传入 `systems`
（`value` 系统代码、`label` 名称、`homeUrl` 经过项目校验的入口地址）、
当前用户的 `permissions`、`permissionLabel(code)` 和 `labels`
（`title`、`empty`、`entryOnly`、`hint`）。
组件仅展示用户持有入口权限的系统，以及对应已授予的细分权限；权限去重，
未知权限名称回退由项目处理。数据加载、URL 校验和登录状态仍由消费项目负责。
Manager 的 `AuthorizedSystemsView.vue` 是接入示例，原路由及页面样式不变。
Manager 在 Header 的用户图标点击后进入本地 `/authorized-systems`。
该页面不放在左侧菜单中，登录用户无需业务页面权限即可查看自己的权限。
公共 `describeSystemCatalog` 提供目录展示转换，`systemHomeUrl` 校验系统入口地址。

## 同源偏好记忆

消费应用从 `@baosys/common-ui/preferences` 使用 `useTheme`、`applyTheme`；
公共 Header 内部使用 `useFullscreen`。统一 localStorage 键：

- `baosys-theme`：`light` / `dark`，沿用已有主题设置。无有效设置时跟随系统初始主题。
- `baosys-fullscreen`：`true` / `false`，记录全屏偏好。按钮及 Esc 引起的状态变更会保存。

同源页面共享存储，并通过 `storage` 事件同步；不同端口、域名或协议各自记忆。
刷新时恢复主题，不用初始的非全屏状态覆盖已保存的全屏偏好。
浏览器要求用户激活才能进入全屏，因此已保存的全屏偏好会在下一次用户点击时尝试恢复；
全屏按钮独立处理点击，避免重复切换。浏览器拒绝恢复时保留偏好，可通过按钮重试。
图标和 `aria-pressed` 始终反映实际全屏状态。退出页面不会主动清除偏好。
存储不可用时不影响当前页面操作。

配置、构建与兼容性说明见 [公共基础设施](../docs/shared-infrastructure.md) / [English](../docs/shared-infrastructure.en.md)。

## 公共后台布局入口

Manager 使用 layout 导出的 AdminLayout。应用仅传入菜单、面包屑、用户状态、国际化文案并处理导航和认证事件，默认插槽承载业务页面。brandIcon 支持系统图标，collapsed 支持双向绑定。Header、Sidebar 的组装及高度、响应式规则由公共库维护；应用不要通过 deep 样式覆盖公共布局，业务样式限定在内容区域。

## 子系统接入规范

子系统主要维护 `views/`、业务 API/状态、业务翻译和 `router/index.ts`。
布局文件仅连接用户状态、系统入口权限和退出回调，参考 Manager 的 `src/layouts/AdminLayout.vue`：

```vue
<RoutedAdminLayout
  system-permission="MGR"
  :can="can"
  :user-name="auth.currentUser?.displayName"
  :sign-out="goToManagerSignOut"
/>
```

从 `@baosys/common-ui/layout` 导入组件。公共布局提供语言、主题、全屏、用户入口、退出、
菜单与面包屑；`remountOnQuery` 为需要随 query 重新创建页面的旧页面提供兼容。
导航不携带旧 query。权限菜单仅影响展示，应用路由守卫和后端鉴权继续保留。

菜单与路由只配置一次：

```ts
meta: {
  titleKey: 'menu.userManagement',
  requiredPermission: 'MGR:user:list',
  menu: {
    icon: IconList,
    order: 20,
    // 可选分组；同组使用相同 key、titleKey、icon 和 order。
    group: { key: 'users', titleKey: 'menu.user', order: 10 }
  }
}
```

未设置 `meta.menu` 的路由不出现在菜单（如授权系统页、登录页、主题页）。
先过滤系统入口权限和页面权限，再生成菜单；空分组不会出现。面包屑按实际匹配路由生成。

## 统一样式入口

每个应用 `main.ts` 只导入公共自定义样式入口 `@baosys/common-ui/main.css`，不再维护子系统 `main.css`。
公共包负责基础排版、布局、卡片宽度约束和进度条主题；第三方组件库自己的 CSS 按需加载。
业务样式使用业务组件的 `scoped` 样式：Manager 的 `components/user/ManagementTable.vue`
维护紧凑表格、首列宽度及详情链接。单页特有样式继续留在对应页面。
统一配置文件为 **`src/styles/main.css`**，内部加载基础、工作区与滚动条样式。
组件保留结构样式，但统一尺寸引用公共变量，不在子系统布局内覆盖公共组件。

| 配置                     | CSS 变量                                                               |
| ------------------------ | ---------------------------------------------------------------------- |
| Header 最小高度          | `--baosys-header-height`                                               |
| Header 内边距            | `--baosys-header-padding-x` / `--baosys-header-padding-y`              |
| 侧栏展开 / 折叠宽度      | `--baosys-sidebar-width` / `--baosys-sidebar-collapsed-width`          |
| 工作区四边内边距         | `--baosys-workspace-padding-top/right/bottom/left`                     |
| 页面最大宽度             | `--baosys-workspace-max-width`（默认 `none`）                          |
| 卡片间距 / 内边距 / 圆角 | `--baosys-card-gap` / `--baosys-card-padding` / `--baosys-card-radius` |
| 分区间距                 | `--baosys-section-gap`                                                 |
| 详情抽屉宽度             | `--baosys-detail-width`                                                |
| 边框 / 滚动条宽度        | `--baosys-border-width` / `--baosys-scrollbar-size`                    |

Header 使用最小高度，窄屏必要时可增长，实际高度通过 ResizeObserver 同步给侧栏顶部。
侧栏实际宽度直接引用 CSS 变量，不再由固定的 Arco 数字属性控制。
响应式变量在同一 main.css 的媒体查询里调整；媒体查询断点本身不能使用 CSS 变量。
颜色沿用 Arco 亮暗主题变量。业务表格列宽、监控图布局等继续在业务页面维护。
修改公共包后需重新安装消费项目的本地依赖并构建；它不是已部署站点的远程动态配置。

## 工作区组件

从 `@baosys/common-ui/components` 导入：

- `PageContainer`：默认网格分区，`flow` 使用普通文档流；统一宽度与居中规则。
- `PageHeader`：`title`、`description` 和 `actions` 插槽。
- `PageCard`：公共卡片外观，透传 Arco 属性、事件与插槽。
- `FilterToolbar`：接收 `model`，内联筛选表单，窄屏自动换行；筛选字段与按钮由插槽提供。
- `DetailsDrawer`：`v-model:visible`、标题属性、默认内容插槽；默认无页脚，宽度来自公共变量。
- `PageState`：`loading` / `error` / 默认空态；传入 `message`，可用 `retryLabel` 和 `retry` 事件重试。

用户/登录管理页已使用页面容器、标题、卡片、筛选和详情抽屉。
通用状态组件按页面需求使用，不替换已有特殊业务状态。表格没有强制统一列宽或溢出策略。

## 语言

语言选择继续内嵌在公共 `Header.vue`，不额外导出 LanguageSelect。
`@baosys/common-ui/i18n` 提供 `createAppI18n`、`useLocale`、`localeOptions`、语言类型和存储键。
应用只传入中英文业务字典；公共 Header 文案由公共包提供，业务字典可按需覆盖同名文案。
`useLocale` 在组件 setup 中使用，联动 Vue I18n、Arco ConfigProvider、HTML lang 和本地存储。
初始化先读 `baosys-locale`，再执行可选旧偏好迁移，最后按浏览器语言选择。
本地存储不可用时仍可在当前页面切换。不同域名或端口不共享 localStorage。
