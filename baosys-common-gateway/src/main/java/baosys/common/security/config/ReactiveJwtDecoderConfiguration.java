package baosys.common.security.config;

import baosys.common.security.jwt.JwtKeyLoader;
import baosys.common.security.jwt.JwtVerification;
import io.netty.channel.ChannelOption;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * 组装 at+jwt 访问令牌校验规则；JWKS 的加载和密钥选择由 Spring/Nimbus 处理。
 * ReactiveJwtDecoderConfiguration
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@EnableConfigurationProperties(OAuth2ResourceServerProperties.class)
public class ReactiveJwtDecoderConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ReactiveJwtDecoderConfiguration.class);
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    @ConditionalOnMissingBean(ReactiveJwtDecoder.class)
    ReactiveJwtDecoder reactiveJwtDecoder(
        OAuth2ResourceServerProperties properties,
        ObjectProvider<WebClient.Builder> webClientBuilders
    ) throws IOException {
        WebClient.Builder builder = webClientBuilders.getIfAvailable(WebClient::builder).clone();
        return createDecoder(properties, builder, DEFAULT_CONNECT_TIMEOUT, DEFAULT_RESPONSE_TIMEOUT);
    }

    ReactiveJwtDecoder reactiveJwtDecoder(OAuth2ResourceServerProperties properties) throws IOException {
        return createDecoder(properties, WebClient.builder(), DEFAULT_CONNECT_TIMEOUT, DEFAULT_RESPONSE_TIMEOUT);
    }

    ReactiveJwtDecoder createDecoder(
        OAuth2ResourceServerProperties properties,
        WebClient.Builder webClientBuilder,
        Duration connectTimeout,
        Duration responseTimeout
    ) throws IOException {
        var jwt = properties.getJwt();

        if (!List.of("RS256").equals(jwt.getJwsAlgorithms())) {
            throw new IllegalArgumentException("Only RS256 is supported");
        }

        NimbusReactiveJwtDecoder decoder;
        String jwkSetUri = jwt.getJwkSetUri();
        if (jwt.getPublicKeyLocation() != null) {
            if (StringUtils.hasText(jwkSetUri)) {
                throw new IllegalArgumentException("Choose JWKS or local public key, not both");
            }
            decoder = NimbusReactiveJwtDecoder.withPublicKey(
                JwtKeyLoader.loadPublicKey(jwt.readPublicKey())
            )
                .validateType(false)
                .build();
        } else {
            if (!StringUtils.hasText(jwkSetUri)) {
                throw new IllegalArgumentException("jwk-set-uri required");
            }
            HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(connectTimeout.toMillis()))
                .responseTimeout(responseTimeout);
            WebClient jwksClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
            decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri)
                .webClient(jwksClient)
                .validateType(false)
                .build();
        }
        decoder.setJwtValidator(JwtVerification.validator(jwt.getIssuerUri()));
        // Nimbus 6.5 将 JWKS 传输失败报告为 IllegalStateException。
        // 转换为认证失败，避免将其作为普通服务端异常返回，同时记录告警便于运维定位。
        return token ->
            decoder
                .decode(token)
                .onErrorMap(IllegalStateException.class, exception -> {
                    log.warn("Unable to retrieve JWKS verification keys from {}: {}", jwkSetUri, exception.getMessage());
                    return new JwtException("Unable to retrieve verification keys", exception);
                });
    }
}
