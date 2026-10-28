package baosys.managerweb.security;

import baosys.common.security.jwt.JwtConstants;
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
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "baosys.browser-oauth2.enabled=true"
)
@Import(GatewaySecurityIntegrationTest.JwtTestConfiguration.class)
@ActiveProfiles("test")
class GatewaySecurityIntegrationTest {

    private static final java.util.concurrent.atomic.AtomicInteger REFRESH_COUNT =
        new java.util.concurrent.atomic.AtomicInteger();
    private static final AtomicReference<String> REVOKED_BODY = new AtomicReference<>();
    private static final AtomicReference<String> FORWARDED_URI = new AtomicReference<>();
    private static final AtomicReference<String> FORWARDED_USER = new AtomicReference<>();
    private static final AtomicReference<String> FORWARDED_AUTH = new AtomicReference<>();
    private static final DisposableServer MANAGER_SERVICE = HttpServer.create()
        .host("127.0.0.1")
        .port(0)
        .handle((request, response) -> {
            if (request.uri().equals("/oauth2/token")) {
                REFRESH_COUNT.incrementAndGet();
                return response
                    .header("Content-Type", "application/json")
                    .sendString(
                        Mono.just(
                            "{\"access_token\":\"user-token\",\"token_type\":\"Bearer\",\"expires_in\":1800,\"refresh_token\":\"rotated\",\"scope\":\"openid profile\"}"
                        )
                    );
            }
            if (request.uri().equals("/oauth2/revoke")) {
                return request
                    .receive()
                    .aggregate()
                    .asString()
                    .doOnNext(REVOKED_BODY::set)
                    .then(response.status(200).send().then());
            }
            FORWARDED_URI.set(request.uri());
            FORWARDED_USER.set(request.requestHeaders().get("X-User-Id"));
            FORWARDED_AUTH.set(request.requestHeaders().get("Authorization"));
            return response.status(204).send();
        })
        .bindNow();

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void managerServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("MANAGER_SERVICE_URL", () -> "http://127.0.0.1:" + MANAGER_SERVICE.port());
        // 覆盖完整规则列表，验证缺省、空字符串、纯空白及系统权限规则的真实配置绑定。
        String prefix = "spring.security.gateway.access-rules";
        registry.add(prefix + "[0].path", () -> "/api/baosys-manager/v1/users/me");
        registry.add(prefix + "[1].path", () -> "/api/baosys-manager/v1/systems");
        registry.add(prefix + "[2].path", () -> "/api/baosys-manager/v1/shared-empty");
        registry.add(prefix + "[2].permission", () -> "");
        registry.add(prefix + "[3].path", () -> "/api/baosys-manager/v1/shared-blank");
        registry.add(prefix + "[3].permission", () -> "   ");
        registry.add(prefix + "[4].path", () -> "/api/baosys-manager/**");
        registry.add(prefix + "[4].permission", () -> "MGR");
    }

    @AfterAll
    static void stopManagerService() {
        MANAGER_SERVICE.disposeNow();
    }

    @Test
    void shouldReturnUnauthorizedWhenTokenIsMissing() {
        webTestClient
            .get()
            .uri("/api/baosys-manager/v1/users")
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    @Test
    void shouldRewritePublicApiPathBeforeRoutingWhenTokenIsValid() {
        FORWARDED_URI.set(null);
        webTestClient
            .get()
            .uri("/api/baosys-manager/v1/users")
            .headers(headers -> headers.setBearerAuth("user-token"))
            .exchange()
            .expectStatus()
            .isNoContent();

        assertThat(FORWARDED_URI.get()).isEqualTo("/baosys-manager/v1/users");
    }

    @Test
    void shouldRejectUnsafeRequestWithoutCsrfToken() {
        webTestClient
            .delete()
            .uri("/api/baosys-manager/v1/users/12")
            .cookie("access_token", "admin-token")
            .exchange()
            .expectStatus()
            .isForbidden()
            .expectHeader()
            .doesNotExist("WWW-Authenticate");
    }

    @Test
    void shouldStripForgedIdentityAndForwardBearerToken() {
        webTestClient
            .get()
            .uri("/api/baosys-manager/v1/users")
            .header("X-User-Id", "forged-admin")
            .headers(headers -> headers.setBearerAuth("user-token"))
            .exchange()
            .expectStatus()
            .isNoContent();
        assertThat(FORWARDED_USER.get()).isNull();
        assertThat(FORWARDED_AUTH.get()).isEqualTo("Bearer user-token");
    }

    @Test
    void ignoresLegacyTokenCookies() {
        webTestClient
            .get()
            .uri("/api/baosys-manager/v1/users")
            .cookie("access_token", "user-token")
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    @Test
    void rejectsMissingManagerEntryEvenWhenApiPermissionMatches() {
        webTestClient
            .get()
            .uri("/api/baosys-manager/v1/users")
            .headers(headers -> headers.setBearerAuth("api-without-entry"))
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    @Test
    void scopeCannotReplaceSystemPermission() {
        webTestClient
            .get()
            .uri("/api/baosys-manager/v1/users")
            .headers(headers -> headers.setBearerAuth("scope-only"))
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    @Test
    void rulesWithoutPermissionStillRequireAuthentication() {
        for (String path : List.of(
            "/api/baosys-manager/v1/users/me",
            "/api/baosys-manager/v1/systems",
            "/api/baosys-manager/v1/shared-empty",
            "/api/baosys-manager/v1/shared-blank"
        )) {
            webTestClient.get().uri(path).exchange().expectStatus().isUnauthorized();
            webTestClient
                .get()
                .uri(path)
                .headers(headers -> headers.setBearerAuth("invalid"))
                .exchange()
                .expectStatus()
                .isUnauthorized();
            webTestClient
                .get()
                .uri(path)
                .headers(headers -> headers.setBearerAuth("api-without-entry"))
                .exchange()
                .expectStatus()
                .isNoContent();
            assertThat(FORWARDED_AUTH.get()).isEqualTo("Bearer api-without-entry");
            // 即使没有任何系统权限，有效登录身份也应能访问全局共享接口。
            webTestClient
                .get()
                .uri(path)
                .headers(headers -> headers.setBearerAuth("no-permissions"))
                .exchange()
                .expectStatus()
                .isNoContent();
        }
    }

    @Test
    void springCsrfTokenAllowsCookieWritesAndRejectsWrongTokens() {
        var session = browserSession("admin-token");
        var csrf = webTestClient
            .get()
            .uri("/csrf")
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .value("Cache-Control", value -> assertThat(value).contains("no-store"))
            .expectBody(CsrfResponse.class)
            .returnResult();
        var cookie = csrf.getResponseCookies().getFirst("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(csrf.getResponseBody().token()).isNotEqualTo(cookie.getValue());
        webTestClient
            .delete()
            .uri("/api/baosys-manager/v1/users/12")
            .cookie("SESSION", session)
            .cookie("XSRF-TOKEN", cookie.getValue())
            .header(csrf.getResponseBody().headerName(), csrf.getResponseBody().token())
            .exchange()
            .expectStatus()
            .isNoContent();
        webTestClient
            .delete()
            .uri("/api/baosys-manager/v1/users/12")
            .cookie("SESSION", session)
            .cookie("XSRF-TOKEN", cookie.getValue())
            .header(csrf.getResponseBody().headerName(), "wrong")
            .exchange()
            .expectStatus()
            .isForbidden()
            .expectHeader()
            .doesNotExist("WWW-Authenticate");
    }

    @Test
    void logoutRequiresCsrfEvenWithBearer() {
        webTestClient.post().uri("/logout").exchange().expectStatus().isForbidden();
        webTestClient
            .post()
            .uri("/logout")
            .headers(headers -> headers.setBearerAuth("user-token"))
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    private String browserSession(String token) {
        return webTestClient
            .get()
            .uri("/test-session/" + token)
            .exchange()
            .expectStatus()
            .isOk()
            .returnResult(Void.class)
            .getResponseCookies()
            .getFirst("SESSION")
            .getValue();
    }

    @Test
    void browserSessionUsesJwtBusinessAuthorities() {
        var session = browserSession("api-without-entry");
        webTestClient
            .get()
            .uri("/api/baosys-manager/v1/users")
            .cookie("SESSION", session)
            .exchange()
            .expectStatus()
            .isForbidden();
        webTestClient
            .get()
            .uri("/api/baosys-manager/v1/users/me")
            .cookie("SESSION", session)
            .exchange()
            .expectStatus()
            .isNoContent();
        assertThat(FORWARDED_AUTH.get()).isEqualTo("Bearer api-without-entry");
    }

    @Test
    void refreshesBrowserTokenAndRevokesOnCsrfProtectedLogout() {
        REFRESH_COUNT.set(0);
        REVOKED_BODY.set(null);
        String session = browserSession("expired");
        for (int index = 0; index < 2; index++) {
            webTestClient
                .get()
                .uri("/api/baosys-manager/v1/users")
                .cookie("SESSION", session)
                .exchange()
                .expectStatus()
                .isNoContent();
        }
        assertThat(REFRESH_COUNT.get()).isEqualTo(1);
        assertThat(FORWARDED_AUTH.get()).isEqualTo("Bearer user-token");
        var csrf = webTestClient
            .get()
            .uri("/csrf")
            .cookie("SESSION", session)
            .exchange()
            .expectBody(CsrfResponse.class)
            .returnResult();
        webTestClient
            .post()
            .uri("/logout")
            .cookie("SESSION", session)
            .cookie("XSRF-TOKEN", csrf.getResponseCookies().getFirst("XSRF-TOKEN").getValue())
            .header(csrf.getResponseBody().headerName(), csrf.getResponseBody().token())
            .exchange()
            .expectStatus()
            .isNoContent();
        assertThat(REVOKED_BODY.get()).contains("token=rotated", "token_type_hint=refresh_token");
        webTestClient
            .get()
            .uri("/api/baosys-manager/v1/users")
            .cookie("SESSION", session)
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    @Test
    void authorizationRedirectUsesStateWithoutPkce() {
        webTestClient
            .get()
            .uri("/oauth2/authorization/baosys")
            .exchange()
            .expectStatus()
            .is3xxRedirection()
            .expectHeader()
            .value("Location", value ->
                assertThat(value)
                    .contains("response_type=code", "state=")
                    .doesNotContain("code_challenge", "code_verifier")
            );
    }

    @Test
    void explicitBearerWritesDoNotRequireCsrfAndTakePriorityOverCookies() {
        webTestClient
            .delete()
            .uri("/api/baosys-manager/v1/users/12")
            .cookie("BAOSYS_ACCESS_TOKEN", "invalid")
            .headers(headers -> headers.setBearerAuth("user-token"))
            .exchange()
            .expectStatus()
            .isNoContent();
        assertThat(FORWARDED_AUTH.get()).isEqualTo("Bearer user-token");
    }

    @Test
    void sharedCookieAuthenticatesWithoutGatewaySessionAndRespectsPermissions() {
        webTestClient
            .get()
            .uri("/api/baosys-manager/v1/users")
            .cookie("BAOSYS_ACCESS_TOKEN", "user-token")
            .exchange()
            .expectStatus()
            .isNoContent();
        assertThat(FORWARDED_AUTH.get()).isEqualTo("Bearer user-token");
        webTestClient
            .get()
            .uri("/api/baosys-manager/v1/users")
            .cookie("BAOSYS_ACCESS_TOKEN", "invalid")
            .exchange()
            .expectStatus()
            .isUnauthorized();
        webTestClient
            .get()
            .uri("/api/baosys-manager/v1/users")
            .cookie("BAOSYS_ACCESS_TOKEN", "api-without-entry")
            .exchange()
            .expectStatus()
            .isForbidden();
        webTestClient
            .get()
            .uri("/api/baosys-manager/v1/users")
            .cookie("BAOSYS_ACCESS_TOKEN", "user-token")
            .headers(headers -> headers.setBearerAuth("invalid"))
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    @Test
    void sharedCookieWritesRequireCsrfAndPublicLoginIgnoresExpiredAccessCookie() {
        webTestClient
            .delete()
            .uri("/api/baosys-manager/v1/users/12")
            .cookie("BAOSYS_ACCESS_TOKEN", "user-token")
            .exchange()
            .expectStatus()
            .isForbidden();
        var csrf = webTestClient
            .get()
            .uri("/csrf")
            .exchange()
            .expectBody(CsrfResponse.class)
            .returnResult();
        webTestClient
            .delete()
            .uri("/api/baosys-manager/v1/users/12")
            .cookie("BAOSYS_ACCESS_TOKEN", "user-token")
            .cookie("XSRF-TOKEN", csrf.getResponseCookies().getFirst("XSRF-TOKEN").getValue())
            .header(csrf.getResponseBody().headerName(), csrf.getResponseBody().token())
            .exchange()
            .expectStatus()
            .isNoContent();
        for (String endpoint : List.of("login", "refresh", "logout")) {
            webTestClient
                .post()
                .uri("/api/baosys-manager/v1/browser-auth/" + endpoint)
                .headers(headers -> headers.setBearerAuth("user-token"))
                .exchange()
                .expectStatus()
                .isForbidden();
            webTestClient
                .post()
                .uri("/api/baosys-manager/v1/browser-auth/" + endpoint)
                .cookie("BAOSYS_ACCESS_TOKEN", "invalid")
                .cookie("XSRF-TOKEN", csrf.getResponseCookies().getFirst("XSRF-TOKEN").getValue())
                .header(csrf.getResponseBody().headerName(), csrf.getResponseBody().token())
                .exchange()
                .expectStatus()
                .isNoContent();
        }
    }

    @Test
    void bearerErrorsUseStandardChallenges() {
        webTestClient
            .get()
            .uri("/api/baosys-manager/v1/users")
            .headers(headers -> headers.setBearerAuth("invalid"))
            .exchange()
            .expectStatus()
            .isUnauthorized()
            .expectHeader()
            .value("WWW-Authenticate", value -> assertThat(value).contains("invalid_token"));
        webTestClient
            .get()
            .uri("/api/baosys-manager/v1/users")
            .headers(headers -> headers.setBearerAuth("api-without-entry"))
            .exchange()
            .expectStatus()
            .isForbidden()
            .expectHeader()
            .value("WWW-Authenticate", value -> assertThat(value).contains("insufficient_scope"));
    }

    record CsrfResponse(String headerName, String parameterName, String token) {}

    @org.springframework.web.bind.annotation.RestController
    static class SessionFixture {

        private final org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository registrations;
        private final org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository clients;

        SessionFixture(
            org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository registrations,
            org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository clients
        ) {
            this.registrations = registrations;
            this.clients = clients;
        }

        @org.springframework.web.bind.annotation.GetMapping("/test-session/{token}")
        Mono<Void> session(
            @org.springframework.web.bind.annotation.PathVariable String token,
            org.springframework.web.server.ServerWebExchange exchange
        ) {
            var principal = new org.springframework.security.oauth2.core.user.DefaultOAuth2User(
                List.of(
                    new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "OAUTH2_USER"
                    )
                ),
                java.util.Map.of("sub", "account"),
                "sub"
            );
            var authentication =
                new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(
                    principal,
                    principal.getAuthorities(),
                    "baosys"
                );
            return registrations.findByRegistrationId("baosys").flatMap(registration -> {
                var client = new org.springframework.security.oauth2.client.OAuth2AuthorizedClient(
                    registration,
                    "account",
                    new org.springframework.security.oauth2.core.OAuth2AccessToken(
                        org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER,
                        token,
                        Instant.now().minusSeconds(7200),
                        token.equals("expired")
                            ? Instant.now().minusSeconds(3600)
                            : Instant.now().plusSeconds(3600)
                    ),
                    new org.springframework.security.oauth2.core.OAuth2RefreshToken(
                        "refresh-token",
                        Instant.now()
                    )
                );
                return clients
                    .saveAuthorizedClient(client, authentication, exchange)
                    .then(
                        new org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository().save(
                            exchange,
                            new org.springframework.security.core.context.SecurityContextImpl(
                                authentication
                            )
                        )
                    );
            });
        }
    }

    @TestConfiguration
    @Import(SessionFixture.class)
    static class JwtTestConfiguration {

        @Bean
        org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository registrations() {
            var registration =
                org.springframework.security.oauth2.client.registration.ClientRegistration.withRegistrationId(
                    "baosys"
                )
                    .clientId(JwtConstants.BROWSER_CLIENT_ID)
                    .clientSecret("test-secret")
                    .authorizationGrantType(
                        org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE
                    )
                    .redirectUri("http://localhost/login/oauth2/code/baosys")
                    .scope("openid", "profile")
                    .authorizationUri("http://localhost/authorize")
                    .tokenUri("http://127.0.0.1:" + MANAGER_SERVICE.port() + "/oauth2/token")
                    .providerConfigurationMetadata(
                        java.util.Map.of(
                            "revocation_endpoint",
                            "http://127.0.0.1:" + MANAGER_SERVICE.port() + "/oauth2/revoke"
                        )
                    )
                    .jwkSetUri("http://localhost/jwks")
                    .userNameAttributeName("sub")
                    .build();
            return new org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository(
                registration
            );
        }

        @Bean
        @Primary
        ReactiveJwtDecoder testReactiveJwtDecoder() {
            return token ->
                token.equals("invalid")
                    ? Mono.error(
                          new org.springframework.security.oauth2.jwt.BadJwtException(
                              "Invalid token"
                          )
                      )
                    : Mono.just(jwt(token));
        }

        private static Jwt jwt(String token) {
            Instant now = Instant.now();
            return Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject("1")
                .issuer("auth-server")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("username", "test-user")
                // 模拟旧令牌只有 scope 声明，但缺少系统入口权限。
                .claims(claims -> {
                    if (token.equals("scope-only")) {
                        claims.put("scope", "MGR");
                    }
                })
                .claim(
                    JwtConstants.PERMISSIONS_CLAIM,
                    token.equals("no-permissions") || token.equals("scope-only")
                        ? List.of()
                        : token.equals("api-without-entry")
                          ? List.of("MGR:user:list")
                          : List.of("MGR")
                )
                .build();
        }
    }
}
