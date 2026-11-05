package baosys.manager.app.services.impl;

import static org.assertj.core.api.Assertions.*;

import baosys.manager.PostgreSqlIntegrationTest;
import baosys.manager.api.module.payloads.UserCreateRequest;
import baosys.manager.api.module.responses.UserResponse;
import baosys.manager.app.error.BusinessException;
import baosys.manager.app.services.BrowserAuthAppService;
import baosys.manager.app.services.UserAppService;
import baosys.manager.infra.security.AccountAuthenticator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class BrowserAuthLifecycleIntegrationTest extends PostgreSqlIntegrationTest {
    @Autowired BrowserAuthAppService auth;
    @Autowired BrowserLoginTransaction login;
    @Autowired AccountAuthenticator accounts;
    @Autowired UserAppService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired baosys.manager.infra.security.AccountAuthorizationService authorizations;
    @Autowired org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository clients;

    private UserResponse createUser() {
        return users.create(new UserCreateRequest("browser-" + UUID.randomUUID(), "secure123",
            Set.of("MGR", "MGR:user:list"), null), 9001L);
    }

    @Test
    void oauthProjectionUsesCodeThenActualTokenExpiryWithoutRefreshToken() {
        var user = createUser();
        try {
            var client = clients.findByClientId(baosys.common.security.jwt.JwtConstants.BROWSER_CLIENT_ID);
            var now = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
            var codeExpiry = now.plusSeconds(60);
            var authorization = org.springframework.security.oauth2.server.authorization.OAuth2Authorization
                .withRegisteredClient(client).principalName(Long.toString(user.id()))
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(new org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode("code", now, codeExpiry))
                .build();
            authorizations.save(authorization);
            assertThat(expiry(authorization.getId())).isEqualTo(codeExpiry);
            var accessExpiry = now.plusSeconds(300);
            var idExpiry = now.plusSeconds(600);
            var complete = org.springframework.security.oauth2.server.authorization.OAuth2Authorization.from(authorization)
                .accessToken(new org.springframework.security.oauth2.core.OAuth2AccessToken(
                    org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER, "access", now, accessExpiry))
                .token(new org.springframework.security.oauth2.core.oidc.OidcIdToken("id", now, idExpiry,
                    java.util.Map.of("sub", Long.toString(user.id())))).build();
            authorizations.save(complete);
            assertThat(expiry(authorization.getId())).isEqualTo(idExpiry);
        } finally { users.delete(user.id(), 9001L); }
    }

    private java.time.Instant expiry(String authorizationId) {
        return jdbc.queryForObject("SELECT expires_at FROM user_login WHERE authorization_id=?",
            java.time.OffsetDateTime.class, authorizationId).toInstant();
    }

    @Test
    void switchesAccountsInBothDirectionsWithoutDeadlock() throws Exception {
        var a = createUser();
        var b = createUser();
        var loginA = auth.login(a.name(), "secure123", null);
        var loginB = auth.login(b.name(), "secure123", null);
        var verifiedA = accounts.authenticate(a.name(), "secure123");
        var verifiedB = accounts.authenticate(b.name(), "secure123");
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var ab = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return login.completeLogin(verifiedB, loginA.refreshToken());
            });
            var ba = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return login.completeLogin(verifiedA, loginB.refreshToken());
            });
            var resultB = ab.get(15, TimeUnit.SECONDS);
            var resultA = ba.get(15, TimeUnit.SECONDS);
            assertThat(auth.refresh(resultA.refreshToken())).isNotNull();
            assertThat(auth.refresh(resultB.refreshToken())).isNotNull();
            assertThatThrownBy(() -> auth.refresh(loginA.refreshToken())).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> auth.refresh(loginB.refreshToken())).isInstanceOf(BusinessException.class);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            users.delete(a.id(), 9001L);
            users.delete(b.id(), 9001L);
        }
    }

    @Test
    void changedCredentialsRejectReplacementWithoutRevokingPreviousAccount() {
        var a = createUser();
        var b = createUser();
        try {
            var previous = auth.login(a.name(), "secure123", null);
            var verified = accounts.authenticate(b.name(), "secure123");
            jdbc.update("UPDATE user_info SET user_name=? WHERE id=?", b.name() + "-renamed", b.id());
            assertThatThrownBy(() -> login.completeLogin(verified, previous.refreshToken()))
                .isInstanceOf(BadCredentialsException.class);
            assertThat(auth.refresh(previous.refreshToken())).isNotNull();
        } finally {
            users.delete(a.id(), 9001L);
            users.delete(b.id(), 9001L);
        }
    }

    @Test
    void replacementRollbackRestoresOldAuthorizationAndRemovesNewOne() {
        var user = createUser();
        try {
            var previous = auth.login(user.name(), "secure123", null);
            var verified = accounts.authenticate(user.name(), "secure123");
            assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                login.completeLogin(verified, previous.refreshToken());
                throw new IllegalStateException("Simulated failure before commit");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_login WHERE user_info_id=?",
                Integer.class, user.id())).isOne();
            assertThat(auth.refresh(previous.refreshToken())).isNotNull();
        } finally { users.delete(user.id(), 9001L); }
    }

    @Test
    void idleSessionSurvivesCleanupAndRefreshKeepsAbsoluteDeadline() {
        var user = createUser();
        try {
            var original = auth.login(user.name(), "secure123", null);
            jdbc.update("UPDATE user_login SET updated_at=now()-interval '2 days' WHERE user_info_id=?", user.id());
            jdbc.queryForObject("SELECT cleanup_inactive_user_login_sessions()", Integer.class);
            var renewed = auth.refresh(original.refreshToken());
            // JDBC timestamps may truncate nanoseconds; compare at database precision.
            assertThat(renewed.refreshExpiry().getEpochSecond()).isEqualTo(original.refreshExpiry().getEpochSecond());
            assertThat(renewed.accessExpiry()).isBeforeOrEqualTo(renewed.refreshExpiry());
            jdbc.update("UPDATE user_login SET expires_at=clock_timestamp()-interval '1 second' WHERE user_info_id=?", user.id());
            assertThatThrownBy(() -> auth.refresh(renewed.refreshToken())).isInstanceOf(BusinessException.class);
            jdbc.queryForObject("SELECT cleanup_inactive_user_login_sessions()", Integer.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM oauth2_authorization WHERE principal_name=?",
                Integer.class, Long.toString(user.id()))).isZero();
        } finally { users.delete(user.id(), 9001L); }
    }

    @Test
    void cleanupSkipsLockedAccountAndRechecksOnNextRun() throws Exception {
        var user = createUser();
        var executor = Executors.newSingleThreadExecutor();
        try {
            auth.login(user.name(), "secure123", null);
            jdbc.update("UPDATE user_login SET expires_at=clock_timestamp()-interval '1 second' WHERE user_info_id=?", user.id());
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbc.queryForObject("SELECT id FROM user_info WHERE id=? FOR UPDATE", Long.class, user.id());
                try {
                    executor.submit(() -> jdbc.queryForObject("SELECT cleanup_inactive_user_login_sessions()", Integer.class))
                        .get(5, TimeUnit.SECONDS);
                } catch (Exception exception) { throw new RuntimeException(exception); }
                assertThat(jdbc.queryForObject("SELECT count(*) FROM user_login WHERE user_info_id=?", Integer.class, user.id())).isOne();
            });
            jdbc.queryForObject("SELECT cleanup_inactive_user_login_sessions()", Integer.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_login WHERE user_info_id=?", Integer.class, user.id())).isZero();
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            users.delete(user.id(), 9001L);
        }
    }
}
