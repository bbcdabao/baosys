package baosys.manager;

import baosys.common.security.jwt.JwtConstants;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Exercises official endpoints, never invokes a custom signer or token service.
 */
public class OAuth2TestClient {

    private final MockMvc mvc;

    public OAuth2TestClient(MockMvc mvc) {
        this.mvc = mvc;
    }

    public record LoginRequest(String username, String password, String loginClient) {}

    public record Tokens(String accessToken, String refreshToken) {}

    public Tokens login(LoginRequest request) {
        return login(request, false);
    }

    public Tokens loginWithPkce(LoginRequest request) {
        return login(request, true);
    }

    private Tokens login(LoginRequest request, boolean pkce) {
        try {
            String verifier = UUID.randomUUID().toString() + UUID.randomUUID();
            String challenge = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                java.security.MessageDigest.getInstance("SHA-256").digest(
                    verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII)
                )
            );
            var authorizationParameters = new org.springframework.util.LinkedMultiValueMap<String, String>();
            var tokenParameters = new org.springframework.util.LinkedMultiValueMap<String, String>();
            if (pkce) {
                authorizationParameters.add("code_challenge", challenge);
                authorizationParameters.add("code_challenge_method", "S256");
                tokenParameters.add("code_verifier", verifier);
            }
            var login = mvc.perform(post("/v1/browser-auth/login")
                .header("Origin", "http://localhost:8080")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(java.util.Map.of(
                    "username", request.username(), "password", request.password()))))
                .andReturn();
            if (login.getResponse().getStatus() != 200) {
                throw new OAuth2AuthenticationException("invalid_grant");
            }
            var cookies = login.getResponse().getCookies();
            var authorization = mvc
                .perform(
                    get("/oauth2/authorize")
                        .cookie(cookies)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", JwtConstants.BROWSER_CLIENT_ID)
                        .queryParam(
                            "redirect_uri",
                            "http://localhost:8080/login/oauth2/code/baosys"
                        )
                        .queryParam("scope", "openid profile")
                        .queryParams(authorizationParameters)
                        .queryParam("state", UUID.randomUUID().toString())
                )
                .andExpect(status().is3xxRedirection())
                .andReturn();
            String location = authorization.getResponse().getRedirectedUrl();
            String code = UriComponentsBuilder.fromUriString(location)
                .build()
                .getQueryParams()
                .getFirst("code");
            if (code == null) {
                throw new IllegalStateException("Missing authorization code: " + location);
            }
            return tokens(
                mvc
                    .perform(
                        post("/oauth2/token")
                            .servletPath("/oauth2/token")
                            .with(httpBasic(JwtConstants.BROWSER_CLIENT_ID, "test-secret"))
                            .param("grant_type", "authorization_code")
                            .params(tokenParameters)
                            .param("code", code)
                            .param("redirect_uri", "http://localhost:8080/login/oauth2/code/baosys")
                    )
                    .andReturn()
            );
        } catch (OAuth2AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Tokens refresh(String token) {
        try {
            return tokens(
                mvc
                    .perform(
                        post("/oauth2/token")
                            .servletPath("/oauth2/token")
                            .with(httpBasic(JwtConstants.BROWSER_CLIENT_ID, "test-secret"))
                            .param("grant_type", "refresh_token")
                            .param("refresh_token", token)
                    )
                    .andReturn()
            );
        } catch (OAuth2AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void logout(String token) {
        try {
            mvc.perform(
                post("/oauth2/revoke")
                    .servletPath("/oauth2/revoke")
                    .with(httpBasic(JwtConstants.BROWSER_CLIENT_ID, "test-secret"))
                    .param("token", token)
                    .param("token_type_hint", "refresh_token")
            ).andExpect(status().isOk());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Tokens tokens(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        var json = new ObjectMapper().readTree(result.getResponse().getContentAsString());
        if (result.getResponse().getStatus() != 200) {
            throw new OAuth2AuthenticationException(json.path("error").asText("invalid_grant"));
        }
        if ("authorization_code".equals(result.getRequest().getParameter("grant_type"))) {
            org.assertj.core.api.Assertions.assertThat(json.hasNonNull("id_token")).isTrue();
            var idToken = com.nimbusds.jwt.SignedJWT.parse(json.get("id_token").asText());
            org.assertj.core.api.Assertions.assertThat(idToken.getJWTClaimsSet().getAudience())
                .containsExactly(JwtConstants.BROWSER_CLIENT_ID);
            org.assertj.core.api.Assertions.assertThat(idToken.getJWTClaimsSet().getClaims())
                .doesNotContainKey(JwtConstants.PERMISSIONS_CLAIM);
        }
        return new Tokens(json.get("access_token").asText(), json.get("refresh_token").asText());
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    public static class Configuration {

        @org.springframework.context.annotation.Bean
        OAuth2TestClient oauth2TestClient(MockMvc mvc) {
            return new OAuth2TestClient(mvc);
        }
    }
}
