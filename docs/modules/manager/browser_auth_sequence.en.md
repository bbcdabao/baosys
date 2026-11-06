# BrowserAuthApi implementation sequences

[简体中文](browser_auth_sequence.md) | **English**

Internal systems use username/password login. OAuth authorization code flows reuse the same login page and internal identity as needed. PKCE is configured per client, not enforced globally. This document describes the current implementation.

## 1. Components and endpoint protection

| Component | Responsibility |
|---|---|
| authCoordinator / request.ts | Same-origin tab authentication locks, version markers, CSRF, and request retries |
| Gateway | CSRF, system access permissions, Cookie-to-Bearer forwarding |
| SharedLoginController | Return URL validation, cookies, and response adaptation |
| BrowserAuthAppService | Application entry, password authentication and transaction orchestration, conversion of authentication failures to business 401 |
| AccountAuthenticator | Password verification outside transactions; returns an internal authentication snapshot |
| BrowserLoginTransaction | Separate Spring transaction entry points for login completion, refresh, and logout |
| AccountAuthorizationService | Framework JDBC authorization adapter, account locks, login projection coordination |
| AccountTokenSubjectResolver / TokenIssuer | Current permissions and subject resolution / shared token issuance |
| UserInfoMapper / UserLoginMapper | Account and login projection persistence |

Browser paths are `/api/baosys-manager/v1/browser-auth/{login,refresh,logout}`. The gateway removes `/api`; Service defaults to context-path `/baosys-manager`; the controller matches `/v1/browser-auth/**`.

All three endpoints are public but not exempt from browser CSRF. Before writes, UI obtains `/api/baosys-manager/csrf` and places the token in the specified header. The gateway validates CSRF; Service additionally requires write-request Origin to exactly match manager-origin. An expired Access JWT does not block authentication requests. All three use JSON; refresh and logout credentials come from the HttpOnly Refresh Cookie.

## 2. Login

The request contains username, password, and optional redirectUrl. Before authentication, the controller checks the return URL. Ordinary targets use the origin allowlist; OAuth resume requests permit only the configured authorization endpoint, rejecting other origins, paths, user information, and fragments.

```mermaid
sequenceDiagram
    participant UI as Manager UI
    participant API as Controller
    participant App as BrowserAuthAppService
    participant Password as AccountAuthenticator
    participant Tx as BrowserLoginTransaction
    participant Store as AccountAuthorizationService
    participant DB as PostgreSQL
    UI->>UI: Acquire same-origin authentication lock; prepare CSRF
    UI->>API: POST login + previous Refresh Cookie
    API->>API: Validate redirectUrl
    API->>App: login
    App->>Password: Verify credentials outside transaction
    Password->>DB: Query account snapshot
    Password-->>App: VerifiedAccount
    App->>Tx: completeLogin (begin transaction)
    Tx->>Store: lockForInternalReplacement
    Store->>DB: Discover old authorization account without locking
    Store->>DB: Deduplicate old/new account IDs; FOR UPDATE in ascending order
    Store->>DB: Reread authorization by old Refresh Token
    Store-->>Tx: Still-matching internal authorization or none
    Tx->>DB: Recheck enabled state, username, and password hash
    opt Old authorization still exists
        Tx->>Store: Remove old authorization
    end
    Tx->>Store: Save initial authorization and login projection
    Tx->>DB: Read permissions and unexpired login ID under lock
    Tx->>Tx: TokenIssuer issues Access / Refresh
    Tx->>Store: Save complete authorization and projection expiry
    Tx->>DB: Commit transaction
    Tx-->>App: Tokens
    App-->>API: Tokens
    API-->>UI: Set-Cookie + data.redirectUrl
    UI->>UI: Update accountEpoch; notify tabs; release lock; navigate
```

Wrong passwords, missing accounts, and disabled accounts all return AUTH_401_001. Missing accounts still perform dummy password verification. The authentication snapshot contains a password hash, stays server-side, hides contents in toString, and is never included in responses or logs.

The first read of the old token only discovers which account to lock; it is not the final authorization decision. If the final result belongs to an unlocked account, reject it rather than adding out-of-order locks. Rotated/deleted old tokens are skipped; external OAuth authorizations are not internal replacement targets.

A failed final credential recheck does not revoke the old authorization. Issuance, save, or commit failures roll back the complete replacement transaction. BCrypt is not rerun after the transaction starts; the hash is checked for changes. New tokens use the latest permissions read under lock.

## 3. Refresh

Internal sessions use an absolute lifetime: initial login sets Refresh expiry, and subsequent rotation does not extend it. Access expiry is `min(now + Access TTL, refreshExpiry)`.

```mermaid
sequenceDiagram
    participant UI as Manager UI
    participant Lock as authCoordinator
    participant API as Controller / AppService
    participant Tx as BrowserLoginTransaction
    participant Store as AccountAuthorizationService
    participant DB as PostgreSQL
    UI->>UI: Record account/refresh version when sending business request
    UI->>Lock: Receive 401; request refresh coordination
    Lock->>Lock: Coalesce within page; acquire same-origin authentication lock
    alt Account epoch changed
        Lock-->>UI: Cancel old request without automatic replay
    else Another tab already refreshed
        Lock-->>UI: Release lock; allow one retry
    else Refresh required
        Lock->>API: CSRF + POST refresh
        API->>Tx: refresh (begin transaction)
        Tx->>Store: findByToken
        Store->>DB: Query; lock account; check enabled state; query again
        Store-->>Tx: Latest authorization or none
        Tx->>Tx: Check internal grant and Refresh validity
        Tx->>DB: Resolve valid login ID; read current permissions
        Tx->>Tx: Recheck expiry with Clock; issue through TokenIssuer
        Tx->>Store: Save rotated tokens
        Tx->>DB: Commit transaction
        API-->>Lock: Set-Cookie
        Lock->>Lock: Update refreshRevision; release lock
        Lock-->>UI: Retry business request at most once
    end
```

