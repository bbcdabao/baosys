package szs.baosys.manager.app.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import szs.baosys.common.security.jwt.SzsJwtTokenService;
import szs.baosys.common.security.password.PasswordHasher;
import szs.baosys.manager.app.config.AuthProperties;
import szs.baosys.manager.app.error.BusinessException;
import szs.baosys.manager.app.error.InvalidCredentialsException;
import szs.baosys.manager.app.services.LoginResult;
import szs.baosys.manager.api.dto.LoginRequest;
import szs.baosys.manager.api.enums.SystemType;
import szs.baosys.manager.infra.database.entity.UserInfoEntity;
import szs.baosys.manager.infra.database.entity.UserLoginEntity;
import szs.baosys.manager.infra.database.mapper.UserInfoMapper;
import szs.baosys.manager.infra.database.mapper.UserLoginMapper;
import szs.baosys.manager.infra.security.RefreshTokenProvider;
import szs.baosys.manager.infra.security.RefreshTokenProvider.IssuedRefreshToken;

class AuthAppServiceImplTest {

    private final SzsJwtTokenService tokenService = mock(SzsJwtTokenService.class);
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
        when(userMapper.findByUserNameAndSystemType("admin", SystemType.MANAGER))
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
        when(userMapper.findByUserNameAndSystemType("admin", SystemType.MANAGER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authAppService.login(
                new LoginRequest("admin", "wrong", SystemType.MANAGER, null)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void shouldRejectCredentialsFromAnotherSystem() {
        when(userMapper.findByUserNameAndSystemType("admin", SystemType.SIMULATION))
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
    void shouldRefreshWhenLoginRecordExists() {
        UserLoginEntity login = new UserLoginEntity();
        login.setId(2001L);
        login.setUserInfoId(1001L);
        when(refreshTokenProvider.hash("current-refresh-token")).thenReturn("current-refresh-hash");
        when(refreshTokenProvider.issue())
                .thenReturn(
                        new IssuedRefreshToken("next-refresh-token", "next-refresh-hash"),
                        new IssuedRefreshToken("csrf-token", "csrf-hash"));
        when(loginMapper.findByRefreshTokenHash("current-refresh-hash"))
                .thenReturn(Optional.of(login));
        when(userMapper.findById(1001L)).thenReturn(Optional.of(user()));

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
        when(loginMapper.findByRefreshTokenHash("missing-hash"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authAppService.refresh("missing-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("登录状态已失效，请重新登录");
    }

    @Test
    void shouldRejectMissingRefreshToken() {
        assertThatThrownBy(() -> authAppService.refresh(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("登录状态已失效，请重新登录");
    }

    private static UserInfoEntity user() {
        UserInfoEntity user = new UserInfoEntity();
        user.setId(1001L);
        user.setUserName("admin");
        user.setSystemType(SystemType.MANAGER);
        user.setUserPass("encoded");
        user.setUserPermissions("user:list,user:add");
        return user;
    }

    private static AuthProperties authProperties() {
        AuthProperties properties = new AuthProperties();
        properties.setRefreshTokenExpireDays(30);
        return properties;
    }
}
