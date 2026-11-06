# Internal authentication improvements (implementation archive)

[简体中文](auth_refactoring_proposal.md) | **English**

Status: all four improvements have been implemented. Refer to the linked topic documents for current behavior. Date: 2026-10-30.

Internal login continues to use a username and password. Third-party authorization code flows are optional; PKCE is configured per client rather than enforced globally. API, cookie, and JWT subject contracts remain unchanged.

| Improvement | Implementation |
|---|---|
| Account-switch lock ordering | AccountAuthenticator checks passwords outside a transaction. BrowserLoginTransaction starts a transaction through a separate proxy. After discovering the old and new accounts, the storage adapter locks them in ascending ID order, rechecks credentials, and atomically replaces the authorization |
| Cross-tab refresh | authCoordinator uses Web Locks to serialize same-origin login, refresh, and logout. Non-sensitive version markers avoid duplicate refreshes and replay of old-account requests; unsupported environments fall back to coordination within a page |
| Session lifetime and cleanup | Internal sessions retain their original absolute expiry. Cleanup uses expires_at, locks accounts first, skips busy accounts, and handles at most 100 accounts per batch. The scheduling example runs every five minutes |
| Separation of responsibilities | Application services own use cases; a separate transactional component orchestrates persistence. Password authentication, internal identity authentication, token subject resolution, and HTTP metadata are handled separately. Login projection SQL belongs in UserLoginMapper |

OAuth authorization projections without a Refresh Token distinguish the authorization-code phase from actual Access/ID Token lifetimes. Account session revocation also locks the account first. Since the repository is explicitly not yet deployed, these changes update V1 initialization SQL; deployed environments must not modify an already executed migration directly.

Verification includes database concurrency and lifecycle tests, real multi-tab browser tests, frontend type checking, and builds. Browser tests use HTTP endpoint doubles. Cross-port coordination, recovery after a lost refresh response, and immediate offline JWT revocation are outside the resolved scope.

To avoid divergence between the proposal and implementation documentation, this file retains only the implementation archive and navigation. Full rules are documented in:

- [Authentication and sessions](auth_and_session.en.md)
- [Browser authentication sequences](browser_auth_sequence.en.md)
- [Database design](database_design.en.md)
- [Module responsibilities and transactions](manager_module_design.en.md)
- [Runtime and verification commands](../../development.en.md)
