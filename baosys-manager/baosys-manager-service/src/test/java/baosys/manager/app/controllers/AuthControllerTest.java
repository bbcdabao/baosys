package baosys.manager.app.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletResponse;

import baosys.common.security.config.SecurityProperties;
import baosys.manager.app.config.AuthProperties;
import baosys.manager.app.services.AuthAppService;
import baosys.manager.app.services.LoginResult;
import baosys.manager.api.dto.LoginRequest;

class AuthControllerTest {

    @Test
    void shouldOmitMaxAgeWhenCookieConfigurationIsZero() {
        AuthAppService authAppService = mock(AuthAppService.class);
        AuthProperties authProperties = new AuthProperties();
        authProperties.setAccessTokenCookieMaxAgeSeconds(0);
        authProperties.setRefreshTokenCookieMaxAgeSeconds(0);
        SecurityProperties securityProperties = new SecurityProperties();
        MockHttpServletResponse response = new MockHttpServletResponse();
        @SuppressWarnings("unchecked")
        ObjectProvider<HttpServletResponse> responseProvider =
                mock(ObjectProvider.class);
        when(responseProvider.getObject()).thenReturn(response);
        when(authAppService.login(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new LoginResult(
                        "access-token",
                        "refresh-token",
                        "csrf-token"));
        AuthController controller = new AuthController(
                authAppService,
                authProperties,
                securityProperties,
                responseProvider);

        controller.login(new LoginRequest(
                "admin",
                "password",
                "MANAGER",
                "test-client"));

        assertThat(response.getHeaders("Set-Cookie"))
                .hasSize(2)
                .allMatch(value -> !value.contains("Max-Age"));
    }
}
