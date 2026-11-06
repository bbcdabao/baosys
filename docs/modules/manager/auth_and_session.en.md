# Authentication and sessions

[简体中文](auth_and_session.md) | **English**

Manager supports internal browser login with shared cookies and external OAuth2/OIDC authorization code flows. Both share accounts, JWK signing keys, the framework JDBC authorization store, and login records, but differ in browser interaction and refresh lifetimes.

For step-by-step internal login, refresh, and logout calls, see [BrowserAuthApi implementation sequences](browser_auth_sequence.en.md), including separate sequence diagrams, transactions, and failure branches for each endpoint.

## 1. Internal shared login

The Vue login page is Manager Web's `/baosys-manager/login?redirect_url=...`. The page parameter is `redirect_url`; the submitted JSON field is `redirectUrl`.

`BrowserAuthApi` in manager-api declares the three shared authentication APIs. Login uses `BrowserLoginRequest` and `BrowserLoginResponse`. SharedLoginController implements the contract, sets cookies, and calls the authentication service.

| Method | Browser path | Request and result |
|---|---|---|
| POST | `/api/baosys-manager/v1/browser-auth/login` | JSON: username, password, optional redirectUrl; response data.redirectUrl; tokens only in cookies |
| POST | `/api/baosys-manager/v1/browser-auth/refresh` | JSON request; rotates tokens using the Refresh Cookie |
| POST | `/api/baosys-manager/v1/browser-auth/logout` | JSON request; revokes the current internal authorization and clears both cookies |
| GET | `/api/baosys-manager/csrf` | Gateway returns a masked CSRF token and headerName |

Login validates username, password, and enabled state; usernames are trimmed first. AccountAuthenticator checks passwords outside a transaction. BrowserLoginTransaction locks the old and new accounts in ascending ID order, rechecks username, password hash, and enabled state, then revokes any internal authorization still matching the old Refresh Cookie and creates a new authorization and login record. Failure rolls back the entire replacement transaction. The grant type is `urn:baosys:grant:shared-login`, using the registered `baosys-web` client settings.

Ordinary return addresses are validated by `SharedLoginProperties`: they must share the host and protocol, and the origin must be manager-origin or an entry in allowed-origins. User information and unsuitable targets such as login/API/authorization paths are rejected. `SharedLoginNavigation` provides an exact exception only for the configured issuer + authorization endpoint to resume OAuth requests. That address must also share the login page's host and protocol for cookie sharing, and may contain neither fragments nor user information. Without an address, the default is `{manager-origin}/users-management`.

| Cookie | Path | Attributes |
|---|---|---|
| BAOSYS_ACCESS_TOKEN | `/` | HttpOnly, SameSite=Lax |
| BAOSYS_REFRESH_TOKEN | `/api/baosys-manager/v1/browser-auth` | HttpOnly, SameSite=Lax |

Domain is not set, so cookies are shared across ports on the same host, not across subdomains. Secure is configurable; an HTTPS manager-origin requires secure-cookie=true. Responses use Cache-Control: no-store.

The gateway prioritizes explicit Authorization headers and otherwise reads the Access Cookie. An invalid explicit header never falls back to cookies. Public shared authentication endpoints do not depend on an old Access JWT, so expiry cannot block refresh.

Before browser writes, the UI obtains a token from `/api/baosys-manager/csrf` and sends it in the specified header. The gateway validates CSRF. Shared authentication writes sent directly to Service must still have an Origin exactly matching manager-origin. This Service chain is stateless with CSRF disabled. Gateway CSRF exemptions apply only to protected APIs carrying explicit Bearer credentials, not public login/refresh/logout requests.

Manager UI coalesces concurrent refreshes for 401 responses from non-authentication APIs, retrying each request at most once. authCoordinator uses same-origin Web Locks to serialize login, refresh, and logout, and non-sensitive accountEpoch/refreshRevision markers to recognize refreshes completed by other tabs. Notifications contain no credentials. Old-account requests are not automatically replayed after account switching, and late responses cannot overwrite the new account's state.

