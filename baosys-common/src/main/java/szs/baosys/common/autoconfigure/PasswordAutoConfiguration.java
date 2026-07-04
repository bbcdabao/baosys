package szs.baosys.common.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import szs.baosys.common.security.password.BCryptPasswordHasher;
import szs.baosys.common.security.password.PasswordHasher;
import szs.baosys.common.security.password.PasswordProperties;

/**
 * 密码加密器自动配置。
 *
 * <p>该配置默认关闭，只有业务服务显式配置
 * {@code szs.common.password.enabled=true} 时才会注册
 * {@link PasswordHasher}，避免公共模块在不需要密码能力的服务中产生多余 Bean。</p>
 */
@AutoConfiguration
@ConditionalOnClass(BCryptPasswordEncoder.class)
@EnableConfigurationProperties(PasswordProperties.class)
@ConditionalOnProperty(
        prefix = "szs.common.password",
        name = "enabled",
        havingValue = "true")
public class PasswordAutoConfiguration {

    /**
     * 创建默认 BCrypt 密码处理器。
     */
    @Bean
    @ConditionalOnMissingBean
    PasswordHasher passwordHasher(PasswordProperties properties) {
        return new BCryptPasswordHasher(properties.getStrength());
    }
}
