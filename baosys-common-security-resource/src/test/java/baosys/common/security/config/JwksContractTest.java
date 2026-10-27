package baosys.common.security.config;

import baosys.common.security.jwt.JwtConstants;
import static org.assertj.core.api.Assertions.*;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

class JwksContractTest {

    static final RSAKey A = key("a"),
        B = key("b");
    final AtomicInteger requests = new AtomicInteger();
    final AtomicReference<String> response = new AtomicReference<>(jwks(A));
    HttpServer server;
    OAuth2ResourceServerProperties properties;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            requests.incrementAndGet();
            byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        properties = new OAuth2ResourceServerProperties();
        properties
            .getJwt()
            .setJwkSetUri("http://127.0.0.1:" + server.getAddress().getPort() + "/jwks");
        properties.getJwt().setIssuerUri("auth-server");
        properties.getJwt().setAudiences(List.of(JwtConstants.ACCESS_TOKEN_AUDIENCE));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    Function<String, Jwt> decoder() throws Exception {
        var decoder = new JwtDecoderConfiguration().jwtDecoder(properties);
        return decoder::decode;
    }

    @Test
    void nativeJwksLoadsLazilyCachesAndDiscoversNewKid() throws Exception {
        var decode = decoder();
        assertThat(requests).hasValue(0);
        assertThat(
            decode.apply(sign(A, claims().build(), "at+jwt", "a", JWSAlgorithm.RS256)).getSubject()
        ).isEqualTo("9527");
        decode.apply(sign(A, claims().build(), "at+jwt", "a", JWSAlgorithm.RS256));
        assertThat(requests).hasValue(1);
        response.set(jwks(A, B));
        decode.apply(sign(B, claims().build(), "at+jwt", "b", JWSAlgorithm.RS256));
        decode.apply(sign(A, claims().build(), "at+jwt", "a", JWSAlgorithm.RS256));
        assertThatThrownBy(() ->
            decode.apply(sign(A, claims().build(), "at+jwt", "unknown", JWSAlgorithm.RS256))
        ).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsInvalidContractAndSignature() throws Exception {
        var decode = decoder();
        for (var bad : List.of(
            claims().issuer("wrong"),
            claims().audience("other"),
            claims().subject(""),
            claims().subject(" "),
            claims().jwtID(" "),
            claims().claim("client_id", " "),
            claims().jwtID(null),
            claims().claim("client_id", null),
            claims().expirationTime(null),
            claims().expirationTime(Date.from(Instant.now().minusSeconds(120))),
            claims().notBeforeTime(Date.from(Instant.now().plusSeconds(120))),
            claims().claim(JwtConstants.PERMISSIONS_CLAIM, "MGR"),
            claims().claim(JwtConstants.PERMISSIONS_CLAIM, List.of(42))
        )) {
            assertThatThrownBy(
                () -> decode.apply(sign(A, bad.build(), "at+jwt", "a", JWSAlgorithm.RS256)),
                "claims: %s",
                bad.build().toJSONObject()
            ).isInstanceOf(JwtException.class);
        }
        for (String type : List.of("JWT", "id+jwt", "application/at+jwt")) {
            assertThatThrownBy(() ->
                decode.apply(sign(A, claims().build(), type, "a", JWSAlgorithm.RS256))
            ).isInstanceOf(JwtException.class);
        }
        assertThatThrownBy(() ->
            decode.apply(sign(A, claims().build(), "at+jwt", null, JWSAlgorithm.RS256))
        ).isInstanceOf(JwtException.class);
        assertThatThrownBy(() ->
            decode.apply(sign(B, claims().build(), "at+jwt", "a", JWSAlgorithm.RS256))
        ).isInstanceOf(JwtException.class);
        assertThatThrownBy(() ->
            decode.apply(sign(A, claims().build(), "at+jwt", "a", JWSAlgorithm.RS512))
        ).isInstanceOf(JwtException.class);
    }

    @Test
    void failsClosedForMalformedJwksAndUnavailableServer() throws Exception {
        response.set("not-json");
        var decode = decoder();
        assertThatThrownBy(() ->
            decode.apply(sign(A, claims().build(), "at+jwt", "a", JWSAlgorithm.RS256))
        ).isInstanceOf(JwtException.class);
        server.stop(0);
        var offline = decoder();
        assertThatThrownBy(() ->
            offline.apply(sign(A, claims().build(), "at+jwt", "a", JWSAlgorithm.RS256))
        ).isInstanceOf(JwtException.class);
    }

    static JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder()
            .issuer("auth-server")
            .subject("9527")
            .audience(JwtConstants.ACCESS_TOKEN_AUDIENCE)
            .jwtID(UUID.randomUUID().toString())
            .claim("client_id", JwtConstants.BROWSER_CLIENT_ID)
            .claim(JwtConstants.PERMISSIONS_CLAIM, List.of("MGR", "MGR:user:list"))
            .issueTime(new Date())
            .expirationTime(Date.from(Instant.now().plusSeconds(1800)));
    }

    static String sign(RSAKey key, JWTClaimsSet claims, String type, String kid, JWSAlgorithm alg) {
        try {
            var jwt = new SignedJWT(
                new JWSHeader.Builder(alg).type(new JOSEObjectType(type)).keyID(kid).build(),
                claims
            );
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    static RSAKey key(String kid) {
        try {
            return new RSAKeyGenerator(2048).keyID(kid).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String jwks(RSAKey... keys) {
        return new JWKSet(
            Arrays.stream(keys)
                .map(RSAKey::toPublicJWK)
                .map(k -> (JWK) k)
                .toList()
        ).toString();
    }
}
