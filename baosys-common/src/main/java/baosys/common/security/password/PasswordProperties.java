package baosys.common.security.password;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 密码加密配置。
 *
 * <p>配置前缀为 {@code common.password}。默认不启用自动配置，避免非认证服务
 * 意外暴露密码处理 Bean。</p>
 */
@ConfigurationProperties("common.password")
public class PasswordProperties {

    /**
     * 是否启用默认密码处理器自动配置。
     *
     * <p>设置为 {@code true} 后，common 模块会注册默认 BCrypt 实现。</p>
     */
    private boolean enabled;
    /**
     * BCrypt cost 参数，取值范围由 {@link BCryptPasswordHasher} 校验。
     *
     * <p>该值影响 hash 和 matches 的 CPU 开销，应结合部署机器性能和登录吞吐量调节。</p>
     */
    private int strength = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getStrength() {
        return strength;
    }

    public void setStrength(int strength) {
        this.strength = strength;
    }
}