A rotated Refresh Token cannot be reused. Invalid refresh, a missing/expired projection, or expiry reached before issuance returns business 401. Database, signing, or configuration faults retain system-error semantics.

Authentication transport still obtains CSRF but bypasses the business 401 interceptor to prevent recursive refresh or lock reentry. Shared-lock waiting is limited to 15 seconds; authentication HTTP timeout is 10 seconds. Only an explicit refresh 401 signals login expiry. Network errors, 5xx, and wait timeouts retain login state and return an error.

Cross-page storage contains only accountEpoch, refreshRevision, and lastOperation, never tokens or permissions. BroadcastChannel, storage, and focus notifications clear old snapshots. Storage is read under the lock to determine whether refresh has completed. Account changes prevent old-request replay and reject late successful responses.

Without Web Locks or usable storage, only within-page coordination is guaranteed. Different ports share cookies but not locks; other subsystems continue using Manager for authentication. Automatic retries of side-effecting requests require the 401 to come from authentication rejection before business execution; this does not guarantee business idempotency.

## 4. Logout

```mermaid
sequenceDiagram
    participant UI as Manager UI
    participant API as Controller / AppService
    participant Tx as BrowserLoginTransaction
    participant Store as AccountAuthorizationService
    participant DB as PostgreSQL
    UI->>UI: Acquire the same authentication lock; prepare CSRF
    UI->>API: POST logout + Refresh Cookie
    API->>Tx: revoke (begin transaction)
    Tx->>Store: findByToken; lock account and reread
    opt Internal shared authorization exists
        Tx->>Store: remove
        Store->>DB: Delete authorization; cascade login record and linked Session
    end
    Tx->>DB: Commit transaction
    API-->>UI: Max-Age=0 for both cookies
    UI->>UI: Update epoch; clear snapshot; notify tabs; release lock
```

Logout succeeds even when internal authorization is absent or cannot be found. This endpoint does not revoke external OAuth authorizations. Database or network failure does not broadcast successful logout; frontend state is retained for retry. JavaScript cannot directly delete HttpOnly cookies.

## 5. Cookies, lifetimes, and cleanup

| Cookie | Path | Attributes |
|---|---|---|
| BAOSYS_ACCESS_TOKEN | `/` | HttpOnly, SameSite=Lax, configurable Secure |
| BAOSYS_REFRESH_TOKEN | `/api/baosys-manager/v1/browser-auth` | Same as above |

Cookies do not set Domain. HTTPS manager-origin requires secure-cookie. Successful responses use Cache-Control: no-store; tokens never appear in JSON. Access Token sub is user_login.id; OAuth principalName and OIDC ID Token sub are account IDs.

Cleanup checks only expires_at, not updated_at or a day without access. Each call handles at most 100 accounts, locks in ascending ID order, skips busy accounts, and rechecks and deletes expired logins under lock. Scheduling every five minutes is recommended. Administrator revocation also locks accounts first. Request-time expiry checks do not depend on scheduled cleanup.

OAuth projections use Refresh Token expiry when present, authorization-code expiry before redemption, and the later Access/ID Token expiry after redemption without a Refresh Token, retaining authorization information for still-valid ID Tokens.

## 6. Atomicity and verification boundaries

- Application services call transactional methods through a separate Spring bean to avoid self-invocation bypassing the proxy. Storage methods join the outer transaction.
- Controllers set cookies only after a successful transaction commit; the database and HTTP response still do not form one atomic operation.
- If refresh commits but the response is lost, the browser may retain the old token and need to log in again. There is no old-token grace window.
- Revocation blocks refresh and operations that query sessions. APIs using only offline signature verification may still accept old JWTs until expiry.
- Database tests cover switching in both directions, credential changes, replacement rollback, cleanup, and skipped locks. Browser tests use real pages and cookies to verify coordination, but HTTP services are test doubles rather than a full deployment end-to-end test.

## 7. Source and tests

- [BrowserAuthAppService](../../../baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/services/BrowserAuthAppService.java)
- [BrowserLoginTransaction](../../../baosys-manager/baosys-manager-service/src/main/java/baosys/manager/app/services/impl/BrowserLoginTransaction.java)
- [AccountAuthorizationService](../../../baosys-manager/baosys-manager-service/src/main/java/baosys/manager/infra/security/AccountAuthorizationService.java)
- [authCoordinator](../../../baosys-manager-ui/src/utils/authCoordinator.ts)
- [Browser coordination tests](../../../baosys-manager-ui/tests/auth-coordination.spec.js)
- [Database lifecycle tests](../../../baosys-manager/baosys-manager-service/src/test/java/baosys/manager/app/services/impl/BrowserAuthLifecycleIntegrationTest.java)
- [Test and deployment commands](../../development.en.md)
