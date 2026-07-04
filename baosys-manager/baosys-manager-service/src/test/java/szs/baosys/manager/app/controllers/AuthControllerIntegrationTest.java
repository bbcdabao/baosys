package szs.baosys.manager.app.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockCookie;

import szs.baosys.manager.PostgreSqlIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldLoginWithDemoCredentials() throws Exception {
        var result = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"Admin123!test","systemType":"MANAGER"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .anyMatch(value -> value.startsWith("access_token=")
                        && value.contains("Max-Age=7200")
                        && value.contains("HttpOnly")
                        && value.contains("SameSite=Strict")
                        && value.contains("Path=/"))
                .anyMatch(value -> value.startsWith("refresh_token=")
                        && value.contains("Max-Age=2592000")
                        && value.contains("HttpOnly")
                        && value.contains("SameSite=Strict")
                        && value.contains(
                                "Path=/api/baosys-manager/v1/auth"));
    }

    @Test
    void shouldRefreshTokensUsingHttpOnlyCookie() throws Exception {
        var loginResult = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"Admin123!test","systemType":"MANAGER"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        MockCookie refreshCookie =
                (MockCookie) loginResult.getResponse().getCookie("refresh_token");

        assertThat(refreshCookie).isNotNull();
        String previousRefreshToken = refreshCookie.getValue();
        var refreshResult = mockMvc.perform(post("/v1/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();

        MockCookie rotatedRefreshCookie =
                (MockCookie) refreshResult.getResponse().getCookie("refresh_token");
        assertThat(rotatedRefreshCookie).isNotNull();
        assertThat(rotatedRefreshCookie.getValue())
                .isNotEqualTo(previousRefreshToken);
    }

    @Test
    void shouldRejectRefreshWithoutCookie() throws Exception {
        mockMvc.perform(post("/v1/auth/refresh"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403_001"));
    }

    @Test
    void shouldRevokeRefreshTokenAndClearCookiesOnLogout() throws Exception {
        var loginResult = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"Admin123!test","systemType":"MANAGER"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        MockCookie refreshCookie =
                (MockCookie) loginResult.getResponse().getCookie("refresh_token");

        var logoutResult = mockMvc.perform(post("/v1/auth/logout")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andReturn();

        assertThat(logoutResult.getResponse().getHeaders("Set-Cookie"))
                .anyMatch(value -> value.startsWith("access_token=")
                        && value.contains("Max-Age=0"))
                .anyMatch(value -> value.startsWith("refresh_token=")
                        && value.contains("Max-Age=0"));
        mockMvc.perform(post("/v1/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403_001"));
    }

    @Test
    void shouldRejectInvalidCredentials() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"wrong","systemType":"MANAGER"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_001"));
    }

    @Test
    void shouldRejectBlankUsername() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":"Admin123!test","systemType":"MANAGER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_400_001"));
    }

    @Test
    void shouldRejectMalformedJson() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_400_001"));
    }

    @Test
    void shouldIgnoreExpiredAccessCookieOnPublicLoginEndpoint() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .cookie(new MockCookie("access_token", "expired-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"Admin123!test","systemType":"MANAGER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    void shouldRejectLoginForAnotherSystem() throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"Admin123!test","systemType":"SIMULATION"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_001"));
    }
}
