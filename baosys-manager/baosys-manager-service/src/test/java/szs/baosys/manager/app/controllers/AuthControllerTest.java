package szs.baosys.manager.app.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletResponse;

import szs.baosys.common.security.config.SzsSecurityProperties;
import szs.baosys.manager.app.config.AuthProperties;
import szs.baosys.manager.app.services.AuthAppService;
import szs.baosys.manager.app.services.LoginResult;
import szs.baosys.manager.api.dto.LoginRequest;
import szs.baosys.manager.api.enums.SystemType;

class AuthControllerTest {

    @Test
    void shouldOmitMaxAgeWhenCookieConfigurationIsZero() {
        AuthAppService authAppService = mock(AuthAppService.class);
        AuthProperties authProperties = new AuthProperties();
        authProperties.setAccessTokenCookieMaxAgeSeconds(0);
        authProperties.setRefreshTokenCookieMaxAgeSeconds(0);
        SzsSecurityProperties securityProperties = new SzsSecurityProperties();
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
                SystemType.MANAGER,
                "test-client"));

        assertThat(response.getHeaders("Set-Cookie"))
                .hasSize(2)
                .allMatch(value -> !value.contains("Max-Age"));
    }
}
