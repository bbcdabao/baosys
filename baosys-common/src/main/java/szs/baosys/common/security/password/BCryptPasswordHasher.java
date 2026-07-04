package szs.baosys.common.security.password;

import java.util.Objects;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 基于 BCrypt 的密码处理器。
 */
public final class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder;

    /**
     * 使用指定 cost 参数创建 BCrypt 处理器。
     */
    public BCryptPasswordHasher(int strength) {
        if (strength < 4 || strength > 31) {
            throw new IllegalArgumentException("BCrypt strength must be between 4 and 31");
        }
        this.encoder = new BCryptPasswordEncoder(strength);
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(Objects.requireNonNull(rawPassword, "rawPassword must not be null"));
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        Objects.requireNonNull(encodedPassword, "encodedPassword must not be null");
        return encoder.matches(rawPassword, encodedPassword);
    }
}
