package szs.baosys.common.security.password;

/**
 * 密码摘要与校验抽象。
 *
 * <p>业务代码依赖该接口而不是具体算法，便于后续统一升级密码算法或调整参数。</p>
 */
public interface PasswordHasher {

    /**
     * 对原始密码生成不可逆摘要。
     *
     * @param rawPassword 用户输入的原始密码，不能为 {@code null}
     * @return 可安全持久化的密码摘要
     */
    String hash(String rawPassword);

    /**
     * 校验原始密码是否匹配已有摘要。
     *
     * @param rawPassword 用户输入的原始密码，不能为 {@code null}
     * @param encodedPassword 数据库中保存的密码摘要，不能为 {@code null}
     * @return 匹配时返回 {@code true}
     */
    boolean matches(String rawPassword, String encodedPassword);
}
