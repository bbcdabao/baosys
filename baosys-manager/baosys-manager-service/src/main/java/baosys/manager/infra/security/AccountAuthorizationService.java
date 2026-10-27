package baosys.manager.infra.security;

import baosys.manager.infra.database.mapper.UserInfoMapper;
import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.*;

/** 关联账户与业务会话的生命周期；OAuth2 令牌存储和序列化交由框架 JDBC 服务处理。 */
public class AccountAuthorizationService implements OAuth2AuthorizationService {

    private final JdbcOAuth2AuthorizationService delegate;
    private final JdbcTemplate jdbc;
    private final RegisteredClientRepository clients;
    private final UserInfoMapper users;

    public AccountAuthorizationService(
        JdbcTemplate jdbc,
        RegisteredClientRepository clients,
        UserInfoMapper users
    ) {
        this.jdbc = jdbc;
        this.clients = clients;
        this.users = users;
        delegate = new JdbcOAuth2AuthorizationService(jdbc, clients);
    }

    /** 保存授权并同步业务会话；禁止已撤销的授权重新写入访问令牌。 */
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
            !SharedLoginService.GRANT.equals(authorization.getAuthorizationGrantType())
        ) {
            return;
        }
        var client = clients.findById(authorization.getRegisteredClientId());
        var expiry =
            authorization.getRefreshToken() == null
                ? Instant.now().plus(client.getTokenSettings().getRefreshTokenTimeToLive())
                : authorization.getRefreshToken().getToken().getExpiresAt();
        var attributes = RequestContextHolder.getRequestAttributes();
        var request =
            attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
        var session = request == null ? null : request.getSession(false);
        String agent = request == null ? "OAuth2" : request.getHeader("User-Agent");
        agent = agent == null ? "OAuth2" : agent.substring(0, Math.min(agent.length(), 512));
        jdbc.update(
            """
            INSERT INTO user_login(user_info_id, client_id, authorization_id, authentication_session_id,
                login_client, expires_at, created_at, updated_at, created_by, updated_by)
            VALUES(?,?,?,?,?,?,now(),now(),'oauth2','oauth2')
            ON CONFLICT (authorization_id) DO UPDATE SET expires_at=EXCLUDED.expires_at, updated_at=now()
            """,
            userId,
            client.getClientId(),
            authorization.getId(),
            session == null ? null : session.getId(),
            agent,
            OffsetDateTime.ofInstant(expiry, ZoneOffset.UTC)
        );
    }

    @Override
    @Transactional
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    /** 查询令牌时锁定账户并复查状态，与刷新、禁用和强制下线互斥。 */
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
}
