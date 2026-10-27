package baosys.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtVerificationTest {

    private final OAuth2TokenValidator<Jwt> validator = JwtVerification.validator(
        "auth-server"
    );

    @Test
    void rejectsWrongTokenIssuerAndAudience() {
        assertThat(
            validator.validate(token().issuer("other-issuer").build()).hasErrors()
        ).isTrue();
        assertThat(
            validator.validate(token().audience(List.of("other")).build()).hasErrors()
        ).isTrue();
    }

    @Test
    void acceptsUnsortedDuplicatePermissionsWithoutChangingClaims() {
        List<String> permissions = List.of("SIM", "MGR:user:list", "MGR", "MGR");
        Jwt jwt = token().claim(JwtConstants.PERMISSIONS_CLAIM, permissions).build();

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
        assertThat(jwt.getClaimAsStringList(JwtConstants.PERMISSIONS_CLAIM)).containsExactlyElementsOf(permissions);
    }

    @Test
    void acceptsEmptyPermissionsWithoutScope() {
        assertThat(
            validator.validate(token().claim(JwtConstants.PERMISSIONS_CLAIM, List.of()).build()).hasErrors()
        ).isFalse();
    }

    @Test
    void rejectsMalformedPermissions() {
        for (Object permissions : List.of(
            "MGR",
            List.of(42),
            Arrays.asList("MGR", null)
        )) {
            assertThat(
                validator.validate(token().claim(JwtConstants.PERMISSIONS_CLAIM, permissions).build()).hasErrors()
            )
                .as("permissions: %s", permissions)
                .isTrue();
        }
        assertThat(
            validator
                .validate(
                    token()
                        .claims(claims -> claims.remove(JwtConstants.PERMISSIONS_CLAIM))
                        .build()
                )
                .hasErrors()
        ).isTrue();
    }

    @Test
    void doesNotValidatePermissionNaming() {
        assertThat(
            validator
                .validate(
                    token()
                        .claim(
                            JwtConstants.PERMISSIONS_CLAIM,
                            List.of("MGR", "customOperation", "other:Read", "x".repeat(129))
                        )
                        .build()
                )
                .hasErrors()
        ).isFalse();
    }

    @Test
    void ignoresScopeForBusinessPermissionValidation() {
        for (Object scope : List.of(" MGR", "MGR ", "MGR SIM", List.of("MGR"))) {
            assertThat(
                validator.validate(token().claim("scope", scope).build()).hasErrors()
            ).isFalse();
        }
    }

    @Test
    void rejectsBlankOrNonStringKeyIdentifiers() {
        for (Object kid : List.of("", " ", 42)) {
            assertThat(validator.validate(token().header("kid", kid).build()).hasErrors()).isTrue();
        }
    }

    @Test
    void acceptsFutureIssuedAtWithoutCustomClockSkewLimit() {
        Instant now = Instant.now();
        // 签发时间超前不再由自定义校验器拒绝，令牌仍须通过框架的有效期检查。
        Jwt jwt = token()
            .issuedAt(now.plusSeconds(3600))
            .expiresAt(now.plusSeconds(7200))
            .build();

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void stillRejectsExpiredAndNotYetValidTokens() {
        Instant now = Instant.now();
        Jwt expired = token()
            .issuedAt(now.minusSeconds(3600))
            .expiresAt(now.minusSeconds(120))
            .build();
        Jwt notYetValid = token()
            .notBefore(now.plusSeconds(120))
            .build();

        // 移除自定义 iat 检查，不影响框架对 exp/nbf 的校验及默认容差。
        assertThat(validator.validate(expired).hasErrors()).isTrue();
        assertThat(validator.validate(notYetValid).hasErrors()).isTrue();
    }

    private static Jwt.Builder token() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .header("typ", "at+jwt")
            .header("kid", "test-key")
            .issuer("auth-server")
            .subject("9527")
            .audience(List.of(JwtConstants.ACCESS_TOKEN_AUDIENCE))
            .claim("jti", "test-id")
            .claim("client_id", JwtConstants.BROWSER_CLIENT_ID)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(1800))
            .claim(JwtConstants.PERMISSIONS_CLAIM, List.of("MGR", "MGR:user:list"));
    }
}