Only an explicit refresh 401 signals login expiry. Network errors, 5xx responses, and lock-wait timeouts retain login state. Frontend state is cleared and other tabs notified only after successful logout. Authentication APIs do not recursively refresh, and 403 never triggers refresh. Without Web Locks or usable storage, coordination falls back to the current page. Ports do not share coordination locks; other subsystems continue to use Manager for login.

## 2. External OAuth2/OIDC

`AuthorizationServerConfiguration` enables Spring Authorization Server OAuth2 and OIDC endpoints, including authorization, token, jwks, revoke, introspect, and discovery. Paths use Manager Service's context path. Unauthenticated browsers use the same Vue login page instead of a generated Spring login form. The page invokes `BrowserAuthAppService` through `/browser-auth/login`; after login, the browser returns to the authorization endpoint with the new internal Access Cookie to resume the authorization code/PKCE request.

`SharedLoginAuthorizationFilter` recognizes internal login at the authorization endpoint. It first validates a gateway-forwarded Bearer Token, or reads the shared Access Cookie when no Authorization header exists. It reuses the framework's `BearerTokenAuthenticationFilter` and `JwtAuthenticationProvider` for signature validation, authentication context management, and error responses. `InternalLoginAuthenticator` checks that the login record is unexpired, the account is enabled, and the authorization is an internal shared-login grant, then resolves the JWT login record ID to an account ID.

A valid internal login needs no further password entry. This identity is not separately stored in Spring Session; every request checks the original login record. Invalid tokens do not fall back to cookies. HTML browser requests redirect to the shared Vue login page; non-HTML requests return 401. Missing authentication also redirects to that page. redirect_url preserves client_id, redirect_uri, state, nonce, and PKCE parameters; only the configured authorization endpoint is permitted as the resume URL. Third-party OAuth Access Tokens cannot authenticate a user to the authorization server, avoiding expansion of their authorization scope. Protocol endpoints such as token/revoke do not use this conversion and retain their original client authentication.

PKCE is configured per client and is not required by default. When supplied, the framework still validates the verifier. Internal login always uses username/password; external authorization code flows are used as needed.

Spring Boot assembles RegisteredClient entries from configuration. The default client supports authorization_code, refresh_token, and openid/profile. The configuration map key is the record ID, which persisted authorizations depend on. There is no database registered-client table.

The gateway retains `BrowserOAuth2Configuration` for external client integration. It is enabled only with `baosys.browser-oauth2.enabled=true` and requires Spring OAuth2 client registration. Manager Web defaults neither enable this switch nor provide `spring.security.oauth2.client` registration, so internal login does not use gateway authorization-code callbacks or WebSession.

## 3. JWT contract

Internal application services delegate to `BrowserLoginTransaction`; both it and external OAuth2's `AccountTokenGenerator` call common-security-core's `TokenIssuer` for Access/Refresh issuance. `AccountTokenSubjectResolver` centrally queries accounts, login records, and current permissions in Manager. Core depends on neither Manager nor authorization-server types. Business Access Tokens use this contract:

| Item | Meaning |
|---|---|
| alg / typ / kid | RS256 / at+jwt / nonempty key ID from the external JWK |
| iss | Issuer from AuthorizationServerSettings |
| sub | `user_login.id`, not the account ID |
| aud | Includes `def` |
| client_id | Registered client identifier; `baosys-web` internally |
| iat, exp, jti | Issued-at time, expiry, token ID |
| permissions | Permission string list filtered through the current catalog; an empty list is valid |

The shared issuer does not write scope, system_type, or systemType. Permission conversion uses only permissions. Framework JwtGenerator still issues OIDC ID Tokens; openid/profile are protocol scopes. ID Token sub remains a stable account ID, distinct from business Access Token sub.

Shared `JwtVerification` combines standard Spring validators with platform contract validation: issuer, audience, temporal validity, at+jwt type, required claims, nonempty kid, and a string-list permissions claim. The decoder validates algorithm and signature. Validation does not query the catalog or database for each token.

The signing key file must contain exactly one private RSA signing key and may include old public keys. Every key needs a unique nonempty kid, at least 2048 bits, use=sig, and alg=RS256. There is currently no permission-size budget or post-issuance JWT character limit check.

