package szs.baosys.common.security.resource;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtPermissionConverterTest {

    @Test
    void shouldUseSubjectAsPrincipalName() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("1001")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("username", "admin")
                .claim("permissions", List.of("user:list"))
                .build();

        var authentication = JwtPermissionConverter.create("permissions", "").convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("1001");
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("user:list");
    }
}
