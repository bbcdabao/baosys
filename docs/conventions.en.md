# Development conventions

[简体中文](conventions.md) | **English**

Manager is currently maintained while the Pingmoniter probing subsystem is being integrated. Implement new capabilities in the relevant business module first; do not create empty modules or business directories in advance. Newly imported modules must inherit the root POM. Use BaoSys versions for existing Java, Spring, and frontend dependencies.

Documentation comments use a multiline format: put `/**` and `*/` on separate lines and begin each body line with ` * `. Do not use a single-line `/** ... */`, even for a one-sentence comment.

## Layers and dependencies

- `manager-api` contains API contracts, request/response DTOs, and validation annotations, not entities, mappers, service implementations, or full starters.
- `manager-service` depends on its own API. Controllers adapt input, application services orchestrate business logic and transactions, and infrastructure implements persistence and security. Controllers must not call mappers directly.
- Shared contracts belong in `common-api`; security and password implementations belong in their existing modules. Do not restore an all-purpose Common module.
- The gateway handles routing, authentication, and system access permissions. It does not implement business CRUD or access the database directly.
- Each module explicitly declares libraries directly used by its source code. The root POM aggregates modules and manages versions; application modules select starters, while shared libraries depend only on the framework libraries they need.
- Do not depend on another business service's implementation JAR or expose database entities. Do not force a domain layer onto simple business logic.

Business API modules use the following directories and packages beneath their Java base package. Directory structure must match package declarations:

| Directory | Responsibility | Manager package example |
| --- | --- | --- |
| `module/payloads` | Request models and parameter validation annotations | `baosys.manager.api.module.payloads` |
| `module/responses` | Response models and nested response structures | `baosys.manager.api.module.responses` |
| `module/contract` | API contracts declaring HTTP mappings, parameters, and return types | `baosys.manager.api.module.contract` |

Store request and response models separately instead of mixing them in a `dto` package. Contracts reference both model groups; controllers in the Service module implement those contracts. `common-api` continues to provide `ApiResponse` and `PageResponse`. When renaming packages, update callers and tests without changing HTTP paths or JSON fields.

## APIs and permissions

Use `ApiResponse` and `PageResponse` consistently. Pagination is zero-based, with at most 200 items per page. Central exception handling returns stable error codes and a traceId without exposing stack traces to clients.

Define parameters, mappings, and request/response DTOs for business APIs and shared browser login APIs in API contracts. Controllers implement contracts and declare authorization. Cookie handling and authentication flow implementations remain in Service; security configuration assembles framework-provided OAuth2/OIDC endpoints. Keep the gateway entry path `/api/baosys-manager`. When changing the context path, update forwarding, frontend, and authentication configuration together.

Register permissions in the root `baosys-systems.yml`, using `MGR`/`CAT` for system access and `<system>:<resource>:<operation>` for API permissions. New APIs require corresponding catalog entries, method authorization, and frontend action controls. Hiding buttons does not replace backend authorization. Adding permissions never automatically elevates existing accounts.

Accounts must store a default login subsystem. This default does not grant permissions. Login first restores a valid return URL, otherwise uses the default subsystem. Users without access to the target system go to the authorized-systems page rather than automatically signing in again. Register all allowed UI return ranges in the system catalog; reject unregistered origins.

Code contracts define request and response fields. Documentation explains important behavior and constraints instead of duplicating every DTO.

## Database and configuration

V1 is currently the complete initialization script for the undeployed first release. After deployment, changes must use incrementally versioned Flyway migrations. Specify defaults, nullability, and handling of existing data for new columns. Preserve database uniqueness constraints and necessary foreign keys.

Business changes and their audit records share a transaction. Use UTC semantics and TIMESTAMPTZ database columns. Changes to accounts, permissions, or sessions must verify revocation behavior, not just successful updates.

Reuse existing configuration properties where possible. Explicitly map new environment variables in application YAML and update the [configuration reference](development.en.md). Never store private keys, production passwords, or client secrets in the repository or frontend.

## Verification and documentation

Verify according to impact: unit tests for ordinary logic; relevant integration tests for database, migration, transaction, or authentication changes; type checking and builds for frontend changes. Commands are collected in [Setup and deployment](development.en.md). Database integration tests use Testcontainers rather than personal development databases.

For dependency changes, also run `mvn dependency:analyze "-Dskip.frontend=true"`. Bytecode analysis cannot identify every auto-configuration or SPI use; do not remove starters, drivers, or runtime components solely because they are reported as unused.

A change description only needs to explain the problem, affected behavior, verification, and any configuration or data adjustments. Do not copy a set of design templates for every feature.

The documentation convention specifies the root README and three topic documents in this directory. Update existing text and remove obsolete descriptions. Describe implemented capabilities, label reserved configuration explicitly, and avoid duplicate upgrade guides or historical implementation plans. Check relative links and command paths after edits.
