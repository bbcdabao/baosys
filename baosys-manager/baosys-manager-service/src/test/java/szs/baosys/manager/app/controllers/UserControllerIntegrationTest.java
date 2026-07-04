package szs.baosys.manager.app.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockCookie;

import szs.baosys.manager.PostgreSqlIntegrationTest;
import szs.baosys.manager.api.enums.SystemType;
import szs.baosys.manager.infra.database.mapper.UserInfoMapper;
import szs.baosys.manager.infra.database.mapper.UserLoginMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserInfoMapper userMapper;

    @Autowired
    private UserLoginMapper loginMapper;

    @Test
    void shouldGetCurrentUserFromAccessTokenSubject() throws Exception {
        var admin = userMapper
                .findByUserNameAndSystemType("admin", SystemType.MANAGER)
                .orElseThrow();

        mockMvc.perform(get("/v1/users/me")
                        .with(jwt().jwt(token -> token
                                .subject(Long.toString(admin.getId()))
                                .claim("username", "ignored-token-username"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(admin.getId()))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.displayName").value("admin"))
                .andExpect(jsonPath("$.data.systemType").value("MANAGER"))
                .andExpect(jsonPath("$.data.permissions").isArray());
    }

    @Test
    void shouldRejectCurrentUserRequestWithoutAccessToken() throws Exception {
        mockMvc.perform(get("/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_001"));
    }

    @Test
    void shouldCreateQueryAndDeleteUserWithHashedPassword() throws Exception {
        String username = "user-" + UUID.randomUUID();
        String response = mockMvc.perform(post("/v1/users")
                        .with(jwt().jwt(token -> token.claim("username", "admin"))
                                .authorities(
                                        () -> "user:add",
                                        () -> "user:list",
                                        () -> "user:delete"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"%s",
                                  "pass":"secure123",
                                  "systemType":"SIMULATORS",
                                  "permissions":[]
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(username))
                .andExpect(jsonPath("$.data.systemType").value("SIMULATORS"))
                .andExpect(jsonPath("$.data.permissions").isArray())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var user = userMapper
                .findByUserNameAndSystemType(username, SystemType.SIMULATORS)
                .orElseThrow();
        assertThat(user.getUserPass()).isNotEqualTo("secure123");

        mockMvc.perform(get("/v1/users/me")
                        .with(jwt().jwt(token -> token
                                .subject(Long.toString(user.getId())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.systemType").value("SIMULATORS"));

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"secure123",
                                  "systemType":"SIMULATORS"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"secure123",
                                  "systemType":"MANAGER"
                                }
                                """.formatted(username)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_001"));
        assertThat(loginMapper.countActiveByUserId(
                user.getId(),
                OffsetDateTime.now(ZoneOffset.UTC))).isEqualTo(1);

        mockMvc.perform(get("/v1/login-sessions")
                        .with(jwt().jwt(token -> token.claim("username", "admin"))
                                .authorities(() -> "user:session:list"))
                        .param("userId", Long.toString(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].userId").value(user.getId()))
                .andExpect(jsonPath("$.data.items[0].userName").value(username))
                .andExpect(jsonPath("$.data.items[0].systemType").value("SIMULATORS"))
                .andExpect(jsonPath("$.data.items[0].expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/v1/users")
                        .with(jwt().jwt(token -> token.claim("username", "admin"))
                                .authorities(() -> "user:list"))
                        .param("name", username)
                        .param("systemType", "SIMULATORS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value(username))
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/v1/users")
                        .with(jwt().jwt(token -> token.claim("username", "admin"))
                                .authorities(() -> "user:list"))
                        .param("name", username)
                        .param("systemType", "MANAGER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(delete("/v1/users/{id}", user.getId())
                        .with(jwt().jwt(token -> token.claim("username", "admin"))
                                .authorities(() -> "user:delete")))
                .andExpect(status().isOk());
        assertThat(userMapper.selectById(user.getId())).isNull();
        assertThat(loginMapper.countActiveByUserId(
                user.getId(),
                OffsetDateTime.now(ZoneOffset.UTC))).isZero();
        assertThat(response).contains("\"code\":\"SUCCESS\"");
    }

    @Test
    void shouldRejectUnauthenticatedUserManagementRequest() throws Exception {
        mockMvc.perform(get("/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_001"));
    }

    @Test
    void shouldRejectUserWithoutListPermission() throws Exception {
        mockMvc.perform(get("/v1/users")
                        .with(jwt().jwt(token -> token.claim("username", "operator"))
                                .authorities(() -> "user:add")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403_001"));
    }

    @Test
    void shouldRejectInvalidPagination() throws Exception {
        mockMvc.perform(get("/v1/users")
                        .with(jwt().jwt(token -> token.claim("username", "admin"))
                                .authorities(() -> "user:list"))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_400_001"));
    }

    @Test
    void shouldAcceptAccessTokenIssuedByLoginEndpoint() throws Exception {
        var loginResult = mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"Admin123!test","systemType":"MANAGER"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        MockCookie accessCookie = (MockCookie) loginResult.getResponse().getCookie("access_token");
        assertThat(accessCookie).isNotNull();

        mockMvc.perform(get("/v1/users")
                        .header("Authorization", "Bearer " + accessCookie.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
