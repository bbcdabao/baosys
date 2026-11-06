# Setup and deployment

[简体中文](development.md) | **English**

The system has not yet been deployed; initialize it against an empty database. Commands below run from the repository root unless stated otherwise. Environment variable examples use PowerShell; set them in each service's own terminal.

## Environment and build

Requires JDK 17, Maven 3.9+, Node.js 20.19+, and PostgreSQL 17. Database integration tests require Docker.

```powershell
docker run --name baosys-postgres -e POSTGRES_DB=app_db -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres123456 -p 5432:5432 -d postgres:17-alpine
mvn install -DskipTests "-Dskip.frontend=true"
npm --prefix baosys-manager-ui install
```

If PostgreSQL is already available, create `app_db` and configure its connection settings instead of starting a container. On Windows, use `npm.cmd` if `npm.ps1` is blocked.

## Start packaged applications with one command

After `mvn install`, run `bash startup.sh` on Linux (Bash 4.3+) or `.\startup.bat` on Windows. The scripts locate `out` relative to their own location and run every `*.jar` in that directory, streaming logs to the current terminal. Ctrl+C stops all Java processes started by this invocation. If any JAR exits, the scripts also stop the remaining processes.

On the first run, if `out/signing-jwks.json` is missing, the scripts use JDK 17+ `jshell` on PATH to execute `scripts/generate-signing-key.jsh`. Later starts reuse the file. The scripts point `JWT_JWK_SET_LOCATION` to it and set `DEFAULT_ADMIN_ENABLED=true`, `DEFAULT_ADMIN_USERNAME=admin`, and `DEFAULT_ADMIN_PASSWORD=LocalAdmin123!`. Other settings, including database configuration, use the current environment and application defaults.

The Maven build currently clears `out`, deleting the key stored there. The next start generates a new key. These scripts are for local development; use the separate key file and manual startup procedure below to retain keys across builds.

## Prepare signing keys for the first run

Manager must load a persistent private JWKSet: RSA keys must be at least 2048 bits, each key must have a unique `kid`, `use=sig`, and `alg=RS256`, and exactly one key must contain private material. The following Node.js command generates a local development key in your user directory. Run it once and reuse the same file after restarting.

```powershell
node --input-type=module -e "import {generateKeyPairSync,randomUUID} from 'node:crypto'; import {mkdirSync,writeFileSync} from 'node:fs'; import {homedir} from 'node:os'; import {join} from 'node:path'; const dir=join(homedir(),'.baosys'); mkdirSync(dir,{recursive:true}); const {privateKey}=generateKeyPairSync('rsa',{modulusLength:2048}); const jwk={...privateKey.export({format:'jwk'}),kid:randomUUID(),use:'sig',alg:'RS256'}; writeFileSync(join(dir,'signing-jwks.json'),JSON.stringify({keys:[jwk]}),{flag:'wx',mode:0o600});"
```

This file contains a private key; do not commit it. Production deployments should use a separate key file with restricted access.

## Start the three processes

In the Manager Service terminal:

```powershell
$env:JWT_JWK_SET_LOCATION = ([System.Uri](Join-Path $HOME '.baosys/signing-jwks.json')).AbsoluteUri
$env:MANAGER_PUBLIC_ORIGIN = "http://localhost:5173"
$env:MANAGER_UI_URL_SUFFIX = "5173/baosys-manager/"
$env:DEFAULT_ADMIN_ENABLED = "true"
$env:DEFAULT_ADMIN_USERNAME = "admin"
$env:DEFAULT_ADMIN_PASSWORD = "LocalAdmin123!"
mvn -f baosys-manager/baosys-manager-service/pom.xml spring-boot:run
```

The default client secret is `{noop}local-dev-client-secret`; local development does not require `OAUTH2_CLIENT_SECRET_HASH`. This default and the example password are for local development only. For production, set `OAUTH2_CLIENT_SECRET_HASH` to an encoded value with a `{bcrypt}` prefix and use a separate administrator password. The current login page still uses the `baosys-web` registration settings; the gateway does not need a client secret.

Administrator creation is disabled by default. When enabled, the password must be at least 12 characters. Initialization only creates an account if the username does not exist; it does not overwrite existing passwords or permissions. Disable initialization after the first creation. Flyway runs V1 automatically; do not execute individual SQL statements manually. The default administrator's default login subsystem is MGR.

Under the repository's undeployed-first-release convention, the default subsystem column is included in V1: `user_info.default_system_type` is required, cannot be blank, and has no database default. Account writes must explicitly select a catalog system. A development database that has run the old V1 needs a separate migration preserving its data, or initialization in a new isolated schema. Do not overwrite historical checksums or delete existing users. Deployed databases must use an incremental Flyway migration to add the column, explicitly backfill each existing account's default system, and then apply the non-null constraint.

