package baosys.common.security.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import baosys.common.security.annotation.EnableAuthenticationCheck;
import baosys.common.security.jwt.JwtConstants;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

class GatewaySecurityConfigTest {

    private final ReactiveWebApplicationContextRunner runner =
        new ReactiveWebApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    WebFluxAutoConfiguration.class,
                    HttpHandlerAutoConfiguration.class,
                    ReactiveSecurityAutoConfiguration.class
                )
            )
            .withUserConfiguration(GatewayApp.class);

    @Test
    void nonPublicActuatorEndpointsRequireAuthenticationWithoutSystemPermission() {
        runner
            .withPropertyValues(
                "spring.security.gateway.permit-all-paths[0]=/actuator/health/**",
                "spring.security.gateway.permit-all-paths[1]=/actuator/info"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                var client = WebTestClient.bindToApplicationContext(context).build();

                client.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();
                client.get().uri("/actuator/info").exchange().expectStatus().isOk();

                for (String path : List.of("/actuator/metrics", "/actuator/gateway/routes")) {
                    client
                        .get()
                        .uri(path)
                        .exchange()
                        .expectStatus()
                        .isUnauthorized()
                        .expectHeader()
                        .exists("WWW-Authenticate")
                        .expectBody()
                        .jsonPath("$.code")
                        .isEqualTo("UNAUTHORIZED");

                    client
                        .get()
                        .uri(path)
                        .headers(headers -> headers.setBearerAuth("invalid"))
                        .exchange()
                        .expectStatus()
                        .isUnauthorized()
                        .expectHeader()
                        .value("WWW-Authenticate", value -> assertThat(value).contains("invalid_token"));

                    client
                        .get()
                        .uri(path)
                        .headers(headers -> headers.setBearerAuth("no-permissions"))
                        .exchange()
                        .expectStatus()
                        .isOk();
                }
            });
    }

    @Test
    void explicitActuatorAccessRuleOverridesDefaultAuthenticatedRule() {
        runner
            .withPropertyValues(
                "spring.security.gateway.access-rules[0].path=/actuator/gateway/**",
                "spring.security.gateway.access-rules[0].permission=OPS"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                var client = WebTestClient.bindToApplicationContext(context).build();

                client
                    .get()
                    .uri("/actuator/gateway/routes")
                    .headers(headers -> headers.setBearerAuth("no-permissions"))
                    .exchange()
                    .expectStatus()
                    .isForbidden();

                client
                    .get()
                    .uri("/actuator/gateway/routes")
                    .headers(headers -> headers.setBearerAuth("ops-user"))
                    .exchange()
                    .expectStatus()
                    .isOk();

                client
                    .get()
                    .uri("/api/unconfigured")
                    .headers(headers -> headers.setBearerAuth("ops-user"))
                    .exchange()
                    .expectStatus()
                    .isForbidden();
            });
    }

    @Test
    void trimsConfiguredRulePermissionAndSetsSameSiteLaxOnCsrfCookie() {
        runner
            .withPropertyValues(
                "spring.security.gateway.csrf-enabled=true",
                "spring.security.gateway.csrf-path=/api/test-gw/csrf",
                "spring.security.gateway.csrf-cookie-name=TEST-XSRF-TOKEN",
                "spring.security.gateway.access-rules[0].path= /actuator/gateway/** ",
                "spring.security.gateway.access-rules[0].permission= OPS "
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                var client = WebTestClient.bindToApplicationContext(context).build();

                client
                    .get()
                    .uri("/actuator/gateway/routes")
                    .headers(headers -> headers.setBearerAuth("ops-user"))
                    .exchange()
                    .expectStatus()
                    .isOk();

                var csrfCookie = client
                    .get()
                    .uri("/api/test-gw/csrf")
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .returnResult(String.class)
                    .getResponseCookies()
                    .getFirst("TEST-XSRF-TOKEN");
                assertThat(csrfCookie).isNotNull();
                assertThat(csrfCookie.isHttpOnly()).isTrue();
                assertThat(csrfCookie.getSameSite()).isEqualTo("Lax");
            });
    }

    @Test
    void rejectsBlankAccessRulePathAndBlankPermitAllEntryAtStartup() {
        runner
            .withPropertyValues("spring.security.gateway.access-rules[0].path=   ")
            .run(context -> assertThat(context).hasFailed());

        runner
            .withPropertyValues("spring.security.gateway.permit-all-paths[0]=   ")
            .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAuthenticationCheck
    static class GatewayApp {

        @Bean
        ReactiveJwtDecoder jwtDecoder() {
            return token -> {
                if ("invalid".equals(token)) {
                    return Mono.error(new BadJwtException("Invalid token"));
                }
                Instant now = Instant.now();
                List<String> permissions = "ops-user".equals(token) ? List.of("OPS") : List.of();
                return Mono.just(
                    Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .subject("1")
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(300))
                        .claim(JwtConstants.PERMISSIONS_CLAIM, permissions)
                        .build()
                );
            };
        }

        @Bean
        StubEndpoints stubEndpoints() {
            return new StubEndpoints();
        }
    }

    @RestController
    static class StubEndpoints {

        @GetMapping({
            "/actuator/health/readiness",
            "/actuator/info",
            "/actuator/metrics",
            "/actuator/gateway/routes",
            "/api/unconfigured"
        })
        Mono<String> ok() {
            return Mono.just("ok");
        }
    }
}
