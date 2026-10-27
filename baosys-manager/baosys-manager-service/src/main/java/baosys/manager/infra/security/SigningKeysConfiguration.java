package baosys.manager.infra.security;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.*;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

/** 加载外部持久化 JWK 密钥；签名及公开 JWKS 的发布交由授权服务器处理。 */
@Configuration(proxyBeanMethods = false)
public class SigningKeysConfiguration {

    @Bean
    @ConditionalOnMissingBean(org.springframework.security.oauth2.jwt.JwtEncoder.class)
    org.springframework.security.oauth2.jwt.JwtEncoder jwtEncoder(JWKSource<SecurityContext> keys) {
        var encoder = new org.springframework.security.oauth2.jwt.NimbusJwtEncoder(keys);
        encoder.setJwkSelector(matches ->
            matches.stream().filter(JWK::isPrivate).findFirst().orElseThrow()
        );
        return encoder;
    }

    @Bean
    @ConditionalOnMissingBean(JWKSource.class)
    JWKSource<SecurityContext> jwkSource(
        @Value("${baosys.authorization.jwk-set-location}") Resource resource
    ) throws Exception {
        JWKSet keys;
        try (var input = resource.getInputStream()) {
            keys = JWKSet.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        var ids = new HashSet<String>();
        int privateKeys = 0;
        for (JWK key : keys.getKeys()) {
            if (
                !(key instanceof RSAKey rsa) ||
                rsa.size() < 2048 ||
                !StringUtils.hasText(key.getKeyID()) ||
                !ids.add(key.getKeyID()) ||
                !com.nimbusds.jose.JWSAlgorithm.RS256.equals(key.getAlgorithm()) ||
                !KeyUse.SIGNATURE.equals(key.getKeyUse())
            ) {
                throw new IllegalArgumentException(
                    "JWKs require unique kid, RSA >= 2048, use=sig and alg=RS256"
                );
            }
            if (key.isPrivate()) {
                privateKeys++;
            }
        }
        if (privateKeys != 1) {
            throw new IllegalArgumentException(
                "Exactly one active private signing JWK is required"
            );
        }
        return new ImmutableJWKSet<>(keys);
    }
}
