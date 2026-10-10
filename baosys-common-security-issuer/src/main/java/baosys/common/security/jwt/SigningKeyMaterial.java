package baosys.common.security.jwt;

import java.security.Signature;
import java.time.Clock;
import java.util.*;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.*;
import baosys.common.security.config.SecurityProperties;

/** One source for signing and public JWKS; retired public keys expire independently. */
public final class SigningKeyMaterial {
    private final RSAKey active;
    private final List<RetainedKey> retained;
    private final Clock clock;
    private record RetainedKey(RSAKey key, java.time.Instant removeAfter) { }
    public SigningKeyMaterial(SecurityProperties properties) { this(properties, Clock.systemUTC()); }
    public SigningKeyMaterial(SecurityProperties properties, Clock clock) {
        this.clock = clock;
        var config = properties.getJwt();
        JwtVerification.validate(properties);
        if (config.getAccessTokenExpireSeconds() <= 0)
            throw new IllegalArgumentException("access-token-expire-seconds must be positive");
        var publicKey = JwtVerification.localKey(config);
        if (publicKey == null) throw new IllegalArgumentException("Issuer requires a local signing public key");
        var privateKey = JwtKeyLoader.loadPrivateKey(config.getPrivateKey());
        try {
            if (publicKey.getModulus().bitLength() < 2048) throw new IllegalArgumentException("RSA key must be at least 2048 bits");
            var signature = Signature.getInstance("SHA256withRSA");
            byte[] challenge = "baosys-key-pair-check".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            signature.initSign(privateKey);
            signature.update(challenge);
            byte[] signed = signature.sign();
            signature.initVerify(publicKey);
            signature.update(challenge);
            if (!signature.verify(signed)) throw new IllegalArgumentException("JWT public/private keys do not match");
            var unsigned = new RSAKey.Builder(publicKey).build();
            String kid = config.getKeyId() == null ? unsigned.computeThumbprint().toString() : config.getKeyId();
            if (kid.isBlank()) throw new IllegalArgumentException("security.jwt.key-id must not be blank");
            active = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(kid)
                    .keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build();
            var ids = new HashSet<String>();
            ids.add(kid);
            var keys = new ArrayList<RetainedKey>();
            for (var entry : config.getPublishedKeys()) {
                if (entry.getKeyId() == null || entry.getKeyId().isBlank() || !ids.add(entry.getKeyId())
                        || entry.getRemoveAfter() == null)
                    throw new IllegalArgumentException("Published keys require unique key-id and finite remove-after");
                var key = JwtKeyLoader.loadPublicKey(entry.getPublicKey());
                if (key.getModulus().bitLength() < 2048) throw new IllegalArgumentException("RSA key must be at least 2048 bits");
                keys.add(new RetainedKey(new RSAKey.Builder(key).keyID(entry.getKeyId())
                        .keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build(), entry.getRemoveAfter()));
            }
            retained = List.copyOf(keys);
        } catch (java.security.GeneralSecurityException | com.nimbusds.jose.JOSEException exception) {
            throw new IllegalArgumentException("Invalid JWT signing key pair", exception);
        }
    }
    public RSAKey signingKey() { return active; }
    public Map<String, Object> publicJwks() {
        List<JWK> keys = new ArrayList<>();
        keys.add(active.toPublicJWK());
        retained.stream().filter(key -> clock.instant().isBefore(key.removeAfter()))
                .forEach(key -> keys.add(key.key()));
        return new JWKSet(keys).toJSONObject(true);
    }
}
