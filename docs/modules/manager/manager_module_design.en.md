# Manager module design

[简体中文](manager_module_design.md) | **English**

Manager provides account management, shared browser login, an OAuth2/OIDC authorization server, a permission catalog, session inspection and revocation, and account authorization auditing. The separate Manager Web application handles gateway routing and Vue static resources.

Related documents: [Authentication and sessions](auth_and_session.en.md), [Permission model](permission_model.en.md), [Database design](database_design.en.md), and [System architecture](../../architecture/system_architecture.en.md).

## 1. Modules and directories

```text
baosys-manager/
├── baosys-manager-api/
│   └── baosys.manager.api
│       ├── UserApi
│       ├── LoginSessionApi
│       ├── SystemCatalogApi
│       ├── BrowserAuthApi
│       └── dto
└── baosys-manager-service/
    └── baosys.manager
        ├── ManagerApplication
        ├── app
        │   ├── controllers
        │   ├── services/impl
        │   ├── assembler
        │   ├── config/web
        │   └── error
        └── infra
            ├── database/entity
            ├── database/mapper
            ├── database/converter
            ├── security
            └── config
```

The API JAR depends on common-api; spring-web and jakarta.validation-api are optional. It contains public interfaces and DTOs, not controllers, entities, or FeignClient definitions.

Service directly depends on manager-api, common-api, common-password, common-security-core, and common-security-resource. It assembles Spring Authorization Server, Spring Session JDBC, MyBatis Plus, Flyway, and PostgreSQL. There is no baosys-common or security-issuer module.

## 2. Calls and responsibilities

Controllers call application services, which directly orchestrate mappers, assemblers, and infrastructure without an additional Domain/Agent layer. UserController, LoginSessionController, SystemCatalogController, and SharedLoginController implement API JAR interfaces. BrowserAuthApi defines browser login, refresh, and logout contracts; BrowserLoginRequest/BrowserLoginResponse define requests and responses. Cookie handling and login orchestration stay in Service.

| Component | Main responsibility |
|---|---|
| UserAppServiceImpl | Account CRUD, login ID resolution, current user, online status, user-client revocation, audits |
| LoginSessionAppServiceImpl | Paginated valid login sessions across users |
| SystemCatalog | Startup catalog loading, permission-write validation, effective grant filtering |
| BrowserAuthAppService / BrowserLoginTransaction | Internal login use cases, ordered account locks, refresh and revocation transactions |
| AccountAuthenticator / InternalLoginAuthenticator | Password verification outside transactions / conversion of verified internal login identity |
| AccountAuthorizationService | Delegates to framework JDBC authorization storage, synchronizes user_login, locks accounts and rechecks during token lookup |
| AuthorizationServerConfiguration | OAuth2/OIDC endpoints, account authentication, shared token issuer assembly |
| AccountTokenSubjectResolver | Account state, login record, and permission lookup shared by both issuance flows |
| AccountTokenGenerator | Adapts OAuth2 token context to shared TokenIssuer |
| TokenIssuer (common-security-core) | Shared business Access JWT claims/signing and random Refresh Token generation |
| SigningKeysConfiguration | External persistent JWK loading and JwtEncoder |
| DefaultAdminInitializer | Optional default administrator creation and initialization audit |

Business DTOs do not expose database entities. Transactions generally live in application or security services; AuthorizationTransactionFilter additionally wraps framework token/revoke POST requests in transactions.

## 3. HTTP API

Paths below omit the common browser prefix `/api/baosys-manager`. The gateway forwards after StripPrefix=1; Service's default context path is `/baosys-manager`.

| Method | Path | Description | Service method permission |
|---|---|---|---|
| GET | /v1/users/me | Current user | Authenticated, with a valid login record resolved |
| GET | /v1/users | User pagination | MGR:user:list |
| POST | /v1/users | Create user | MGR:user:add |
| PUT | /v1/users | Update user | MGR:user:update |
| DELETE | /v1/users/{id} | Delete user | MGR:user:delete |
| GET | /v1/users/{id}/login-clients | User's valid login clients | MGR:user:session:list |
| DELETE | /v1/users/{id}/login-clients/{loginId} | Revoke one client | MGR:user:session:kick |
| DELETE | /v1/users/{id}/login-clients | Revoke all clients | MGR:user:session:kick |
| GET | /v1/login-sessions | Valid-session pagination across users | MGR:user:session:list |
| GET | /v1/systems | All systems and assignable permission catalog | Authenticated |
| POST | /v1/browser-auth/login | Log in and set shared cookies | Public authentication entry; validates origin and credentials |
| POST | /v1/browser-auth/refresh | Rotate using Refresh Cookie | Public entry; validates origin and refresh credentials |
| POST | /v1/browser-auth/logout | Revoke current internal authorization and clear cookies | Public entry; validates origin |

