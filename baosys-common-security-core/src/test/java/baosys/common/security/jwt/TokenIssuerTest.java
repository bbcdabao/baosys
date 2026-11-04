package baosys.common.security.jwt;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class TokenIssuerTest {
    private final TokenIssuer issuer = new TokenIssuer(parameters -> new Jwt(
        "encoded", parameters.getClaims().getIssuedAt(), parameters.getClaims().getExpiresAt(),
        parameters.getJwsHeader().getHeaders(), parameters.getClaims().getClaims()
    ));

    @Test
    void accessTokenUsesBusinessContractAndCallerLifetime() {
        var now = Instant.now();
        var subject = new TokenIssuer.AccessTokenSubject("100", "client", List.of("MGR:z", "MGR", "MGR"));
        var token = issuer.issueAccessToken("https://issuer.example", subject, now, now.plusSeconds(60));
        assertThat(token.getHeaders()).containsEntry("typ", "at+jwt")
            .containsEntry("alg", org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256);
        assertThat(token.getClaims()).doesNotContainKey("scope")
            .containsEntry("sub", "100").containsEntry("client_id", "client")
            .containsEntry("permissions", List.of("MGR", "MGR:z"));
        assertThat(token.getIssuer().toString()).isEqualTo("https://issuer.example");
        assertThat(token.getAudience()).containsExactly(JwtConstants.ACCESS_TOKEN_AUDIENCE);
        assertThat(token.getExpiresAt()).isEqualTo(now.plusSeconds(60));
        assertThat(issuer.issueAccessToken("https://issuer.example", subject, now, now.plusSeconds(60)).getId())
            .isNotEqualTo(token.getId());
    }

    @Test
    void refreshTokensAreRandomOpaqueValuesWithExplicitExpiry() {
        var now = Instant.now();
        var first = issuer.issueRefreshToken(now, now.plusSeconds(600));
        var second = issuer.issueRefreshToken(now, now.plusSeconds(600));
        assertThat(second.getTokenValue()).isNotEqualTo(first.getTokenValue());
        assertThat(Base64.getUrlDecoder().decode(first.getTokenValue())).hasSize(96);
        assertThat(first.getIssuedAt()).isEqualTo(now);
        assertThat(first.getExpiresAt()).isEqualTo(now.plusSeconds(600));
        assertThatThrownBy(() -> issuer.issueRefreshToken(now, now))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
