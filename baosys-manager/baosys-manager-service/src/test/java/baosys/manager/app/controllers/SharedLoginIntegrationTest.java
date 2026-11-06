package baosys.manager.app.controllers;

import baosys.common.security.jwt.JwtConstants;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import baosys.common.security.resource.SharedTokenCookies;
import baosys.manager.PostgreSqlIntegrationTest;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.config.import=classpath:shared-login-catalog-test.yml")
@ActiveProfiles("test")
class SharedLoginIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    org.springframework.security.crypto.password.PasswordEncoder passwords;

    private static final String PREFIX = "/v1/browser-auth";

    @Autowired
    baosys.manager.app.services.UserAppService users;

    @Test
    void defaultSubsystemIsPersistedAndExplicitDeepLinkTakesPrecedence() throws Exception {
        var user = users.create(new baosys.manager.api.module.payloads.UserCreateRequest(
            "nav-" + java.util.UUID.randomUUID(), "secure123", java.util.Set.of("MGR", "CAT"), true, "CAT"), 1L);
        try {
            assertThat(user.defaultSystemType()).isEqualTo("CAT");
            assertThat(jdbc.queryForObject("SELECT default_system_type FROM user_info WHERE id=?", String.class, user.id()))
                .isEqualTo("CAT");
            assertThat(users.list(user.name(), null, 0, 20).items().get(0).defaultSystemType()).isEqualTo("CAT");
            loginAs(user.name(), null, "http://localhost:8081/");
            loginAs(user.name(), "http://localhost:8080/login-sessions?page=2#row", "http://localhost:8080/login-sessions?page=2#row");
            var updated = users.update(new baosys.manager.api.module.payloads.UserUpdateRequest(
                user.id(), user.name(), "", java.util.Set.of("MGR", "CAT"), true, "MGR"), 1L);
            assertThat(updated.defaultSystemType()).isEqualTo("MGR");
            loginAs(user.name(), null, "http://localhost:8080/users-management");
            loginAs(user.name(), "http://localhost:8081/xxxx/zzzz/yyy?task=123#detail",
                "http://localhost:8081/xxxx/zzzz/yyy?task=123#detail");
        } finally {
            users.delete(user.id(), 1L);
        }
    }

    @Test
    void missingTargetEntryAndEmptyPermissionsAlwaysLeadToAuthorizedSystems() throws Exception {
        var user = users.create(new baosys.manager.api.module.payloads.UserCreateRequest(
            "fallback-" + java.util.UUID.randomUUID(), "secure123", java.util.Set.of("MGR"), true, "CAT"), 1L);
        try {
            String fallback = "http://localhost:8080/authorized-systems";
            loginAs(user.name(), null, fallback);
            loginAs(user.name(), "http://localhost:8081/task?id=1", fallback);
            users.update(new baosys.manager.api.module.payloads.UserUpdateRequest(
                user.id(), user.name(), "", java.util.Set.of(), true, "CAT"), 1L);
            var result = loginAs(user.name(), null, fallback);
            loginAs(user.name(), fallback, fallback);
            String token = result.getResponse().getCookie(SharedTokenCookies.ACCESS).getValue();
            mvc.perform(get("/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.permissions").isEmpty());
            mvc.perform(get("/v1/systems").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[1].uiBaseUrl").value("http://localhost:8081/"));
            mvc.perform(post(PREFIX + "/logout").header("Origin", "http://localhost:8080")
                .cookie(result.getResponse().getCookie(SharedTokenCookies.REFRESH))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(cookie().maxAge(SharedTokenCookies.ACCESS, 0));
        } finally {
            users.delete(user.id(), 1L);
        }
    }

    private MvcResult loginAs(String name, String target, String expected) throws Exception {
        var body = new java.util.HashMap<String, String>();
        body.put("username", name);
        body.put("password", "secure123");
        if (target != null) body.put("redirectUrl", target);
        return mvc.perform(post(PREFIX + "/login").header("Origin", "http://localhost:8080")
            .contentType(MediaType.APPLICATION_JSON).content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.redirectUrl").value(expected))
            .andExpect(cookie().httpOnly(SharedTokenCookies.ACCESS, true)).andReturn();
    }

    private MvcResult login(Cookie... cookies) throws Exception {
        var request = post(PREFIX + "/login");
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        return mvc
            .perform(
                request
                    .header("Origin", "http://localhost:8080")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"admin\",\"password\":\"Admin123!test\",\"redirectUrl\":\"http://localhost:8081/orders?page=2#row\"}"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.data.redirectUrl").value("http://localhost:8080/authorized-systems")
            )
            .andExpect(jsonPath("$.data.accessToken").doesNotExist())
            .andReturn();
    }

    @Test
    void loginRotatesRefreshAndLogoutRevokesPersistentGrant() throws Exception {
        var result = login();
        var access = result.getResponse().getCookie(SharedTokenCookies.ACCESS);
        var refresh = result.getResponse().getCookie(SharedTokenCookies.REFRESH);
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(access.getPath()).isEqualTo("/");
        assertThat(access.getDomain()).isNull();
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(refresh.getPath()).isEqualTo(SharedTokenCookies.REFRESH_PATH);
        var jwt = SignedJWT.parse(access.getValue());
        assertThat(jwt.getHeader().getType().toString()).isEqualTo("at+jwt");
        assertThat(jwt.getJWTClaimsSet().getClaims()).doesNotContainKey("scope");
        long id = Long.parseLong(jwt.getJWTClaimsSet().getSubject());
        assertThat(
            jdbc.queryForObject("SELECT count(*) FROM user_login WHERE id=?", Integer.class, id)
        ).isEqualTo(1);
        var renewed = mvc
            .perform(
                post(PREFIX + "/refresh")
                    .cookie(refresh)
                    .header("Origin", "http://localhost:8080")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie(SharedTokenCookies.REFRESH);
        assertThat(renewed.getValue()).isNotEqualTo(refresh.getValue());
        mvc.perform(
            post(PREFIX + "/refresh")
                .cookie(refresh)
                .header("Origin", "http://localhost:8080")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        ).andExpect(status().isUnauthorized());
        var loggedOut = mvc
            .perform(
                post(PREFIX + "/logout")
                    .cookie(renewed)
                    .header("Origin", "http://localhost:8080")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isOk())
            .andReturn();
        assertThat(
            loggedOut.getResponse().getCookie(SharedTokenCookies.ACCESS).getMaxAge()
        ).isZero();
        assertThat(
            jdbc.queryForObject("SELECT count(*) FROM user_login WHERE id=?", Integer.class, id)
        ).isZero();
    }

    @Test
    void newLoginReplacesPreviousBrowserGrant() throws Exception {
        var first = login();
        long id = Long.parseLong(
            SignedJWT.parse(first.getResponse().getCookie(SharedTokenCookies.ACCESS).getValue())
                .getJWTClaimsSet()
                .getSubject()
        );
        var second = login(first.getResponse().getCookie(SharedTokenCookies.REFRESH));
        assertThat(
            second.getResponse().getCookie(SharedTokenCookies.ACCESS).getValue()
        ).isNotEqualTo(first.getResponse().getCookie(SharedTokenCookies.ACCESS).getValue());
        assertThat(
            jdbc.queryForObject("SELECT count(*) FROM user_login WHERE id=?", Integer.class, id)
        ).isZero();
    }

    @Test
    void rejectsInvalidCredentialsOriginAndReturnAddress() throws Exception {
        for (String endpoint : new String[] { "login", "refresh", "logout" }) {
            mvc.perform(
                post(PREFIX + "/" + endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            ).andExpect(status().isForbidden());
            mvc.perform(
                post(PREFIX + "/" + endpoint)
                    .header("Origin", "http://localhost:8081")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            ).andExpect(status().isForbidden());
        }
        mvc.perform(
            post(PREFIX + "/login")
                .header("Origin", "http://localhost:8080")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"wrong\"}")
        )
            .andExpect(status().isUnauthorized())
            .andExpect(cookie().doesNotExist(SharedTokenCookies.ACCESS));
        mvc.perform(
            post(PREFIX + "/login")
                .header("Origin", "http://localhost:8080")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"admin\",\"password\":\"Admin123!test\",\"redirectUrl\":\"https://evil.test\"}"
                )
        )
            .andExpect(status().isBadRequest())
            .andExpect(cookie().doesNotExist(SharedTokenCookies.ACCESS));
    }

    @Test
    void switchingAccountsChangesSharedIdentityAndDefaultReturnIsManager() throws Exception {
        String name = "shared-" + java.util.UUID.randomUUID();
        Long userId = jdbc.queryForObject(
            """
            INSERT INTO user_info(user_name,user_pass,user_permissions,default_system_type,enabled,created_at,updated_at,created_by,updated_by)
            VALUES(?,?,?,'MGR',true,now(),now(),'test','test') RETURNING id
            """,
            Long.class,
            name,
            passwords.encode("Other123!test"),
            "MGR"
        );
        try {
            var first = login();
            var result = mvc
                .perform(
                    post(PREFIX + "/login")
                        .header("Origin", "http://localhost:8080")
                        .cookie(first.getResponse().getCookie(SharedTokenCookies.REFRESH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"password\":\"Other123!test\"}")
                )
                .andExpect(status().isOk())
                .andExpect(
                    jsonPath("$.data.redirectUrl").value("http://localhost:8080/users-management")
                )
                .andReturn();
            var jwt = SignedJWT.parse(
                result.getResponse().getCookie(SharedTokenCookies.ACCESS).getValue()
            );
            assertThat(
                jdbc.queryForObject(
                    "SELECT user_info_id FROM user_login WHERE id=?",
                    Long.class,
                    Long.parseLong(jwt.getJWTClaimsSet().getSubject())
                )
            ).isEqualTo(userId);
            assertThat(jwt.getJWTClaimsSet().getStringListClaim(JwtConstants.PERMISSIONS_CLAIM)).containsExactly(
                "MGR"
            );
        } finally {
            jdbc.update("DELETE FROM user_info WHERE id=?", userId);
        }
    }
}
