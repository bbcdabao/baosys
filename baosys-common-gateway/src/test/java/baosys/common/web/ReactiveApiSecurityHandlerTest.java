package baosys.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.time.Instant;
import java.util.*;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class ReactiveApiSecurityHandlerTest {
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

    private BearerTokenAuthentication principal() {
        List<org.springframework.security.core.GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("CAT"));
        return new BearerTokenAuthentication(new DefaultOAuth2AuthenticatedPrincipal(Map.of("sub", "user"), authorities),
            new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token", Instant.now(), Instant.now().plusSeconds(60)), authorities);
    }

    @Test
    void reactivePreservesBearerChallengeAndWritesBodyBeforeCommit() throws Exception {
        var handler = new ReactiveApiSecurityHandler(mapper);
        var unauthorized = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test").header(TraceIds.HEADER_NAME, "security-trace"));
        handler.commence(unauthorized, new InvalidBearerTokenException("invalid token")).block();
        assertThat(unauthorized.getResponse().getStatusCode().value()).isEqualTo(401);
        assertThat(unauthorized.getResponse().getHeaders().getFirst("WWW-Authenticate")).contains("invalid_token");
        assertBody(unauthorized.getResponse().getBodyAsString().block(), "UNAUTHORIZED", "security-trace");
        var forbidden = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test").header(TraceIds.HEADER_NAME, "security-trace"));
        handler.handle(forbidden.mutate().principal(Mono.just(principal())).build(), new AccessDeniedException("secret")).block();
        assertThat(forbidden.getResponse().getStatusCode().value()).isEqualTo(403);
        assertThat(forbidden.getResponse().getHeaders().getFirst("WWW-Authenticate")).contains("insufficient_scope");
        assertBody(forbidden.getResponse().getBodyAsString().block(), "FORBIDDEN", "security-trace");
    }

    private void assertBody(String body, String code, String trace) throws Exception {
        var json = mapper.readTree(body);
        assertThat(json.get("code").asText()).isEqualTo(code);
        assertThat(json.get("traceId").asText()).isEqualTo(trace);
        assertThat(json.hasNonNull("timestamp")).isTrue();
        assertThat(body).doesNotContain("secret");
    }
}
