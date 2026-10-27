package baosys.common.security.config;

import baosys.common.security.jwt.JwtKeyLoader;
import baosys.common.security.jwt.JwtVerification;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.util.StringUtils;

/** 组装 at+jwt 访问令牌校验规则；JWKS 的加载和密钥选择由 Spring/Nimbus 处理。 */
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(OAuth2ResourceServerProperties.class)
public class JwtDecoderConfiguration {

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder jwtDecoder(OAuth2ResourceServerProperties properties) throws java.io.IOException {
        var jwt = properties.getJwt();
        if (!List.of("RS256").equals(jwt.getJwsAlgorithms())) {
            throw new IllegalArgumentException("Only RS256 is supported");
        }
        NimbusJwtDecoder decoder;
        if (jwt.getPublicKeyLocation() != null) {
            if (StringUtils.hasText(jwt.getJwkSetUri())) {
                throw new IllegalArgumentException("Choose JWKS or local public key, not both");
            }
            decoder = NimbusJwtDecoder.withPublicKey(
                JwtKeyLoader.loadPublicKey(jwt.readPublicKey())
            )
                .validateType(false)
                .build();
        } else {
            if (!StringUtils.hasText(jwt.getJwkSetUri())) {
                throw new IllegalArgumentException("jwk-set-uri required");
            }
            decoder = NimbusJwtDecoder.withJwkSetUri(jwt.getJwkSetUri())
                .validateType(false)
                .build();
        }
        decoder.setJwtValidator(JwtVerification.validator(jwt.getIssuerUri()));
        return decoder;
    }
}
