package baosys.managerweb;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@org.springframework.context.annotation.Import(TestOAuth2Configuration.class)
@SpringBootTest(
    classes = ManagerWebApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
class ManagerWebApplicationTest {

    @MockitoBean
    private ReactiveJwtDecoder jwtDecoder;

    @Test
    void contextLoads() {}

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.core.env.Environment environment;

    @Test
    void importsSharedOperationsAndLoggingDefaults() {
        org.assertj.core.api.Assertions.assertThat(environment.getProperty("server.shutdown")).isEqualTo("graceful");
        org.assertj.core.api.Assertions.assertThat(environment.getProperty("spring.lifecycle.timeout-per-shutdown-phase"))
            .isEqualTo("20s");
        org.assertj.core.api.Assertions.assertThat(environment.getProperty("management.endpoint.health.show-details"))
            .isEqualTo("never");
        org.assertj.core.api.Assertions.assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
            .isEqualTo("health,info,metrics,gateway");
        var root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        org.assertj.core.api.Assertions.assertThat(root.getAppender("CONSOLE")).isNotNull();
        org.assertj.core.api.Assertions.assertThat(root.getAppender("FILE")).isNotNull();
    }
}
