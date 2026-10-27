# 新增子系统接入指南

内部子系统统一使用 [同域共享 Manager 登录](deployment/shared_login.md)。接入时升级公共网关认证模块、登记允许返回的 Origin，并添加前端 401 整页跳转；无需各自建立 OAuth2 网关会话。

## 目录配置

工程根目录 `baosys-systems.yml` 是子系统及可分配权限的统一目录，manager 前端不再维护对应的枚举、名称或权限数组。

在 `baosys.systems` 列表追加：

```yaml
    - system-type: LAB
      name: { zh-CN: 实验系统, en-US: Laboratory }
      permissions:
        - code: LAB:experiment:run
          name: { zh-CN: 执行实验, en-US: Run experiment }
```

- `system-type`：约定使用 3～8 个大写字母，使用易识别且不重复的缩写；全局唯一。
- `name`：必须提供非空 `zh-CN` 和 `en-US` 名称。
- `permissions`：该系统可分配的接口权限，允许空列表。权限编码格式为 `<系统缩写>:<资源>:<操作>`；资源可含多级冒号（如 `MGR:user:session:list`），各段小写字母开头，后续允许小写字母、数字、下划线和连字符。约定前缀与所属 system-type 一致，不允许通配符。
- 系统和权限选项按配置顺序显示。约定接口前缀与所属系统一致；系统入口直接使用 system-type 缩写。
- `MGR` 必须保留；内置管理接口仍使用 `MGR:user:list` 等权限编码，修改这些编码需要同步修改对应接口和按钮判断。
- 不合法的目录在启动时失败；没有热更新，修改后重启 manager。

## 构建与部署

Maven 构建 manager-service 时，把根目录文件作为资源打入 Jar。新增目录项后重新构建、部署 manager 即可，无需修改 manager Java 枚举、前端常量或数据库名单。

也可指定外部文件，免于重新打包：

```powershell
$env:BAOSYS_SYSTEMS_CONFIG = 'file:D:/baosys/config/baosys-systems.yml'
java -jar out/baosys-manager.jar
```

Linux/容器可设置 `BAOSYS_SYSTEMS_CONFIG=file:/config/baosys-systems.yml`，并挂载该文件。外部文件提供完整目录，替代内置文件；显式指定文件却不存在时启动失败。直接使用 IDE 启动时，先执行 Maven 资源处理，或通过此变量指定根目录文件。

目录接口：服务直连 `GET /baosys-manager/v1/systems`；通过网关 `GET /api/baosys-manager/v1/systems`（默认路径）。要求有效的访问令牌（已登录），返回系统标识、双语名称和权限列表。manager UI 在恢复登录时读取，语言切换无需重新请求；配置更新后刷新页面。

## 子系统代码

1. 新增后端服务及自己的 API，按现有公共资源服务器模块接入 JWT；使用 spring.security.oauth2.resourceserver.jwt 标准配置连接 Manager JWKS。资源服务对非公开路径要求有效 JWT，具体 API 权限通过方法级鉴权声明。
2. 在业务接口声明权限，例如 `@PreAuthorize("hasAuthority('LAB:experiment:run')")`。配置目录只声明可分配权限，不会自动保护接口。
3. 新增网关，在 spring.security.gateway.access-rules 中将业务路径的 permission 配置为 LAB，网关自动匹配 JWT 中的 LAB 入口权限；跨子系统共享接口也配置到 access-rules，省略 permission（或设为空字符串/空白字符串），仍需登录并接受后端 API 权限检查。规则按顺序首次匹配，共享路径须放在子系统通配规则之前。同时配置认证接口路由、业务路由及前端资源。浏览器先通过同源 GET /csrf 获取 Spring Token；登录、刷新、退出也需要 CSRF。公开路径不等于免 CSRF，已删除旧 CSRF 开关和 Cookie 名称配置。
4. 新增前端，登录仅传用户名、密码和终端描述，系统入口检查 LAB，功能入口按接口权限判断。
5. 在 Manager 创建或编辑账户，勾选 LAB 及接口权限；同一账户可同时勾选其他系统。

若使用根 Maven 聚合构建，仍需登记新增 Maven 模块及其打包方式；目录配置不生成代码、不自动创建网关路由。

## 账号和变更语义

一个账号可拥有多个子系统，用户名全局唯一。V1 直接创建当前权限模型，仅支持空库初始化；已有数据库的数据导入需单独规划，详见 [部署指南](deployment/oauth2_upgrade.md)。

删除目录中的系统或权限后，不再将对应授权写入新 Token；数据库历史授权保留以便管理员清理。已有 Token 有效至过期。新增权限不会自动授予已有账户。

user_permissions 使用 TEXT 存储排序后的逗号分隔编码；账户保存时限制编码长度和 Token 大小预算。系统目录定义不自动创建服务、网关路由或前端 URL。

权限命名是开发约定，不对编码格式、单个编码长度或系统前缀做运行时校验。权限提取仍按纯大写字母识别系统入口；目录唯一性、已登记权限与入口依赖、Token 大小预算及实际鉴权继续保留。
