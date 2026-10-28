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

@SpringBootTest(properties = "baosys.shared-login.allowed-origins=http://localhost:8081")
@ActiveProfiles("test")
class SharedLoginIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    org.springframework.security.crypto.password.PasswordEncoder passwords;

    private static final String PREFIX = "/v1/browser-auth";

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
                jsonPath("$.data.redirectUrl").value("http://localhost:8081/orders?page=2#row")
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
            INSERT INTO user_info(user_name,user_pass,user_permissions,enabled,created_at,updated_at,created_by,updated_by)
            VALUES(?,?,?,true,now(),now(),'test','test') RETURNING id
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
