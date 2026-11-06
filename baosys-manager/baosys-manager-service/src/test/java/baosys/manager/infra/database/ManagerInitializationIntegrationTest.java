package baosys.manager.infra.database;

import static org.assertj.core.api.Assertions.*;

import baosys.manager.PostgreSqlIntegrationTest;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ManagerInitializationIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void initializesFinalSchemaInOneMigrationAndDoesNotRepeatIt() {
        String schema = "init_" + UUID.randomUUID().toString().replace("-", "");
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .schemas(schema)
            .defaultSchema(schema)
            .locations("classpath:db/migration")
            .load();
        try {
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
            assertThat(jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema=?",
                String.class, schema
            )).contains("user_info", "user_login", "permission_change_audit",
                "oauth2_authorization", "oauth2_authorization_consent",
                "spring_session", "spring_session_attributes");
            assertThat(jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema=? AND table_name='user_info'",
                String.class, schema
            )).contains("default_system_type").doesNotContain("system_type");
            assertThat(jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema=? AND table_name='user_login'",
                String.class, schema
            )).contains("client_id", "authorization_id", "authentication_session_id")
                .doesNotContain("refresh_token_hash");
            assertThat(jdbc.queryForList(
                "SELECT data_type FROM information_schema.columns WHERE table_schema=? AND " +
                    "((table_name='user_info' AND column_name='user_permissions') OR " +
                    "(table_name='permission_change_audit' AND column_name IN ('before_permissions','after_permissions')))",
                String.class, schema
            )).containsExactlyInAnyOrder("text", "text", "text");
            String insert = "INSERT INTO " + schema +
                ".user_info(user_name,user_pass,user_permissions,default_system_type,created_at,updated_at,created_by,updated_by) " +
                "VALUES ('alice','hash','MGR,MGR:user:list','MGR',now(),now(),'test','test')";
            assertThatThrownBy(() -> jdbc.update(insert.replace("'MGR',now()", "NULL,now()")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(insert.replace("'MGR',now()", "' ',now()")))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            jdbc.update(insert);
            assertThatThrownBy(() -> jdbc.update(insert))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".user_info", Integer.class))
                .isEqualTo(1);
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }
}