In the Manager Web terminal:

```powershell
mvn -f baosys-manager-web/pom.xml spring-boot:run "-Dskip.frontend=true"
```

In the frontend terminal:

```powershell
npm --prefix baosys-manager-ui run dev
```

Open `http://localhost:5173/baosys-manager/`. Vite proxies `/api`, including CSRF, to port 8080. The Service health endpoint is `http://localhost:9090/baosys-manager/actuator/health`; the gateway health endpoint is `http://localhost:8080/actuator/health`.

## Configuration reference

See [Service application.yml](../baosys-manager/baosys-manager-service/src/main/resources/application.yml) and [Web application.yml](../baosys-manager-web/src/main/resources/application.yml) for actual configuration.

| Variable | Process | Default or requirement |
|---|---|---|
| `DB_URL` | Service | `jdbc:postgresql://localhost:5432/app_db` |
| `DB_USERNAME / DB_PASSWORD` | Service | `postgres / postgres123456`, local defaults |
| `MANAGER_DB_SCHEMA` | Service | `manager`; the database user needs permission to create schemas |
| `MANAGER_PORT / MANAGER_CONTEXT_PATH` | Service | `9090 / /baosys-manager` |
| `JWT_JWK_SET_LOCATION` | Service | Required; resource URI for the private JWKSet file |
| `OAUTH2_CLIENT_SECRET_HASH` | Service | Defaults to `{noop}local-dev-client-secret`; override with a separate encoded secret for production |
| `DEFAULT_ADMIN_ENABLED` | Service | `false` |
| `DEFAULT_ADMIN_USERNAME / DEFAULT_ADMIN_PASSWORD` | Service | `admin / empty` |
| `MANAGER_PUBLIC_ORIGIN` | Service | `http://localhost:8080`; use port 5173 for Vite development, without a path or trailing slash |
| `SHARED_LOGIN_SECURE_COOKIE` | Service | `false`; must be `true` for HTTPS |
| `BASE_URL` | Service (system catalog) | `http://localhost:`; concatenated directly with each suffix |
| `MANAGER_UI_URL_SUFFIX` | Service (system catalog) | `8080/baosys-manager/`; use `5173/baosys-manager/` for Vite development |
| `CAT_UI_URL_SUFFIX` | Service (system catalog) | `8081/baosys-xxxx/`; an example path, since CAT is not deployed |
| `VITE_MANAGER_UI_BASE_URL` | Frontend build | Other subsystems must provide the complete Manager UI base URL; Manager derives it from the current origin and Vite base by default |
| `BAOSYS_SYSTEMS_CONFIG` | Service | `classpath:baosys-systems.yml`; can be a full external catalog file URI |
| `GATEWAY_PORT` | Web | `8080` |
| `MANAGER_SERVICE_URL` | Web | `http://127.0.0.1:9090` |
| `JWT_ISSUER` | Both | Both default to `http://localhost:9090/baosys-manager`; no local override needed; keep overrides consistent |
| `JWT_JWK_SET_URI` | Both | `/baosys-manager/oauth2/jwks`, at localhost:9090 for Service and 127.0.0.1:9090 for Web by default |

The catalog is packaged from the repository root at build time. Rebuild after changes, or override it with `BAOSYS_SYSTEMS_CONFIG=file:/config/baosys-systems.yml` and restart. Retain `MGR` and its API permissions; every entry needs `ui-base-url` and `home-path`. Each base URL concatenates `BASE_URL` with its `*_UI_URL_SUFFIX`. The MGR base URL's origin must match `MANAGER_PUBLIC_ORIGIN`; `home-path` is relative to the subsystem root. The catalog directly defines the return URL allowlist; `SHARED_LOGIN_ALLOWED_ORIGINS` is no longer used. Registering CAT does not deploy a CAT service; other subsystems must be deployed and integrated with shared login separately.

## Testing and packaging

```powershell
# Default tests exclude *IntegrationTest and do not require Docker
mvn test "-Dskip.frontend=true"
# Full backend tests: Manager database tests use an isolated Testcontainers database
mvn -Pintegration-tests verify "-Dskip.frontend=true"
# Frontend type checking and production build
npm --prefix baosys-manager-ui run build
# Login returns, default-system form, fallback page, and same-origin two-tab authentication
# Uses the local Edge browser by default; HTTP endpoints are test doubles
npm --prefix baosys-manager-ui run test:browser
# Full package including frontend; verify also copies application JARs to out
mvn clean verify
```

Artifacts:

- `baosys-manager/baosys-manager-service/target/baosys-manager.jar`
- `baosys-manager-web/target/baosys-manager-web.jar`

