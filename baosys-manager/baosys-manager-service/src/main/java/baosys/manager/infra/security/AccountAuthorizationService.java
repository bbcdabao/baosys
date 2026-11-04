package baosys.manager.infra.security;

import baosys.manager.infra.database.mapper.UserInfoMapper;
import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.transaction.annotation.Transactional;
import baosys.manager.infra.database.mapper.UserLoginMapper;
import org.springframework.transaction.annotation.Propagation;

/**
 * 关联账户与业务会话的生命周期；OAuth2 令牌存储和序列化交由框架 JDBC 服务处理。
 * AccountAuthorizationService
 */
public class AccountAuthorizationService implements OAuth2AuthorizationService {

    private final JdbcOAuth2AuthorizationService delegate;
    private final UserLoginMapper logins;
    private final LoginRequestMetadata metadata;
    private final Clock clock;
    private final RegisteredClientRepository clients;
    private final UserInfoMapper users;

    public AccountAuthorizationService(
        JdbcTemplate jdbc,
        RegisteredClientRepository clients,
        UserInfoMapper users, UserLoginMapper logins, LoginRequestMetadata metadata, Clock clock
    ) {
        this.logins = logins;
        this.metadata = metadata;
        this.clock = clock;
        this.clients = clients;
        this.users = users;
        delegate = new JdbcOAuth2AuthorizationService(jdbc, clients);
    }

    /**
     * 保存授权并同步业务会话；禁止已撤销的授权重新写入访问令牌。
     * @see OAuth2AuthorizationService#save(OAuth2Authorization)
     */
    @Override
    @Transactional
    public void save(OAuth2Authorization authorization) {
        long userId = Long.parseLong(authorization.getPrincipalName());
        users
            .findByIdForUpdate(userId)
            .filter(user -> user.isEnabled())
            .orElseThrow(() -> new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT));
        boolean existing = delegate.findById(authorization.getId()) != null;
        if (authorization.getAccessToken() != null && !existing) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }
        if (
            authorization.getRefreshToken() != null &&
            authorization.getRefreshToken().isInvalidated()
        ) {
            delegate.remove(authorization); // 关联的业务会话及 SSO 会话由数据库级联清理。
            return;
        }
        delegate.save(authorization);
        if (
            !AuthorizationGrantType.AUTHORIZATION_CODE.equals(
                authorization.getAuthorizationGrantType()
            ) &&
            !SharedLoginGrant.TYPE.equals(authorization.getAuthorizationGrantType())
        ) {
            return;
        }
        var client = clients.findById(authorization.getRegisteredClientId());
        Instant expiry = projectionExpiry(authorization, client);
        var context = metadata.current();
        logins.saveProjection(userId, client.getClientId(), authorization.getId(),
            context.sessionId(), context.agent(), OffsetDateTime.ofInstant(expiry, ZoneOffset.UTC));
    }

    @Override
    @Transactional
    public void remove(OAuth2Authorization authorization) {
        users.findByIdForUpdate(Long.parseLong(authorization.getPrincipalName()));
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    /**
     * 查询令牌时锁定账户并复查状态，与刷新、禁用和强制下线互斥。
     * @param token
     * @param type
     * @return
     */
    @Override
    @Transactional
    public OAuth2Authorization findByToken(String token, OAuth2TokenType type) {
        var authorization = delegate.findByToken(token, type);
        if (authorization == null) {
            return null;
        }
        if (
            users
                .findByIdForUpdate(Long.parseLong(authorization.getPrincipalName()))
                .filter(user -> user.isEnabled())
                .isEmpty()
        ) {
            return null;
        }
        // 取得账户锁后重新读取，避免使用已被并发刷新或强制下线移除的令牌。
        return delegate.findByToken(token, type);
    }

    /**
     * 仅用于账户切换：发现账户、排序加锁、重新读取；调用方必须持有外层事务。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OAuth2Authorization lockForInternalReplacement(long accountId, String refresh) {
        var candidate = findInternalWithoutLock(refresh);
        var ids = new java.util.TreeSet<Long>();
        ids.add(accountId);
        if (candidate != null) ids.add(Long.parseLong(candidate.getPrincipalName()));
        ids.forEach(users::findByIdForUpdate);
        var current = findInternalWithoutLock(refresh);
        if (current != null && !ids.contains(Long.parseLong(current.getPrincipalName()))) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }
        return current;
    }

    private OAuth2Authorization findInternalWithoutLock(String refresh) {
        if (refresh == null || refresh.isBlank()) return null;
        var value = delegate.findByToken(refresh, OAuth2TokenType.REFRESH_TOKEN);
        return value != null && SharedLoginGrant.TYPE.equals(value.getAuthorizationGrantType()) ? value : null;
    }

    private Instant projectionExpiry(OAuth2Authorization authorization,
        org.springframework.security.oauth2.server.authorization.client.RegisteredClient client) {
        if (authorization.getRefreshToken() != null) return authorization.getRefreshToken().getToken().getExpiresAt();
        if (authorization.getAccessToken() != null) {
            var expiry = authorization.getAccessToken().getToken().getExpiresAt();
            // OIDC 注销验证仍可能使用 ID Token，至少保留到两种令牌都过期。
            var idToken = authorization.getToken(org.springframework.security.oauth2.core.oidc.OidcIdToken.class);
            if (idToken != null && idToken.getToken().getExpiresAt().isAfter(expiry)) {
                expiry = idToken.getToken().getExpiresAt();
            }
            return expiry;
        }
        var code = authorization.getToken(OAuth2AuthorizationCode.class);
        if (code != null) return code.getToken().getExpiresAt();
        // 内部初次签发的临时投影，必须与签发处于同一事务。
        return clock.instant().plus(client.getTokenSettings().getRefreshTokenTimeToLive());
    }
}
