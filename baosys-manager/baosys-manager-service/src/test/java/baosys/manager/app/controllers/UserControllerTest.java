package baosys.manager.app.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import baosys.manager.api.dto.CurrentUserResponse;
import baosys.manager.app.services.UserAppService;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserControllerTest {

    @Test
    void shouldLoadCurrentUserFromAccessTokenSubject() {
        UserAppService userAppService = mock(UserAppService.class);
        Principal principal = () -> "2001";
        when(userAppService.userIdForLogin(2001L)).thenReturn(1001L);
        CurrentUserResponse expected = new CurrentUserResponse(
            1001L,
            "admin",
            "admin",
            List.of("MGR", "MGR:user:list")
        );
        when(userAppService.currentUser(1001L)).thenReturn(expected);
        UserController controller = new UserController(userAppService);

        var response = controller.currentUser(principal);

        assertThat(response.data()).isEqualTo(expected);
        verify(userAppService).currentUser(1001L);
    }

    @Test
    void shouldUseAuthenticatedSubjectAsDeleteOperator() {
        UserAppService service = mock(UserAppService.class);
        when(service.userIdForLogin(2001L)).thenReturn(9001L);
        new UserController(service).delete(1001L, () -> "2001");
        verify(service).delete(1001L, 9001L);
    }
}
