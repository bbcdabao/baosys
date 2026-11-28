package baosys.common.autoconfigure;

import baosys.common.security.password.PasswordProperties;
import java.util.Map;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 统一密码编码器自动配置。
 *
 * <p>该配置默认关闭，只有业务服务显式配置
 * {@code common.password.enabled=true} 时才会注册唯一的 {@link PasswordEncoder}，
 * 同时服务账户密码的写入、登录校验以及 OAuth2 客户端密钥校验。
 * 认证中心可开启该配置，纯网关或只校验 token 的资源服务通常不需要。</p>
 */
@AutoConfiguration
@ConditionalOnClass(PasswordEncoder.class)
@EnableConfigurationProperties(PasswordProperties.class)
@ConditionalOnProperty(prefix = "common.password", name = "enabled", havingValue = "true")
public class PasswordAutoConfiguration {

    private static final String BCRYPT = "bcrypt";

    /**
     * 创建默认密码编码器。
     *
     * <p>新密码使用带 {@code {bcrypt}} 前缀的 BCrypt 摘要，cost 取自
     * {@code common.password.strength}。校验时兼容历史无前缀的 BCrypt 摘要，
     * 以及 Spring 标准前缀（如 {@code {noop}}、{@code {argon2}}）。
     * 如果业务服务提供了自己的 {@link PasswordEncoder}，该 Bean 会自动退让。</p>
     */
    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    PasswordEncoder passwordEncoder(PasswordProperties properties) {
        int strength = properties.getStrength();
        if (strength < 4 || strength > 31) {
            throw new IllegalArgumentException("BCrypt strength must be between 4 and 31");
        }
        var bcrypt = new BCryptPasswordEncoder(strength);
        var standard = (DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
        standard.setDefaultPasswordEncoderForMatches(bcrypt);
        var encoder = new DelegatingPasswordEncoder(BCRYPT, Map.of(BCRYPT, bcrypt));
        encoder.setDefaultPasswordEncoderForMatches(standard);
        return encoder;
    }
}
