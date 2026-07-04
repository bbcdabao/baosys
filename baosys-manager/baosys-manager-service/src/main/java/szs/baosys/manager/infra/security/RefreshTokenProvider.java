package szs.baosys.manager.infra.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * 生成高熵刷新令牌，并提供仅保存摘要所需的单向哈希。
 *
 * <p>令牌明文只返回给浏览器；数据库保存 SHA-256 摘要。数据库泄露时，
 * 攻击者不能直接把摘要当作刷新令牌使用。</p>
 */
@Component
public class RefreshTokenProvider {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public IssuedRefreshToken issue() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new IssuedRefreshToken(value, hash(value));
    }

    /**
     * 计算刷新令牌的稳定摘要，用于持久化和等值查找。
     *
     * @param token 刷新令牌明文
     * @return 小写十六进制 SHA-256 摘要
     */
    public String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record IssuedRefreshToken(String value, String hash) {
    }
}
