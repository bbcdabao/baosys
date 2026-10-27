package baosys.common.security.config;

import baosys.common.security.jwt.JwtKeyLoader;
import baosys.common.security.jwt.JwtVerification;
import java.io.IOException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.util.StringUtils;

/**
 * 组装 at+jwt 访问令牌校验规则；JWKS 的加载和密钥选择由 Spring/Nimbus 处理。
 * ReactiveJwtDecoderConfiguration
 */
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@EnableConfigurationProperties(OAuth2ResourceServerProperties.class)
public class ReactiveJwtDecoderConfiguration {

    @Bean
    @ConditionalOnMissingBean(ReactiveJwtDecoder.class)
    ReactiveJwtDecoder reactiveJwtDecoder(OAuth2ResourceServerProperties properties)
        throws IOException {
        var jwt = properties.getJwt();

        if (!List.of("RS256").equals(jwt.getJwsAlgorithms())) {
            throw new IllegalArgumentException("Only RS256 is supported");
        }

        NimbusReactiveJwtDecoder decoder;
        if (jwt.getPublicKeyLocation() != null) {
            if (StringUtils.hasText(jwt.getJwkSetUri())) {
                throw new IllegalArgumentException("Choose JWKS or local public key, not both");
            }
            decoder = NimbusReactiveJwtDecoder.withPublicKey(
                JwtKeyLoader.loadPublicKey(jwt.readPublicKey())
            )
                .validateType(false)
                .build();
        } else {
            if (!StringUtils.hasText(jwt.getJwkSetUri())) {
                throw new IllegalArgumentException("jwk-set-uri required");
            }
            decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwt.getJwkSetUri())
                .validateType(false)
                .build();
        }
        decoder.setJwtValidator(JwtVerification.validator(jwt.getIssuerUri()));
        // Nimbus 6.5 将 JWKS 传输失败报告为 IllegalStateException。
        // 转换为认证失败，避免将其作为普通服务端异常返回。
        return token ->
            decoder
                .decode(token)
                .onErrorMap(IllegalStateException.class, exception ->
                    new JwtException("Unable to retrieve verification keys", exception)
                );
    }
}
