package baosys.manager.app.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;

import org.junit.jupiter.api.Test;

import baosys.manager.app.services.UserAppService;
import baosys.manager.api.dto.CurrentUserResponse;
import baosys.manager.api.enums.SystemType;

class UserControllerTest {

    @Test
    void shouldLoadCurrentUserFromAccessTokenSubject() {
        UserAppService userAppService = mock(UserAppService.class);
        Principal principal = () -> "1001";
        CurrentUserResponse expected = new CurrentUserResponse(
                1001L,
                "admin",
                "admin",
                SystemType.MANAGER,
                List.of("user:list"));
        when(userAppService.currentUser(1001L)).thenReturn(expected);
        UserController controller = new UserController(userAppService);

        var response = controller.currentUser(principal);

        assertThat(response.data()).isEqualTo(expected);
        verify(userAppService).currentUser(1001L);
    }
    @Test
    void shouldUseAuthenticatedSubjectAsDeleteOperator() {
        UserAppService service = mock(UserAppService.class);
        new UserController(service).delete(1001L, () -> "9001");
        verify(service).delete(1001L, 9001L);
    }
}
