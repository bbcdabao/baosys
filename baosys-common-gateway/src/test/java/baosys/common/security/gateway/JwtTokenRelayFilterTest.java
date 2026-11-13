package baosys.common.security.gateway;

import baosys.common.security.config.SecurityProperties;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenRelayFilterTest {

    @Test
    void defaultsRemoveIdentityHeadersWithoutAuthentication() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
            .header("uSeRiD", "forged", "another")
            .header("TENANTID", "forged")
            .header("X-USER", "forged")
            .header("x-tenant", "forged")
            .header("X-uSeR-Role", "admin")
            .header("X-TENANT-Id", "forged")
            .header("X-Username", "keep")
            .header("Authorization", "Bearer original")
            .header("Cookie", "session=original"));

        var headers = filter(new SecurityProperties(), exchange);

        assertThat(headers.keySet()).containsExactlyInAnyOrder("X-Username", "Authorization", "Cookie");
        assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer original");
        assertThat(exchange.getRequest().getHeaders().get("uSeRiD")).containsExactly("forged", "another");
    }

    @Test
    void boundCustomRulesReplaceDefaultsAndRespectMatchBoundaries() {
        var source = new MapConfigurationPropertySource(Map.of(
            "spring.security.gateway.identity-header-names[0]", "X-Account",
            "spring.security.gateway.identity-header-prefixes[0]", "X-Internal-"
        ));
        var properties = new Binder(source)
            .bind("spring.security", Bindable.of(SecurityProperties.class)).get();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
            .header("x-ACCOUNT", "forged")
            .header("x-INTERNAL-role", "admin")
            .header("X-Account-Other", "keep")
            .header("X-Internal", "keep")
            .header("UserId", "keep"));

        assertThat(filter(properties, exchange).keySet())
            .containsExactlyInAnyOrder("X-Account-Other", "X-Internal", "UserId");
    }

    @Test
    void emptyRulesDisableCleaning() {
        var properties = new SecurityProperties();
        properties.getGateway().setIdentityHeaderNames(List.of());
        properties.getGateway().setIdentityHeaderPrefixes(List.of());
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
            .header("UserId", "keep").header("X-User-Role", "keep"));

        assertThat(filter(properties, exchange)).isEqualTo(exchange.getRequest().getHeaders());
    }

    @Test
    void ignoresBlankRulesAndRelaysAuthenticatedJwtAfterCleaning() {
        var properties = new SecurityProperties();
        properties.getGateway().setIdentityHeaderNames(Arrays.asList(null, " ", " X-Account "));
        properties.getGateway().setIdentityHeaderPrefixes(Arrays.asList(null, " "));
        var jwt = Jwt.withTokenValue("trusted-token").header("alg", "RS256").subject("user").build();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
            .header("X-Account", "forged")
            .header("Authorization", "Bearer forged")
            .header("X-Trace", "keep"))
            .mutate().principal(Mono.just(new JwtAuthenticationToken(jwt))).build();

        var headers = filter(properties, exchange);

        assertThat(headers.keySet()).containsExactlyInAnyOrder("Authorization", "X-Trace");
        assertThat(headers.getFirst("Authorization")).isEqualTo("Bearer trusted-token");
    }

    private HttpHeaders filter(SecurityProperties properties, ServerWebExchange exchange) {
        var forwarded = new AtomicReference<ServerWebExchange>();
        new JwtTokenRelayFilter(properties).filter(exchange, next -> {
            assertThat(forwarded.getAndSet(next)).isNull();
            return Mono.empty();
        }).block();
        return forwarded.get().getRequest().getHeaders();
    }
}
