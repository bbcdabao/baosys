# Manager system design

[简体中文](system.md) | **English**

Manager Service centrally maintains users, permissions, sessions, and audits. The workspace contains Manager and shared modules. CAT has an entry in the system catalog, but its independent subsystem project is not in this workspace.

## Request flow and module boundaries

```text
Development: Browser :5173 → Vite proxy → Manager Web :8080
Packaged:    Browser :8080 → Manager Web (also serves Vue static resources)
                                         ↓
                           Manager Service :9090 → PostgreSQL
```

The gateway removes `/api` from `/api/baosys-manager/**` before forwarding. Service's context path is `/baosys-manager`. For example, external `/api/baosys-manager/v1/users` maps to the Service controller at `/v1/users`.

Manager API provides only contracts and DTOs. Within Service, `app` owns controllers, business orchestration, and transactions; `infra` owns database and authentication implementations. The gateway does not access the business database. Shared security dependencies are separated into core, resource, gateway, and password modules.

## Login and sessions

1. Before writes, the frontend requests same-origin `GET /api/baosys-manager/csrf` and sends the returned `token` using the returned `headerName`.
2. Login submits JSON to `POST /api/baosys-manager/v1/browser-auth/login`. Service validates the account, enabled state, and return URL, then creates authorization and login records.
3. The response stores `BAOSYS_ACCESS_TOKEN` and `BAOSYS_REFRESH_TOKEN` in HttpOnly cookies. JSON contains the navigation URL; tokens are not written to frontend storage.
4. The gateway reads the Access Cookie or explicit Bearer header, validates the JWT, and forwards a Bearer token. Resource services validate the JWT again. Explicit Authorization headers take precedence.
5. On an API 401, the frontend performs at most one refresh and retry, sharing refreshes across concurrent requests. Failure leads to login. A 403 means insufficient permissions.

Access Tokens use RS256, `aud=def`, permissions from `permissions`, and the login record ID as `sub`. Only Manager Service loads the signing private key; public keys are published at `/baosys-manager/oauth2/jwks`. The gateway retrieves public keys from its configured JWKS URL.

Cookies are host-only with SameSite=Lax. Access uses Path `/`; Refresh uses `/api/baosys-manager/v1/browser-auth`. Login, refresh, and logout all undergo CSRF validation. Service also requires write-request Origin to exactly match `MANAGER_PUBLIC_ORIGIN`.

Default Access lifetime is 30 minutes and Refresh lifetime is 30 days, from the `baosys-web` client's token settings. Refresh rotates the Refresh Token while retaining the absolute expiry established at initial login. Logout or session revocation deletes authorization and login records. Username, password, permission, and enabled-state changes, as well as account deletion, also revoke related authorizations. Offline verification does not query revocation in real time; a copied Access Token may remain valid until expiry.

Service retains standard OAuth2 Authorization Server support. The Manager UI uses the username/password flow above; the gateway needs no OAuth2 Client secret. The system catalog's UI URLs define cross-port return ranges; SHARED_LOGIN_ALLOWED_ORIGINS is no longer used.

## Default subsystem and login returns

Account creation and editing must submit a `defaultSystemType` present in the catalog. It is stored in `user_info.default_system_type`, which is non-null and cannot be blank. The initialized administrator defaults to MGR. The default system determines navigation only, not access or operation permissions. An account can default to CAT without CAT access; login then uses the shared fallback.

`baosys-systems.yml` registers each system's `ui-base-url` and `home-path`. The former is the full UI address range ending in a slash; the latter is the home path starting at the origin root and may include a hash route. The home page must stay within its UI range. System ranges must not overlap; every origin must share Manager's host and protocol. The MGR base URL must equal the Manager origin plus a trailing slash. The catalog API returns both fields for authorized-system links.

Login validates the return URL's format and ownership before authenticating the account. Inside the session-creation transaction, locked account data determines the final destination:

- A valid return URL takes priority, preserving query parameters and hash. Access requires the target system's entry permission.
- Without a return URL, use the account's default subsystem home page. Do not choose another system based on permission count.
- Missing target system access, no grants, or a default system removed from configuration always leads to Manager's `/baosys-manager/authorized-systems`.
- `/baosys-manager/authorized-systems` requires login but not MGR. It shows authorized systems or a message if none are available. Users can sign out from the top bar and sign in again; there is no automatic redirect.
- Unregistered external origins, non-HTTP(S) URLs, user information, ambiguous encodings, and login/authentication/API targets return `400/AUTH_400_REDIRECT` without creating a new login session. Standard OAuth2 authorization endpoints retain a separate exact-address check.

On authentication expiry, subsystem frontends use `URL.searchParams.set('redirect_url', window.location.href)` to navigate to Manager's `/baosys-manager/login`. A signed-in user lacking system access goes directly to `/baosys-manager/authorized-systems`. Insufficient operation permission displays 403 rather than repeatedly redirecting to login. Subsystem Web hosts must support SPA fallback for deep routes, read the shared Access Cookie through their own gateway, and forward Bearer tokens. Services independently enforce access and operation permissions.

