package baosys.manager.app.services.impl;

import baosys.common.api.PageResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import baosys.manager.api.module.responses.CurrentUserResponse;
import baosys.manager.api.module.responses.LoginClientResponse;
import baosys.manager.api.module.payloads.UserCreateRequest;
import baosys.manager.api.module.responses.UserResponse;
import baosys.manager.api.module.payloads.UserUpdateRequest;
import baosys.manager.app.assembler.UserAssembler;
import baosys.manager.app.config.SystemCatalog;
import baosys.common.web.TraceIds;
import baosys.manager.app.error.BusinessException;
import baosys.manager.app.services.UserAppService;
import baosys.manager.infra.database.converter.PermissionPersistenceConverter;
import baosys.manager.infra.database.entity.PermissionChangeAuditEntity;
import baosys.manager.infra.database.entity.UserInfoEntity;
import baosys.manager.infra.database.mapper.PermissionChangeAuditMapper;
import baosys.manager.infra.database.mapper.UserInfoMapper;
import baosys.manager.infra.database.mapper.UserLoginMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class UserAppServiceImpl implements UserAppService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserAppServiceImpl.class);

    private final UserInfoMapper userMapper;
    private final UserLoginMapper loginMapper;
    private final PermissionChangeAuditMapper permissionChangeAuditMapper;
    private final PasswordEncoder passwordEncoder;
    private final SystemCatalog catalog;
    private final TransactionOperations transactions;

    @Autowired
    public UserAppServiceImpl(
        UserInfoMapper userMapper,
        UserLoginMapper loginMapper,
        PermissionChangeAuditMapper permissionChangeAuditMapper,
        PasswordEncoder passwordEncoder,
        SystemCatalog catalog,
        PlatformTransactionManager transactionManager
    ) {
        this(
            userMapper,
            loginMapper,
            permissionChangeAuditMapper,
            passwordEncoder,
            catalog,
            new TransactionTemplate(transactionManager)
        );
    }

    UserAppServiceImpl(
        UserInfoMapper userMapper,
        UserLoginMapper loginMapper,
        PermissionChangeAuditMapper permissionChangeAuditMapper,
        PasswordEncoder passwordEncoder,
        SystemCatalog catalog,
        TransactionOperations transactions
    ) {
        this.catalog = catalog;
        this.userMapper = userMapper;
        this.loginMapper = loginMapper;
        this.permissionChangeAuditMapper = permissionChangeAuditMapper;
        this.passwordEncoder = passwordEncoder;
        this.transactions = transactions;
    }

    @Override
    @Transactional(readOnly = true)
    public long userIdForLogin(long loginId) {
        var login = loginMapper.selectById(loginId);
        if (login == null || !login.getExpiresAt().isAfter(now())) {
            throw new BusinessException(
                "AUTH_401_001",
                HttpStatus.UNAUTHORIZED,
                "登录状态缺失或已过期"
            );
        }
        var user = userMapper
            .findById(login.getUserInfoId())
            .filter(UserInfoEntity::isEnabled)
            .orElseThrow(() ->
                new BusinessException(
                    "AUTH_401_001",
                    HttpStatus.UNAUTHORIZED,
                    "登录状态缺失或已过期"
                )
            );
        return user.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(long userId) {
        // 调用方已将令牌中的登录记录标识解析为用户 ID；权限以数据库和当前目录为准。
        UserInfoEntity user = requireUser(userId);
        List<String> permissions = catalog
            .effectiveGrants(PermissionPersistenceConverter.decode(user.getUserPermissions()))
            .stream()
            .sorted()
            .toList();
        return new CurrentUserResponse(
            user.getId(),
            user.getUserName(),
            user.getUserName(),
            permissions
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String name, String systemType, int page, int size) {
        if (systemType != null && !catalog.contains(systemType)) {
            throw new BusinessException("USER_400_002", HttpStatus.BAD_REQUEST, "未知子系统");
        }
        var result = userMapper.selectPageByName(name, systemType, page, size);
        Set<Long> onlineUserIds = loginMapper.selectActiveUserIds(
            result.getRecords().stream().map(UserInfoEntity::getId).toList(),
            now()
        );
        List<UserResponse> items = result
            .getRecords()
            .stream()
            .map(user -> UserAssembler.toResponse(user, onlineUserIds.contains(user.getId())))
            .toList();
        return PageResponse.of(items, result.getTotal(), page, size);
    }

    @Override
    public UserResponse create(UserCreateRequest request, long operatorUserId) {
        String username = normalizeName(request.name());
        Set<String> permissions = validatePermissions(request.permissions());
        String defaultSystem = validateDefaultSystem(request.defaultSystemType());
        String encodedPermissions = PermissionPersistenceConverter.encode(permissions);
        boolean enabled = request.enabled() == null || request.enabled();
        // 密码哈希属于 CPU 耗时操作，在开启数据库事务前完成，避免占用数据库连接。
        String passwordHash = passwordEncoder.encode(request.pass());

        UserResponse response = transactions.execute(status -> {
            if (userMapper.existsByUserName(username)) {
                throw new BusinessException("USER_409_001", HttpStatus.CONFLICT, "账户名称已存在");
            }

            UserInfoEntity user = new UserInfoEntity();
            user.setUserName(username);
            user.setUserPass(passwordHash);
            user.setUserPermissions(encodedPermissions);
            user.setDefaultSystemType(defaultSystem);
            user.setEnabled(enabled);
            userMapper.insert(user);
            auditAuthorizationChange(
                operatorUserId,
                user.getId(),
                "",
                encodedPermissions,
                false,
                enabled
            );
            return UserAssembler.toResponse(user, false);
        });
        LOGGER.info(
            "module=user action=create userId={} username={} result=success",
            response.id(),
            username
        );
        return response;
    }

    @Override
    public UserResponse update(UserUpdateRequest request, long operatorUserId) {
        String username = normalizeName(request.name());
        Set<String> permissions = validatePermissions(request.permissions());
        String defaultSystem = validateDefaultSystem(request.defaultSystemType());
        String afterPermissions = PermissionPersistenceConverter.encode(permissions);
        String passwordHash = null;
        if (StringUtils.hasText(request.pass())) {
            if (request.pass().length() < 8 || request.pass().length() > 128) {
                throw new BusinessException(
                    "USER_400_003",
                    HttpStatus.BAD_REQUEST,
                    "密码长度必须为 8 到 128 个字符"
                );
            }
            // 密码哈希属于 CPU 耗时操作，在开启数据库事务与获取账户行锁前完成。
            passwordHash = passwordEncoder.encode(request.pass());
        }
        String encodedPassword = passwordHash;

        UserResponse response = transactions.execute(status -> {
            UserInfoEntity user = requireUserForUpdate(request.id());
            String beforePermissions = PermissionPersistenceConverter.encode(
                PermissionPersistenceConverter.decode(user.getUserPermissions())
            );
            boolean beforeEnabled = user.isEnabled();
            boolean afterEnabled = request.enabled() == null ? beforeEnabled : request.enabled();
            if (userMapper.existsByUserNameExcludingId(username, request.id())) {
                throw new BusinessException("USER_409_001", HttpStatus.CONFLICT, "账户名称已存在");
            }

            boolean passwordChanged = encodedPassword != null;
            boolean authorizationChanged =
                !Objects.equals(beforePermissions, afterPermissions) || beforeEnabled != afterEnabled;
            user.setUserName(username);
            if (passwordChanged) {
                user.setUserPass(encodedPassword);
            }
            user.setUserPermissions(afterPermissions);
            user.setDefaultSystemType(defaultSystem);
            user.setEnabled(afterEnabled);
            userMapper.updateById(user);
            if (authorizationChanged || passwordChanged) {
                loginMapper.deleteByUserId(user.getId());
            }
            if (authorizationChanged) {
                auditAuthorizationChange(
                    operatorUserId,
                    user.getId(),
                    beforePermissions,
                    afterPermissions,
                    beforeEnabled,
                    afterEnabled
                );
            }
            return UserAssembler.toResponse(
                user,
                loginMapper.countActiveByUserId(user.getId(), now()) > 0
            );
        });
        LOGGER.info(
            "module=user action=update userId={} username={} result=success",
            response.id(),
            username
        );
        return response;
    }

    @Override
    @Transactional
    public void delete(long id, long operatorUserId) {
        UserInfoEntity user = requireUserForUpdate(id);
        auditAuthorizationChange(
            operatorUserId,
            user.getId(),
            PermissionPersistenceConverter.encode(
                PermissionPersistenceConverter.decode(user.getUserPermissions())
            ),
            "",
            user.isEnabled(),
            false
        );
        loginMapper.deleteByUserId(id);
        userMapper.deleteById(id);
        LOGGER.info(
            "module=user action=delete userId={} username={} result=success",
            id,
            user.getUserName()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoginClientResponse> loginClients(long userId) {
        requireUser(userId);
        return loginMapper
            .selectActiveByUserId(userId, now())
            .stream()
            .map(login ->
                new LoginClientResponse(login.getId(), login.getLoginClient(), login.getCreatedAt())
            )
            .toList();
    }

    @Override
    @Transactional
    public void kickClient(long userId, long loginId) {
        requireUserForUpdate(userId);
        var login = loginMapper
            .findActiveByIdAndUserId(loginId, userId, now())
            .orElseThrow(() ->
                new BusinessException("LOGIN_404_001", HttpStatus.NOT_FOUND, "登录客户端不存在")
            );
        loginMapper.deleteById(login.getId());
        LOGGER.info(
            "module=user action=kick-client userId={} loginId={} result=success",
            userId,
            loginId
        );
    }

    @Override
    @Transactional
    public void kickAllClients(long userId) {
        requireUserForUpdate(userId);
        long count = loginMapper.deleteByUserId(userId);
        LOGGER.info("module=user action=kick-all userId={} count={} result=success", userId, count);
    }

    private UserInfoEntity requireUser(long id) {
        return userMapper
            .findById(id)
            .orElseThrow(() ->
                new BusinessException("USER_404_001", HttpStatus.NOT_FOUND, "账户不存在")
            );
    }

    private UserInfoEntity requireUserForUpdate(long id) {
        return userMapper
            .findByIdForUpdate(id)
            .orElseThrow(() ->
                new BusinessException("USER_404_001", HttpStatus.NOT_FOUND, "账户不存在")
            );
    }

    private void auditAuthorizationChange(
        long operatorUserId,
        long targetUserId,
        String beforePermissions,
        String afterPermissions,
        boolean beforeEnabled,
        boolean afterEnabled
    ) {
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

    private String validateDefaultSystem(String type) {
        if (type == null || type.isBlank() || !catalog.contains(type)) {
            throw new BusinessException("USER_400_DEFAULT_SYSTEM", HttpStatus.BAD_REQUEST, "默认子系统必须是目录中登记的子系统");
        }
        return type;
    }

    private Set<String> validatePermissions(Set<String> permissions) {
        try {
            return catalog.validateGrants(permissions);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                "USER_400_002",
                HttpStatus.BAD_REQUEST,
                "权限配置错误：" + exception.getMessage()
            );
        }
    }

    private static String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (!StringUtils.hasLength(normalized)) {
            throw new BusinessException("USER_400_001", HttpStatus.BAD_REQUEST, "账户名称不能为空");
        }
        if (normalized.length() > 32) {
            throw new BusinessException("USER_400_001", HttpStatus.BAD_REQUEST, "账户名称不能超过 32 个字符");
        }
        return normalized;
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
