package baosys.manager.app.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import baosys.common.security.password.PasswordHasher;
import baosys.manager.api.dto.UserUpdateRequest;
import baosys.manager.app.error.BusinessException;
import baosys.manager.infra.database.entity.PermissionChangeAuditEntity;
import baosys.manager.infra.database.entity.UserInfoEntity;
import baosys.manager.infra.database.mapper.PermissionChangeAuditMapper;
import baosys.manager.infra.database.mapper.UserInfoMapper;
import baosys.manager.infra.database.mapper.UserLoginMapper;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserAppServiceImplTest {

    private final UserInfoMapper userMapper = mock(UserInfoMapper.class);
    private final UserLoginMapper loginMapper = mock(UserLoginMapper.class);
    private final PermissionChangeAuditMapper auditMapper = mock(PermissionChangeAuditMapper.class);
    private final PasswordHasher passwordHasher = mock(PasswordHasher.class);
    private final UserAppServiceImpl userAppService = new UserAppServiceImpl(
        userMapper,
        loginMapper,
        auditMapper,
        passwordHasher,
        baosys.manager.app.config.CatalogFixture.load()
    );

    @Test
    void shouldLoadFlatPermissionsFromPersistedUser() {
        UserInfoEntity user = new UserInfoEntity();
        user.setId(1001L);
        user.setUserName("manager-user");
        user.setUserPermissions("MGR:user:list,MGR");
        when(userMapper.findById(1001L)).thenReturn(Optional.of(user));

        var currentUser = userAppService.currentUser(1001L);

        assertThat(currentUser.userId()).isEqualTo(1001L);
        assertThat(currentUser.permissions()).containsExactly("MGR", "MGR:user:list");
    }

    @Test
    void shouldAllowManagerEntryWithoutApiPermissions() {
        UserInfoEntity user = user("MGR", "");
        when(userMapper.findByIdForUpdate(1001L)).thenReturn(Optional.of(user));

        var updated = userAppService.update(
            new UserUpdateRequest(1001L, "manager-user", "", Set.of("MGR"), true),
            9001L
        );

        assertThat(updated.permissions()).containsExactly("MGR");
        assertThat(user.getUserPermissions()).isEqualTo("MGR");
    }

    @Test
    void shouldRejectApiPermissionWithoutItsSystemEntry() {
        UserInfoEntity user = user("MGR", "MGR:user:list");
        when(userMapper.findByIdForUpdate(1001L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
            userAppService.update(
                new UserUpdateRequest(1001L, "manager-user", "", Set.of("MGR:user:list"), true),
                9001L
            )
        )
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("权限配置错误");
    }

    @Test
    void shouldRevokeSessionsAndAuditPermissionChange() {
        UserInfoEntity user = user("MGR", "MGR:user:list");
        when(userMapper.findByIdForUpdate(1001L)).thenReturn(Optional.of(user));

        userAppService.update(
            new UserUpdateRequest(
                1001L,
                "manager-user",
                "",
                Set.of("MGR", "MGR:user:update"),
                true
            ),
            9001L
        );

        verify(loginMapper).deleteByUserId(1001L);
        ArgumentCaptor<PermissionChangeAuditEntity> captor = ArgumentCaptor.forClass(
            PermissionChangeAuditEntity.class
        );
        verify(auditMapper).insert(captor.capture());
        assertThat(captor.getValue().getOperatorUserId()).isEqualTo(9001L);
        assertThat(captor.getValue().getTargetUserId()).isEqualTo(1001L);
        assertThat(captor.getValue().getBeforePermissions()).isEqualTo("MGR,MGR:user:list");
        assertThat(captor.getValue().getAfterPermissions()).isEqualTo("MGR,MGR:user:update");
        assertThat(captor.getValue().getTraceId()).isNotBlank();
        assertThat(captor.getValue().getOccurredAt()).isNotNull();
    }

    @Test
    void shouldRevokeSessionsAndAuditWhenAccountIsDisabled() {
        UserInfoEntity user = user("MGR", "MGR:user:list");
        when(userMapper.findByIdForUpdate(1001L)).thenReturn(Optional.of(user));

        userAppService.update(
            new UserUpdateRequest(1001L, "manager-user", "", Set.of("MGR", "MGR:user:list"), false),
            9001L
        );

        verify(loginMapper).deleteByUserId(1001L);
        ArgumentCaptor<PermissionChangeAuditEntity> captor = ArgumentCaptor.forClass(
            PermissionChangeAuditEntity.class
        );
        verify(auditMapper).insert(captor.capture());
        assertThat(captor.getValue().isBeforeEnabled()).isTrue();
        assertThat(captor.getValue().isAfterEnabled()).isFalse();
    }

    @Test
    void shouldKeepSessionsWhenAuthorizationDoesNotChange() {
        UserInfoEntity user = user("MGR", "MGR:user:list");
        when(userMapper.findByIdForUpdate(1001L)).thenReturn(Optional.of(user));

        userAppService.update(
            new UserUpdateRequest(1001L, "renamed-user", "", Set.of("MGR", "MGR:user:list"), true),
            9001L
        );

        verify(loginMapper, never()).deleteByUserId(1001L);
        verify(auditMapper, never()).insert(
            org.mockito.ArgumentMatchers.any(PermissionChangeAuditEntity.class)
        );
    }

    @Test
    void shouldPreserveDisabledStateWhenUpdateOmitsEnabled() {
        UserInfoEntity user = user("MGR", "MGR:user:list");
        user.setEnabled(false);
        when(userMapper.findByIdForUpdate(1001L)).thenReturn(Optional.of(user));
        var result = userAppService.update(
            new UserUpdateRequest(1001L, "renamed", "", Set.of("MGR", "MGR:user:list"), null),
            9001L
        );
        assertThat(result.enabled()).isFalse();
        verify(loginMapper, never()).deleteByUserId(1001L);
    }

    @Test
    void shouldRevokeSessionsAfterPasswordChange() {
        UserInfoEntity user = user("MGR", "MGR:user:list");
        when(userMapper.findByIdForUpdate(1001L)).thenReturn(Optional.of(user));
        when(passwordHasher.hash("new-password")).thenReturn("encoded");
        userAppService.update(
            new UserUpdateRequest(
                1001L,
                "test-user",
                "new-password",
                Set.of("MGR", "MGR:user:list"),
                true
            ),
            9001L
        );
        verify(loginMapper).deleteByUserId(1001L);
        assertThat(user.getUserPass()).isEqualTo("encoded");
    }

    @Test
    void shouldDeleteSessionsAndRetainDeletionAudit() {
        UserInfoEntity user = user("MGR", "MGR:user:list");
        when(userMapper.findByIdForUpdate(1001L)).thenReturn(Optional.of(user));
        userAppService.delete(1001L, 9001L);
        verify(loginMapper).deleteByUserId(1001L);
        verify(userMapper).deleteById(1001L);
        ArgumentCaptor<PermissionChangeAuditEntity> audit = ArgumentCaptor.forClass(
            PermissionChangeAuditEntity.class
        );
        verify(auditMapper).insert(audit.capture());
        assertThat(audit.getValue().getBeforePermissions()).isEqualTo("MGR,MGR:user:list");
        assertThat(audit.getValue().getAfterPermissions()).isEmpty();
        assertThat(audit.getValue().isAfterEnabled()).isFalse();
    }

    private static UserInfoEntity user(String systemType, String permissions) {
        UserInfoEntity user = new UserInfoEntity();
        user.setId(1001L);
        user.setUserName("test-user");
        user.setUserPermissions(systemType + (permissions.isEmpty() ? "" : "," + permissions));
        user.setEnabled(true);
        return user;
    }

    private static UserInfoEntity userWithId(long id, String name, String permissions) {
        UserInfoEntity user = user("MGR", permissions);
        user.setId(id);
        user.setUserName(name);
        return user;
    }
}
