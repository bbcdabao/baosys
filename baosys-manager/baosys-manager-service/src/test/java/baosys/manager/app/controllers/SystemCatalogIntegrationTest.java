package baosys.manager.app.controllers;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import baosys.manager.PostgreSqlIntegrationTest;
import baosys.manager.api.dto.LoginRequest;
import baosys.manager.api.dto.UserCreateRequest;
import baosys.manager.app.config.SystemCatalog;
import baosys.manager.app.error.BusinessException;
import baosys.manager.app.error.InvalidCredentialsException;
import baosys.manager.app.services.AuthAppService;
import baosys.manager.app.services.UserAppService;
import baosys.common.security.jwt.JwtTokenService;

@SpringBootTest(properties = "spring.config.import=classpath:catalog-test.yml")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemCatalogIntegrationTest extends PostgreSqlIntegrationTest {
    @Autowired SystemCatalog catalog;
    @Autowired UserAppService users;
    @Autowired AuthAppService auth;
    @Autowired JwtTokenService tokens;
    @Autowired MockMvc mvc;

    @Test
    void configAloneEnablesNewSystemThroughDatabaseLoginAndRefresh() throws Exception {
        assertThat(catalog.systems()).extracting(system -> system.systemType()).containsExactly("MANAGER", "LAB");
        var user = users.create(new UserCreateRequest("lab-" + UUID.randomUUID(), "secure123",
                "LAB", Set.of("experiment:run"), true), 1L);
        assertThat(users.list(user.name(), "LAB", 0, 20).items()).hasSize(1);
        var login = auth.login(new LoginRequest(user.name(), "secure123", "LAB", null));
        var claims = SignedJWT.parse(login.accessToken()).getJWTClaimsSet();
        assertThat(claims.getStringClaim("systemType")).isEqualTo("LAB");
        assertThat(claims.getStringListClaim("permissions")).containsExactly("experiment:run");
        assertThat(auth.refresh(login.refreshToken()).accessToken()).isNotBlank();
        users.delete(user.id(), 1L);
    }

    @Test
    void rejectsUnknownSystemAndCrossSystemPermissions() {
        for (var request : List.of(
                new UserCreateRequest("unknown", "secure123", "UNKNOWN", Set.of(), true),
                new UserCreateRequest("lab", "secure123", "LAB", Set.of("user:list"), true))) {
            assertThatThrownBy(() -> users.create(request, 1L)).isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> auth.login(new LoginRequest("admin", "secure123", "UNKNOWN", null)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void catalogRequiresManagerTokenAndReturnsBothLanguages() throws Exception {
        mvc.perform(get("/v1/systems")).andExpect(status().isUnauthorized());
        String manager = tokens.generateAccessToken("1", "admin", "MANAGER", List.of(), Map.of());
        mvc.perform(get("/v1/systems").header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].systemType").value("LAB"))
                .andExpect(jsonPath("$.data[1].name['zh-CN']").value("实验系统"))
                .andExpect(jsonPath("$.data[1].permissions[0].name['en-US']").value("Run experiment"));
        String lab = tokens.generateAccessToken("1", "lab", "LAB", List.of(), Map.of());
        mvc.perform(get("/v1/systems").header("Authorization", "Bearer " + lab))
                .andExpect(status().isForbidden());
    }
}
