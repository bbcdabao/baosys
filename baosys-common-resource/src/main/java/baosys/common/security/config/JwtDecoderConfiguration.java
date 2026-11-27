package baosys.common.security.config;

import baosys.common.security.jwt.JwtConstants;
import baosys.common.security.jwt.JwtKeyLoader;
import baosys.common.security.jwt.JwtVerification;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 组装 at+jwt 访问令牌校验规则；JWKS 的加载和密钥选择由 Spring/Nimbus 处理。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(OAuth2ResourceServerProperties.class)
public class JwtDecoderConfiguration {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder jwtDecoder(OAuth2ResourceServerProperties properties) throws IOException {
        var jwt = properties.getJwt();
        if (!List.of("RS256").equals(jwt.getJwsAlgorithms())) {
            throw new IllegalArgumentException("Only RS256 is supported");
        }
        if (
            jwt.getAudiences() != null &&
            !jwt.getAudiences().isEmpty() &&
            !List.of(JwtConstants.ACCESS_TOKEN_AUDIENCE).equals(jwt.getAudiences())
        ) {
            throw new IllegalArgumentException(
                "Only audience '" + JwtConstants.ACCESS_TOKEN_AUDIENCE + "' is supported"
            );
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
            var requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT);
            requestFactory.setReadTimeout(DEFAULT_READ_TIMEOUT);
            decoder = NimbusJwtDecoder.withJwkSetUri(jwt.getJwkSetUri())
                .restOperations(new RestTemplate(requestFactory))
                .validateType(false)
                .build();
        }
        decoder.setJwtValidator(JwtVerification.validator(jwt.getIssuerUri()));
        return decoder;
    }
}
