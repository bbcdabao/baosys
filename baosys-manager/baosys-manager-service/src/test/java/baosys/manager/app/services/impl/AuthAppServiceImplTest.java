package baosys.manager.app.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import baosys.common.security.jwt.JwtTokenService;
import baosys.common.security.password.PasswordHasher;
import baosys.manager.app.config.AuthProperties;
import baosys.manager.app.error.BusinessException;
import baosys.manager.app.error.InvalidCredentialsException;
import baosys.manager.app.services.LoginResult;
import baosys.manager.api.dto.LoginRequest;
import baosys.manager.api.enums.SystemType;
import baosys.manager.infra.database.entity.UserInfoEntity;
import baosys.manager.infra.database.entity.UserLoginEntity;
import baosys.manager.infra.database.mapper.UserInfoMapper;
import baosys.manager.infra.database.mapper.UserLoginMapper;
import baosys.manager.infra.security.RefreshTokenProvider;
import baosys.manager.infra.security.RefreshTokenProvider.IssuedRefreshToken;

class AuthAppServiceImplTest {

    private final JwtTokenService tokenService = mock(JwtTokenService.class);
    private final PasswordHasher passwordHasher = mock(PasswordHasher.class);
    private final RefreshTokenProvider refreshTokenProvider = mock(RefreshTokenProvider.class);
    private final UserInfoMapper userMapper = mock(UserInfoMapper.class);
    private final UserLoginMapper loginMapper = mock(UserLoginMapper.class);
    private final AuthProperties authProperties = authProperties();
    private final AuthAppServiceImpl authAppService = new AuthAppServiceImpl(
            tokenService,
            passwordHasher,
            refreshTokenProvider,
            userMapper,
            loginMapper,
            authProperties);

    @BeforeEach
    void setUp() {
        when(refreshTokenProvider.issue())
                .thenReturn(
                        new IssuedRefreshToken("refresh-token", "refresh-hash"),
                        new IssuedRefreshToken("csrf-token", "csrf-hash"));
        when(loginMapper.insert(any(UserLoginEntity.class))).thenReturn(1);
        when(tokenService.generateAccessToken(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyList(),
                        anyMap()))
                .thenReturn("test-token");
    }

