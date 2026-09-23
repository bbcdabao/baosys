package baosys.common.security.jwt;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import baosys.common.security.config.SecurityProperties;

/**
 * 基于 Spring Security {@link JwtEncoder} 的访问令牌签发实现。
 *
 * <p>该实现集中写入平台标准 claim：{@code iss} 标识鉴权中心，{@code sub}
 * 标识业务用户，{@code systemType} 标识前端子系统，{@code iat}/{@code exp}
 * 控制有效期，权限列表写入配置指定的 claim。下游服务只要共享相同公钥和 issuer
 * 配置，就能按同一套规则完成解码与鉴权。</p>
 */
public final class JwtTokenServiceImpl implements JwtTokenService {

    private static final String SYSTEM_TYPE_CLAIM_NAME = "systemType";

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;

    /**
     * 创建访问令牌签发服务。
     *
     * @param jwtEncoder Spring Security JWT 编码器，负责完成签名
     * @param properties 平台安全配置，提供 issuer、有效期和权限 claim 名称
     */
    public JwtTokenServiceImpl(JwtEncoder jwtEncoder, SecurityProperties properties) {
        this.jwtEncoder = Objects.requireNonNull(jwtEncoder, "jwtEncoder must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public String generateAccessToken(
            String userId,
            String username,
            String systemType,
            List<String> permissions,
            Map<String, Object> extraClaims) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (systemType == null || systemType.isBlank()) {
            throw new IllegalArgumentException("systemType must not be blank");
        }
        String normalizedSystemType = systemType.trim();

        // access token 是短期凭证，只记录签发时间和过期时间，不在服务端保存会话状态。
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getJwt().getAccessTokenExpireSeconds());
        Map<String, Object> claims = new HashMap<>();
        if (extraClaims != null) {
            claims.putAll(extraClaims);
        }
        // username 仅作为日志和展示上下文，业务身份以 subject(userId) 为准。
        claims.put("username", username);
        // systemType 用于网关隔离子系统，解码端可按自身配置拒绝其他系统的 token。
        claims.put(SYSTEM_TYPE_CLAIM_NAME, normalizedSystemType);
        claims.put(properties.getPermissionsClaimName(), permissions == null ? List.of() : List.copyOf(permissions));

        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                // issuer 用于标识鉴权中心，解码端会校验它以避免多个签发方的 token 混用。
                .issuer(properties.getJwt().getIssuer())
                .subject(userId)
                .issuedAt(now)
                .expiresAt(expiresAt);
        claims.forEach(builder::claim);
        return jwtEncoder.encode(JwtEncoderParameters.from(builder.build())).getTokenValue();
    }
}
