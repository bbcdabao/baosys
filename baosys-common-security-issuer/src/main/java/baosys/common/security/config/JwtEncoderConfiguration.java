package baosys.common.security.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import baosys.common.security.jwt.JwtTokenService;
import baosys.common.security.jwt.JwtTokenServiceImpl;
import baosys.common.security.jwt.SigningKeyMaterial;

/**
 * JWT 编码器自动配置。
 *
 * <p>只在认证中心配置 {@code security.jwt.private-key} 后启用。其他网关或资源服务
 * 只需要公钥校验 token，不应该具备签发 token 的能力。这个约束可以降低私钥泄露面，
 * 也让“谁能签发 token”在部署配置上清晰可见。</p>
 */
@AutoConfiguration
@ConditionalOnClass(JwtEncoder.class)
@EnableConfigurationProperties(SecurityProperties.class)
@ConditionalOnProperty(prefix = "security.jwt", name = "private-key")
public class JwtEncoderConfiguration {

    /**
     * 使用 RSA 公私钥创建 Nimbus JWT 编码器。
     *
     * <p>Nimbus 编码器需要同时拿到公钥和私钥来构造 RSA JWK；真正用于签名的是
     * 私钥，公钥会成为密钥描述的一部分，便于编码器完成算法选择。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    JwtEncoder jwtEncoder(SigningKeyMaterial material) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(material.signingKey())));
    }

    @Bean
    @ConditionalOnMissingBean
    SigningKeyMaterial signingKeyMaterial(SecurityProperties properties) {
        return new SigningKeyMaterial(properties);
    }

    /**
     * 平台访问令牌签发服务。
     *
     * <p>业务侧不直接操作 {@link JwtEncoder}，统一通过
     * {@link JwtTokenService} 写入平台约定的 issuer、subject、权限 claim 和过期时间。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    JwtTokenService jwtTokenService(JwtEncoder jwtEncoder, SecurityProperties properties) {
        return new JwtTokenServiceImpl(jwtEncoder, properties);
    }
}
