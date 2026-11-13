package baosys.managerweb.security;

import static org.assertj.core.api.Assertions.assertThat;

import baosys.common.security.config.SecurityProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GatewayCsrfDisabledIntegrationTest.Tokens.class)
class GatewayCsrfDisabledIntegrationTest {
    static final DisposableServer upstream = HttpServer.create().host("127.0.0.1").port(0)
        .handle((request, response) -> response.status(204).send()).bindNow();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("MANAGER_SERVICE_URL", () -> "http://127.0.0.1:" + upstream.port());
    }

    @AfterAll
    static void stop() { upstream.disposeNow(); }

    @Autowired WebTestClient client;
    @Autowired SecurityProperties properties;

    @Test
    void disabledByDefaultAndEndpointDoesNotIssueTokenCookie() {
        assertThat(properties.getGateway().isCsrfEnabled()).isFalse();
        client.get().uri("/api/baosys-manager/csrf").exchange().expectStatus().isOk()
            .expectCookie().doesNotExist("BAOSYS-MGR-XSRF-TOKEN")
            .expectBody().jsonPath("$.enabled").isEqualTo(false).jsonPath("$.token").doesNotExist();
    }

    @Test
    void publicAuthenticationWritesDoNotNeedCsrf() {
        for (String endpoint : List.of("login", "refresh", "logout")) {
            client.post().uri("/api/baosys-manager/v1/browser-auth/" + endpoint)
                .exchange().expectStatus().isNoContent();
        }
    }

    @Test
    void cookieWritesStillRequireAuthenticationAndPermission() {
        String path = "/api/baosys-manager/v1/users/12";
        client.delete().uri(path).exchange().expectStatus().isUnauthorized();
        client.delete().uri(path).cookie("BAOSYS_ACCESS_TOKEN", "CAT")
            .exchange().expectStatus().isForbidden();
        client.delete().uri(path).cookie("BAOSYS_ACCESS_TOKEN", "MGR")
            .exchange().expectStatus().isNoContent();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Tokens {
        @Bean
        ReactiveJwtDecoder jwtDecoder() {
            return token -> Mono.just(Jwt.withTokenValue(token).header("alg", "RS256")
                .subject("1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("permissions", List.of(token)).build());
        }
    }
}
