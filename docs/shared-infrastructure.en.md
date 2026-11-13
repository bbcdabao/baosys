# Shared infrastructure

[中文](shared-infrastructure.md)

Base utilities, logging and operations resources live in `baosys-common`. WebFlux, SPA hosting and gateway security live in `baosys-common-gateway`; MVC and resource-server security live in `baosys-common-resource`. HTTP models remain in `baosys-common-api`; security-core and password remain separate.
Frontend implementations live in `baosys-common-ui`, consumed by Manager UI as the local npm package `@baosys/common-ui`.

## SPA hosting

Manager Web uses the shared `SpaWebConfiguration`. Enable it with `common.spa.enabled=true`.
Set `common.spa.base-path` to `/baosys-manager`, and
`common.spa.resource-location` to `classpath:/static/` (the default) or a `file:/` directory.
The prefix must be an absolute, non-root path without a trailing slash. Resource directories get a trailing slash automatically.
The entry document is `index.html` in that directory.

Both prefixed requests and proxy-stripped root requests are supported. A missing trailing slash gets a 308 redirect preserving the raw query.
GET/HEAD client routes accepting HTML may fall back to the entry document; API, Actuator, OAuth2 and asset paths do not.
Changing the hosting prefix still requires matching Vite/Router bases, proxy rules and login return URLs.

## Logging

The Manager Service and Manager Web `logback-spring.xml` files include `baosys/logging/logback-common.xml`.
Defaults remain INFO, console and UTF-8 file output, MDC traceId, daily/10MB rotation and 7-day/1GB archive limits.
Use `logging.file.*`, `logging.level.*` and `logging.logback.rollingpolicy.*` for overrides.
Application-specific loggers may follow the include; edit shared policies in the common fragment only.

## Frontend requests and authentication

The shared authCoordinator owns account versions, page-local queues, Web Locks, cross-tab notifications and refresh coalescing.
`createRequests` owns CSRF acquisition, account-version checks, at most one recovery retry after a 401,
response unwrapping and `RequestError` conversion. Applications use `ApiResponse<T>`; `ApiResult` was removed.

Applications provide `csrfPath` and `recoverSession`: Manager uses browser-auth refresh.
Manager's `authPrefix` excludes authentication endpoints from automatic retries. Login/logout use a separate request channel,
with CSRF protection and coordinated authentication operations. Credentials are never stored in browser storage.

The package exports TypeScript sources and has no publishing step. Each UI's `.npmrc` sets `install-links=true`,
so the local package is packed into that UI's `node_modules`; the application supplies Axios.
After editing the common package, run `npm ci` in Manager UI to refresh installed copies.
Update the corresponding lockfiles when changing dependencies; do not edit them manually.
Vite explicitly prebundles the shared entries. Browser suites run serially within each application to avoid dependency-discovery reloads interrupting authentication tests.

## Build

The root POM manages Node `v22.4.0`, npm `10.8.1`, frontend-maven-plugin `1.15.1` and its executions.
Manager Web supplies `ui.path` and activates the plugin. It runs `npm ci --no-audit --no-fund` followed by `npm run build`.
UI/npm modules are outside the Maven reactor; preserve their relative repository layout.
`-Dskip.frontend=true` skips frontend installation/build and should not be used for release builds.

## Operations

Manager Service and Manager Web import `classpath:baosys/operations.yml` through `spring.config.import`.
Imports override values in the importing YAML document. Put application overrides in a later `---` document,
a profile or environment variables; Manager Web uses a later document for its additional gateway endpoint.
Web services shut down gracefully; `SHUTDOWN_TIMEOUT` defaults to `20s` per Spring shutdown phase.
The baseline exposes health/info/metrics, hides health details, enables liveness/readiness and tags metrics with the application name.

Gateway probe paths are `/actuator/health/liveness` and `/actuator/health/readiness`.
Servlet services prepend their context path, for example `/baosys-manager/actuator/health/readiness`.
Health and info are public; other `/actuator/**` paths require `MGR`, with explicit Bearer authentication supported by gateways.
Manager Web additionally retains the read-only gateway endpoint. Do not add metrics to permit-all-paths.

Default probes reflect Spring availability, not the correctness of all external dependencies or business tasks.
Executor `shutdown-timeout-seconds` is separate from Spring's phase timeout and can involve a second wait after interruption.
Forced process termination cannot guarantee cleanup. Existing ports and environment names remain compatible.

## Storage JSON compatibility

HTTP, including SSE, uses Spring's ObjectMapper and application Jackson settings.
Applications can use `StorageJson.legacy()` for storage serialization compatible with legacy Jackson defaults.
Its mapper is configured once, never exposed or mutated, and shared safely across threads.

Storage intentionally retains the old `new ObjectMapper()` behavior: field names, annotations, enums, nulls, numbers and dates.
Unknown fields still fail. HTTP modules and naming strategies are not inherited. UTF-8 conversion and non-JSON formats such as Properties remain unchanged.
The platform includes no business storage service and performs no data migration.

`StorageJsonCompatibilityTest` uses frozen legacy-format examples for probe results, master nodes, robot groups, system/Redis configuration,
missing fields, generic lists, UTF-8, unknown fields, enum errors and concurrent access.
It checks that old data is readable, new output is readable by the old mapper, and output remains identical.
These are contract samples, not production data exports. Future format changes require versioning, migration and anonymized historical fixtures.

## Verification

Run `mvn verify -Pintegration-tests` at the root for frontend install/build, default backend tests, Manager gateway tests and PostgreSQL integration tests.
Docker is required for database tests; local HTTP tests need loopback ports.
Run `npm run test:browser` in Manager UI.
Browser tests default to Edge and support `PLAYWRIGHT_CHANNEL`.
Coverage includes custom SPA resources and route exclusions, storage compatibility, shared logging/operations imports and management endpoint authorization.
