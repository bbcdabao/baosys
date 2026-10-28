package baosys.manager.app.controllers;

import baosys.common.security.jwt.JwtConstants;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import baosys.manager.OAuth2TestClient;
import baosys.manager.OAuth2TestClient.LoginRequest;
import baosys.manager.PostgreSqlIntegrationTest;
import baosys.manager.api.dto.*;
import baosys.manager.app.services.*;
import baosys.manager.infra.database.mapper.*;
import com.nimbusds.jwt.SignedJWT;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    OAuth2TestClient auth;

    @Autowired
    UserAppService users;

    @Autowired
    UserInfoMapper userMapper;

    @Autowired
    UserLoginMapper loginMapper;

    String adminToken() {
        return auth.login(new LoginRequest("admin", "Admin123!test", "test")).accessToken();
    }

    @Test
    void currentUserResolvesLoginSubjectToUser() throws Exception {
        var admin = userMapper.findByUserName("admin").orElseThrow();
        var result = auth.login(new LoginRequest("admin", "Admin123!test", "test"));
        // Deliberately obtain a session whose id differs from the user's id.
        if (
            SignedJWT.parse(result.accessToken())
                .getJWTClaimsSet()
                .getSubject()
                .equals(admin.getId().toString())
        ) {
            result = auth.login(new LoginRequest("admin", "Admin123!test", "test-2"));
        }
        long loginId = Long.parseLong(
            SignedJWT.parse(result.accessToken()).getJWTClaimsSet().getSubject()
        );
        assertThat(loginId).isNotEqualTo(admin.getId());
        assertThat(loginMapper.selectById(loginId).getUserInfoId()).isEqualTo(admin.getId());
        mvc.perform(get("/v1/users/me").header("Authorization", "Bearer " + result.accessToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value(admin.getId()))
            .andExpect(jsonPath("$.data.permissions[0]").value("MGR"))
            .andExpect(jsonPath("$.data.systemTypes").doesNotExist());
        auth.logout(result.refreshToken());
        mvc.perform(
            get("/v1/users/me").header("Authorization", "Bearer " + result.accessToken())
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void createManagerAccountFilterAndDeleteThroughApi() throws Exception {
        String admin = adminToken();
        String name = "manager-" + UUID.randomUUID();
        mvc.perform(
            post("/v1/users")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"%s","pass":"secure123","permissions":["MGR","MGR:user:list"]}
                    """.formatted(name)
                )
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.permissions").value(org.hamcrest.Matchers.contains("MGR", "MGR:user:list")))
            .andExpect(jsonPath("$.data.systemTypes").doesNotExist());
        var user = userMapper.findByUserName(name).orElseThrow();
        assertThat(user.getUserPass()).isNotEqualTo("secure123");
        var login = auth.login(new LoginRequest(name, "secure123", "test"));
        var claims = SignedJWT.parse(login.accessToken()).getJWTClaimsSet();
        assertThat(claims.getClaims()).doesNotContainKey("scope");
        assertThat(claims.getStringListClaim(JwtConstants.PERMISSIONS_CLAIM)).containsExactly(
            "MGR",
            "MGR:user:list"
        );
        mvc.perform(
            get("/v1/users").header("Authorization", "Bearer " + login.accessToken())
        ).andExpect(status().isOk());
        mvc.perform(
            delete("/v1/users/" + user.getId()).header(
                "Authorization",
                "Bearer " + login.accessToken()
            )
        ).andExpect(status().isForbidden());
        mvc.perform(
            get("/v1/users")
                .param("name", name)
                .param("systemType", "MGR")
                .header("Authorization", "Bearer " + admin)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].permissions").value(org.hamcrest.Matchers.contains("MGR", "MGR:user:list")))
            .andExpect(jsonPath("$.data.items[0].systemTypes").doesNotExist());
        mvc.perform(
            get("/v1/login-sessions")
                .param("userId", user.getId().toString())
                .header("Authorization", "Bearer " + admin)
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].permissions").value(org.hamcrest.Matchers.contains("MGR", "MGR:user:list")))
            .andExpect(jsonPath("$.data.items[0].systemTypes").doesNotExist());
        mvc.perform(
            delete("/v1/users/" + user.getId()).header("Authorization", "Bearer " + admin)
        ).andExpect(status().isOk());
        assertThat(userMapper.findById(user.getId())).isEmpty();
    }

    @Test
    void subsystemEntryWithoutApiPermissionIsInsufficient() throws Exception {
        var user = users.create(
            new UserCreateRequest("entry-" + UUID.randomUUID(), "secure123", Set.of("MGR"), true),
            1L
        );
        var login = auth.login(new LoginRequest(user.name(), "secure123", null));
        mvc.perform(
            get("/v1/users").header("Authorization", "Bearer " + login.accessToken())
        ).andExpect(status().isForbidden());
        users.delete(user.id(), 1L);
    }

    @Test
    void rejectsOrphanPermissionAndGloballyDuplicateUsername() throws Exception {
        String admin = adminToken();
        mvc.perform(
            post("/v1/users")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"orphan\",\"pass\":\"secure123\",\"permissions\":[\"MGR:user:list\"]}"
                )
        ).andExpect(status().isBadRequest());
        mvc.perform(
            post("/v1/users")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"admin\",\"pass\":\"secure123\",\"permissions\":[\"MGR\"]}")
        ).andExpect(status().isConflict());
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/v1/users/me")).andExpect(status().isUnauthorized());
    }
}
