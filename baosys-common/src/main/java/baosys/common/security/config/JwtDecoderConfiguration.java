package baosys.common.security.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

import baosys.common.security.jwt.JwtKeyLoader;

/**
 * JWT 解码器配置。
 *
 * <p>由网关认证或资源服务越权检查组合配置通过 {@code @Import} 引入。
 * 配置了 {@code security.jwt.public-key} 后，会根据 RSA 公钥创建
 * Servlet 与 WebFlux 两套可用的 JWT 解码器，供资源服务和网关校验 access token。
 * 公钥负责确认 token 未被篡改，issuer 校验负责确认 token 来自当前服务信任的
 * 鉴权中心。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(JwtDecoder.class)
@EnableConfigurationProperties(SecurityProperties.class)
@ConditionalOnProperty(prefix = "security.jwt", name = "public-key")
public class JwtDecoderConfiguration {

    private static final String SYSTEM_TYPE_CLAIM_NAME = "systemType";

    /**
     * Servlet 资源服务使用的 JWT 解码器。
     *
     * <p>该 Bean 面向 Spring MVC / Servlet 资源服务，由
     * {@link baosys.common.security.resource.ResourceServerSecurityConfig} 接入
     * Spring Security 过滤链。</p>
     */
    @Bean
    @ConditionalOnMissingBean
    JwtDecoder jwtDecoder(SecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(
                        JwtKeyLoader.loadPublicKey(properties.getJwt().getPublicKey()))
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
    ReactiveJwtDecoder reactiveJwtDecoder(SecurityProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey(
                        JwtKeyLoader.loadPublicKey(properties.getJwt().getPublicKey()))
                .build();
        decoder.setJwtValidator(jwtValidator(properties));
        return decoder;
    }

    /**
     * 组合默认 JWT 校验、平台 issuer 校验和可选子系统校验。
     *
     * <p>Spring 默认 issuer 校验偏向 URL 形式，这里使用自定义校验器支持
     * {@code auth-server} 这类稳定服务名。issuer 在本项目中表示鉴权中心名称：
     * 当存在多个鉴权中心或多套签发密钥时，资源服务只接受配置中声明的签发方，
     * 避免不同系统的 token 被混用。</p>
     *
     * <p>当 {@code security.system-type} 有配置时，解码器还会校验 token 中的
     * {@code systemType} claim。网关和下游资源服务使用相同配置形成双重边界：
     * 例如 Manager 只接受 {@code MANAGER} token。</p>
     */
    private static OAuth2TokenValidator<Jwt> jwtValidator(SecurityProperties properties) {
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
