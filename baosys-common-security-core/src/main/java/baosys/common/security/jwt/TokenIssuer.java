package baosys.common.security.jwt;

import baosys.common.security.resource.PermissionSet;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

/**
 * 两种登录流程共用的业务令牌签发器，不依赖授权服务器、数据库或 Web 会话。
 * 调用方负责认证、授权及有效期策略；本类统一声明、签名和刷新令牌随机生成。
 */
public final class TokenIssuer {
    private final JwtEncoder encoder;
    private final SecureRandom random = new SecureRandom();

    public TokenIssuer(JwtEncoder encoder) {
        this.encoder = Objects.requireNonNull(encoder);
    }

    /**
     * 已由业务层验证的访问令牌主体；subject 是登录记录 ID。
     * AccessTokenSubject
     * @param subject
     * @param clientId
     * @param permissions
     */
    public record AccessTokenSubject(String subject, String clientId, List<String> permissions) {
        public AccessTokenSubject {
            Objects.requireNonNull(subject);
            Objects.requireNonNull(clientId);
            permissions = PermissionSet.normalize(permissions);
        }
    }

    public Jwt issueAccessToken(
        String issuer, AccessTokenSubject subject, Instant issuedAt, Instant expiresAt
    ) {
        requireLifetime(issuedAt, expiresAt);
        var claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(subject.subject())
            // JDBC 授权元数据反序列化支持 ArrayList，不能持久化 List.of/toList 的内部类型。
            .audience(new ArrayList<>(List.of(JwtConstants.ACCESS_TOKEN_AUDIENCE)))
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .claim("client_id", subject.clientId())
            .claim(JwtConstants.PERMISSIONS_CLAIM, new ArrayList<>(subject.permissions()))
            .build();
        return encoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(SignatureAlgorithm.RS256).type("at+jwt").build(), claims
        ));
    }

    public OAuth2RefreshToken issueRefreshToken(Instant issuedAt, Instant expiresAt) {
        requireLifetime(issuedAt, expiresAt);
        byte[] bytes = new byte[96];
        random.nextBytes(bytes);
        return new OAuth2RefreshToken(
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), issuedAt, expiresAt
        );
    }

    private static void requireLifetime(Instant issuedAt, Instant expiresAt) {
        if (!Objects.requireNonNull(expiresAt).isAfter(Objects.requireNonNull(issuedAt))) {
            throw new IllegalArgumentException("Token expiry must be after issuance");
        }
    }
}
