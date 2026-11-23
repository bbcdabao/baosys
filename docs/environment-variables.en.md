# Environment Variable Reference

[简体中文](environment-variables.md) | **English**

This document centrally describes the environment variables currently used by BaoSys. They fall into three categories:

- Java Service and Web startup variables, read by Spring `application.yml` or shared configuration.
- System-catalog variables used to construct subsystem UI addresses, loaded when the Service starts.
- Node/Vite and Playwright build or test variables, read only during frontend development, builds, or tests.

Environment variables may replace local defaults, but they do not change the following relationships:

- `JWT_ISSUER` must be identical in Manager Service and Manager Web.
- `MANAGER_PUBLIC_ORIGIN` must match the origin of the MGR entry's `ui-base-url`.
- `JWT_JWK_SET_LOCATION` and `JWT_JWK_SET_URI` serve different roles: the former loads private JWK material in the authorization server, while the latter lets verification services retrieve public JWKS.
- Restart Manager Service after changing the system catalog. Rebuild the frontend after changing frontend build variables.

## 1. Manager Service

Configuration: [application.yml](../baosys-manager/baosys-manager-service/src/main/resources/application.yml).

| Variable | Default | Description |
|---|---|---|
| `MANAGER_PORT` | `9090` | Manager Service HTTP port |
| `MANAGER_CONTEXT_PATH` | `/baosys-manager` | Service context path |
| `DB_URL` | `jdbc:postgresql://localhost:5432/app_db` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `postgres` | Database username |
| `DB_PASSWORD` | `postgres123456` | Database password; local-development default only |
| `MANAGER_DB_SCHEMA` | `manager` | Hikari and Flyway database schema |
| `JWT_ISSUER` | `http://localhost:9090/baosys-manager` | JWT issuer; must be aligned across Service and Web |
| `JWT_JWK_SET_LOCATION` | None | Private JWKSet resource URI; required for normal startup |
| `JWT_JWK_SET_URI` | `http://localhost:9090/baosys-manager/oauth2/jwks` | Public JWKS URL used by Manager's resource server |
| `OAUTH2_CLIENT_SECRET_HASH` | `{noop}local-dev-client-secret` | Encoded secret for the built-in `baosys-web` OAuth client |
| `OAUTH2_REDIRECT_URI` | `http://localhost:8080/login/oauth2/code/baosys` | OAuth client callback URI |
| `DEFAULT_ADMIN_ENABLED` | `false` | Initialize a default administrator on startup |
| `DEFAULT_ADMIN_USERNAME` | `admin` | Default administrator username |
| `DEFAULT_ADMIN_PASSWORD` | Empty | Default administrator password; at least 12 characters when enabled |
| `MANAGER_PUBLIC_ORIGIN` | `http://localhost:8080` | Browser-visible Manager origin without path, trailing slash, query, or fragment |
| `BAOSYS_SYSTEMS_CONFIG` | `classpath:baosys-systems.yml` | System-catalog URI; may point to an external file |

At minimum, production deployments must override database credentials, signing JWK material, OAuth client secret, and public origin. Never commit or expose real private keys or production passwords.

## 2. Manager Web

Configuration: [application.yml](../baosys-manager-web/src/main/resources/application.yml).

| Variable | Default | Description |
|---|---|---|
| `GATEWAY_PORT` | `8080` | Manager Web gateway port |
| `CSRF_ENABLED` | `false` | Enable gateway browser CSRF validation |
| `MANAGER_SERVICE_URL` | `http://localhost:9090` | Manager Service upstream URL |
| `JWT_ISSUER` | `http://localhost:9090/baosys-manager` | Gateway JWT issuer |
| `JWT_JWK_SET_URI` | `http://localhost:9090/baosys-manager/oauth2/jwks` | Public JWKS URL used by the gateway |

Fixed gateway names:

- CSRF path: `/api/baosys-manager/csrf`
- CSRF cookie: `BAOSYS-MGR-XSRF-TOKEN`
- Access Token cookie: `BAOSYS_ACCESS_TOKEN`
- Refresh Token cookie: `BAOSYS_REFRESH_TOKEN`

For production HTTPS, configure CSRF, proxy headers, TLS, cookies, and external origins consistently. The current cookie implementation has no environment-variable-controlled `Secure` attribute.

## 3. System catalog

Catalog: [baosys-systems.yml](../baosys-systems.yml).

