package baosys.manager.app.services.impl;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import baosys.common.api.PageResponse;
import baosys.common.security.password.PasswordHasher;
import baosys.manager.app.assembler.UserAssembler;
import baosys.manager.app.config.web.TraceIds;
import baosys.manager.app.config.SystemCatalog;
import baosys.manager.app.error.BusinessException;
import baosys.manager.app.services.UserAppService;
import baosys.manager.api.dto.CurrentUserResponse;
import baosys.manager.api.dto.LoginClientResponse;
import baosys.manager.api.dto.UserCreateRequest;
import baosys.manager.api.dto.UserResponse;
import baosys.manager.api.dto.UserUpdateRequest;
import baosys.manager.infra.database.converter.PermissionPersistenceConverter;
import baosys.manager.infra.database.entity.PermissionChangeAuditEntity;
import baosys.manager.infra.database.entity.UserInfoEntity;
import baosys.manager.infra.database.mapper.PermissionChangeAuditMapper;
import baosys.manager.infra.database.mapper.UserInfoMapper;
import baosys.manager.infra.database.mapper.UserLoginMapper;

@Service
public class UserAppServiceImpl implements UserAppService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserAppServiceImpl.class);

    private final UserInfoMapper userMapper;
    private final UserLoginMapper loginMapper;
    private final PermissionChangeAuditMapper permissionChangeAuditMapper;
    private final PasswordHasher passwordHasher;
    private final SystemCatalog catalog;

    public UserAppServiceImpl(
            UserInfoMapper userMapper,
            UserLoginMapper loginMapper,
            PermissionChangeAuditMapper permissionChangeAuditMapper,
            PasswordHasher passwordHasher, SystemCatalog catalog) {
        this.catalog = catalog;
        this.userMapper = userMapper;
        this.loginMapper = loginMapper;
        this.permissionChangeAuditMapper = permissionChangeAuditMapper;
        this.passwordHasher = passwordHasher;
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(long userId) {
        // 令牌只负责提供用户标识；系统归属和权限以数据库当前配置为准。
        UserInfoEntity user = requireUser(userId);
        List<String> permissions = PermissionPersistenceConverter
                .decode(user.getUserPermissions())
                .stream()
                .sorted()
                .toList();
        return new CurrentUserResponse(
                user.getId(),
                user.getUserName(),
                user.getUserName(),
                user.getSystemType(),
                permissions);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(
            String name,
            String systemType,
            int page,
            int size) {
        var result = userMapper.selectPageByNameAndSystemType(
                name,
                systemType,
                page,
                size);
        Set<Long> onlineUserIds = loginMapper.selectActiveUserIds(
                result.getRecords().stream().map(UserInfoEntity::getId).toList(),
                now());
        List<UserResponse> items = result.getRecords().stream()
                .map(user -> UserAssembler.toResponse(
                        user,
                        onlineUserIds.contains(user.getId())))
                .toList();
        return PageResponse.of(
                items,
                result.getTotal(),
                page,
                size);
    }

    @Override
    @Transactional
    public UserResponse create(UserCreateRequest request, long operatorUserId) {
        String username = normalizeName(request.name());
        Set<String> permissions = validatePermissions(
                request.systemType(),
                request.permissions());
        String encodedPermissions = PermissionPersistenceConverter.encode(permissions);
        boolean enabled = request.enabled() == null || request.enabled();
        if (userMapper.existsByUserNameAndSystemType(username, request.systemType())) {
            throw new BusinessException("USER_409_001", HttpStatus.CONFLICT, "账户名称已存在");
        }

        UserInfoEntity user = new UserInfoEntity();
        user.setUserName(username);
        user.setSystemType(request.systemType());
        user.setUserPass(passwordHasher.hash(request.pass()));
        user.setUserPermissions(encodedPermissions);
        user.setEnabled(enabled);
        userMapper.insert(user);
        auditAuthorizationChange(
                operatorUserId,
                user.getId(),
                "",
                encodedPermissions,
                false,
                enabled);
        LOGGER.info(
                "module=user action=create userId={} username={} result=success",
                user.getId(),
                username);
        return UserAssembler.toResponse(user, false);
    }

    @Override
    @Transactional
    public UserResponse update(UserUpdateRequest request, long operatorUserId) {
        UserInfoEntity user = requireUserForUpdate(request.id());
        String username = normalizeName(request.name());
        Set<String> permissions = validatePermissions(
                request.systemType(),
                request.permissions());
        String beforePermissions = PermissionPersistenceConverter.encode(
                PermissionPersistenceConverter.decode(user.getUserPermissions()));
        String afterPermissions = PermissionPersistenceConverter.encode(permissions);
        boolean beforeEnabled = user.isEnabled();
        boolean afterEnabled = request.enabled() == null
                ? beforeEnabled
                : request.enabled();
        if (userMapper.existsByUserNameAndSystemTypeExcludingId(
                username,
                request.systemType(),
                request.id())) {
            throw new BusinessException("USER_409_001", HttpStatus.CONFLICT, "账户名称已存在");
        }

        boolean systemTypeChanged = !Objects.equals(user.getSystemType(), request.systemType());
        boolean passwordChanged = false;
        boolean authorizationChanged = !Objects.equals(
                beforePermissions,
                afterPermissions) || beforeEnabled != afterEnabled;
        user.setUserName(username);
        user.setSystemType(request.systemType());
        if (request.pass() != null && !request.pass().isBlank()) {
            if (request.pass().length() < 8 || request.pass().length() > 128) {
                throw new BusinessException(
                        "USER_400_003",
                        HttpStatus.BAD_REQUEST,
                        "密码长度必须为 8 到 128 个字符");
            }
            user.setUserPass(passwordHasher.hash(request.pass()));
            passwordChanged = true;
        }
        user.setUserPermissions(afterPermissions);
        user.setEnabled(afterEnabled);
        userMapper.updateById(user);
        if (authorizationChanged || passwordChanged || systemTypeChanged) {
            loginMapper.deleteByUserId(user.getId());
        }
        if (authorizationChanged) {
            auditAuthorizationChange(
                    operatorUserId,
                    user.getId(),
                    beforePermissions,
                    afterPermissions,
                    beforeEnabled,
                    afterEnabled);
        }
        LOGGER.info(
                "module=user action=update userId={} username={} result=success",
                user.getId(),
                username);
        return UserAssembler.toResponse(
                user,
                loginMapper.countActiveByUserId(user.getId(), now()) > 0);
    }

    @Override
    @Transactional
    public void delete(long id, long operatorUserId) {
        UserInfoEntity user = requireUserForUpdate(id);
        auditAuthorizationChange(
                operatorUserId,
                user.getId(),
                PermissionPersistenceConverter.encode(
                        PermissionPersistenceConverter.decode(
                                user.getUserPermissions())),
                "",
                user.isEnabled(),
                false);
        loginMapper.deleteByUserId(id);
        userMapper.deleteById(id);
        LOGGER.info(
                "module=user action=delete userId={} username={} result=success",
                id,
                user.getUserName());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoginClientResponse> loginClients(long userId) {
        requireUser(userId);
        return loginMapper.selectActiveByUserId(userId, now()).stream()
                .map(login -> new LoginClientResponse(
                        login.getId(),
                        login.getLoginClient(),
                        login.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void kickClient(long userId, long loginId) {
        requireUser(userId);
        var login = loginMapper.findActiveByIdAndUserId(loginId, userId, now())
                .orElseThrow(() -> new BusinessException(
                        "LOGIN_404_001",
                        HttpStatus.NOT_FOUND,
                        "登录客户端不存在"));
        loginMapper.deleteById(login.getId());
        LOGGER.info(
                "module=user action=kick-client userId={} loginId={} result=success",
                userId,
                loginId);
    }

    @Override
    @Transactional
    public void kickAllClients(long userId) {
        requireUser(userId);
        long count = loginMapper.deleteByUserId(userId);
        LOGGER.info(
                "module=user action=kick-all userId={} count={} result=success",
                userId,
                count);
    }

    private UserInfoEntity requireUser(long id) {
        return userMapper.findById(id)
                .orElseThrow(() -> new BusinessException(
                        "USER_404_001",
                        HttpStatus.NOT_FOUND,
                        "账户不存在"));
    }

    private UserInfoEntity requireUserForUpdate(long id) {
        return userMapper.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(
                        "USER_404_001",
                        HttpStatus.NOT_FOUND,
                        "账户不存在"));
    }

    private void auditAuthorizationChange(
            long operatorUserId,
            long targetUserId,
            String beforePermissions,
            String afterPermissions,
            boolean beforeEnabled,
            boolean afterEnabled) {
        PermissionChangeAuditEntity audit = new PermissionChangeAuditEntity();
        audit.setOperatorUserId(operatorUserId);
        audit.setTargetUserId(targetUserId);
        audit.setBeforePermissions(beforePermissions);
        audit.setAfterPermissions(afterPermissions);
        audit.setBeforeEnabled(beforeEnabled);
        audit.setAfterEnabled(afterEnabled);
        audit.setTraceId(TraceIds.current());
        audit.setOccurredAt(now());
        permissionChangeAuditMapper.insert(audit);
    }

    private Set<String> validatePermissions(
            String systemType,
            Set<String> permissions) {
        Set<String> availablePermissions = catalog.codes(systemType);
        if (!catalog.contains(systemType)
                || permissions == null
                || (!availablePermissions.isEmpty() && permissions.isEmpty())
                || permissions.stream().anyMatch(
                        permission -> permission == null || !availablePermissions.contains(permission))) {
            throw new BusinessException(
                    "USER_400_002",
                    HttpStatus.BAD_REQUEST,
                    "权限配置错误");
        }
        if (PermissionPersistenceConverter.encode(permissions).length() > 1024) {
            throw new BusinessException("USER_400_002", HttpStatus.BAD_REQUEST, "权限配置总长度不能超过 1024 个字符");
        }
        return Set.copyOf(permissions);
    }

    private static String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(
                    "USER_400_001",
                    HttpStatus.BAD_REQUEST,
                    "账户名称不能为空");
        }
        return normalized;
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
