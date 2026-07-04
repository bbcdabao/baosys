package szs.baosys.common.security.jwt;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import szs.baosys.common.security.config.SzsSecurityProperties;

/**
 * 基于 Spring Security {@link JwtEncoder} 的访问令牌签发实现。
 */
public final class SzsJwtTokenServiceImpl implements SzsJwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final SzsSecurityProperties properties;

    /**
     * 创建访问令牌签发服务。
     */
    public SzsJwtTokenServiceImpl(JwtEncoder jwtEncoder, SzsSecurityProperties properties) {
        this.jwtEncoder = Objects.requireNonNull(jwtEncoder, "jwtEncoder must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public String generateAccessToken(
            String userId,
            String username,
            List<String> permissions,
            Map<String, Object> extraClaims) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getJwt().getAccessTokenExpireSeconds());
        Map<String, Object> claims = new HashMap<>();
        if (extraClaims != null) {
            claims.putAll(extraClaims);
        }
        // username 仅作为日志和展示上下文，业务身份以 subject(userId) 为准。
        claims.put("username", username);
        claims.put(properties.getPermissionsClaimName(), permissions == null ? List.of() : List.copyOf(permissions));

        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(properties.getJwt().getIssuer())
                .subject(userId)
                .issuedAt(now)
                .expiresAt(expiresAt);
        claims.forEach(builder::claim);
        return jwtEncoder.encode(JwtEncoderParameters.from(builder.build())).getTokenValue();
    }
}
