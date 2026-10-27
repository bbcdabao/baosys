package baosys.manager.infra.database;

import baosys.common.security.jwt.JwtConstants;

import static org.assertj.core.api.Assertions.assertThat;

import baosys.manager.PostgreSqlIntegrationTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class UserLoginCleanupFunctionIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldDeleteLoginRowsNotUpdatedForOneDay() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Long userId = createUser(now);
        String inactiveHash = "inactive-" + UUID.randomUUID();
        String activeHash = "active-" + UUID.randomUUID();
        insertLogin(userId, inactiveHash, now.minusDays(2));
        insertLogin(userId, activeHash, now);

        Integer deleted = jdbcTemplate.queryForObject(
            "SELECT cleanup_inactive_user_login_sessions()",
            Integer.class
        );

        assertThat(deleted).isNotNull().isGreaterThanOrEqualTo(1);
        assertThat(countLogin(inactiveHash)).isZero();
        assertThat(countLogin(activeHash)).isOne();
    }

    private Long createUser(OffsetDateTime now) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO user_info (
                user_name,
                user_pass,
                user_permissions,
                created_at,
                updated_at,
                created_by,
                updated_by)
            VALUES (?, 'encoded', 'MGR', ?, ?, 'test', 'test')
            RETURNING id
            """,
            Long.class,
            "cleanup-test-" + UUID.randomUUID(),
            now,
            now
        );
    }

    private void insertLogin(Long userId, String refreshTokenHash, OffsetDateTime updatedAt) {
        jdbcTemplate.update(
            """
            INSERT INTO oauth2_authorization (
                id, registered_client_id, principal_name, authorization_grant_type)
            VALUES (?, ?, ?, 'authorization_code')
            """,
            refreshTokenHash,
            JwtConstants.BROWSER_CLIENT_ID,
            userId.toString()
        );
        jdbcTemplate.update(
            """
            INSERT INTO user_login (
                user_info_id,
                client_id,
                login_client,
                authorization_id,
                expires_at,
                created_at,
                updated_at,
                created_by,
                updated_by)
            VALUES (?, ?, 'test-client', ?, ?, ?, ?, 'test', 'test')
            """,
            userId,
            JwtConstants.BROWSER_CLIENT_ID,
            refreshTokenHash,
            updatedAt.plusDays(30),
            updatedAt,
            updatedAt
        );
    }

    private int countLogin(String refreshTokenHash) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_login WHERE authorization_id = ?",
            Integer.class,
            refreshTokenHash
        );
        return count == null ? 0 : count;
    }
}
