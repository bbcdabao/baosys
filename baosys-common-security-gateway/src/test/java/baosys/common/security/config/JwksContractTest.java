package baosys.common.security.config;

import static org.assertj.core.api.Assertions.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import com.sun.net.httpserver.HttpServer;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

class JwksContractTest {
    static final RSAKey A = key("a");
    static final RSAKey B = key("b");
    static final RSAKey C = key("c");
    final AtomicInteger requests = new AtomicInteger();
    final AtomicReference<String> response = new AtomicReference<>(jwks(A));
    final AtomicInteger status = new AtomicInteger(200);
    final MutableClock clock = new MutableClock();
    HttpServer server;
    SecurityProperties properties;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            requests.incrementAndGet();
            byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        properties = new SecurityProperties();
        properties.getJwt().setJwkSetUri("http://127.0.0.1:" + server.getAddress().getPort() + "/jwks");
        properties.getJwt().setIssuerUri("trusted-issuer");
        properties.setSystemType("MANAGER");
    }
    @AfterEach void stop() { server.stop(0); }

    @Test void lazyTtlRotationAndReplacement() {
        var decode = remote();
        assertThat(requests).hasValue(0);
        decode.apply(token(A));
        decode.apply(token(A));
        response.set(jwks(A, B));
        assertThatThrownBy(() -> decode.apply(token(B))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decode.apply(sign(A, claims().build(), JWSAlgorithm.RS256, "unknown")))
                .isInstanceOf(JwtException.class);
        assertThat(requests).hasValue(1);
        clock.advance(Duration.ofMinutes(5));
        assertThat(requests).hasValue(1);
        decode.apply(token(B));
        decode.apply(token(A));
        assertThat(requests).hasValue(2);
        response.set(jwks(B, C));
        clock.advance(Duration.ofMinutes(5));
        decode.apply(token(C));
        assertThatThrownBy(() -> decode.apply(token(A))).isInstanceOf(JwtException.class);
        assertThat(requests).hasValue(3);
    }
    @Test void failureBackoffRejectsStaleKeysAndDoesNotExtendTtl() {
        var decode = remote();
        decode.apply(token(A));
        status.set(503);
        clock.advance(Duration.ofMinutes(5));
        assertThatThrownBy(() -> decode.apply(token(A))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decode.apply(token(A))).isInstanceOf(JwtException.class);
        assertThat(requests).hasValue(2);
        clock.advance(Duration.ofSeconds(5));
        status.set(200);
        decode.apply(token(A));
        assertThat(requests).hasValue(3);
    }
    @Test void emptyAndMalformedSnapshotsAreFailures() {
        var decode = remote();
        response.set("{\"keys\":[]}");
        assertThatThrownBy(() -> decode.apply(token(A))).isInstanceOf(JwtException.class);
        response.set("not-json");
        clock.advance(Duration.ofSeconds(5));
        assertThatThrownBy(() -> decode.apply(token(A))).isInstanceOf(JwtException.class);
        response.set(jwks(A));
        clock.advance(Duration.ofSeconds(5));
        decode.apply(token(A));
        assertThat(requests).hasValue(3);
    }
    @Test void concurrentRefreshIsSingleFlight() throws Exception {
        var decode = remote();
        var executor = Executors.newFixedThreadPool(12);
        String token = token(A);
        try {
            for (int round = 0; round < 2; round++) {
                var gate = new CountDownLatch(1);
                var futures = new ArrayList<Future<Jwt>>();
                for (int i = 0; i < 12; i++) futures.add(executor.submit(() -> { gate.await(); return decode.apply(token); }));
                gate.countDown();
                for (var future : futures) assertThat(future.get(10, TimeUnit.SECONDS).getSubject()).isEqualTo("user-1");
                assertThat(requests).hasValue(round + 1);
                clock.advance(Duration.ofMinutes(5));
            }
        } finally { executor.shutdownNow(); }
    }
    @Test void localKeyNeverFetchesEvenAfterRotationAndFailures() throws Exception {
        properties.getJwt().setPublicKey(Base64.getEncoder().encodeToString(A.toRSAPublicKey().getEncoded()));
        var decode = configured();
        decode.apply(token(A));
        for (RSAKey key : List.of(B, C)) {
            response.set(jwks(key));
            clock.advance(Duration.ofHours(2));
            assertThatThrownBy(() -> decode.apply(token(key))).isInstanceOf(JwtException.class);
            decode.apply(token(A));
        }
        assertThat(requests).hasValue(0);
    }
    @Test void badLocalConfigurationNeverFallsBack() {
        for (String bad : List.of("", "broken")) {
            properties.getJwt().setPublicKey(bad);
            assertThatThrownBy(this::configured).isInstanceOf(RuntimeException.class);
        }
        properties.getJwt().setPublicKey(null);
        properties.getJwt().setPublicKeyLocation("file:/nonexistent-baosys-public.pem");
        assertThatThrownBy(this::configured).isInstanceOf(RuntimeException.class);
        assertThat(requests).hasValue(0);
    }
    @Test void allModesEnforceSignatureAndRequiredClaims() throws Exception {
        var remote = remote();
        properties.getJwt().setPublicKey(Base64.getEncoder().encodeToString(A.toRSAPublicKey().getEncoded()));
        var local = configured();
        for (var decode : List.of(local, remote)) {
            decode.apply(token(A));
            var invalid = List.of(
                claims().issuer("wrong").build(),
                claims().claim("systemType", "OTHER").build(),
                claims().claim("systemType", null).build(),
                claims().expirationTime(Date.from(Instant.now().minusSeconds(120))).build(),
                claims().notBeforeTime(Date.from(Instant.now().plusSeconds(120))).build(),
                claims().expirationTime(null).build(),
                claims().subject(null).build());
            for (var claims : invalid)
                assertThatThrownBy(() -> decode.apply(sign(A, claims, JWSAlgorithm.RS256, "a"))).isInstanceOf(JwtException.class);
            assertThatThrownBy(() -> decode.apply(sign(A, claims().build(), JWSAlgorithm.RS512, "a"))).isInstanceOf(JwtException.class);
            assertThatThrownBy(() -> decode.apply(sign(B, claims().build(), JWSAlgorithm.RS256, "a"))).isInstanceOf(JwtException.class);
        }
        assertThat(requests).hasValue(1);
    }

        Function<String, Jwt> remote() {
        var decoder = new baosys.common.security.gateway.LazyReactiveJwksJwtDecoder(properties, clock);
        return token -> decoder.decode(token).block(Duration.ofSeconds(10));
    }
    Function<String, Jwt> configured() {
        var decoder = new ReactiveJwtDecoderConfiguration().reactiveJwtDecoder(properties);
        return token -> decoder.decode(token).block(Duration.ofSeconds(10));
    }

    static JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer("trusted-issuer").subject("user-1").claim("systemType", "MANAGER")
                .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(1800)));
    }
    static String token(RSAKey key) { return sign(key, claims().build(), JWSAlgorithm.RS256, key.getKeyID()); }
    static String sign(RSAKey key, JWTClaimsSet claims, JWSAlgorithm algorithm, String kid) {
        try {
            var jwt = new SignedJWT(new JWSHeader.Builder(algorithm).keyID(kid).build(), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    static RSAKey key(String id) {
        try { return new RSAKeyGenerator(2048).keyID(id).generate(); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    static String jwks(RSAKey... keys) { return new JWKSet(Arrays.asList(keys)).toString(); }
    static class MutableClock extends Clock {
        private Instant now = Instant.now();
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
        void advance(Duration value) { now = now.plus(value); }
    }
}