Deploy each application with `java -jar <application-jar-path>`. The Web JAR includes frontend resources, so Vite is not required. For local packaged execution, restore `MANAGER_PUBLIC_ORIGIN=http://localhost:8080`. For production, use the browser's actual HTTPS origin and enable Secure cookies. Both processes must use the same issuer and reachable JWKS URLs. In containers, use actual network addresses for the database, Service, and JWKS.

The first deployment runs only V1. After deployment, add V2 and subsequent migrations for schema changes; do not modify an executed V1.

## Scheduled cleanup and troubleshooting

V1 creates `cleanup_inactive_user_login_sessions()` but does not schedule it. To clean up expired sessions, schedule `SELECT manager.cleanup_inactive_user_login_sessions();` every five minutes, replacing the schema if customized. For pg_cron, see the [scheduler script](../baosys-manager/baosys-manager-service/src/main/resources/db/scheduler/pg_cron_user_login_cleanup.sql); install and enable the extension first. Scheduling uses the database scheduler's time zone. Each call handles at most 100 accounts, locks accounts first, and skips busy ones. Adjust frequency to match the backlog. Function and pg_cron job names retain compatibility, but cleanup no longer deletes sessions simply because they have not been updated for one day.

| Symptom | Check |
|---|---|
| Service fails to start | Database connection, JWK file, encoded client secret, and administrator password |
| Login or refresh returns 403 | Whether browser Origin matches MANAGER_PUBLIC_ORIGIN and the CSRF header is correct |
| API returns 401 | Issuer consistency, JWKS reachability, cookies, and token expiry |
| Signed-in user receives 403 from a management API | Whether the account has both MGR and the required API permission |
| Gateway cannot connect downstream | Whether Service is running and MANAGER_SERVICE_URL is correct |
| Integration tests cannot find Docker | Start Docker Desktop and confirm docker version can connect to the server |
| Old code still runs after shared-module edits | Run install again from the root and restart services |

Stop the local database container with `docker stop baosys-postgres`; resume it with `docker start baosys-postgres`.

Browser tests use an installed Edge browser by default. Elsewhere, set `PLAYWRIGHT_CHANNEL=chromium` and run `npx playwright install chromium` in the UI directory. Tests start an independent Vite server on local port 5178 and cover real cookies, Web Locks, cross-tab notifications, and automatic retries. They do not replace backend PostgreSQL integration tests. Production coordination depends on same-origin access and browser capabilities; without Web Locks or usable storage, coordination is limited to one page.

## Path-based deployment and subsystem integration

The packaged Manager entry point is `http://localhost:8080/baosys-manager/`. When switching from Vite to packaged execution, also restore `MANAGER_UI_URL_SUFFIX=8080/baosys-manager/`. Web adds a missing trailing slash while preserving query parameters.

Production settings: `BASE_URL=https://example.com`, `MANAGER_PUBLIC_ORIGIN=https://example.com`, `MANAGER_UI_URL_SUFFIX=/baosys-manager/`, `CAT_UI_URL_SUFFIX=/baosys-xxxx/`, and `SHARED_LOGIN_SECURE_COOKIE=true`. Prefixes and suffixes are concatenated literally. Restart Service after changes. The old `CAT_UI_BASE_URL` and `VITE_MANAGER_ORIGIN` are no longer used.

See [nginx-path-routing.conf](nginx-path-routing.conf) for an Nginx example. Frontend forwarding strips the frontend prefix; API forwarding retains the complete path. OAuth2 issuer, JWKS, and callback addresses did not change with the UI path migration; protocol endpoints keep their existing independent addresses. The example does not include TLS certificates or OAuth2 protocol forwarding; configure these for the actual deployment.

Other subsystems need their own Vite base, Router base, and static-host prefix, and must set `VITE_MANAGER_UI_BASE_URL=https://example.com/baosys-manager/` at build time. Each gateway must configure `spring.security.gateway.csrf-path=/api/baosys-xxxx/csrf` and a distinct `spring.security.gateway.csrf-cookie-name=BAOSYS-CAT-XSRF-TOKEN`. The frontend requests the same CSRF path.

The shared security module automatically permits this exact path, and the controller handles it locally at the gateway. CSRF cookies are HttpOnly with Path=/ and distinct names per gateway. Authentication cookie sharing and the Refresh Cookie path remain unchanged.

Local applications on different ports share cookies, but not localStorage, BroadcastChannel, or Web Locks; this does not provide cross-port refresh coordination. The repository has no CAT application. Two-system account switching, logout, concurrent refresh, and the production Nginx/TLS chain require acceptance testing in the deployment environment.