“Public” does not mean CSRF-exempt: browser writes through the gateway still carry the token returned by /api/baosys-manager/csrf. Shared login requests use application/json. The framework provides OAuth2/OIDC protocol endpoints with their own protocol responses. See [Authentication and sessions](auth_and_session.en.md).

The default gateway requires only login for /users/me and /systems; other management APIs additionally require MGR access. Ordered spring.security.gateway.access-rules declare this behavior.

User pagination supports fuzzy name search, systemType access filtering, page (default 0), and size (default 20, range 1–200). Unknown systemType returns USER_400_002. Session pagination supports userId (at least 1) or userName, with userId taking precedence and the same pagination rules.

## 4. Request and response fields

| DTO | Current fields |
|---|---|
| UserCreateRequest | name, pass, permissions, enabled |
| BrowserLoginRequest | username, password, redirectUrl |
| BrowserLoginResponse | redirectUrl |
| UserUpdateRequest | id, name, optional pass, permissions, enabled |
| UserResponse | id, name, permissions, enabled, status |
| CurrentUserResponse | userId, username, displayName, permissions |
| LoginClientResponse | id, loginClient, loginAt |
| LoginSessionResponse | id, userId, userName, permissions, loginClient, loginAt, expiresAt |
| SystemDefinition | systemType, name (language map), permissions (code/name list) |

Account responses omit systemType/systemTypes; clients derive system access from permissions. CurrentUserResponse.displayName currently equals the username. LoginClientResponse.id is a login record ID, not OAuth2 client_id. Although client_id exists in the database, these session DTOs do not return it.

UserResponse.status indicates the presence of an unexpired login record; enabled controls account activation. Current-user permissions are catalog-filtered, while user/session lists retain database permissions to identify obsolete historical codes.

Shared login accepts username (required, at most 128 characters), password (required, at most 256), and redirectUrl (optional, at most 8192). Success returns data.redirectUrl; tokens are issued in HttpOnly cookies.

## 5. Core business rules

1. Account names are trimmed before writing, at most 128 characters, and globally unique. Database uniqueness protects concurrent writes; duplicate-key errors map to 409.
2. Creation passwords are required and 8–128 characters. A null, empty, or whitespace-only update password leaves it unchanged; a new password remains subject to the 8–128 limit.
3. permissions is required, but an empty set is valid. Unknown permissions or API permissions without their owning system access are rejected.
4. Omitted enabled defaults to true on creation and preserves the existing value on update. Disabled accounts cannot log in or refresh.
5. Username, password, enabled-state, or permission changes revoke related authorizations and authorization-server sessions. Account deletion also removes login records.
6. Creation, deletion, and permission/enabled-state changes are audited in the user-write transaction. Username/password-only changes do not write permission-change audits.
7. For create, update, and delete operations, controllers resolve operatorUserId from JWT sub (login record ID) to account ID; callers cannot provide the operator in the request body.
8. Single-client revocation must match user ID and login record ID and target an unexpired record. All-client revocation deletes every login record for the user.
9. Read-only lists consider sessions valid when expires_at > current time. Online status is queried in a batch for the whole page.
10. Revocation blocks refresh immediately, but APIs using only offline JWT verification may accept old tokens until Access expiry; the default Access lifetime is 30 minutes.

Internal shared login and external OAuth2 refresh differ in lifetime and error semantics; see [Authentication and sessions](auth_and_session.en.md). There is no last-administrator protection, role authorization, or data-level permission enforcement.

## 6. Transaction boundaries

| Operation | Implementation |
|---|---|
| User, current-identity, and session queries | Application service @Transactional(readOnly=true) |
| User create/update/delete and client revocation | Application service @Transactional |
| Internal login completion/refresh/logout | BrowserLoginTransaction @Transactional; password verification outside the transaction |
| Authorization save/delete/token lookup | Transactional AccountAuthorizationService methods |
| Framework POST /oauth2/token and /oauth2/revoke | AuthorizationTransactionFilter transaction wrapper |
| Default administrator initialization | Initializer @Transactional |

