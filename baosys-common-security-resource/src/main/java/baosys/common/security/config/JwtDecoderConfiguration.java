package baosys.common.security.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import baosys.common.security.jwt.JwtVerification;
import baosys.common.security.resource.LazyJwksJwtDecoder;

/** Explicitly imported only; intentionally not a component-scan candidate. */
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(SecurityProperties.class)
public class JwtDecoderConfiguration {
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder jwtDecoder(SecurityProperties properties) {
        JwtVerification.validate(properties);
        var key = JwtVerification.localKey(properties.getJwt());
        if (key == null) {
            org.slf4j.LoggerFactory.getLogger(getClass()).info("JWT verification mode: JWKS (request-driven refresh)");
            return new LazyJwksJwtDecoder(properties);
        }
        org.slf4j.LoggerFactory.getLogger(getClass()).info("JWT verification mode: LOCAL (remote fetching disabled)");
        var decoder = NimbusJwtDecoder.withPublicKey(key).build();
        decoder.setJwtValidator(JwtVerification.validator(properties));
        return decoder;
    }
}
