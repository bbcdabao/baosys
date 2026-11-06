package baosys.manager.app.controllers;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import baosys.common.security.resource.SharedTokenCookies;
import baosys.manager.app.error.GlobalExceptionHandler;
import baosys.manager.infra.security.SharedLoginProperties;
import baosys.manager.app.services.BrowserAuthAppService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 验证接口注解继承后，HTTP 映射、Cookie 绑定和请求体校验保持兼容。
 */
class SharedLoginControllerTest {
    private BrowserAuthAppService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(BrowserAuthAppService.class);
        mvc = MockMvcBuilders.standaloneSetup(
            new SharedLoginController(
                service,
                new SharedLoginProperties("http://localhost:8080", false)
            )
        ).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void loginBindsCookieAndPreservesResponseContract() throws Exception {
        when(service.login("admin", "password", "previous", null)).thenReturn(tokens());
        mvc.perform(
            post("/v1/browser-auth/login")
                .cookie(new Cookie(SharedTokenCookies.REFRESH, "previous"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"password\"}")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.redirectUrl").value("http://localhost:8080/baosys-manager/users-management"))
            .andExpect(jsonPath("$.data.accessToken").doesNotExist())
            .andExpect(cookie().value(SharedTokenCookies.ACCESS, "access"))
            .andExpect(cookie().httpOnly(SharedTokenCookies.ACCESS, true))
            .andExpect(cookie().value(SharedTokenCookies.REFRESH, "renewed"))
            .andExpect(header().string("Cache-Control", "no-store"));
        verify(service).login("admin", "password", "previous", null);
    }

    @Test
    void rejectsInvalidRequestBeforeCallingService() throws Exception {
        var mapper = new ObjectMapper();
        for (var request : List.of(
            Map.of("username", "", "password", "password"),
            Map.of("username", "admin", "password", " "),
            Map.of("username", "a".repeat(129), "password", "password"),
            Map.of("username", "admin", "password", "a".repeat(257)),
            Map.of("username", "admin", "password", "password", "redirectUrl", "a".repeat(8193))
        )) {
            mvc.perform(
                post("/v1/browser-auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_400_001"))
                .andExpect(cookie().doesNotExist(SharedTokenCookies.ACCESS));
        }
        verifyNoInteractions(service);
    }

    @Test
    void refreshAndLogoutInheritMappingsAndOptionalCookieBinding() throws Exception {
        when(service.refresh("previous")).thenReturn(tokens());
        mvc.perform(
            post("/v1/browser-auth/refresh")
                .cookie(new Cookie(SharedTokenCookies.REFRESH, "previous"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")
        ).andExpect(status().isOk())
            .andExpect(cookie().value(SharedTokenCookies.REFRESH, "renewed"));
        verify(service).refresh("previous");

        mvc.perform(
            post("/v1/browser-auth/logout")
                .cookie(new Cookie(SharedTokenCookies.REFRESH, "renewed"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")
        ).andExpect(status().isOk())
            .andExpect(cookie().maxAge(SharedTokenCookies.ACCESS, 0))
            .andExpect(cookie().maxAge(SharedTokenCookies.REFRESH, 0));
        verify(service).revoke("renewed");

        mvc.perform(
            post("/v1/browser-auth/logout")
                .contentType(MediaType.APPLICATION_JSON).content("{}")
        ).andExpect(status().isOk());
        verify(service).revoke(null);
    }

    private static BrowserAuthAppService.Tokens tokens() {
        return new BrowserAuthAppService.Tokens(
            "access", "renewed", Instant.now().plusSeconds(1800), Instant.now().plusSeconds(86400), "http://localhost:8080/baosys-manager/users-management"
        );
    }
}
