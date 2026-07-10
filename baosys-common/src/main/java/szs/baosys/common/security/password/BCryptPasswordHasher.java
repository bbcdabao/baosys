package szs.baosys.common.security.password;

import java.util.Objects;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 基于 BCrypt 的密码处理器。
 *
 * <p>BCrypt 会为每次摘要生成独立 salt，因此同一个原始密码多次 hash 的结果也会不同。
 * 校验时必须使用 {@link BCryptPasswordEncoder#matches(CharSequence, String)}，不能直接
 * 比较摘要字符串。</p>
 */
public final class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder;

    /**
     * 使用指定 cost 参数创建 BCrypt 处理器。
     *
     * <p>strength 越大计算成本越高，默认值由 {@link PasswordProperties} 提供。</p>
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
