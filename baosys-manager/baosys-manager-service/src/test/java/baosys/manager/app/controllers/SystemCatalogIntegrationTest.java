package baosys.manager.app.controllers;

import baosys.common.security.jwt.JwtConstants;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import baosys.manager.OAuth2TestClient;
import baosys.manager.OAuth2TestClient.LoginRequest;
import baosys.manager.PostgreSqlIntegrationTest;
import baosys.manager.api.dto.UserCreateRequest;
import baosys.manager.app.config.SystemCatalog;
import baosys.manager.app.error.BusinessException;
import baosys.manager.app.services.UserAppService;
import com.nimbusds.jwt.SignedJWT;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.config.import=classpath:catalog-test.yml")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemCatalogIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    SystemCatalog catalog;

    @Autowired
    UserAppService users;

    @Autowired
    OAuth2TestClient auth;

    @Autowired
    MockMvc mvc;

    @Test
    void managerCatalogSupportsDatabaseLoginAndRefresh() throws Exception {
        assertThat(catalog.systems())
            .extracting(system -> system.systemType())
            .containsExactly("MGR");
        var user = users.create(
            new UserCreateRequest(
                "manager-" + UUID.randomUUID(),
                "secure123",
                Set.of("MGR", "MGR:user:list"),
                true
            ),
            1L
        );
        assertThat(users.list(user.name(), "MGR", 0, 20).items()).hasSize(1);
        var login = auth.login(new LoginRequest(user.name(), "secure123", null));
        var claims = SignedJWT.parse(login.accessToken()).getJWTClaimsSet();
        assertThat(claims.getClaims()).doesNotContainKey("scope");
        assertThat(claims.getStringListClaim(JwtConstants.PERMISSIONS_CLAIM)).containsExactly(
            "MGR",
            "MGR:user:list"
        );
        assertThat(auth.refresh(login.refreshToken()).accessToken()).isNotBlank();
        users.delete(user.id(), 1L);
    }

    @Test
    void rejectsUnknownSystemAndOrphanPermissions() {
        for (var request : List.of(
            new UserCreateRequest("unknown", "secure123", Set.of("UNKNOWN"), true),
            new UserCreateRequest("orphan", "secure123", Set.of("MGR:user:list"), true)
        )) {
            assertThatThrownBy(() -> users.create(request, 1L)).isInstanceOf(
                BusinessException.class
            );
        }
        assertThatThrownBy(() ->
            auth.login(new LoginRequest("admin", "secure123", null))
        ).isInstanceOf(
            org.springframework.security.oauth2.core.OAuth2AuthenticationException.class
        );
    }

    @Test
    void catalogRequiresAuthenticationAndReturnsBothLanguages() throws Exception {
        mvc.perform(get("/v1/systems")).andExpect(status().isUnauthorized());
        String manager = auth
            .login(new LoginRequest("admin", "Admin123!test", "test"))
            .accessToken();
        mvc.perform(get("/v1/systems").header("Authorization", "Bearer " + manager))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].systemType").value("MGR"))
            .andExpect(jsonPath("$.data[0].name['zh-CN']").value("后台管理"))
            .andExpect(jsonPath("$.data[0].permissions[0].name['en-US']").value("View users"));
        String token = auth.login(new LoginRequest("admin", "Admin123!test", "test")).accessToken();
        mvc.perform(get("/v1/systems").header("Authorization", "Bearer " + token)).andExpect(
            status().isOk()
        );
    }
}
