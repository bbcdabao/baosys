package baosys.common.security.resource;

import java.net.URI;
import java.net.http.*;
import java.time.Clock;
import java.time.Instant;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.*;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.*;
import baosys.common.security.config.SecurityProperties;
import baosys.common.security.jwt.*;

/** Servlet adapter; network access exists only in the expired-cache branch. */
public final class LazyJwksJwtDecoder implements JwtDecoder {
    private final SecurityProperties properties;
    private final URI uri;
    private final HttpClient client;
    private final Clock clock;
    private NimbusJwtDecoder snapshot;
    private Instant expiresAt = Instant.MIN;
    private Instant retryAt = Instant.MIN;
    public LazyJwksJwtDecoder(SecurityProperties properties) { this(properties, Clock.systemUTC()); }
    public LazyJwksJwtDecoder(SecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.uri = JwtVerification.jwksUri(properties.getJwt());
        this.client = HttpClient.newBuilder().connectTimeout(properties.getJwt().getJwks().getConnectTimeout()).build();
    }
    @Override public Jwt decode(String token) { return current().decode(token); }
    // Serializes refresh only; signature validation runs outside the monitor.
    private synchronized NimbusJwtDecoder current() {
        Instant now = clock.instant();
        if (snapshot != null && now.isBefore(expiresAt)) return snapshot;
        if (now.isBefore(retryAt)) throw new JwtException("JWKS refresh is in failure backoff");
        try {
            var request = HttpRequest.newBuilder(uri).timeout(properties.getJwt().getJwks().getReadTimeout())
                    .header("Accept", "application/json").GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("JWKS endpoint returned non-200 status");
            var keys = JwksSnapshot.parse(response.body());
            var processor = new DefaultJWTProcessor<SecurityContext>();
            processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, new ImmutableJWKSet<>(keys)));
            processor.setJWTClaimsSetVerifier((claims, context) -> { });
            var decoder = new NimbusJwtDecoder(processor);
            decoder.setJwtValidator(JwtVerification.validator(properties));
            snapshot = decoder;
            expiresAt = clock.instant().plus(properties.getJwt().getJwks().getCacheTtl());
            retryAt = Instant.MIN;
            LoggerFactory.getLogger(getClass()).debug("JWKS snapshot refreshed: {} keys", keys.size());
            return snapshot;
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            retryAt = clock.instant().plus(properties.getJwt().getJwks().getFailureBackoff());
            LoggerFactory.getLogger(getClass()).warn("JWKS refresh failed; requests rejected until retry window");
            throw new JwtException("Unable to refresh JWT verification keys", exception);
        }
    }
}
