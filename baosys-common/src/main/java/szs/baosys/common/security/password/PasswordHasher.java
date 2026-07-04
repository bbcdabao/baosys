package szs.baosys.common.security.password;

/**
 * 密码摘要与校验抽象。
 */
public interface PasswordHasher {

    /**
     * 对原始密码生成不可逆摘要。
     */
    String hash(String rawPassword);

    /**
     * 校验原始密码是否匹配已有摘要。
     */
    boolean matches(String rawPassword, String encodedPassword);
}
