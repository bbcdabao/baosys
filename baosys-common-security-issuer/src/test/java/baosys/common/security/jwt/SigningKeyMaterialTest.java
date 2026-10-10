package baosys.common.security.jwt;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.*;
import baosys.common.security.config.SecurityProperties;

class SigningKeyMaterialTest {
    @Test void encoderAndJwksShareKidAndOnlyPublicMaterialIsPublished() throws Exception {
        var properties = properties(new RSAKeyGenerator(2048).generate());
        var material = new SigningKeyMaterial(properties);
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(material.signingKey())));
        var service = new JwtTokenServiceImpl(encoder, properties);
        String token = service.generateAccessToken("user-1", "user", "MANAGER", List.of("user:list"),
                Map.of("sub", "forged-user", "exp", Instant.now().plusSeconds(90000), "systemType", "forged-system"));
        var parsed = SignedJWT.parse(token);
        assertThat(parsed.getHeader().getKeyID()).isEqualTo(material.signingKey().getKeyID());
        assertThat(parsed.getJWTClaimsSet().getSubject()).isEqualTo("user-1");
        assertThat(parsed.getJWTClaimsSet().getStringClaim("systemType")).isEqualTo("MANAGER");
        assertThat(parsed.getJWTClaimsSet().getExpirationTime().toInstant()).isBefore(Instant.now().plusSeconds(7201));
        var publicKey = (RSAKey) JWKSet.parse(material.publicJwks()).getKeys().get(0);
        assertThat(publicKey.isPrivate()).isFalse();
        assertThat(publicKey.toJSONObject()).doesNotContainKeys("d", "p", "q", "dp", "dq", "qi");
        assertThat(NimbusJwtDecoder.withPublicKey(publicKey.toRSAPublicKey()).build().decode(token).getSubject()).isEqualTo("user-1");
    }
    @Test void retentionIsByDeadlineNotNumberOfGenerations() throws Exception {
        var properties = properties(new RSAKeyGenerator(2048).generate());
        Instant now = Instant.now();
        var entries = new ArrayList<SecurityProperties.PublishedKey>();
        for (int i = 0; i < 3; i++) {
            var entry = new SecurityProperties.PublishedKey();
            entry.setKeyId("retired-" + i);
            entry.setPublicKey(properties.getJwt().getPublicKey());
            entry.setRemoveAfter(now.plusSeconds((i + 1) * 60));
            entries.add(entry);
        }
        properties.getJwt().setPublishedKeys(entries);
        for (int i = 0; i <= 3; i++) {
            var material = new SigningKeyMaterial(properties, Clock.fixed(now.plusSeconds(i * 60), ZoneOffset.UTC));
            assertThat(JWKSet.parse(material.publicJwks()).size()).isEqualTo(4 - i);
        }
    }
    @Test void mismatchedOrBlankSigningKeysFail() throws Exception {
        var a = new RSAKeyGenerator(2048).generate();
        var b = new RSAKeyGenerator(2048).generate();
        var properties = properties(a);
        properties.getJwt().setPrivateKey(Base64.getEncoder().encodeToString(b.toRSAPrivateKey().getEncoded()));
        assertThatThrownBy(() -> new SigningKeyMaterial(properties)).isInstanceOf(IllegalArgumentException.class);
        properties.getJwt().setPrivateKey("");
        assertThatThrownBy(() -> new SigningKeyMaterial(properties)).isInstanceOf(IllegalArgumentException.class);
    }
    private static SecurityProperties properties(RSAKey key) throws Exception {
        var properties = new SecurityProperties();
        properties.getJwt().setPublicKey(Base64.getEncoder().encodeToString(key.toRSAPublicKey().getEncoded()));
        properties.getJwt().setPrivateKey(Base64.getEncoder().encodeToString(key.toRSAPrivateKey().getEncoded()));
        return properties;
    }
}
