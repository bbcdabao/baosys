package baosys.common.security.jwt;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import baosys.common.security.config.SecurityProperties;

/** Shared policy only: no web stack, network client or security chain. */
public final class JwtVerification {
    private JwtVerification() { }
    public static void validate(SecurityProperties properties) {
        var jwt = properties.getJwt();
        if (!List.of("RS256").equals(jwt.getAllowedAlgorithms()))
            throw new IllegalArgumentException("security.jwt.allowed-algorithms currently supports only [RS256]");
        if (jwt.getExpectedIssuer() == null || jwt.getExpectedIssuer().isBlank())
            throw new IllegalArgumentException("security.jwt issuer must not be blank");
        if (jwt.getClockSkew() == null || jwt.getClockSkew().isNegative())
            throw new IllegalArgumentException("security.jwt.clock-skew must be nonnegative");
    }
    /** Explicitly empty local values are errors, never remote fallback. */
    public static RSAPublicKey localKey(SecurityProperties.Jwt jwt) {
        if (jwt.getPublicKey() != null && jwt.getPublicKeyLocation() != null)
            throw new IllegalArgumentException("Configure only one of public-key and public-key-location");
        if (jwt.getPublicKey() != null) return JwtKeyLoader.loadPublicKey(jwt.getPublicKey());
        if (jwt.getPublicKeyLocation() == null) return null;
        String location = jwt.getPublicKeyLocation();
        if (!(location.startsWith("classpath:") || location.startsWith("file:")))
            throw new IllegalArgumentException("public-key-location must use classpath: or file:");
        try (var input = new DefaultResourceLoader().getResource(location).getInputStream()) {
            return JwtKeyLoader.loadPublicKey(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Cannot read security.jwt.public-key-location", exception);
        }
    }
    public static URI jwksUri(SecurityProperties.Jwt jwt) {
        if (jwt.getJwkSetUri() == null || jwt.getJwkSetUri().isBlank())
            throw new IllegalArgumentException("JWT requires a local public key or security.jwt.jwk-set-uri; issuer discovery is not enabled");
        URI uri = URI.create(jwt.getJwkSetUri());
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("security.jwt.jwk-set-uri must be an HTTP(S) endpoint without credentials or fragment");
        var cache = jwt.getJwks();
        positive(cache.getCacheTtl(), "cache-ttl");
        positive(cache.getFailureBackoff(), "failure-backoff");
        positive(cache.getConnectTimeout(), "connect-timeout");
        positive(cache.getReadTimeout(), "read-timeout");
        return uri;
    }
    private static void positive(Duration duration, String name) {
        if (duration == null || duration.toMillis() <= 0 || duration.toMillis() > Integer.MAX_VALUE)
            throw new IllegalArgumentException("security.jwt.jwks." + name + " must be positive and fit milliseconds in an integer");
    }
    public static OAuth2TokenValidator<Jwt> validator(SecurityProperties properties) {
        var config = properties.getJwt();
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            boolean valid = jwt.getExpiresAt() != null && jwt.getSubject() != null && !jwt.getSubject().isBlank()
                    && config.getExpectedIssuer().equals(jwt.getClaimAsString("iss"))
                    && (properties.getSystemType() == null || properties.getSystemType().isBlank()
                        || properties.getSystemType().trim().equals(jwt.getClaimAsString("systemType")));
            return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Required JWT claims are missing or invalid", null));
        };
        return new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(config.getClockSkew()), claims);
    }
}
