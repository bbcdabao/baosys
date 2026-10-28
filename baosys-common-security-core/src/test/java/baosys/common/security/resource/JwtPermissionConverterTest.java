package baosys.common.security.resource;

import baosys.common.security.jwt.JwtConstants;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtPermissionConverterTest {

    private static final List<String> MANAGER_PERMISSIONS = List.of(
        "MGR:user:list",
        "MGR:user:add",
        "MGR:user:update",
        "MGR:user:delete",
        "MGR:user:session:list",
        "MGR:user:session:kick"
    );

    @Test
    void shouldUseSubjectAsPrincipalNameAndIgnoreScope() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("1001")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .claim("scope", "MGR")
            .claim(JwtConstants.PERMISSIONS_CLAIM, List.of("MGR", "MGR:user:list"))
            .build();

        var authentication = JwtPermissionConverter.create().convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("1001");
        assertThat(authentication.getAuthorities())
            .extracting("authority")
            .containsExactly("MGR", "MGR:user:list");
    }

    @Test
    void scopeAndScpCannotGrantAuthorities() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("1001")
            .claim("scope", "MGR")
            .claim("scp", List.of("MGR"))
            .claim(JwtConstants.PERMISSIONS_CLAIM, List.of())
            .build();

        assertThat(JwtPermissionConverter.create().convert(jwt).getAuthorities()).isEmpty();
    }

    @Test
    void shouldConvertManagerPermissionsWithoutPrefix() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("2001")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .claim(JwtConstants.PERMISSIONS_CLAIM, MANAGER_PERMISSIONS)
            .build();

        var authentication = JwtPermissionConverter.create().convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
            .extracting("authority")
            .containsExactlyElementsOf(MANAGER_PERMISSIONS);
    }
}