Account updates and token issuance/refresh use account row locks. Specific-client revocation currently deletes login records through a mapper and database cascades/triggers; do not describe that method as explicitly locking the account.

## 7. Responses and errors

Business APIs return ApiResponse with code, message, data, traceId, and timestamp. Paginated data uses PageResponse. TraceIds.current() correlates business logs and responses.

| Error code | HTTP status | Scenario |
|---|---:|---|
| REQUEST_400_001 | 400 | Method parameter validation or missing query parameter |
| AUTH_400_001 | 400 | Body validation or unreadable JSON, including account request bodies |
| AUTH_400_REDIRECT | 400 | Disallowed login return URL |
| AUTH_401_001 | 401 | Invalid shared login/refresh credentials or invalid business login record |
| USER_400_001 | 400 | Empty username |
| USER_400_002 | 400 | Invalid permission configuration or unknown system filter |
| USER_400_003 | 400 | Invalid updated password length |
| USER_404_001 | 404 | User not found |
| USER_409_001 | 409 | Duplicate username |
| LOGIN_404_001 | 404 | Valid login client not found |
| SYS_500_001 | 500 | Unexpected exception |

Spring Security handles JWT authentication/authorization failures with standard 401/403 and WWW-Authenticate where applicable; the business JSON structure is not guaranteed. CSRF and Origin rejection return 403. OAuth2 token/revoke endpoints use protocol responses.

## 8. Startup configuration

Defaults: application.name=baosys-manager, port 9090, context-path=/baosys-manager. See [application.yml](../../../baosys-manager/baosys-manager-service/src/main/resources/application.yml).

JWT_JWK_SET_LOCATION is required. Override OAUTH2_CLIENT_SECRET_HASH for production and keep JWT_ISSUER consistent on both sides. Default issuers are aligned; see [Authentication configuration](auth_and_session.en.md#6-key-configuration).

BAOSYS_SYSTEMS_CONFIG selects the catalog, defaulting to classpath:baosys-systems.yml. The database defaults to app_db/manager, with Flyway running the unified V1 initialization.

Default administrator initialization is disabled. Enabling DEFAULT_ADMIN_ENABLED requires DEFAULT_ADMIN_PASSWORD of at least 12 characters. Existing same-name users are unchanged; newly created administrators receive all catalog MGR permissions and an audit record.

## 9. Tests and execution

Existing tests include these actual classes:

| Area | Representative tests |
|---|---|
| Account service and controller | UserAppServiceImplTest, UserControllerTest, UserControllerIntegrationTest |
| Shared login and authorization server | SharedLoginControllerTest, SharedLoginIntegrationTest, AuthorizationServerIntegrationTest, AuthorizationClaimsTest |
| Resource API security and catalog | ResourceApiSecurityTest, SystemCatalogTest, SystemCatalogIntegrationTest |
| Keys and login configuration | SigningKeysConfigurationTest, SharedLoginPropertiesTest |
| Initialization, cleanup, lifecycle | ManagerInitializationIntegrationTest, UserLoginCleanupFunctionIntegrationTest, AccountLifecycleIntegrationTest |
| Gateway and shared security | GatewaySecurityIntegrationTest, JwtVerificationTest, JwtPermissionConverterTest, JwksContractTest |

AccountLifecycleIntegrationTest covers V1 initialization, audit retention after deletion, a single successful concurrent refresh, and authorization revocation after a disable/refresh race. Database tests extend PostgreSqlIntegrationTest, use postgres:17-alpine, and initialize with Flyway.

Root Surefire excludes *IntegrationTest by default; the integration-tests profile runs them through Failsafe:

```powershell
mvn -pl :baosys-manager-service -am test '-Dskip.frontend=true'
mvn -Pintegration-tests -pl :baosys-manager-service -am verify '-Dskip.frontend=true'
```

The second command needs Docker/Testcontainers. These commands and coverage descriptions do not mean the tests were rerun when this document was updated. There is no separate LoginSessionController test class.

## 10. Capabilities not yet implemented

There are no separate self-service password-change or administrator password-reset endpoints, although administrators can set passwords through the existing user-update API. Roles, organizations, tenants, data permissions, account locking, and online catalog editing are not implemented. The frontend system configuration page does not imply backend configuration CRUD exists.
