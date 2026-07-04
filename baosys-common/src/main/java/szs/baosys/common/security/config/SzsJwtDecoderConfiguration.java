package szs.baosys.common.security.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import szs.baosys.common.security.jwt.SzsJwtKeyLoader;

/**
 * JWT 解码器自动配置。
 *
 * <p>只要配置了 {@code szs.security.jwt.public-key}，就会根据 RSA 公钥创建
 * Servlet 与 WebFlux 两套可用的 JWT 解码器，供资源服务和网关校验 access token。</p>
 */
@AutoConfiguration
@ConditionalOnClass(JwtDecoder.class)
@EnableConfigurationProperties(SzsSecurityProperties.class)
@ConditionalOnProperty(prefix = "szs.security.jwt", name = "public-key")
public class SzsJwtDecoderConfiguration {

    /**
     * Servlet 资源服务使用的 JWT 解码器。
     */
    @Bean
    @ConditionalOnMissingBean
    JwtDecoder jwtDecoder(SzsSecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(
                        SzsJwtKeyLoader.loadPublicKey(properties.getJwt().getPublicKey()))
                .build();
        decoder.setJwtValidator(jwtValidator(properties));
        return decoder;
    }

    /**
     * WebFlux 网关使用的响应式 JWT 解码器。
     */
    @Bean
    @ConditionalOnClass(name = {
            "org.springframework.security.oauth2.jwt.ReactiveJwtDecoder",
            "reactor.core.publisher.Mono"
    })
    @ConditionalOnMissingBean
    ReactiveJwtDecoder reactiveJwtDecoder(SzsSecurityProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey(
                        SzsJwtKeyLoader.loadPublicKey(properties.getJwt().getPublicKey()))
                .build();
        decoder.setJwtValidator(jwtValidator(properties));
        return decoder;
    }

    /**
     * 组合默认 JWT 校验和平台 issuer 校验。
     *
     * <p>Spring 默认 issuer 校验偏向 URL 形式，这里使用自定义校验器支持
     * {@code szs-auth-server} 这类稳定服务名。</p>
     */
    private static OAuth2TokenValidator<Jwt> jwtValidator(SzsSecurityProperties properties) {
        OAuth2TokenValidator<Jwt> issuerValidator = jwt -> properties.getJwt().getIssuer()
                .equals(jwt.getClaimAsString("iss"))
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                                new OAuth2Error("invalid_token", "Required issuer is missing", null));
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                issuerValidator);
    }
}
