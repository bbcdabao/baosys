package baosys.manager.app.controllers;

import baosys.common.security.jwt.JwtConstants;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import baosys.manager.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
class AuthorizationServerIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    OAuth2TestClient client;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void issuesContractThroughOfficialEndpointsAndRevokesPersistentSession() throws Exception {
        var tokens = client.login(
            new OAuth2TestClient.LoginRequest("admin", "Admin123!test", "test")
        );
        var jwt = SignedJWT.parse(tokens.accessToken());
        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(jwt.getHeader().getType().toString()).isEqualTo("at+jwt");
        assertThat(jwt.getJWTClaimsSet().getClaims()).doesNotContainKey("scope");
        assertThat(jwt.getJWTClaimsSet().getJWTID()).isNotBlank();
        assertThat(jwt.getJWTClaimsSet().getStringClaim("client_id")).isEqualTo(JwtConstants.BROWSER_CLIENT_ID);
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly(JwtConstants.ACCESS_TOKEN_AUDIENCE);
        var json = mvc
            .perform(get("/oauth2/jwks"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        var key = (RSAKey) JWKSet.parse(json).getKeyByKeyId(jwt.getHeader().getKeyID());
        assertThat(key.isPrivate()).isFalse();
        assertThat(jwt.verify(new com.nimbusds.jose.crypto.RSASSAVerifier(key))).isTrue();
        long loginId = Long.parseLong(jwt.getJWTClaimsSet().getSubject());
        var sessionId = jdbc.queryForObject(
            "SELECT authentication_session_id FROM user_login WHERE id=?",
            String.class,
            loginId
        );
        assertThat(sessionId).isNotBlank();
        var renewed = client.refresh(tokens.refreshToken());
        assertThat(renewed.refreshToken()).isNotEqualTo(tokens.refreshToken());
        assertThat(SignedJWT.parse(renewed.accessToken()).getJWTClaimsSet().getSubject()).isEqualTo(
            Long.toString(loginId)
        );
        assertThatThrownBy(() -> client.refresh(tokens.refreshToken())).isInstanceOf(
            org.springframework.security.oauth2.core.OAuth2AuthenticationException.class
        );
        client.logout(renewed.refreshToken());
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_login WHERE id=?",
                Integer.class,
                loginId
            )
        ).isZero();
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM spring_session WHERE session_id=?",
                Integer.class,
                sessionId
            )
        ).isZero();
        assertThatThrownBy(() -> client.refresh(renewed.refreshToken())).isInstanceOf(
            org.springframework.security.oauth2.core.OAuth2AuthenticationException.class
        );
    }

    @Test
    void publishesMetadataAndAllowsAuthorizationWithoutPkceButRequiresClientCredentials()
        throws Exception {
        mvc.perform(get("/.well-known/openid-configuration"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issuer").value("http://localhost:9090/baosys-manager"))
            .andExpect(
                jsonPath("$.revocation_endpoint").value(
                    "http://localhost:9090/baosys-manager/oauth2/revoke"
                )
            );
        mvc.perform(
            get("/oauth2/authorize")
                .with(user("1"))
                .queryParam("response_type", "code")
                .queryParam("client_id", JwtConstants.BROWSER_CLIENT_ID)
                .queryParam("redirect_uri", "http://localhost:8080/login/oauth2/code/baosys")
                .queryParam("scope", "openid profile")
        )
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("code=")));
        mvc.perform(
            post("/oauth2/token")
                .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                .param("grant_type", "refresh_token")
                .param("refresh_token", "invalid")
        ).andExpect(status().isUnauthorized());
        mvc.perform(
            post("/login").param("username", "admin").param("password", "Admin123!test")
        ).andExpect(status().isForbidden());
    }
}
