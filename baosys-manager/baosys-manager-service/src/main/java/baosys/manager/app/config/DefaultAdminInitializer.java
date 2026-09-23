package baosys.manager.app.config;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import baosys.common.security.password.PasswordHasher;
import baosys.manager.app.model.PermissionCode;
import baosys.manager.api.enums.SystemType;
import baosys.manager.infra.database.converter.PermissionPersistenceConverter;
import baosys.manager.infra.database.entity.PermissionChangeAuditEntity;
import baosys.manager.infra.database.entity.UserInfoEntity;
import baosys.manager.infra.database.mapper.PermissionChangeAuditMapper;
import baosys.manager.infra.database.mapper.UserInfoMapper;

@Component
@ConditionalOnProperty(
        prefix = "auth",
        name = "default-admin-enabled",
        havingValue = "true")
public class DefaultAdminInitializer implements ApplicationRunner {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(DefaultAdminInitializer.class);

    private final UserInfoMapper userMapper;
    private final PermissionChangeAuditMapper permissionChangeAuditMapper;
    private final PasswordHasher passwordHasher;
    private final AuthProperties properties;

    public DefaultAdminInitializer(
            UserInfoMapper userMapper,
            PermissionChangeAuditMapper permissionChangeAuditMapper,
            PasswordHasher passwordHasher,
            AuthProperties properties) {
        this.userMapper = userMapper;
        this.permissionChangeAuditMapper = permissionChangeAuditMapper;
        this.passwordHasher = passwordHasher;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = requireUsername(properties.getDefaultAdminUsername());
        String password = requirePassword(properties.getDefaultAdminPassword());
        if (userMapper.existsByUserNameAndSystemType(username, SystemType.MANAGER)) {
            return;
        }

        UserInfoEntity admin = new UserInfoEntity();
        String permissions = PermissionPersistenceConverter.encode(
                PermissionCode.codes(SystemType.MANAGER));
        admin.setUserName(username);
        admin.setSystemType(SystemType.MANAGER);
        admin.setUserPass(passwordHasher.hash(password));
        admin.setUserPermissions(permissions);
        admin.setEnabled(true);
        userMapper.insert(admin);

        PermissionChangeAuditEntity audit = new PermissionChangeAuditEntity();
        audit.setOperatorUserId(admin.getId());
        audit.setTargetUserId(admin.getId());
        audit.setBeforePermissions("");
        audit.setAfterPermissions(permissions);
        audit.setBeforeEnabled(false);
        audit.setAfterEnabled(true);
        audit.setTraceId("bootstrap-default-admin");
        audit.setOccurredAt(OffsetDateTime.now(ZoneOffset.UTC));
        permissionChangeAuditMapper.insert(audit);
        LOGGER.info(
                "module=auth action=initialize-admin userId={} username={} result=created",
                admin.getId(),
                username);
    }

    private static String requireUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalStateException(
                    "DEFAULT_ADMIN_USERNAME is required when default admin initialization is enabled");
        }
        return username.trim();
    }

    private static String requirePassword(String password) {
        if (password == null || password.length() < 12) {
            throw new IllegalStateException(
                    "DEFAULT_ADMIN_PASSWORD must contain at least 12 characters");
        }
        return password;
    }
}
