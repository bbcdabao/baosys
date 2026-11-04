package baosys.manager.infra.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import baosys.common.security.jwt.TokenIssuer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.authentication.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;

class AccountTokenGeneratorTest {
    private final TokenIssuer issuer = spy(new TokenIssuer(parameters -> new Jwt(
        "encoded", parameters.getClaims().getIssuedAt(), parameters.getClaims().getExpiresAt(),
        parameters.getJwsHeader().getHeaders(), parameters.getClaims().getClaims()
    )));
    private final AccountTokenSubjectResolver subjects = mock(AccountTokenSubjectResolver.class);
    private final OAuth2TokenContext context = mock(OAuth2TokenContext.class);
    private final RegisteredClient client = RegisteredClient.withId("id").clientId("client")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("https://client.example/callback").tokenSettings(TokenSettings.builder().build()).build();

    @Test
    void oauthAccessAndRefreshUseCommonIssuer() {
        when(context.getRegisteredClient()).thenReturn(client);
        var server = mock(AuthorizationServerContext.class);
        when(context.getAuthorizationServerContext()).thenReturn(server);
        when(server.getIssuer()).thenReturn("https://issuer.example");
        var authorization = mock(OAuth2Authorization.class);
        when(context.getAuthorization()).thenReturn(authorization);
        var subject = new TokenIssuer.AccessTokenSubject("100", "client", List.of("MGR"));
        when(subjects.resolve(authorization, "client")).thenReturn(subject);
        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        var generator = new AccountTokenGenerator(issuer, subjects, java.time.Clock.systemUTC());
        var access = (Jwt) generator.generate(context);
        verify(issuer).issueAccessToken(eq("https://issuer.example"), eq(subject), any(), any());
        assertThat(access.getClaims()).doesNotContainKey("scope").containsEntry("sub", "100");
        assertThat(access.getExpiresAt()).isEqualTo(access.getIssuedAt().plus(client.getTokenSettings().getAccessTokenTimeToLive()));

        when(context.getTokenType()).thenReturn(OAuth2TokenType.REFRESH_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.REFRESH_TOKEN);
        var refresh = (OAuth2RefreshToken) generator.generate(context);
        verify(issuer).issueRefreshToken(any(), any());
        assertThat(refresh.getExpiresAt()).isEqualTo(refresh.getIssuedAt().plus(client.getTokenSettings().getRefreshTokenTimeToLive()));
    }

    @Test
    void preservesPublicClientRestrictionAndLeavesIdTokensToFramework() {
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getTokenType()).thenReturn(OAuth2TokenType.REFRESH_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE);
        var principal = new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null);
        var grant = new OAuth2AuthorizationCodeAuthenticationToken("code", principal, "https://client.example/callback", null);
        when(context.getAuthorizationGrant()).thenReturn(grant);
        var generator = new AccountTokenGenerator(issuer, subjects, java.time.Clock.systemUTC());
        assertThat(generator.generate(context)).isNull();
        when(context.getTokenType()).thenReturn(new OAuth2TokenType("id_token"));
        assertThat(generator.generate(context)).isNull();
        verifyNoInteractions(issuer, subjects);
    }
}