    @Test
    void shouldLoginWithPersistedCredentials() {
        UserInfoEntity user = user();
        when(userMapper.findByUserNameAndSystemTypeForUpdate(
                "admin", SystemType.MANAGER))
                .thenReturn(Optional.of(user));
        when(passwordHasher.matches("Admin123!test", "encoded")).thenReturn(true);

        LoginResult result = authAppService.login(
                new LoginRequest(
                        "admin",
                        "Admin123!test",
                        SystemType.MANAGER,
                        "test-client"));

        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.csrfToken()).isEqualTo("csrf-token");
        assertThat(result.accessToken()).isEqualTo("test-token");
        verify(tokenService).generateAccessToken(
                "1001",
                "admin",
                "MANAGER",
                List.of("user:add", "user:list"),
                Map.of("displayName", "admin"));
    }

    @Test
    void shouldRejectInvalidCredentials() {
        when(userMapper.findByUserNameAndSystemTypeForUpdate(
                "admin", SystemType.MANAGER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authAppService.login(
                new LoginRequest("admin", "wrong", SystemType.MANAGER, null)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void shouldRejectCredentialsFromAnotherSystem() {
        when(userMapper.findByUserNameAndSystemTypeForUpdate(
                "admin", SystemType.SIMULATION))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authAppService.login(
                new LoginRequest(
                        "admin",
                        "Admin123!test",
                        SystemType.SIMULATION,
                        null)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void shouldRejectLoginForDisabledAccount() {
        UserInfoEntity user = user();
        user.setEnabled(false);
        when(userMapper.findByUserNameAndSystemTypeForUpdate(
                "admin", SystemType.MANAGER))
                .thenReturn(Optional.of(user));
        when(passwordHasher.matches("Admin123!test", "encoded")).thenReturn(true);

        assertThatThrownBy(() -> authAppService.login(new LoginRequest(
                "admin",
                "Admin123!test",
                SystemType.MANAGER,
                null)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void shouldRefreshWhenLoginRecordExists() {
        UserLoginEntity login = new UserLoginEntity();
        login.setId(2001L);
        login.setUserInfoId(1001L);
        when(refreshTokenProvider.hash("current-refresh-token")).thenReturn("current-refresh-hash");
        when(refreshTokenProvider.issue())
                .thenReturn(
                        new IssuedRefreshToken("next-refresh-token", "next-refresh-hash"),
                        new IssuedRefreshToken("csrf-token", "csrf-hash"));
        when(loginMapper.findActiveByRefreshTokenHash(
                eq("current-refresh-hash"),
                any(OffsetDateTime.class)))
                .thenReturn(Optional.of(login));
        when(userMapper.findByIdForUpdate(1001L)).thenReturn(Optional.of(user()));
        when(loginMapper.findActiveByRefreshTokenHashForUpdate(
                eq("current-refresh-hash"),
                any(OffsetDateTime.class)))
                .thenReturn(Optional.of(login));

        LoginResult result = authAppService.refresh("current-refresh-token");

        assertThat(result.refreshToken()).isEqualTo("next-refresh-token");
        assertThat(result.csrfToken()).isEqualTo("csrf-token");
        assertThat(result.accessToken()).isEqualTo("test-token");
        assertThat(login.getRefreshTokenHash()).isEqualTo("next-refresh-hash");
        verify(loginMapper).updateById(login);
    }

    @Test
    void shouldRejectMissingRefreshLogin() {
        when(refreshTokenProvider.hash("missing-token")).thenReturn("missing-hash");
        when(loginMapper.findActiveByRefreshTokenHash(
                eq("missing-hash"),
                any(OffsetDateTime.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authAppService.refresh("missing-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("登录状态已失效，请重新登录");
    }

    @Test
    void shouldRejectRefreshForDisabledAccount() {
        UserLoginEntity login = new UserLoginEntity();
        login.setId(2001L);
        login.setUserInfoId(1001L);
        UserInfoEntity user = user();
        user.setEnabled(false);
        when(refreshTokenProvider.hash("current-refresh-token"))
                .thenReturn("current-refresh-hash");
        when(loginMapper.findActiveByRefreshTokenHash(
                eq("current-refresh-hash"),
                any(OffsetDateTime.class)))
                .thenReturn(Optional.of(login));
        when(userMapper.findByIdForUpdate(1001L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authAppService.refresh("current-refresh-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("登录状态已失效，请重新登录");
    }

    @Test
    void shouldRejectMissingRefreshToken() {
        assertThatThrownBy(() -> authAppService.refresh(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("登录状态已失效，请重新登录");
    }

    @Test
    void shouldRejectExpiredRefreshToken() {
        when(refreshTokenProvider.hash("expired-token"))
                .thenReturn("expired-hash");
        when(loginMapper.findActiveByRefreshTokenHash(
                eq("expired-hash"),
                any(OffsetDateTime.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authAppService.refresh("expired-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("登录状态已失效，请重新登录");
    }

    @Test
    void shouldRejectRefreshTokenRotatedByConcurrentRequest() {
        UserLoginEntity candidate = new UserLoginEntity();
        candidate.setId(2001L);
        candidate.setUserInfoId(1001L);
        when(refreshTokenProvider.hash("current-refresh-token"))
                .thenReturn("current-refresh-hash");
        when(loginMapper.findActiveByRefreshTokenHash(
                eq("current-refresh-hash"),
                any(OffsetDateTime.class)))
                .thenReturn(Optional.of(candidate));
        when(userMapper.findByIdForUpdate(1001L))
                .thenReturn(Optional.of(user()));
        when(loginMapper.findActiveByRefreshTokenHashForUpdate(
                eq("current-refresh-hash"),
                any(OffsetDateTime.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authAppService.refresh("current-refresh-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("登录状态已失效，请重新登录");
        verify(loginMapper, never()).updateById(candidate);
    }

    private static UserInfoEntity user() {
        UserInfoEntity user = new UserInfoEntity();
        user.setId(1001L);
        user.setUserName("admin");
        user.setSystemType(SystemType.MANAGER);
        user.setUserPass("encoded");
        user.setUserPermissions("user:list,user:add");
        user.setEnabled(true);
        return user;
    }

    private static AuthProperties authProperties() {
        AuthProperties properties = new AuthProperties();
        properties.setRefreshTokenExpireDays(30);
        return properties;
    }
}
