package szs.baosys.manager.app.services.impl;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import szs.baosys.common.security.jwt.SzsJwtTokenService;
import szs.baosys.common.security.password.PasswordHasher;
import szs.baosys.manager.app.config.AuthProperties;
import szs.baosys.manager.app.error.BusinessException;
import szs.baosys.manager.app.error.InvalidCredentialsException;
import szs.baosys.manager.app.services.AuthAppService;
import szs.baosys.manager.app.services.LoginResult;
import szs.baosys.manager.api.dto.LoginRequest;
import szs.baosys.manager.infra.database.converter.PermissionPersistenceConverter;
import szs.baosys.manager.infra.database.entity.UserInfoEntity;
import szs.baosys.manager.infra.database.entity.UserLoginEntity;
import szs.baosys.manager.infra.database.mapper.UserInfoMapper;
import szs.baosys.manager.infra.database.mapper.UserLoginMapper;
import szs.baosys.manager.infra.security.RefreshTokenProvider;
import szs.baosys.manager.infra.security.RefreshTokenProvider.IssuedRefreshToken;

@Service
public class AuthAppServiceImpl implements AuthAppService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthAppServiceImpl.class);

    private final SzsJwtTokenService tokenService;
    private final PasswordHasher passwordHasher;
    private final RefreshTokenProvider refreshTokenProvider;
    private final UserInfoMapper userMapper;
    private final UserLoginMapper loginMapper;
    private final AuthProperties authProperties;

    public AuthAppServiceImpl(
            SzsJwtTokenService tokenService,
            PasswordHasher passwordHasher,
            RefreshTokenProvider refreshTokenProvider,
            UserInfoMapper userMapper,
            UserLoginMapper loginMapper,
            AuthProperties authProperties) {
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
        String username = request.username().trim();
        // 账户不存在和密码错误对外使用同一响应，避免登录接口泄露有效账户名。
        UserInfoEntity user = userMapper
                .findByUserNameAndSystemType(username, request.systemType())
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
        UserLoginEntity login = loginMapper
                .findActiveByRefreshTokenHash(
                        refreshTokenProvider.hash(refreshToken),
                        now())
                .orElseThrow(AuthAppServiceImpl::refreshDenied);
        UserInfoEntity user = userMapper.findById(login.getUserInfoId())
                .orElseThrow(AuthAppServiceImpl::refreshDenied);

        // 刷新令牌每次使用后立即轮换；旧摘要被覆盖后无法再次换取访问令牌。
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
            // 注销保持幂等，Cookie 已丢失的客户端仍可安全完成本地退出。
            return;
        }
        loginMapper.deleteByRefreshTokenHash(
                refreshTokenProvider.hash(refreshToken));
    }

    /**
     * 创建登录或刷新成功后的令牌结果。
     *
     * <p>用户所属的 {@link SystemType} 同时作为子系统级基础权限写入 JWT，
     * 用于标识用户可以访问的子系统；{@code userPermissions} 用于保存该子系统内
     * 更细粒度的功能权限。两者合并后通过
     * {@link PermissionPersistenceConverter#decode(String)} 统一完成去重和规范化，
     * 再按稳定顺序写入访问令牌的权限 claim。</p>
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
        String systemType = user.getSystemType().toString();
        String permissionsWithSystemType =
                userPermissions == null || userPermissions.isBlank()
                        ? systemType
                        : userPermissions + "," + systemType;
        Set<String> permissionSet =
                PermissionPersistenceConverter.decode(permissionsWithSystemType);
        List<String> permissions = permissionSet.stream().sorted().toList();
        String accessToken = tokenService.generateAccessToken(
                Long.toString(user.getId()),
                user.getUserName(),
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
