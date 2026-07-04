package szs.baosys.manager.app.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import szs.baosys.common.security.password.PasswordHasher;
import szs.baosys.manager.app.error.BusinessException;
import szs.baosys.manager.api.dto.UserUpdateRequest;
import szs.baosys.manager.api.enums.SystemType;
import szs.baosys.manager.infra.database.entity.UserInfoEntity;
import szs.baosys.manager.infra.database.mapper.UserInfoMapper;
import szs.baosys.manager.infra.database.mapper.UserLoginMapper;

class UserAppServiceImplTest {

    private final UserInfoMapper userMapper = mock(UserInfoMapper.class);
    private final UserLoginMapper loginMapper = mock(UserLoginMapper.class);
    private final PasswordHasher passwordHasher = mock(PasswordHasher.class);
    private final UserAppServiceImpl userAppService = new UserAppServiceImpl(
            userMapper,
            loginMapper,
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
        when(userMapper.findById(1001L)).thenReturn(Optional.of(user));

        var updated = userAppService.update(new UserUpdateRequest(
                1001L,
                "simulation-user",
                "",
                SystemType.SIMULATION,
                Set.of()));

        assertThat(updated.permissions()).isEmpty();
        assertThat(user.getUserPermissions()).isEmpty();
    }

    @Test
    void shouldRejectEmptyPermissionsWhenSystemPermissionCatalogIsNotEmpty() {
        UserInfoEntity user = user(SystemType.MANAGER, "user:list");
        when(userMapper.findById(1001L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userAppService.update(new UserUpdateRequest(
                1001L,
                "manager-user",
                "",
                SystemType.MANAGER,
                Set.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessage("权限配置错误");
    }

    private static UserInfoEntity user(
            SystemType systemType,
            String permissions) {
        UserInfoEntity user = new UserInfoEntity();
        user.setId(1001L);
        user.setUserName("test-user");
        user.setSystemType(systemType);
        user.setUserPermissions(permissions);
        return user;
    }
}
