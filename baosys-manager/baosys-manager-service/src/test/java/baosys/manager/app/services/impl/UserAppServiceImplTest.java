package baosys.manager.app.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import baosys.common.security.password.PasswordHasher;
import baosys.manager.app.error.BusinessException;
import baosys.manager.api.dto.UserUpdateRequest;
import baosys.manager.api.enums.SystemType;
import baosys.manager.infra.database.entity.PermissionChangeAuditEntity;
import baosys.manager.infra.database.entity.UserInfoEntity;
import baosys.manager.infra.database.mapper.PermissionChangeAuditMapper;
import baosys.manager.infra.database.mapper.UserInfoMapper;
import baosys.manager.infra.database.mapper.UserLoginMapper;

class UserAppServiceImplTest {

    private final UserInfoMapper userMapper = mock(UserInfoMapper.class);
    private final UserLoginMapper loginMapper = mock(UserLoginMapper.class);
    private final PermissionChangeAuditMapper auditMapper = mock(PermissionChangeAuditMapper.class);
    private final PasswordHasher passwordHasher = mock(PasswordHasher.class);
    private final UserAppServiceImpl userAppService = new UserAppServiceImpl(
            userMapper,
            loginMapper,
            auditMapper,
            passwordHasher);

    @Test
    void shouldLoadCurrentSystemTypeFromPersistedUser() {
        UserInfoEntity user = new UserInfoEntity();
        user.setId(1001L);
        user.setUserName("simulator");
        user.setSystemType(SystemType.SIMULATORS);
        user.setUserPermissions("");
        when(userMapper.findById(1001L)).thenReturn(Optional.of(user));

        var currentUser = userAppService.currentUser(1001L);

        assertThat(currentUser.userId()).isEqualTo(1001L);
        assertThat(currentUser.systemType()).isEqualTo(SystemType.SIMULATORS);
        assertThat(currentUser.permissions()).isEmpty();
    }

    @Test
    void shouldAllowEmptyPermissionsWhenSystemPermissionCatalogIsEmpty() {
        UserInfoEntity user = user(SystemType.SIMULATION, "");
        when(userMapper.findByIdForUpdate(1001L)).thenReturn(Optional.of(user));

        var updated = userAppService.update(new UserUpdateRequest(
                1001L,
                "simulation-user",
                "",
                SystemType.SIMULATION,
                Set.of(),
                true), 9001L);

        assertThat(updated.permissions()).isEmpty();
        assertThat(user.getUserPermissions()).isEmpty();
    }

