package szs.baosys.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import szs.baosys.common.security.config.SzsSecurityProperties;

class SzsJwtTokenServiceTest {

    private static final KeyMaterial KEY_MATERIAL = createKeyMaterial();

    @Test
    void shouldGenerateAccessTokenWithPermissionsClaim() {
        SzsSecurityProperties properties = new SzsSecurityProperties();
        properties.getJwt().setIssuer("szs-auth-server");
        properties.getJwt().setAccessTokenExpireSeconds(900);

        RSAPublicKey publicKey = SzsJwtKeyLoader.loadPublicKey(KEY_MATERIAL.publicKeyPem());
        RSAPrivateKey privateKey = SzsJwtKeyLoader.loadPrivateKey(KEY_MATERIAL.privateKeyPem());
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(
                new JWKSet(new RSAKey.Builder(publicKey).privateKey(privateKey).build())));

        SzsJwtTokenService service = new SzsJwtTokenServiceImpl(encoder, properties);
        String token = service.generateAccessToken(
                "1001",
                "admin",
                "MANAGER",
                List.of("user:list", "user:add"),
                Map.of("displayName", "System Admin"));

        var jwt = NimbusJwtDecoder.withPublicKey(publicKey).build().decode(token);

        assertThat(jwt.getSubject()).isEqualTo("1001");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("szs-auth-server");
        assertThat(jwt.getClaimAsString("username")).isEqualTo("admin");
        assertThat(jwt.getClaimAsString("systemType")).isEqualTo("MANAGER");
        assertThat(jwt.getClaimAsString("displayName")).isEqualTo("System Admin");
        assertThat(jwt.getClaimAsStringList("permissions"))
                .containsExactly("user:list", "user:add");
        assertThat(jwt.getClaims()).doesNotContainKey("roles");
    }

    @Test
    void shouldLoadPemAndRawBase64Keys() {
        String rawPublicKey = raw(KEY_MATERIAL.publicKeyPem(), "PUBLIC KEY");
        String rawPrivateKey = raw(KEY_MATERIAL.privateKeyPem(), "PRIVATE KEY");

        assertThat(SzsJwtKeyLoader.loadPublicKey(rawPublicKey).getModulus())
                .isEqualTo(SzsJwtKeyLoader.loadPublicKey(KEY_MATERIAL.publicKeyPem()).getModulus());
        assertThat(SzsJwtKeyLoader.loadPrivateKey(rawPrivateKey).getModulus())
                .isEqualTo(SzsJwtKeyLoader.loadPrivateKey(KEY_MATERIAL.privateKeyPem()).getModulus());
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
