# Maven dependency boundaries

[简体中文](dependency-boundaries.md) | **English**

This document follows the current module POMs. See the [root POM](../../pom.xml) for aggregation.

## 1. Modules and dependency direction

The root project uses Java 17, Spring Boot 3.5.7, and Spring Cloud 2025.0.1. The root POM aggregates modules and manages versions; it does not inject dependencies into every module through its own `dependencies` section. `baosys-manager` is an aggregator POM; both its API and Service modules use the root project directly as their Maven parent.

The table omits the `baosys-` prefix:

| Module | Current direct dependencies and responsibility |
|---|---|
| common | common-api, SLF4J, Jackson, base Spring beans/core/context/boot and spring-security-crypto; error codes, trace IDs, managed executors, the unified `PasswordEncoder` auto-configuration and shared resources; no Servlet/MVC/WebFlux/Reactor/Security-core dependency |
| common-api | No third-party dependencies; provides `ApiResponse` and `PageResponse` |
| common-security-core | Spring Boot configuration properties and Spring Security JWT/authority fundamentals; no common-api or Web starter dependency |
| common-resource | security-core, common, common-api, MVC and Servlet security; Servlet error responses, tracing and MVC async configuration; Servlet API is provided |
| common-gateway | security-core, common, common-api, Gateway/WebFlux/Reactor and OAuth2; reactive errors, tracing, context-propagation and SPA hosting |
| manager-api | common-api; spring-web and jakarta.validation-api are optional |
| manager-service | manager-api, common-api, core, resource; selects Servlet, authorization server, resource server, database, and Session JDBC runtime support |
| manager-web | common, gateway; selects Gateway, OAuth2 Client/Resource Server, and Actuator runtime support; LoadBalancer and Caffeine are runtime dependencies |

`baosys-common` provides shared errors, tracing and managed execution. Manager Service and Manager Web depend on it directly. There is no `baosys-common-security-issuer` module. JWT issuance and authorization server assembly live in Manager Service's `infra/security`.

`baosys-manager-ui` is not part of the Maven reactor. Manager Web's `frontend-maven-plugin` installs Node/npm, runs `npm ci --no-audit --no-fund` and `npm run build`, and the resources plugin copies `dist` into the JAR's static resource directory.

## 2. Declaration rules

- Declare the library owning each type directly referenced by the module's source, including tests, in that module's POM.
- Shared libraries declare the framework libraries they use; executable applications select starters and runtime support. Do not rely on another business implementation module to bring dependencies in transitively.
- Use `test` for testing tools, `runtime` for runtime features not referenced at compile time, and `provided` for host-provided Servlet APIs.
- Do not repeat versions managed by the Boot/Cloud BOM. Manage extra versions, such as Nimbus and MyBatis, in the root POM.
- API JARs must not depend on Service implementations; Common modules must not depend on business modules.
- Let Maven resolve ordinary transitive dependencies. Do not apply exclusions or optional flags indiscriminately.

Manager Service's PostgreSQL driver and Flyway PostgreSQL dialect are runtime dependencies. Manager Web retains the OAuth2 Client starter, but its default configuration contains no client registration. Internal shared-cookie login does not depend on an OAuth2 Client WebSession.

## 3. Verification

Base common has no Web auto-configuration imports. MVC auto-configuration lives in common-resource;
Reactive and SPA auto-configuration live in common-gateway. Java packages and properties are unchanged;
consumers must use the new Maven coordinates. Neither adapter depends on the other.
Only gateway brings context-propagation; non-web applications can use base common.

DependencyIsolationTest checks that foreign framework and adapter classes are absent and verifies auto-configuration discovery.
Use a clean build so stale target/classes cannot mask migration errors:

```powershell
mvn -B clean verify -Pintegration-tests '-Dskip.frontend=true' '-Dmaven.antrun.skip=true'
./scripts/check-dependency-isolation.ps1
```

The script checks actual runtime dependency trees for common, both adapters, the Manager gateway and MVC service.
common-api must have no runtime dependencies. MVC exclude WebFlux, Reactor and context-propagation;
gateways exclude MVC, Servlet API and the Tomcat container. The validator's tomcat-embed-el is not forbidden.

Run from the repository root:

```powershell
mvn -B clean verify dependency:analyze -Pintegration-tests '-Dskip.frontend=true'
```

This is the verification command for dependency changes, not a claim that it passed when this document was updated. Database integration tests need a working Docker/Testcontainers environment.

`dependency:analyze` uses bytecode and cannot alone determine whether a dependency should be removed:

- Starters, JUnit engines, database drivers, and Flyway dialects may be used through auto-configuration, reflection, or SPI.
- A type referenced directly only by tests may still belong to a runtime dependency required to start the application.
- Dependency changes must also pass compilation, unit tests, integration tests, and application packaging.

`skip.frontend=true` skips frontend plugin execution. A complete release build should generate up-to-date frontend static resources.

Manager UI depend on the local npm package `baosys-common-ui`. SPA hosting lives in `baosys-common-gateway`; Logback, operations defaults and storage JSON live in `baosys-common`; the root POM manages frontend builds. See [shared infrastructure](../shared-infrastructure.en.md).
