# 新增子系统接入指南

## 目录配置

工程根目录 `baosys-systems.yml` 是子系统及可分配权限的统一目录，manager 前端不再维护对应的枚举、名称或权限数组。

在 `baosys.systems` 列表追加：

```yaml
    - system-type: LAB
      name: { zh-CN: 实验系统, en-US: Laboratory }
      permissions:
        - code: experiment:run
          name: { zh-CN: 执行实验, en-US: Run experiment }
```

- `system-type`：1～32 个字符，大写字母开头，后续允许大写字母、数字和下划线；全局唯一。
- `name`：必须提供非空 `zh-CN` 和 `en-US` 名称。
- `permissions`：该系统可分配的接口权限，允许空列表。权限编码在本系统内唯一，1～128 个字符，以字母或数字开头，后续允许字母、数字及 `: _ . * -`；不允许逗号和空白。`*` 只是普通字符，不提供通配授权。
- 系统和权限选项按配置顺序显示。不同系统可以使用相同权限编码，由 `systemType` 隔离。
- `MANAGER` 必须保留；内置管理接口仍使用 `user:list` 等权限编码，修改这些编码需要同步修改对应接口和按钮判断。
- 不合法的目录在启动时失败；没有热更新，修改后重启 manager。

## 构建与部署

Maven 构建 manager-service 时，把根目录文件作为资源打入 Jar。新增目录项后重新构建、部署 manager 即可，无需修改 manager Java 枚举、前端常量或数据库名单。

也可指定外部文件，免于重新打包：

```powershell
$env:BAOSYS_SYSTEMS_CONFIG = 'file:D:/baosys/config/baosys-systems.yml'
java -jar out/baosys-manager.jar
```

Linux/容器可设置 `BAOSYS_SYSTEMS_CONFIG=file:/config/baosys-systems.yml`，并挂载该文件。外部文件提供完整目录，替代内置文件；显式指定文件却不存在时启动失败。直接使用 IDE 启动时，先执行 Maven 资源处理，或通过此变量指定根目录文件。

目录接口：服务直连 `GET /baosys-manager/v1/systems`；通过网关 `GET /api/baosys-manager/v1/systems`（默认路径）。要求有效的 MANAGER 令牌，返回系统标识、双语名称和权限列表。manager UI 在恢复登录时读取，语言切换无需重新请求；配置更新后刷新页面。

## 子系统代码

1. 新增后端服务及自己的 API，按现有公共资源服务器模块接入 JWT；配置本系统的 `systemType` 校验，使用 manager 的 JWKS 或约定的公钥。
2. 在业务接口声明权限，例如 `@PreAuthorize("hasAuthority('experiment:run')")`。配置目录只声明可分配权限，不会自动保护接口。
3. 新增网关，配置本系统 `systemType`、认证接口路由、业务路由及前端资源。保持现有 Cookie、CSRF 和放行配置方式。
4. 新增前端，登录请求传 `systemType: 'LAB'`，功能入口按权限判断。
5. 在 manager 创建属于 LAB 的账号，勾选权限，然后使用该账号登录 LAB。

若使用根 Maven 聚合构建，仍需登记新增 Maven 模块及其打包方式；目录配置不生成代码、不自动创建网关路由。

## 账号和变更语义

一个账号记录只属于一个子系统；同名账号可以在不同系统存在。同一系统可为账号分配多个接口权限，不引入角色、租户或单点登录。继续接受同主机下各子系统 Cookie 相互覆盖的行为。

V2 Flyway 迁移移除 V1 的固定系统类型名单，保留格式检查、账号数据及 `(user_name, system_type)` 唯一约束。已经执行过 V1 的环境正常升级，不要修改已执行的 V1 文件。

删除系统目录项后，该系统不能新建账号、登录或刷新令牌；已有账号数据不自动删除。删除权限目录项后，新签发/刷新的 JWT 只包含仍在目录内的已授权权限；数据库旧授权保留，重新编辑账号时需清理无效选项。已有 JWT 继续有效至过期，不增加即时撤销机制。新增权限也不会自动授予已有账号，包括已有管理员，需显式分配。

权限仍使用逗号分隔字段持久化，每个账号的编码总长度最多 1024 字符；保存前会校验长度。
