package baosys.manager.app.controllers;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import baosys.common.security.jwt.JwtConstants;
import baosys.common.security.jwt.TokenIssuer;
import baosys.common.security.resource.SharedTokenCookies;
import baosys.manager.PostgreSqlIntegrationTest;
import baosys.manager.OAuth2TestClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest
@ActiveProfiles("test")
class SharedLoginAuthorizationIntegrationTest extends PostgreSqlIntegrationTest {
    private static final String CALLBACK = "http://localhost:8080/login/oauth2/code/baosys";
    private static final String VERIFIER = "a".repeat(64);
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenIssuer issuer;
    @Autowired OAuth2TestClient oauth;

    private String internalLogin() throws Exception {
        var response = mvc.perform(post("/v1/browser-auth/login")
            .header("Origin", "http://localhost:8080")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"admin\",\"password\":\"Admin123!test\"}"))
            .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getCookie("SESSION")).isNull();
        return response.getCookie(SharedTokenCookies.ACCESS).getValue();
    }

    private MockHttpServletRequestBuilder authorize() throws Exception {
        var challenge = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(VERIFIER.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        return get("/oauth2/authorize").accept(MediaType.TEXT_HTML)
            .queryParam("response_type", "code")
            .queryParam("client_id", JwtConstants.BROWSER_CLIENT_ID)
            .queryParam("redirect_uri", CALLBACK)
            .queryParam("scope", "openid profile")
            .queryParam("code_challenge", challenge)
            .queryParam("code_challenge_method", "S256");
    }

    @Test
    void relayedInternalTokenAuthorizesPkceWithoutFormLoginAndUsesAccountIdentity() throws Exception {
        internalLogin(); // 确保登录 ID 不碰巧等于账户 ID。
        var token = internalLogin();
        var loginId = Long.valueOf(SignedJWT.parse(token).getJWTClaimsSet().getSubject());
        var accountId = jdbc.queryForObject("SELECT user_info_id FROM user_login WHERE id=?", Long.class, loginId);
        assertThat(loginId).isNotEqualTo(accountId);
        var response = mvc.perform(authorize().header("Authorization", "Bearer " + token))
            .andExpect(status().is3xxRedirection()).andReturn().getResponse();
        assertThat(response.getRedirectedUrl()).startsWith(CALLBACK + "?code=");
        var code = UriComponentsBuilder.fromUriString(response.getRedirectedUrl()).build().getQueryParams().getFirst("code");
        var result = mvc.perform(post("/oauth2/token").servletPath("/oauth2/token")
            .with(httpBasic(JwtConstants.BROWSER_CLIENT_ID, "test-secret"))
            .param("grant_type", "authorization_code").param("code", code)
            .param("redirect_uri", CALLBACK).param("code_verifier", VERIFIER))
            .andExpect(status().isOk()).andReturn().getResponse();
        var json = new ObjectMapper().readTree(result.getContentAsString());
        var id = SignedJWT.parse(json.get("id_token").asText());
        assertThat(id.getJWTClaimsSet().getSubject()).isEqualTo(accountId.toString());
        var access = SignedJWT.parse(json.get("access_token").asText());
        assertThat(jdbc.queryForObject("SELECT user_info_id FROM user_login WHERE id=?", Long.class,
            Long.parseLong(access.getJWTClaimsSet().getSubject()))).isEqualTo(accountId);
        assertThat(oauth.refresh(json.get("refresh_token").asText()).accessToken()).isNotBlank();

        // 不把内部 Token 换成独立的长期认证 Session；撤销后不能靠本次响应 Cookie 绕过。
        jdbc.update("DELETE FROM user_login WHERE id=?", loginId);
        var request = authorize().header("Authorization", "Bearer " + token).accept(MediaType.APPLICATION_JSON);
        if (response.getCookies().length > 0) request.cookie(response.getCookies());
        mvc.perform(request).andExpect(status().isUnauthorized());
        var noToken = authorize();
        if (response.getCookies().length > 0) noToken.cookie(response.getCookies());
        mvc.perform(noToken).andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("http://localhost:8080/login?redirect_url=")));
    }

    @Test
    void rejectsInvalidExpiredAndExternalTokensWithoutWeakeningProtocolEndpoints() throws Exception {
        mvc.perform(authorize().header("Authorization", "Bearer invalid").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
        var token = internalLogin();
        var subject = SignedJWT.parse(token).getJWTClaimsSet().getSubject();
        var expired = issuer.issueAccessToken("http://localhost:9090/baosys-manager",
            new TokenIssuer.AccessTokenSubject(subject, JwtConstants.BROWSER_CLIENT_ID, List.of("MGR")),
            Instant.now().minusSeconds(600), Instant.now().minusSeconds(300));
        mvc.perform(authorize().header("Authorization", "Bearer " + expired.getTokenValue()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
        var external = oauth.loginWithPkce(new OAuth2TestClient.LoginRequest("admin", "Admin123!test", "test"));
        mvc.perform(authorize().header("Authorization", "Bearer " + external.accessToken()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/oauth2/jwks")).andExpect(status().isOk());
        mvc.perform(post("/oauth2/token").servletPath("/oauth2/token")
            .header("Authorization", "Bearer " + token)
            .param("grant_type", "refresh_token").param("refresh_token", external.refreshToken()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    void directCookieAlsoWorksButCannotOverrideInvalidExplicitBearer() throws Exception {
        var token = internalLogin();
        var cookie = new jakarta.servlet.http.Cookie(SharedTokenCookies.ACCESS, token);
        mvc.perform(authorize().cookie(cookie)).andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(CALLBACK + "?code=")));
        mvc.perform(authorize().cookie(cookie).header("Authorization", "Bearer invalid").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
        jdbc.update("UPDATE user_login SET expires_at=now()-interval '1 minute' WHERE id=?",
            Long.parseLong(SignedJWT.parse(token).getJWTClaimsSet().getSubject()));
        mvc.perform(authorize().cookie(cookie).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
    }

    @Test
    void missingLoginUsesVueAndSharedLoginThenResumesOriginalPkceRequest() throws Exception {
        var staleToken = internalLogin();
        jdbc.update("DELETE FROM user_login WHERE id=?",
            Long.parseLong(SignedJWT.parse(staleToken).getJWTClaimsSet().getSubject()));
        String state = "state+with & reserved/字符";
        var response = mvc.perform(authorize().queryParam("state", state).queryParam("nonce", "nonce-value")
            .cookie(new jakarta.servlet.http.Cookie(SharedTokenCookies.ACCESS, staleToken)))
            .andExpect(status().is3xxRedirection()).andReturn().getResponse();
        var location = response.getRedirectedUrl();
        assertThat(location).startsWith("http://localhost:8080/login?redirect_url=");
        String encodedTarget = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("redirect_url");
        String target = java.net.URLDecoder.decode(encodedTarget, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(target).startsWith("http://localhost:9090/baosys-manager/oauth2/authorize?");
        var body = new ObjectMapper().writeValueAsString(java.util.Map.of(
            "username", "admin", "password", "Admin123!test", "redirectUrl", target));
        var login = mvc.perform(post("/v1/browser-auth/login")
            .header("Origin", "http://localhost:8080").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.redirectUrl").value(target))
            .andReturn().getResponse();
        var authorized = mvc.perform(get(java.net.URI.create(target)).contextPath("/baosys-manager")
            .servletPath("/oauth2/authorize").cookie(login.getCookies()).accept(MediaType.TEXT_HTML))
            .andExpect(status().is3xxRedirection()).andReturn().getResponse();
        var params = UriComponentsBuilder.fromUriString(authorized.getRedirectedUrl()).build().getQueryParams();
        assertThat(java.net.URLDecoder.decode(params.getFirst("state"), java.nio.charset.StandardCharsets.UTF_8))
            .isEqualTo(state);
        var result = mvc.perform(post("/oauth2/token").servletPath("/oauth2/token")
            .with(httpBasic(JwtConstants.BROWSER_CLIENT_ID, "test-secret"))
            .param("grant_type", "authorization_code").param("code", params.getFirst("code"))
            .param("redirect_uri", CALLBACK).param("code_verifier", VERIFIER))
            .andExpect(status().isOk()).andReturn().getResponse();
        var json = new ObjectMapper().readTree(result.getContentAsString());
        assertThat(SignedJWT.parse(json.get("id_token").asText()).getJWTClaimsSet().getStringClaim("nonce"))
            .isEqualTo("nonce-value");
        mvc.perform(get("/login").accept(MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("http://localhost:8080/login?")));
    }

    @Test
    void loginRejectsUntrustedAuthorizationReturnTargets() throws Exception {
        for (String target : List.of(
            "https://evil.example/oauth2/authorize?client_id=baosys-web",
            "http://localhost:9091/baosys-manager/oauth2/authorize",
            "http://localhost:9090/baosys-manager/oauth2/token",
            "http://localhost:9090/baosys-manager/oauth2/authorize#fragment")) {
            mvc.perform(post("/v1/browser-auth/login").header("Origin", "http://localhost:8080")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(java.util.Map.of(
                    "username", "admin", "password", "Admin123!test", "redirectUrl", target))))
                .andExpect(status().isBadRequest());
        }
    }
}
