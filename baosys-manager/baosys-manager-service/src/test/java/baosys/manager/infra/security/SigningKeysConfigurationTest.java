package baosys.manager.infra.security;

import static org.assertj.core.api.Assertions.*;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.*;

class SigningKeysConfigurationTest {

    @TempDir
    Path temporary;

    private RSAKey key(String id) throws Exception {
        return new RSAKeyGenerator(2048)
            .keyID(id)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .generate();
    }

    private ApplicationContextRunner context(JWKSet keys) throws Exception {
        var path = temporary.resolve("keys.json");
        Files.writeString(path, keys.toString(false));
        return new ApplicationContextRunner()
            .withUserConfiguration(SigningKeysConfiguration.class)
            .withPropertyValues("baosys.authorization.jwk-set-location=" + path.toUri());
    }

    @Test
    void signsWithActivePrivateKeyWhenOldPublicKeyIsRetained() throws Exception {
        var old = key("old").toPublicJWK();
        var active = key("active");
        context(new JWKSet(List.of(old, active))).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            var jwt = ctx
                .getBean(JwtEncoder.class)
                .encode(
                    JwtEncoderParameters.from(
                        JwsHeader.with(
                            org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256
                        )
                            .type("at+jwt")
                            .build(),
                        JwtClaimsSet.builder()
                            .subject("1")
                            .issuedAt(Instant.now())
                            .expiresAt(Instant.now().plusSeconds(300))
                            .build()
                    )
                );
            assertThat(jwt.getHeaders().get("kid")).isEqualTo("active");
            assertThat(
                NimbusJwtDecoder.withPublicKey(active.toRSAPublicKey())
                    .validateType(false)
                    .build()
                    .decode(jwt.getTokenValue())
                    .getSubject()
            ).isEqualTo("1");
        });
    }

    @Test
    void rejectsAmbiguousPrivateSigningKeys() throws Exception {
        context(new JWKSet(List.of(key("one"), key("two")))).run(ctx ->
            assertThat(ctx).hasFailed()
        );
    }

    @Test
    void rejectsDuplicateIdsAndPublicOnlyMaterial() throws Exception {
        var key = key("same");
        context(new JWKSet(List.of(key, key.toPublicJWK()))).run(ctx ->
            assertThat(ctx).hasFailed()
        );
        context(new JWKSet(key.toPublicJWK())).run(ctx -> assertThat(ctx).hasFailed());
    }
}
