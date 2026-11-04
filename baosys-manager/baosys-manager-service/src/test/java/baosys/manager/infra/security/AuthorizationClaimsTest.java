package baosys.manager.infra.security;

import baosys.common.security.jwt.JwtConstants;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import baosys.manager.app.config.CatalogFixture;
import baosys.manager.infra.database.entity.UserInfoEntity;
import baosys.manager.infra.database.mapper.UserInfoMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

class AuthorizationClaimsTest {

    @Test
    void resolvesCurrentLoginAndBusinessPermissions() {
        var logins = mock(baosys.manager.infra.database.mapper.UserLoginMapper.class);
        var users = mock(UserInfoMapper.class);
        var authorization = mock(OAuth2Authorization.class);
        var client = mock(RegisteredClient.class);
        var user = new UserInfoEntity();
        user.setEnabled(true);
        user.setUserPermissions("MGR,MGR:user:list");

        when(authorization.getPrincipalName()).thenReturn("1");
        when(authorization.getId()).thenReturn("authorization-id");
        when(client.getClientId()).thenReturn(JwtConstants.BROWSER_CLIENT_ID);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(logins.findActiveIdsByAuthorization("authorization-id")).thenReturn(List.of(100L));

        var subject = new AccountTokenSubjectResolver(logins, users, CatalogFixture.load())
            .resolve(authorization, client.getClientId());

        assertThat(subject.permissions()).containsExactly("MGR", "MGR:user:list");
        assertThat(subject.subject()).isEqualTo("100");
        assertThat(subject.clientId()).isEqualTo(JwtConstants.BROWSER_CLIENT_ID);
    }
}