    @Test
    void shouldRejectEmptyPermissionsWhenSystemPermissionCatalogIsNotEmpty() {
        UserInfoEntity user = user(
                SystemType.MANAGER,
                "user:list");
        when(userMapper.findByIdForUpdate(1001L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userAppService.update(new UserUpdateRequest(
                1001L,
                "manager-user",
                "",
                SystemType.MANAGER,
                Set.of(),
                true), 9001L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("权限配置错误");
    }

    @Test
    void shouldRevokeSessionsAndAuditPermissionChange() {
        UserInfoEntity user = user(
                SystemType.MANAGER,
                "user:list");
        when(userMapper.findByIdForUpdate(1001L)).thenReturn(Optional.of(user));

        userAppService.update(new UserUpdateRequest(
                1001L,
                "manager-user",
                "",
                SystemType.MANAGER,
                Set.of("user:update"),
                true), 9001L);

        verify(loginMapper).deleteByUserId(1001L);
        ArgumentCaptor<PermissionChangeAuditEntity> captor =
                ArgumentCaptor.forClass(PermissionChangeAuditEntity.class);
        verify(auditMapper).insert(captor.capture());
        assertThat(captor.getValue().getOperatorUserId()).isEqualTo(9001L);
        assertThat(captor.getValue().getTargetUserId()).isEqualTo(1001L);
        assertThat(captor.getValue().getBeforePermissions())
                .isEqualTo("user:list");
        assertThat(captor.getValue().getAfterPermissions())
                .isEqualTo("user:update");
        assertThat(captor.getValue().getTraceId()).isNotBlank();
        assertThat(captor.getValue().getOccurredAt()).isNotNull();
    }

    @Test
    void shouldRevokeSessionsAndAuditWhenAccountIsDisabled() {
        UserInfoEntity user = user(
                SystemType.MANAGER,
                "user:list");
        when(userMapper.findByIdForUpdate(1001L)).thenReturn(Optional.of(user));

        userAppService.update(new UserUpdateRequest(
                1001L,
                "manager-user",
                "",
                SystemType.MANAGER,
                Set.of("user:list"),
                false), 9001L);

        verify(loginMapper).deleteByUserId(1001L);
        ArgumentCaptor<PermissionChangeAuditEntity> captor =
                ArgumentCaptor.forClass(PermissionChangeAuditEntity.class);
        verify(auditMapper).insert(captor.capture());
        assertThat(captor.getValue().isBeforeEnabled()).isTrue();
        assertThat(captor.getValue().isAfterEnabled()).isFalse();
    }

    @Test
    void shouldKeepSessionsWhenAuthorizationDoesNotChange() {
        UserInfoEntity user = user(
                SystemType.MANAGER,
                "user:list");
        when(userMapper.findByIdForUpdate(1001L)).thenReturn(Optional.of(user));

        userAppService.update(new UserUpdateRequest(
                1001L,
                "renamed-user",
                "",
                SystemType.MANAGER,
                Set.of("user:list"),
                true), 9001L);

        verify(loginMapper, never()).deleteByUserId(1001L);
        verify(auditMapper, never()).insert(
                org.mockito.ArgumentMatchers.any(PermissionChangeAuditEntity.class));
    }

    @Test
    void shouldPreserveDisabledStateWhenUpdateOmitsEnabled() {
        UserInfoEntity user = user(SystemType.MANAGER, "user:list");
        user.setEnabled(false);
        when(userMapper.findByIdForUpdate(1001L)).thenReturn(Optional.of(user));
        var result = userAppService.update(new UserUpdateRequest(
                1001L, "renamed", "", SystemType.MANAGER, Set.of("user:list"), null), 9001L);
        assertThat(result.enabled()).isFalse();
        verify(loginMapper, never()).deleteByUserId(1001L);
    }

    @Test
    void shouldRevokeSessionsAfterPasswordChange() {
        UserInfoEntity user = user(SystemType.MANAGER, "user:list");
        when(userMapper.findByIdForUpdate(1001L)).thenReturn(Optional.of(user));
        when(passwordHasher.hash("new-password")).thenReturn("encoded");
        userAppService.update(new UserUpdateRequest(
                1001L, "test-user", "new-password", SystemType.MANAGER, Set.of("user:list"), true), 9001L);
        verify(loginMapper).deleteByUserId(1001L);
        assertThat(user.getUserPass()).isEqualTo("encoded");
    }

    @Test
    void shouldDeleteSessionsAndRetainDeletionAudit() {
        UserInfoEntity user = user(SystemType.MANAGER, "user:list");
        when(userMapper.findByIdForUpdate(1001L)).thenReturn(Optional.of(user));
        userAppService.delete(1001L, 9001L);
        verify(loginMapper).deleteByUserId(1001L);
        verify(userMapper).deleteById(1001L);
        ArgumentCaptor<PermissionChangeAuditEntity> audit = ArgumentCaptor.forClass(PermissionChangeAuditEntity.class);
        verify(auditMapper).insert(audit.capture());
        assertThat(audit.getValue().getBeforePermissions()).isEqualTo("user:list");
        assertThat(audit.getValue().getAfterPermissions()).isEmpty();
        assertThat(audit.getValue().isAfterEnabled()).isFalse();
    }

    private static UserInfoEntity user(
            SystemType systemType,
            String permissions) {
        UserInfoEntity user = new UserInfoEntity();
        user.setId(1001L);
        user.setUserName("test-user");
        user.setSystemType(systemType);
        user.setUserPermissions(permissions);
        user.setEnabled(true);
        return user;
    }

    private static UserInfoEntity userWithId(
            long id,
            String name,
            String permissions) {
        UserInfoEntity user = user(SystemType.MANAGER, permissions);
        user.setId(id);
        user.setUserName(name);
        return user;
    }
}
