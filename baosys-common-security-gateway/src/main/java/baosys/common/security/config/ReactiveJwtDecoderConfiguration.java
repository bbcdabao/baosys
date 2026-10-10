package baosys.common.security.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.*;
import baosys.common.security.jwt.JwtVerification;
import baosys.common.security.gateway.LazyReactiveJwksJwtDecoder;

/** Imported only by the gateway enable annotation, never component-scanned. */
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@EnableConfigurationProperties(SecurityProperties.class)
public class ReactiveJwtDecoderConfiguration {
    @Bean
    @ConditionalOnMissingBean(ReactiveJwtDecoder.class)
    ReactiveJwtDecoder reactiveJwtDecoder(SecurityProperties properties) {
        JwtVerification.validate(properties);
        var key = JwtVerification.localKey(properties.getJwt());
        if (key == null) {
            org.slf4j.LoggerFactory.getLogger(getClass()).info("JWT verification mode: JWKS (request-driven refresh)");
            return new LazyReactiveJwksJwtDecoder(properties);
        }
        org.slf4j.LoggerFactory.getLogger(getClass()).info("JWT verification mode: LOCAL (remote fetching disabled)");
        var decoder = NimbusReactiveJwtDecoder.withPublicKey(key).build();
        decoder.setJwtValidator(JwtVerification.validator(properties));
        return decoder;
    }
}
