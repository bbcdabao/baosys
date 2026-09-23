package baosys.common.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import baosys.common.security.jwt.JwtKeyLoader;
import baosys.common.security.jwt.JwtTokenService;
import baosys.common.security.jwt.JwtTokenServiceImpl;

class JwtDecoderConfigurationTest {

    private static final KeyMaterial KEY_MATERIAL = createKeyMaterial();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JwtDecoderConfiguration.class))
            .withPropertyValues(
                    "security.jwt.issuer=auth-server",
                    "security.jwt.public-key=" + raw(KEY_MATERIAL.publicKeyPem(), "PUBLIC KEY"));

    @Test
    void shouldAcceptTokenForConfiguredSystemType() {
        contextRunner
                .withPropertyValues("security.system-type=MANAGER")
                .run(context -> {
                    JwtDecoder decoder = context.getBean(JwtDecoder.class);

                    var jwt = decoder.decode(accessToken("MANAGER"));

                    assertThat(jwt.getClaimAsString("systemType")).isEqualTo("MANAGER");
                });
    }

    @Test
    void shouldRejectTokenForAnotherSystemType() {
        contextRunner
                .withPropertyValues("security.system-type=MANAGER")
                .run(context -> {
                    JwtDecoder decoder = context.getBean(JwtDecoder.class);

                    assertThatThrownBy(() -> decoder.decode(accessToken("SIMULATORS")))
                            .isInstanceOf(JwtException.class);
                });
    }

    private static String accessToken(String systemType) {
        var publicKey = JwtKeyLoader.loadPublicKey(KEY_MATERIAL.publicKeyPem());
        var privateKey = JwtKeyLoader.loadPrivateKey(KEY_MATERIAL.privateKeyPem());
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(
                new JWKSet(new RSAKey.Builder(publicKey).privateKey(privateKey).build())));
        SecurityProperties properties = new SecurityProperties();
        properties.getJwt().setIssuer("auth-server");
        properties.getJwt().setAccessTokenExpireSeconds(900);
        JwtTokenService tokenService = new JwtTokenServiceImpl(encoder, properties);
        return tokenService.generateAccessToken(
                "1001",
                "admin",
                systemType,
                List.of("user:list"),
                Map.of("displayName", "System Admin"));
    }

    private static KeyMaterial createKeyMaterial() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new KeyMaterial(
                    pem("PUBLIC KEY", keyPair.getPublic().getEncoded()),
                    pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded)
                + "\n-----END " + type + "-----";
    }

    private static String raw(String pem, String type) {
        return pem.replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
    }

    private record KeyMaterial(String publicKeyPem, String privateKeyPem) {
    }
}
