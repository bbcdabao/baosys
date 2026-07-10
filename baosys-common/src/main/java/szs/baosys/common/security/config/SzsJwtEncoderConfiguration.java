package szs.baosys.common.security.config;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
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

import szs.baosys.common.security.jwt.SzsJwtKeyLoader;
import szs.baosys.common.security.jwt.SzsJwtTokenService;
import szs.baosys.common.security.jwt.SzsJwtTokenServiceImpl;

/**
 * JWT 编码器自动配置。
 *
 * <p>只在认证中心配置 {@code szs.security.jwt.private-key} 后启用。其他网关或资源服务
 * 只需要公钥校验 token，不应该具备签发 token 的能力。这个约束可以降低私钥泄露面，
 * 也让“谁能签发 token”在部署配置上清晰可见。</p>
 */
@AutoConfiguration(after = SzsJwtDecoderConfiguration.class)
@ConditionalOnClass(JwtEncoder.class)
@EnableConfigurationProperties(SzsSecurityProperties.class)
@ConditionalOnProperty(prefix = "szs.security.jwt", name = "private-key")
public class SzsJwtEncoderConfiguration {

    /**
     * 使用 RSA 公私钥创建 Nimbus JWT 编码器。
     *
     * <p>Nimbus 编码器需要同时拿到公钥和私钥来构造 RSA JWK；真正用于签名的是
     * 私钥，公钥会成为密钥描述的一部分，便于编码器完成算法选择。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    JwtEncoder jwtEncoder(SzsSecurityProperties properties) {
        RSAPublicKey publicKey = SzsJwtKeyLoader.loadPublicKey(properties.getJwt().getPublicKey());
        RSAPrivateKey privateKey = SzsJwtKeyLoader.loadPrivateKey(properties.getJwt().getPrivateKey());
        RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(rsaKey)));
    }

    /**
     * 平台访问令牌签发服务。
     *
     * <p>业务侧不直接操作 {@link JwtEncoder}，统一通过
     * {@link SzsJwtTokenService} 写入平台约定的 issuer、subject、权限 claim 和过期时间。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    SzsJwtTokenService szsJwtTokenService(JwtEncoder jwtEncoder, SzsSecurityProperties properties) {
        return new SzsJwtTokenServiceImpl(jwtEncoder, properties);
    }
}