| Variable | Default | Description |
|---|---|---|
| `BASE_URL` | `http://localhost:` | Protocol and host prefix for subsystem UI URLs |
| `MANAGER_UI_URL_SUFFIX` | `8080/baosys-manager/` | MGR UI suffix; use `5173/baosys-manager/` for Vite development |

Future subsystem suffixes may follow the same pattern, for example `CAT_UI_URL_SUFFIX=/baosys-xxxx/`, but only when a corresponding catalog entry actually reads that placeholder. The current catalog registers MGR only, so no CAT variable is active today.

Every generated UI URL must satisfy:

- MGR shares the host, protocol, and port with `MANAGER_PUBLIC_ORIGIN`.
- Subsystem UI ranges do not overlap.
- `home-path` starts with `/` and belongs inside the subsystem UI range.
- Restart the Service after catalog changes.

## 4. Frontend build and tests

Configuration: [.env.production](../baosys-manager-ui/.env.production), [vite.config.mts](../baosys-manager-ui/vite.config.mts), [playwright.config.js](../baosys-manager-ui/playwright.config.js).

| Variable | Default or location | Description |
|---|---|---|
| `VITE_MANAGER_UI_BASE_URL` | `http://localhost:8080/baosys-manager/` in `.env.production` | Complete Manager UI base URL |
| `PLAYWRIGHT_CHANNEL` | `msedge` | Playwright browser channel |

Vite exposes only `VITE_` variables to frontend code. Never place private keys, database passwords, or JWT secrets in these variables.

## 5. Logging, shutdown, and optional Nacos

Shared baseline: [operations.yml](../baosys-common/src/main/resources/baosys/operations.yml).

| Variable | Default | Description |
|---|---|---|
| `SHUTDOWN_TIMEOUT` | `20s` | Wait time for each Spring graceful-shutdown phase |
| `LOGGING_FILE_PATH` or `LOG_PATH` | Logging configuration default | Log-file directory |
| `LOGGING_FILE_NAME` | Application-specific default | Complete log-file path |
| `LOGGING_LEVEL_ROOT` | `INFO` | Root log level |
| `LOGGING_LOGBACK_ROLLINGPOLICY_MAX_FILE_SIZE` | `10MB` | Per-log rollover size |
| `LOGGING_LOGBACK_ROLLINGPOLICY_MAX_HISTORY` | `7` | Retention period |
| `LOGGING_LOGBACK_ROLLINGPOLICY_TOTAL_SIZE_CAP` | `1GB` | Total archive size limit |

Nacos variables are currently commented examples. Enabling discovery requires Maven dependencies and application registration to be enabled consistently:

| Variable | Purpose |
|---|---|
| `NACOS_SERVER_ADDR` | Nacos server address |
| `NACOS_NAMESPACE` | Namespace |
| `NACOS_GROUP` | Group |
| `NACOS_USERNAME` | Username |
| `NACOS_PASSWORD` | Password |

## 6. Variables set by local startup scripts

The startup scripts are local-development helpers, not production deployment templates:

| Variable | Script behavior |
|---|---|
| `JWT_JWK_SET_LOCATION` | Points to `out/signing-jwks.json` |
| `DEFAULT_ADMIN_ENABLED` | Set to `true` |
| `DEFAULT_ADMIN_USERNAME` | Set to `admin` |
| `DEFAULT_ADMIN_PASSWORD` | Set to `LocalAdmin123!` |

The generated key lives in `out/signing-jwks.json`. Maven clean or deleting `out` removes it or causes the next start to generate a new key. Production deployments must not use fixed script credentials or example keys.

## 7. Recommended startup checks

For local startup, at least confirm:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
JWT_JWK_SET_LOCATION
MANAGER_PUBLIC_ORIGIN
MANAGER_UI_URL_SUFFIX
```

For production startup, at least confirm:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
MANAGER_DB_SCHEMA
JWT_ISSUER
JWT_JWK_SET_LOCATION
JWT_JWK_SET_URI
OAUTH2_CLIENT_SECRET_HASH
DEFAULT_ADMIN_PASSWORD
MANAGER_PUBLIC_ORIGIN
MANAGER_SERVICE_URL
MANAGER_UI_URL_SUFFIX
CSRF_ENABLED
```

Behind an HTTPS reverse proxy, verify the external origin, catalog, OAuth redirect URI, issuer, JWKS reachability, and forwarded headers together. Inconsistent values commonly produce login 403s, refresh 401s, missing shared cookies, or signature failures.
