package baosys.manager.infra.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.web.util.UriComponentsBuilder;

class SharedLoginNavigationTest {
    @Test
    void authorizationEntryUsesCatalogLoginAndPreservesOnlyExactOAuthEndpoint() throws Exception {
        var subsystems = mock(SubsystemNavigation.class);
        when(subsystems.loginUrl()).thenReturn("http://localhost:8080/baosys-manager/login");
        var settings = AuthorizationServerSettings.builder().issuer("http://localhost:9090/baosys-manager").build();
        var navigation = new SharedLoginNavigation(new SharedLoginProperties("http://localhost:8080", false), settings, subsystems);
        var request = new MockHttpServletRequest("GET", "/baosys-manager/oauth2/authorize");
        request.setContextPath("/baosys-manager");
        request.addParameter("client_id", "test");
        request.addParameter("state", "a+b &c");
        var response = new MockHttpServletResponse();
        navigation.commence(request, response, null);
        assertThat(response.getRedirectedUrl()).startsWith("http://localhost:8080/baosys-manager/login?redirect_url=");
        String target = java.net.URLDecoder.decode(UriComponentsBuilder.fromUriString(response.getRedirectedUrl())
            .build().getQueryParams().getFirst("redirect_url"), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(target).startsWith("http://localhost:9090/baosys-manager/oauth2/authorize?");
        assertThat(navigation.destination(target, "MGR", Set.of())).isEqualTo(target);
        verify(subsystems, never()).validateReturnUrl(target);
        String forged = "http://localhost:9090/baosys-manager/oauth2/authorize/other?client_id=test";
        navigation.returnUrl(forged);
        verify(subsystems).validateReturnUrl(forged);
    }
}
