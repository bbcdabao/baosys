package baosys.manager.app.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import baosys.manager.OAuth2TestClient;
import baosys.manager.OAuth2TestClient.LoginRequest;
import baosys.manager.PostgreSqlIntegrationTest;
import baosys.manager.api.module.payloads.UserCreateRequest;
import baosys.manager.api.module.responses.UserResponse;
import baosys.manager.api.module.payloads.UserUpdateRequest;
import baosys.manager.app.error.BusinessException;
import baosys.manager.app.services.UserAppService;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AccountLifecycleIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private OAuth2TestClient auth;

    @Autowired
    private UserAppService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void shouldInitializeVersionOneAndRetainAuditAfterDeletion() {
        assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).isEqualTo(
            "manager"
        );
        assertThat(
            jdbc.queryForList(
                "SELECT version FROM flyway_schema_history_manager WHERE success AND type = 'SQL'",
                String.class
            )
        ).containsExactly("1");
        UserResponse user = createUser();
        assertThat(user.enabled()).isTrue();
        var login = auth.login(loginRequest(user));
        users.update(
            new UserUpdateRequest(
                user.id(),
                user.name(),
                "",
                Set.of("MGR", "MGR:user:list"),
                false,
                "MGR"
            ),
            9001L
        );
        assertThatThrownBy(() -> auth.login(loginRequest(user))).isInstanceOf(
            org.springframework.security.oauth2.core.OAuth2AuthenticationException.class
        );
        assertThatThrownBy(() -> auth.refresh(login.refreshToken())).isInstanceOf(
            org.springframework.security.oauth2.core.OAuth2AuthenticationException.class
        );
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_login WHERE user_info_id = ?",
                Integer.class,
                user.id()
            )
        ).isZero();
        users.delete(user.id(), 9001L);
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM permission_change_audit WHERE target_user_id = ?",
                Integer.class,
                user.id()
            )
        ).isEqualTo(3);
        assertThat(
            jdbc.queryForList(
                "SELECT operator_user_id FROM permission_change_audit WHERE target_user_id = ?",
                Long.class,
                user.id()
            )
        ).containsOnly(9001L);
    }

    @Test
    void shouldAllowOnlyOneConcurrentRefreshAndProjectOfficialExpiry() throws Exception {
        UserResponse user = createUser();
        var login = auth.login(loginRequest(user));
        Object originalExpiry = jdbc.queryForObject(
            "SELECT l.expires_at FROM user_login l JOIN oauth2_authorization a ON a.id=l.authorization_id WHERE l.user_info_id = ? AND a.authorization_grant_type='authorization_code'",
            Object.class,
            user.id()
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        Callable<Boolean> refresh = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Start timed out");
            }
            try {
                auth.refresh(login.refreshToken());
                return true;
            } catch (
                org.springframework.security.oauth2.core.OAuth2AuthenticationException denied
            ) {
                return false;
            }
        };
        try {
            var first = executor.submit(refresh);
            var second = executor.submit(refresh);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(
                java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))
            ).containsExactlyInAnyOrder(true, false);
            assertThat(
                jdbc.queryForObject(
                    "SELECT l.expires_at FROM user_login l JOIN oauth2_authorization a ON a.id=l.authorization_id WHERE l.user_info_id = ? AND a.authorization_grant_type='authorization_code'",
                    Object.class,
                    user.id()
                )
            ).isNotNull();
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            users.delete(user.id(), 9001L);
        }
    }

    @Test
    void shouldLeaveNoRefreshSessionWhenDisableRacesWithRefresh() throws Exception {
        UserResponse user = createUser();
        var login = auth.login(loginRequest(user));
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var refresh = executor.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Start timed out");
                }
                try {
                    auth.refresh(login.refreshToken());
                } catch (
                    org.springframework.security.oauth2.core.OAuth2AuthenticationException denied
                ) {
                    /* disable won */
                }
                return null;
            });
            var disable = executor.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Start timed out");
                }
                users.update(
                    new UserUpdateRequest(
                        user.id(),
                        user.name(),
                        "",
                        Set.of("MGR", "MGR:user:list"),
                        false,
                        "MGR"
                    ),
                    9001L
                );
                return null;
            });
            start.countDown();
            refresh.get(15, TimeUnit.SECONDS);
            disable.get(15, TimeUnit.SECONDS);
            assertThat(
                jdbc.queryForObject(
                    "SELECT enabled FROM user_info WHERE id = ?",
                    Boolean.class,
                    user.id()
                )
            ).isFalse();
            assertThat(
                jdbc.queryForObject(
                    "SELECT count(*) FROM user_login WHERE user_info_id = ?",
                    Integer.class,
                    user.id()
                )
            ).isZero();
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            users.delete(user.id(), 9001L);
        }
    }

    private UserResponse createUser() {
        return users.create(
            new UserCreateRequest(
                "lifecycle-" + UUID.randomUUID(),
                "secure123",
                Set.of("MGR", "MGR:user:list"),
                null,
                "MGR"
            ),
            9001L
        );
    }

    private LoginRequest loginRequest(UserResponse user) {
        return new LoginRequest(user.name(), "secure123", "integration-test");
    }
}
