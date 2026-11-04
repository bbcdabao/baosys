package baosys.manager.app.services.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import baosys.common.security.jwt.TokenIssuer;
import baosys.manager.infra.database.mapper.UserInfoMapper;
import baosys.manager.infra.security.*;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

class BrowserLoginTransactionTest {
    @Test
    void refreshExpiringDuringSubjectResolutionIsRejectedBeforeSigning() {
        var store = mock(AccountAuthorizationService.class);
        var clients = mock(RegisteredClientRepository.class);
        var issuer = mock(TokenIssuer.class);
        var subjects = mock(AccountTokenSubjectResolver.class);
        var clock = mock(Clock.class);
        var expiry = Instant.now().plusSeconds(3600);
        var client = RegisteredClient.withId("browser").clientId("browser")
            .authorizationGrantType(SharedLoginGrant.TYPE).build();
        var authorization = OAuth2Authorization.withRegisteredClient(client).principalName("1")
            .authorizationGrantType(SharedLoginGrant.TYPE)
            .refreshToken(new OAuth2RefreshToken("refresh", expiry.minusSeconds(7200), expiry)).build();
        when(store.findByToken("refresh", OAuth2TokenType.REFRESH_TOKEN)).thenReturn(authorization);
        when(clients.findById("browser")).thenReturn(client);
        when(clock.instant()).thenReturn(expiry.minusSeconds(1), expiry);
        when(subjects.resolve(authorization, "browser"))
            .thenReturn(new TokenIssuer.AccessTokenSubject("1", "browser", List.of()));
        var transaction = new BrowserLoginTransaction(mock(UserInfoMapper.class), store, clients, issuer,
            subjects, AuthorizationServerSettings.builder().issuer("https://issuer.example").build(), clock);
        assertThatThrownBy(() -> transaction.refresh("refresh")).isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(issuer);
        verify(store, never()).save(any());
    }
}
