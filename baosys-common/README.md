# baosys-common

`baosys-common` contains the shared OAuth2/JWT/Spring Security platform foundation used by
the authentication center, API gateway and protected backend services.

## Package layout

| Package | Responsibility |
| --- | --- |
| `baosys.common.api` | Shared API response shape for security errors |
| `baosys.common.security.password` | Password hashing abstraction and BCrypt implementation |
| `baosys.common.security.jwt` | RSA JWT access-token issuing |
| `baosys.common.security.gateway` | Cookie token to Authorization header conversion and gateway security |
| `baosys.common.security.resource` | JWT `permissions` claim to `GrantedAuthority` conversion |
| `baosys.common.security.annotation` | `@EnableAuthenticationCheck` and `@EnableUltraViresCheck` |
| `baosys.common.autoconfigure` | Opt-in Spring Boot auto-configuration |

## Configuration

All infrastructure beans are opt-in. JSON support is registered automatically and reuses the
application's `ObjectMapper`; a default mapper is supplied only when the application has none.

```yaml
common:
  password:
    enabled: true
    strength: 10
security:
  jwt:
    issuer: auth-server
    access-token-expire-seconds: 7200
    public-key: ${JWT_PUBLIC_KEY}
    private-key: ${JWT_PRIVATE_KEY}
  access-token-cookie-name: access_token
  permissions-claim-name: permissions
  authority-prefix: ""
  gateway:
    csrf:
      enabled: false
      cookie-name: BAOSYS_CSRF_TOKEN
    permit-all-paths:
      - /api/v1/auth/login
      - /api/v1/auth/refresh
      - /actuator/health/**
  resource:
    permit-all-paths:
      - /actuator/health/**
```

Only the authentication center should configure `private-key`. Gateways and resource services only
need `public-key`.

JWT `sub` is the stable user id used as the Spring Security principal. The `username` claim is issued
only as log/display context and should not drive authorization or business decisions.

Gateway CSRF validation is disabled by default for trusted internal deployments. Set
`security.gateway.csrf.enabled=true` when browser-facing deployments need double-submit CSRF
protection for cookie-based access tokens.
