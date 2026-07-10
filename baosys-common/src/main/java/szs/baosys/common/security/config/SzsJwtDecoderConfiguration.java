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
 * Servlet 与 WebFlux 两套可用的 JWT 解码器，供资源服务和网关校验 access token。
 * 公钥负责确认 token 未被篡改，issuer 校验负责确认 token 来自当前服务信任的
 * 鉴权中心。</p>
 */
@AutoConfiguration
@ConditionalOnClass(JwtDecoder.class)
@EnableConfigurationProperties(SzsSecurityProperties.class)
@ConditionalOnProperty(prefix = "szs.security.jwt", name = "public-key")
public class SzsJwtDecoderConfiguration {

    private static final String SYSTEM_TYPE_CLAIM_NAME = "systemType";

    /**
     * Servlet 资源服务使用的 JWT 解码器。
     *
     * <p>该 Bean 面向 Spring MVC / Servlet 资源服务，由
     * {@link szs.baosys.common.security.resource.ResourceServerSecurityConfig} 接入
     * Spring Security 过滤链。</p>
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
     *
     * <p>该 Bean 面向 Spring Cloud Gateway。它与 Servlet 解码器使用同一套公钥和
     * issuer 校验规则，保证网关和下游服务对 token 的信任边界一致。</p>
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
     * 组合默认 JWT 校验、平台 issuer 校验和可选子系统校验。
     *
     * <p>Spring 默认 issuer 校验偏向 URL 形式，这里使用自定义校验器支持
     * {@code szs-auth-server} 这类稳定服务名。issuer 在本项目中表示鉴权中心名称：
     * 当存在多个鉴权中心或多套签发密钥时，资源服务只接受配置中声明的签发方，
     * 避免不同系统的 token 被混用。</p>
     *
     * <p>当 {@code szs.security.system-type} 有配置时，解码器还会校验 token 中的
     * {@code systemType} claim。Manager 网关配置为 {@code MANAGER} 后，只接受
     * 同一子系统签发的 token。</p>
     */
    private static OAuth2TokenValidator<Jwt> jwtValidator(SzsSecurityProperties properties) {
        // 默认校验 exp、nbf 等标准声明；这里额外校验 iss，锁定可信鉴权中心。
        OAuth2TokenValidator<Jwt> issuerValidator = jwt -> properties.getJwt().getIssuer()
                .equals(jwt.getClaimAsString("iss"))
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                                new OAuth2Error("invalid_token", "Required issuer is missing", null));
        OAuth2TokenValidator<Jwt> systemTypeValidator = jwt -> {
            String requiredSystemType = properties.getSystemType();
            if (requiredSystemType == null || requiredSystemType.isBlank()) {
                return OAuth2TokenValidatorResult.success();
            }
            return requiredSystemType.trim().equals(jwt.getClaimAsString(SYSTEM_TYPE_CLAIM_NAME))
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(
                            new OAuth2Error("invalid_token", "Required system type is missing", null));
        };
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                issuerValidator,
                systemTypeValidator);
    }
}
