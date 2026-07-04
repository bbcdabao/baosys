package szs.baosys.managerweb.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.core.publisher.Mono;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "szs.security.gateway.csrf.enabled=true")
@Import(GatewaySecurityIntegrationTest.JwtTestConfiguration.class)
@ActiveProfiles("test")
class GatewaySecurityIntegrationTest {

    private static final AtomicReference<String> FORWARDED_URI = new AtomicReference<>();
    private static final DisposableServer MANAGER_SERVICE = HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .handle((request, response) -> {
                FORWARDED_URI.set(request.uri());
                return response.status(204).send();
            })
            .bindNow();

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void managerServiceProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "MANAGER_SERVICE_URL",
                () -> "http://127.0.0.1:" + MANAGER_SERVICE.port());
    }

    @AfterAll
    static void stopManagerService() {
        MANAGER_SERVICE.disposeNow();
    }

    @Test
    void shouldReturnUnauthorizedWhenTokenIsMissing() {
        webTestClient.get()
                .uri("/api/baosys-manager/v1/users")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTH_401_001");
    }

    @Test
    void shouldRewritePublicApiPathBeforeRoutingWhenTokenIsValid() {
        FORWARDED_URI.set(null);
        webTestClient.get()
                .uri("/api/baosys-manager/v1/users")
                .headers(headers -> headers.setBearerAuth("user-token"))
                .exchange()
                .expectStatus().isNoContent();

        assertThat(FORWARDED_URI.get()).isEqualTo("/baosys-manager/v1/users");
    }

    @Test
    void shouldRejectUnsafeRequestWithoutCsrfToken() {
        webTestClient.delete()
                .uri("/api/baosys-manager/v1/users/12")
                .cookie("access_token", "admin-token")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTH_403_002");
    }

    @TestConfiguration
    static class JwtTestConfiguration {

        @Bean
        @Primary
        ReactiveJwtDecoder testReactiveJwtDecoder() {
            return token -> Mono.just(jwt(token));
        }

        private static Jwt jwt(String token) {
            Instant now = Instant.now();
            return Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .subject("1")
                    .issuer("szs-auth-server")
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(300))
                    .claim("username", "test-user")
                    .claim("permissions", List.of("user:list"))
                    .build();
        }
    }
}
