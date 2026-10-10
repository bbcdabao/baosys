package baosys.common.security.jwt;

import java.util.HashSet;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.*;

/** Public-only snapshot; never merges previously cached keys. */
public final class JwksSnapshot {
    private JwksSnapshot() { }
    public static JWKSet parse(String json) {
        try {
            var keys = JWKSet.parse(json).getKeys().stream()
                    .filter(key -> key instanceof RSAKey rsa && rsa.size() >= 2048)
                    .filter(key -> key.getKeyUse() == null || KeyUse.SIGNATURE.equals(key.getKeyUse()))
                    .filter(key -> key.getAlgorithm() == null || JWSAlgorithm.RS256.equals(key.getAlgorithm()))
                    .filter(key -> key.getKeyOperations() == null || key.getKeyOperations().contains(KeyOperation.VERIFY))
                    .map(JWK::toPublicJWK).toList();
            var ids = new HashSet<String>();
            for (var key : keys) {
                if (key.getKeyID() == null || key.getKeyID().isBlank() || !ids.add(key.getKeyID()))
                    throw new IllegalArgumentException("JWKS signing keys require unique nonblank kid values");
            }
            if (keys.isEmpty()) throw new IllegalArgumentException("JWKS contains no usable RS256 signing keys");
            return new JWKSet(keys);
        } catch (java.text.ParseException exception) {
            throw new IllegalArgumentException("Invalid JWKS document", exception);
        }
    }
}
