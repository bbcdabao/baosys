package baosys.manager.app.services.impl;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import baosys.common.security.jwt.JwtTokenService;
import baosys.common.security.password.PasswordHasher;
import baosys.manager.app.config.AuthProperties;
import baosys.manager.app.config.SystemCatalog;
import baosys.manager.app.error.BusinessException;
import baosys.manager.app.error.InvalidCredentialsException;
import baosys.manager.app.services.AuthAppService;
import baosys.manager.app.services.LoginResult;
import baosys.manager.api.dto.LoginRequest;
import baosys.manager.infra.database.converter.PermissionPersistenceConverter;
import baosys.manager.infra.database.entity.UserInfoEntity;
import baosys.manager.infra.database.entity.UserLoginEntity;
import baosys.manager.infra.database.mapper.UserInfoMapper;
import baosys.manager.infra.database.mapper.UserLoginMapper;
import baosys.manager.infra.security.RefreshTokenProvider;
import baosys.manager.infra.security.RefreshTokenProvider.IssuedRefreshToken;

@Service
public class AuthAppServiceImpl implements AuthAppService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthAppServiceImpl.class);

    private final JwtTokenService tokenService;
    private final PasswordHasher passwordHasher;
    private final RefreshTokenProvider refreshTokenProvider;
    private final UserInfoMapper userMapper;
    private final UserLoginMapper loginMapper;
    private final AuthProperties authProperties;
    private final SystemCatalog catalog;

    public AuthAppServiceImpl(
            JwtTokenService tokenService,
            PasswordHasher passwordHasher,
            RefreshTokenProvider refreshTokenProvider,
            UserInfoMapper userMapper,
            UserLoginMapper loginMapper,
            AuthProperties authProperties, SystemCatalog catalog) {
        this.catalog = catalog;
        this.tokenService = tokenService;
        this.passwordHasher = passwordHasher;
        this.refreshTokenProvider = refreshTokenProvider;
        this.userMapper = userMapper;
        this.loginMapper = loginMapper;
        this.authProperties = authProperties;
    }

    @Override
    @Transactional
    public LoginResult login(LoginRequest request) {
        if (!catalog.contains(request.systemType())) {
            throw new InvalidCredentialsException();
        }
        String username = request.username().trim();
        UserInfoEntity user = userMapper
                .findByUserNameAndSystemTypeForUpdate(
                        username,
                        request.systemType())
                .filter(UserInfoEntity::isEnabled)
                .filter(candidate -> passwordHasher.matches(
                        request.password(),
                        candidate.getUserPass()))
                .orElseThrow(() -> {
                    LOGGER.warn(
                            "module=auth action=login username={} systemType={} result=failed errorCode=AUTH_401_001",
                            username,
                            request.systemType());
                    return new InvalidCredentialsException();
                });

        IssuedRefreshToken refreshToken = refreshTokenProvider.issue();
        UserLoginEntity login = new UserLoginEntity();
        login.setUserInfoId(user.getId());
        login.setLoginClient(normalizeClient(request.loginClient()));
        login.setRefreshTokenHash(refreshToken.hash());
        login.setExpiresAt(now().plusDays(authProperties.getRefreshTokenExpireDays()));
        login.setCreatedBy(user.getUserName());
        loginMapper.insert(login);

        LoginResult result = createResult(user, refreshToken.value());
        LOGGER.info(
                "module=auth action=login userId={} username={} systemType={} result=success",
                user.getId(),
                username,
                user.getSystemType());
        return result;
    }

    @Override
    @Transactional
    public LoginResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw refreshDenied();
        }
        String refreshTokenHash = refreshTokenProvider.hash(refreshToken);
        UserLoginEntity candidate = loginMapper
                .findActiveByRefreshTokenHash(
                        refreshTokenHash,
                        now())
                .orElseThrow(AuthAppServiceImpl::refreshDenied);
        UserInfoEntity user = userMapper.findByIdForUpdate(candidate.getUserInfoId())
                .filter(UserInfoEntity::isEnabled)
                .filter(candidateUser -> catalog.contains(candidateUser.getSystemType()))
                .orElseThrow(AuthAppServiceImpl::refreshDenied);
        UserLoginEntity login = loginMapper
                .findActiveByRefreshTokenHashForUpdate(
                        refreshTokenHash,
                        now())
                .filter(active -> active.getId().equals(candidate.getId()))
                .orElseThrow(AuthAppServiceImpl::refreshDenied);

        IssuedRefreshToken nextRefreshToken = refreshTokenProvider.issue();
        login.setRefreshTokenHash(nextRefreshToken.hash());
        loginMapper.updateById(login);
        LOGGER.info(
                "module=auth action=refresh userId={} loginId={} result=success",
                user.getId(),
                login.getId());
        return createResult(user, nextRefreshToken.value());
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        loginMapper.deleteByRefreshTokenHash(
                refreshTokenProvider.hash(refreshToken));
    }

    /**
     * 创建登录或刷新成功后的令牌结果。
     *
     * <p>用户所属子系统会作为独立的 {@code systemType} claim 写入 JWT，
     * {@code userPermissions} 只保存该子系统内更细粒度的功能权限。功能权限通过
     * {@link PermissionPersistenceConverter#decode(String)} 统一完成去重和规范化，
     * 再按稳定顺序写入访问令牌的 permissions claim。</p>
     *
     * <p>访问令牌用于后续接口认证，传入的刷新令牌由调用方持久化并返回客户端；
     * CSRF 令牌在此单独生成，用于网关启用 Cookie 认证保护时校验非安全请求。</p>
     *
     * @param user 已通过身份校验的用户
     * @param refreshToken 本次签发或轮换后的刷新令牌明文
     * @return 包含访问令牌、刷新令牌和 CSRF 令牌的登录结果
     */
    private LoginResult createResult(UserInfoEntity user, String refreshToken) {
        String userPermissions = user.getUserPermissions();
        String systemType = user.getSystemType();
        List<String> permissions = PermissionPersistenceConverter.decode(userPermissions)
                .stream()
                .filter(catalog.codes(systemType)::contains)
                .sorted()
                .toList();
        String accessToken = tokenService.generateAccessToken(
                Long.toString(user.getId()),
                user.getUserName(),
                systemType,
                permissions,
                Map.of("displayName", user.getUserName()));
        String csrfToken = refreshTokenProvider.issue().value();
        return new LoginResult(accessToken, refreshToken, csrfToken);
    }

    private static BusinessException refreshDenied() {
        return new BusinessException(
                "AUTH_403_001",
                HttpStatus.FORBIDDEN,
                "登录状态已失效，请重新登录");
    }

    private static String normalizeClient(String client) {
        return client == null || client.isBlank() ? "unknown-client" : client.trim();
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
