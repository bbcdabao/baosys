package szs.baosys.manager.infra.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * 用于产生 Refresh Token
 * RefreshTokenProvider
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
     * 不可逆HASH
     * @param token
     * @return
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
