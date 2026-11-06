package baosys.manager.app.services.impl;

import baosys.common.security.jwt.JwtConstants;
import baosys.common.security.jwt.TokenIssuer;
import baosys.manager.app.services.BrowserAuthAppService.Tokens;
import baosys.manager.infra.database.mapper.UserInfoMapper;
import baosys.manager.infra.security.*;
import baosys.manager.infra.security.AccountAuthenticator.VerifiedAccount;
import java.time.*;
import java.util.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证成功后的持久化事务；所有账户锁先于授权与会话写入。
 */
@Service
public class BrowserLoginTransaction {
    private final UserInfoMapper users;
    private final AccountAuthorizationService authorizations;
    private final RegisteredClientRepository clients;
    private final TokenIssuer issuer;
    private final AccountTokenSubjectResolver subjects;
    private final AuthorizationServerSettings settings;
    private final Clock clock;
    private final SharedLoginNavigation navigation;

    public BrowserLoginTransaction(UserInfoMapper users, AccountAuthorizationService authorizations,
        RegisteredClientRepository clients, TokenIssuer issuer, AccountTokenSubjectResolver subjects,
        AuthorizationServerSettings settings, Clock clock, SharedLoginNavigation navigation) {
        this.users = users;
        this.authorizations = authorizations;
        this.clients = clients;
        this.issuer = issuer;
        this.subjects = subjects;
        this.settings = settings;
        this.clock = clock;
        this.navigation = navigation;
    }

    @Transactional
    public Tokens completeLogin(VerifiedAccount verified, String previousRefresh, String target) {
        var previous = authorizations.lockForInternalReplacement(verified.id(), previousRefresh);
        var user = users.findByIdForUpdate(verified.id()).filter(value -> value.isEnabled())
            .orElseThrow(BrowserLoginTransaction::unauthorized);
        if (!Objects.equals(user.getUserName(), verified.username())
            || !Objects.equals(user.getUserPass(), verified.passwordHash())) throw unauthorized();
        String redirect = navigation.destination(target, user.getDefaultSystemType(),
            baosys.manager.infra.database.converter.PermissionPersistenceConverter.decode(user.getUserPermissions()));
        if (previous != null) authorizations.remove(previous);
        var client = Objects.requireNonNull(clients.findByClientId(JwtConstants.BROWSER_CLIENT_ID));
        var authorization = OAuth2Authorization.withRegisteredClient(client)
            .principalName(user.getId().toString()).authorizationGrantType(SharedLoginGrant.TYPE)
            .authorizedScopes(Set.of()).build();
        authorizations.save(authorization);
        var tokens = issue(authorization, clock.instant().plus(client.getTokenSettings().getRefreshTokenTimeToLive()));
        return new Tokens(tokens.accessToken(), tokens.refreshToken(), tokens.accessExpiry(), tokens.refreshExpiry(), redirect);
    }

    @Transactional
    public Tokens refresh(String refresh) {
        var authorization = find(refresh);
        if (authorization == null || !authorization.getRefreshToken().isActive()) throw unauthorized();
        return issue(authorization, authorization.getRefreshToken().getToken().getExpiresAt());
    }

    @Transactional
    public void revoke(String refresh) {
        var authorization = find(refresh);
        if (authorization != null) authorizations.remove(authorization);
    }

    private OAuth2Authorization find(String refresh) {
        if (refresh == null || refresh.isBlank()) return null;
        var value = authorizations.findByToken(refresh, OAuth2TokenType.REFRESH_TOKEN);
        return value != null && SharedLoginGrant.TYPE.equals(value.getAuthorizationGrantType()) ? value : null;
    }

    private Tokens issue(OAuth2Authorization authorization, Instant refreshExpiry) {
        var client = clients.findById(authorization.getRegisteredClientId());
        Instant now = clock.instant();
        Instant expiry = now.plus(client.getTokenSettings().getAccessTokenTimeToLive());
        // 访问令牌的有效期不能超过当前会话的刷新期限。
        if (expiry.isAfter(refreshExpiry)) {
            expiry = refreshExpiry;
        }
        TokenIssuer.AccessTokenSubject subject;
        try {
            subject = subjects.resolve(authorization, client.getClientId());
        } catch (OAuth2AuthenticationException exception) {
            throw unauthorized();
        }
        if (!refreshExpiry.isAfter(clock.instant())) throw unauthorized();
        var jwt = issuer.issueAccessToken(settings.getIssuer(), subject, now, expiry);
        var refresh = issuer.issueRefreshToken(now, refreshExpiry);
        authorizations.save(
            OAuth2Authorization.from(authorization)
                .token(
                    new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        jwt.getTokenValue(),
                        now,
                        expiry
                    ),
                    metadata ->
                        metadata.put(
                            OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                            jwt.getClaims()
                        )
                )
                .refreshToken(refresh)
                .build()
        );
        return new Tokens(jwt.getTokenValue(), refresh.getTokenValue(), expiry, refreshExpiry, null);
    }


    private static BadCredentialsException unauthorized() {
        return new BadCredentialsException("Invalid internal login");
    }
}
