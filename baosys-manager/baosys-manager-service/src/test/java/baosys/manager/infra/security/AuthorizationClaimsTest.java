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
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

class AuthorizationClaimsTest {

    @Test
    void removesFrameworkScopeAndKeepsBusinessPermissions() {
        var jdbc = mock(JdbcTemplate.class);
        var users = mock(UserInfoMapper.class);
        var context = mock(JwtEncodingContext.class);
        var authorization = mock(OAuth2Authorization.class);
        var client = mock(RegisteredClient.class);
        var user = new UserInfoEntity();
        user.setEnabled(true);
        user.setUserPermissions("MGR,MGR:user:list");

        // 模拟框架预填的协议 scope，确保定制后的访问令牌不会保留它。
        var claims = JwtClaimsSet.builder().claim("scope", List.of("openid", "profile"));
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorization()).thenReturn(authorization);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getClaims()).thenReturn(claims);
        when(context.getJwsHeader()).thenReturn(JwsHeader.with(SignatureAlgorithm.RS256));
        when(authorization.getPrincipalName()).thenReturn("1");
        when(authorization.getId()).thenReturn("authorization-id");
        when(client.getClientId()).thenReturn(JwtConstants.BROWSER_CLIENT_ID);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(jdbc.queryForList(
            "SELECT id FROM user_login WHERE authorization_id=? AND expires_at>now()",
            Long.class,
            "authorization-id"
        )).thenReturn(List.of(100L));

        new AuthorizationServerConfiguration()
            .accountClaims(jdbc, users, CatalogFixture.load())
            .customize(context);

        assertThat(claims.build().getClaims())
            .doesNotContainKey("scope")
            .containsEntry(JwtConstants.PERMISSIONS_CLAIM, List.of("MGR", "MGR:user:list"))
            .containsEntry("sub", "100");
    }
}
