package baosys.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ServletApiSecurityHandlerTest {
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

    private BearerTokenAuthentication principal() {
        List<org.springframework.security.core.GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("CAT"));
        return new BearerTokenAuthentication(new DefaultOAuth2AuthenticatedPrincipal(Map.of("sub", "user"), authorities),
            new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token", Instant.now(), Instant.now().plusSeconds(60)), authorities);
    }

    @Test
    void servletPreservesBearerChallengeAndUniformBody() throws Exception {
        var handler = new ServletApiSecurityHandler(mapper);
        var request = new MockHttpServletRequest();
        request.addHeader(TraceIds.HEADER_NAME, "security-trace");
        var unauthorized = new MockHttpServletResponse();
        handler.commence(request, unauthorized, new InvalidBearerTokenException("invalid token"));
        assertThat(unauthorized.getHeader("WWW-Authenticate")).contains("invalid_token");
        assertThat(unauthorized.getStatus()).isEqualTo(401);
        assertBody(unauthorized.getContentAsString(), "UNAUTHORIZED", "security-trace");
        var forbidden = new MockHttpServletResponse();
        try {
            SecurityContextHolder.getContext().setAuthentication(principal());
            request.setUserPrincipal(principal());
            handler.handle(request, forbidden, new AccessDeniedException("secret"));
        } finally { SecurityContextHolder.clearContext(); }
        assertThat(forbidden.getHeader("WWW-Authenticate")).contains("insufficient_scope");
        assertThat(forbidden.getStatus()).isEqualTo(403);
        assertBody(forbidden.getContentAsString(), "FORBIDDEN", "security-trace");
    }

    private void assertBody(String body, String code, String trace) throws Exception {
        var json = mapper.readTree(body);
        assertThat(json.get("code").asText()).isEqualTo(code);
        assertThat(json.get("traceId").asText()).isEqualTo(trace);
        assertThat(json.hasNonNull("timestamp")).isTrue();
        assertThat(body).doesNotContain("secret");
    }
}