## 4. Authorization storage and refresh

`AccountAuthorizationService` delegates token storage to `JdbcOAuth2AuthorizationService` and maintains the `user_login` projection. The projection stores account, client, authorization ID, login description, expiry, and an optional authorization-server Session ID. It does not store a custom refresh-token hash.

| Behavior | Internal shared login | External OAuth2 |
|---|---|---|
| Refresh entry | browser-auth/refresh | Framework token endpoint |
| Implementation | BrowserAuthAppService calls shared TokenIssuer through BrowserLoginTransaction | Framework provider calls the same TokenIssuer through AccountTokenGenerator |
| Refresh expiry | Retains the initial absolute login expiry | Follows TokenSettings; new tokens may receive a new lifetime |
| Access expiry | No later than current Refresh expiry | Follows framework TokenSettings |
| Default lifetime | Access 30m, Refresh 30d | Access 30m, Refresh 30d, reuse-refresh-tokens=false |

Authorization lookup locks the account and rereads the token, preventing use of authorizations rotated or deleted while waiting for the lock. BrowserLoginTransaction covers login completion, refresh, and revocation; password verification holds no transaction. `AuthorizationTransactionFilter` wraps framework POST `/oauth2/token` and `/oauth2/revoke` in transactions.

A rotated Refresh Token cannot be reused. Invalid internal refresh returns business 401 (AUTH_401_001); external token endpoints return OAuth2 invalid_grant. Do not mix these error formats.

## 5. Revocation and validity boundaries

Changes to username, password, permissions, or enabled state, and account deletion, trigger database cleanup of related authorizations and authorization-server sessions. Deleting `user_login` for single-session revocation, all-session revocation, or cleanup also deletes its authorization and associated Session through a trigger. Deleting authorization cascades to the login record through a foreign key.

Internal logout revokes only the internal authorization corresponding to the current Refresh Cookie and clears cookies. External authorization-server sessions and gateway OAuth2 WebSession belong to another login flow; internal logout should not be described as clearing a gateway-local session.

`cleanup_inactive_user_login_sessions()` deletes only expired expires_at records. Each call locks at most 100 accounts in ID order, skips busy accounts, rechecks expiry under lock, and deletes matching records. Scheduling every five minutes is recommended. updated_at records modifications, not idle timeout. One day without access does not revoke an internal login still within its absolute lifetime.

OAuth projections use Refresh Token expiry when present. Unredeemed authorizations use authorization-code expiry; redeemed authorizations without Refresh Tokens last until the later Access/ID Token expiry. Cleanup does not replace request-time validity checks.

Revocation immediately blocks future refresh. `/users/me` and operations resolving login records also become invalid when those records are deleted. APIs using only offline JWT checks may still accept issued tokens until expiry. jti is neither a denylist nor a replay-prevention mechanism.

## 6. Key configuration

See [Manager application.yml](../../../baosys-manager/baosys-manager-service/src/main/resources/application.yml) and [Manager Web application.yml](../../../baosys-manager-web/src/main/resources/application.yml).

| Setting | Purpose |
|---|---|
| spring.security.oauth2.authorizationserver | Issuer, clients, token lifetimes |
| spring.security.oauth2.resourceserver.jwt | Verification issuer, JWKS, algorithms, audiences |
| baosys.authorization.jwk-set-location | Persistent JWK file; environment variable JWT_JWK_SET_LOCATION |
| baosys.shared-login.manager-origin | Public Manager origin; defaults to http://localhost:8080 |
| baosys.shared-login.allowed-origins | Same-host, same-protocol origins permitted after login |
| baosys.shared-login.secure-cookie | Secure cookie switch; defaults to false |
| spring.security.gateway.access-rules | Ordered path authorization rules |
| spring.security.gateway.permit-all-paths | Public entry paths |

Both JWT_ISSUER defaults are `http://localhost:9090/baosys-manager`; keep overrides consistent. Signing JWKs are required. Override OAUTH2_CLIENT_SECRET_HASH for production and make the JWKS URL reachable by verifying services.

See [Database design](database_design.en.md) for tables and the [Permission model](permission_model.en.md) for authorization rules.