The repository does not implement a cross-port `/auth-bridge` refresh coordination page. Manager coordinates same-origin tabs; independent subsystems still need session checks, 401 returns, and cross-port session coordination. Manager return-navigation testing is not full CAT end-to-end testing. Never pass tokens through URLs or frontend storage.

## Permissions

[baosys-systems.yml](../baosys-systems.yml) is the system and permission catalog, loaded at startup. External overrides require a restart. Manager's entry permission is `MGR`, with these API permissions:

| Permission | Purpose |
|---|---|
| `MGR:user:list` | List users |
| `MGR:user:add` | Create users |
| `MGR:user:update` | Update accounts, permissions, and enabled state |
| `MGR:user:delete` | Delete users |
| `MGR:user:session:list` | List login sessions |
| `MGR:user:session:kick` | Revoke login sessions |

Accounts store permission sets directly; there is no role model. System access and API permissions are assigned separately. `MGR` alone does not grant all API permissions. Example submission: `["MGR", "MGR:user:list"]`. Service rejects unknown permissions and API permissions missing their owning system access. Values are deduplicated, sorted, and stored in a TEXT column.

User creation, updates, current-user responses, user lists, and login-session responses use a flat `permissions` collection without duplicating `systemTypes`. The frontend derives system tags and editing groups from it. Catalog `systemType` and the user-query `systemType` filter remain. Current-user responses contain catalog-valid permissions; management lists retain stored permissions so obsolete grants can be identified and removed. Session-list permissions likewise reflect the account's current stored permissions, not a token-issuance snapshot.

The gateway checks `MGR` by path; Service uses `@PreAuthorize` for API permissions. `/v1/users/me` and `/v1/systems` require authentication only; their gateway rules must precede wildcard rules. JWT permissions map unchanged, without treating `scope` or `SCOPE_` as business authorization.

Uppercase system abbreviations and forms such as `MGR:user:list` are naming conventions, not full runtime format validation. The catalog checks global uniqueness, permission registration, and system-access dependencies. New catalog permissions are not automatically assigned to existing accounts.

## Main APIs

Paths below are relative to the external prefix `/api/baosys-manager`. [API contracts](../baosys-manager/baosys-manager-api/src/main/java/baosys/manager/api/module/contract) define request and response fields. Request models live in `module/payloads`; responses live in `module/responses`.

| Method and path | Behavior |
|---|---|
| `POST /v1/browser-auth/login` | Log in |
| `POST /v1/browser-auth/refresh` | Refresh |
| `POST /v1/browser-auth/logout` | Log out |
| `GET /v1/users/me` | Current user |
| `GET /v1/systems` | System and permission catalog |
| `GET /v1/users` | Paginated users |
| `POST /v1/users` | Create user |
| `PUT /v1/users` | Update user |
| `DELETE /v1/users/{id}` | Delete user |
| `GET /v1/login-sessions` | Paginated sessions |
| `GET /v1/users/{id}/login-clients` | User sessions |
| `DELETE /v1/users/{id}/login-clients/{loginId}` | Revoke one session |
| `DELETE /v1/users/{id}/login-clients` | Revoke all user sessions |

Responses contain `code`, `message`, `data`, `traceId`, and `timestamp`; the success code is `SUCCESS`. Pagination contains `items`, `total`, `page`, `size`, and `totalPages`, with zero-based pages and at most 200 items per page.

## Database

The [V1 initialization script](../baosys-manager/baosys-manager-service/src/main/resources/db/migration/V1__init_manager.sql) creates seven tables, indexes, foreign keys, a cleanup function, and revocation triggers:

| Table | Contents |
|---|---|
| `user_info` | Globally unique username, BCrypt password, required default subsystem, enabled state, permissions, and audit fields |
| `user_login` | Account, client, authorization link, authentication session, and expiry |
| `permission_change_audit` | Operator, target, permissions and enabled state before/after, traceId, and time |
| `oauth2_authorization` | Authorizations and tokens |
| `oauth2_authorization_consent` | OAuth2 consent |
| `spring_session` | Authorization-server sessions |
| `spring_session_attributes` | Authorization-server session attributes |

The default schema is `manager`, and the Flyway history table is `flyway_schema_history_manager`. There are no legacy `system_type` or `refresh_token_hash` columns, and no V2–V4 upgrade scripts are needed for initialization.

Audit records have no user foreign keys and survive account deletion. Online status is derived from the presence of unexpired login records, not a user-table field. Cleanup deletes only expired `expires_at` records, locks accounts in ID order, skips busy accounts, handles at most 100 accounts per call, and triggers authorization revocation. Flyway creates the function but does not schedule it. See [Setup and deployment](development.en.md).
