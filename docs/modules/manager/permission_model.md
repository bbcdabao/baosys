# Manager 权限模型

**简体中文** | [English](permission_model.en.md)

账户用户名全局唯一，一个账户可以拥有多个系统入口及接口权限。`user_info.user_permissions` 是持久化授权来源；当前没有 `system_type` 列，也没有角色或租户模型。

## 1. 系统目录

根 [baosys-systems.yml](../../../baosys-systems.yml) 当前定义：

| 系统 | 名称 | 接口权限 |
|---|---|---|
| MGR | 后台管理 | user:list、user:add、user:update、user:delete、user:session:list、user:session:kick，均带 `MGR:` 前缀 |
| CAT | 拨测平台 | 空列表，仅提供系统入口 |

`SystemCatalog` 在启动时校验：目录非空、系统编码非空且不重复、必须包含 MGR、接口编码非空且在所有系统中全局唯一、系统及接口名称均含非空的 `zh-CN` 和 `en-US`。展示名称可以重复。

系统入口直接使用 `MGR`、`CAT` 等编码，无需放入接口权限列表。接口所属系统由目录分组决定，代码不会根据权限字符串前缀校验归属。

命名约定为纯大写字母系统缩写和 `<系统>:<资源>:<操作>` 接口编码。当前不运行时校验编码格式、长度或前缀；`PermissionSet.systems` 仅按 `[A-Z]+` 提取入口。目录维护应遵循该约定，避免与前端分类及逗号分隔存储冲突。

## 2. 写入和读取

创建/更新用户提交完整 `permissions` 集合，例如：

```json
["MGR", "CAT", "MGR:user:list"]
```

请求字段不可为 null，但空集合合法，表示没有子系统授权。仅有入口权限不会自动获得接口权限。服务端通过 `SystemCatalog.validateGrants` 拒绝未知编码，以及缺少所属系统入口的接口权限。

集合去除 null、去重和排序后，由 `PermissionPersistenceConverter` 以逗号连接写入 TEXT 字段，例如：

```text
CAT,MGR,MGR:user:list
```

当前代码没有 1536 字符的权限总长限制、JSON/scope 预算或 3800 字符的 JWT 上限检查；不能把这些数值当作已实现的保护。

API 的账户和当前用户响应只提供 `permissions`，不额外返回 `systemTypes`。目录 DTO 的 `systemType` 仍存在，用户列表查询参数 `systemType` 也保留，含义为“拥有该入口权限”，通过 SQL 精确集合成员匹配。

读取行为有区别：

- `/users/me` 和令牌签发使用当前目录过滤后的有效权限；缺少系统入口时，该系统接口权限一并过滤。
- 用户列表、创建/更新响应、会话列表返回持久化权限的排序结果，可能包含目录中已经删除的历史编码。
- 目录变化不会自动改写账户数据，也不会给现有账户追加新权限；用户编辑时可清理失效授权。

## 3. 网关与资源服务

`JwtPermissionConverter` 仅把 JWT 的 `permissions` 原样映射成无前缀 authority，不再合并 `scope` 或 `SCOPE_` 权限。内部共享登录和外部 OAuth2 业务 Access Token 均使用这一契约。

默认网关配置：

```yaml
spring:
  security:
    gateway:
      access-rules:
        - path: /api/baosys-manager/v1/users/me
        - path: /api/baosys-manager/v1/systems
        - path: /api/baosys-manager/**
          permission: MGR
```

规则按顺序首次匹配，省略 permission 表示仅要求认证；宽泛规则应放在共享接口之后。`permit-all-paths` 的公开路径先匹配，其余未配置 `/api/**` 默认拒绝。当前没有 `skip-system-permission-paths` 配置。

资源服务再次校验 JWT，并由方法上的 `@PreAuthorize` 检查具体 API 权限。例如用户分页需要 `MGR:user:list`，踢下线需要 `MGR:user:session:kick`。资源服务不统一校验 MGR 入口；未声明方法权限的非公开端点仅要求登录。

因此非 MGR 用户可通过网关访问 `/users/me` 与 `/systems`，但访问管理 API 需要 MGR 入口和对应接口权限。前端按权限隐藏入口，最终授权仍由后端决定。

## 4. 变更、审计与撤销

创建、删除及权限/启停变化写入 `permission_change_audit`，与账户修改同事务。单独修改用户名或密码会撤销授权，但不产生权限变更审计。

账户用户名、密码、权限、启停变化或删除时，数据库触发器撤销其授权和认证中心 Session；应用服务也对权限、启停或密码变化显式删除登录记录。已签发 JWT 在离线验签服务上仍可能有效至到期；依赖登录记录的身份查询立即失效。

默认管理员初始化仅在 `DEFAULT_ADMIN_ENABLED=true` 时启用。新建账户获得 MGR 入口及目录中全部 MGR 接口权限；若同名账户已存在则保留原账户，不自动提升权限。初始化密码至少 12 字符。

实现依据：

- [SystemCatalog](../../../baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/config/SystemCatalog.java)
- [PermissionSet](../../../baosys-common-security-core/src/main/java/baosys/common/security/resource/PermissionSet.java)
- [JwtPermissionConverter](../../../baosys-common-security-core/src/main/java/baosys/common/security/resource/JwtPermissionConverter.java)
- [UserAppServiceImpl](../../../baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/services/impl/UserAppServiceImpl.java)
