package baosys.common.security.gateway;

import java.time.Clock;
import java.time.Instant;
import io.netty.channel.ChannelOption;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.security.oauth2.jwt.*;
import reactor.core.publisher.*;
import reactor.netty.http.client.HttpClient;
import baosys.common.security.config.SecurityProperties;
import baosys.common.security.jwt.*;

/** Nonblocking, single-flight, request-driven JWKS refresh. No inner remote key source. */
public final class LazyReactiveJwksJwtDecoder implements ReactiveJwtDecoder {
    private final SecurityProperties properties;
    private final WebClient client;
    private final String uri;
    private final Clock clock;
    private NimbusReactiveJwtDecoder snapshot;
    private Instant expiresAt = Instant.MIN;
    private Instant retryAt = Instant.MIN;
    private Mono<NimbusReactiveJwtDecoder> inFlight;
    public LazyReactiveJwksJwtDecoder(SecurityProperties properties) { this(properties, Clock.systemUTC()); }
    public LazyReactiveJwksJwtDecoder(SecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.uri = JwtVerification.jwksUri(properties.getJwt()).toString();
        var config = properties.getJwt().getJwks();
        var http = HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                (int) config.getConnectTimeout().toMillis()).responseTimeout(config.getReadTimeout());
        this.client = WebClient.builder().clientConnector(new ReactorClientHttpConnector(http)).build();
    }
    @Override public Mono<Jwt> decode(String token) {
        return Mono.defer(this::current).flatMap(decoder -> decoder.decode(token));
    }
    private synchronized Mono<NimbusReactiveJwtDecoder> current() {
        Instant now = clock.instant();
        if (snapshot != null && now.isBefore(expiresAt)) return Mono.just(snapshot);
        if (inFlight != null) return inFlight;
        if (now.isBefore(retryAt)) return Mono.error(new JwtException("JWKS refresh is in failure backoff"));
        inFlight = client.get().uri(uri).retrieve().bodyToMono(String.class)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Empty JWKS response")))
                .timeout(properties.getJwt().getJwks().getReadTimeout())
                .map(JwksSnapshot::parse)
                .map(keys -> {
                    var decoder = NimbusReactiveJwtDecoder.withJwkSource(jwt -> Flux.fromIterable(keys.getKeys())).build();
                    decoder.setJwtValidator(JwtVerification.validator(properties));
                    return decoder;
                })
                .doOnNext(this::refreshed)
                .doOnError(this::failed)
                .onErrorMap(error -> new JwtException("Unable to refresh JWT verification keys", error))
                .cache();
        return inFlight;
    }
    private synchronized void refreshed(NimbusReactiveJwtDecoder decoder) {
        snapshot = decoder;
        expiresAt = clock.instant().plus(properties.getJwt().getJwks().getCacheTtl());
        retryAt = Instant.MIN;
        inFlight = null;
        LoggerFactory.getLogger(getClass()).debug("JWKS snapshot refreshed");
    }
    private synchronized void failed(Throwable error) {
        retryAt = clock.instant().plus(properties.getJwt().getJwks().getFailureBackoff());
        inFlight = null;
        LoggerFactory.getLogger(getClass()).warn("JWKS refresh failed; requests rejected until retry window");
    }
}
