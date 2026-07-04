package szs.baosys.common.security.password;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 密码加密配置。
 */
@ConfigurationProperties("szs.common.password")
public class PasswordProperties {

    /**
     * 是否启用默认密码处理器自动配置。
     */
    private boolean enabled;
    /**
     * BCrypt cost 参数，取值范围由 {@link BCryptPasswordHasher} 校验。
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
