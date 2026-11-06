# Manager permission model

[简体中文](permission_model.md) | **English**

Usernames are globally unique. One account can have access to multiple systems and their APIs. `user_info.user_permissions` is the persistent source of grants. There is currently no `system_type` column, role model, or tenant model.

## 1. System catalog

The root [baosys-systems.yml](../../../baosys-systems.yml) currently defines:

| System | Name | API permissions |
|---|---|---|
| MGR | Management | user:list, user:add, user:update, user:delete, user:session:list, user:session:kick, all prefixed with `MGR:` |
| CAT | Probing platform | Empty list; system access only |

At startup, `SystemCatalog` validates that the catalog is nonempty; system codes are nonempty and unique; MGR is present; API codes are nonempty and globally unique across systems; and system/API names contain nonempty `zh-CN` and `en-US` values. Display names may repeat.

System access uses codes such as `MGR` and `CAT` directly; do not add them to the API permission list. Catalog grouping determines which system owns an API permission. Code does not validate ownership from the permission string's prefix.

Naming conventions use uppercase-letter system abbreviations and `<system>:<resource>:<operation>` API codes. Runtime code does not currently validate code format, length, or prefix; `PermissionSet.systems` extracts system entries only with `[A-Z]+`. Maintain this convention to avoid conflicts with frontend classification and comma-separated storage.

## 2. Writes and reads

Creating or updating an account submits its complete `permissions` set, for example:

```json
["MGR", "CAT", "MGR:user:list"]
```

The field cannot be null, but an empty set is valid and means no subsystem grants. System access alone does not grant API permissions. `SystemCatalog.validateGrants` rejects unknown codes and API permissions without the owning system's access permission.

After removing nulls, deduplicating, and sorting, `PermissionPersistenceConverter` joins the set with commas into a TEXT column, for example:

```text
CAT,MGR,MGR:user:list
```

There is no current 1536-character permission length limit, JSON/scope budget, or 3800-character JWT limit check. Do not treat these values as implemented protections.

Account and current-user API responses expose only `permissions`, without an additional `systemTypes`. Catalog DTOs retain `systemType`; user-list queries also retain `systemType`, meaning “has this system access permission,” implemented as exact SQL set membership.

Read behavior differs by context:

- `/users/me` and token issuance use effective permissions filtered through the current catalog. Missing system access also removes that system's API permissions.
- User lists, create/update responses, and session lists return sorted persisted permissions, which may include historical codes removed from the catalog.
- Catalog changes neither rewrite account data nor automatically grant new permissions. Editing an account can remove obsolete grants.

## 3. Gateway and resource services

`JwtPermissionConverter` maps JWT `permissions` directly to authorities without adding prefixes or merging `scope`/`SCOPE_` permissions. Internal shared login and external OAuth2 business Access Tokens use the same contract.

Default gateway configuration:

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

Rules use the first match in order. Omitting permission means authentication only; broad rules belong after shared endpoints. Public `permit-all-paths` are matched first, and other unconfigured `/api/**` paths are denied. There is no `skip-system-permission-paths` setting.

Resource services verify JWTs again and check API permissions with method-level `@PreAuthorize`. User pagination requires `MGR:user:list`, for example, and session revocation requires `MGR:user:session:kick`. Resource services do not universally check MGR access; nonpublic endpoints without method permissions require authentication only.

Thus, non-MGR users can access `/users/me` and `/systems` through the gateway, while management APIs require MGR access and the relevant API permission. Frontend visibility follows permissions; the backend remains the final authority.

## 4. Changes, auditing, and revocation

Account creation, deletion, and permission/enabled-state changes write `permission_change_audit` in the same transaction as the account change. Changing only a username or password revokes authorization but does not create a permission-change audit record.

When an account's username, password, permissions, or enabled state changes, or the account is deleted, database triggers revoke its authorizations and authorization-server sessions. Application services also explicitly delete login records for permission, enabled-state, or password changes. Already issued JWTs may remain valid until expiry on services using offline verification; identity lookups relying on login records become invalid immediately.

Default administrator initialization runs only with `DEFAULT_ADMIN_ENABLED=true`. A new account receives MGR access and every MGR API permission in the catalog. An existing account with the same name is preserved without automatic privilege elevation. The initialization password must be at least 12 characters.

Implementation references:

- [SystemCatalog](../../../baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/config/SystemCatalog.java)
- [PermissionSet](../../../baosys-common-security-core/src/main/java/baosys/common/security/resource/PermissionSet.java)
- [JwtPermissionConverter](../../../baosys-common-security-core/src/main/java/baosys/common/security/resource/JwtPermissionConverter.java)
- [UserAppServiceImpl](../../../baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/services/impl/UserAppServiceImpl.java)
