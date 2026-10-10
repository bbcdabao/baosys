package baosys.common.security.resource;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtPermissionConverterTest {

    private static final List<String> MANAGER_PERMISSIONS = List.of(
            "user:list",
            "user:add",
            "user:update",
            "user:delete",
            "user:session:list",
            "user:session:kick");

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

    @Test
    void shouldConvertManagerPermissionsWithoutPrefix() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("2001")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("permissions", MANAGER_PERMISSIONS)
                .build();

        var authentication = JwtPermissionConverter.create("permissions", "").convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactlyElementsOf(MANAGER_PERMISSIONS);
    }
}
