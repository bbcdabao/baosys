package baosys.manager.infra.security;

import baosys.common.security.jwt.TokenIssuer.AccessTokenSubject;
import baosys.manager.app.config.SystemCatalog;
import baosys.manager.infra.database.converter.PermissionPersistenceConverter;
import baosys.manager.infra.database.mapper.UserInfoMapper;
import baosys.manager.infra.database.mapper.UserLoginMapper;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.stereotype.Component;

/** 签发时解析登录记录主体与锁内最新权限，不负责浏览器身份认证。 */
@Component
public class AccountTokenSubjectResolver {
    private final UserLoginMapper logins;
    private final UserInfoMapper users;
    private final SystemCatalog catalog;

    public AccountTokenSubjectResolver(UserLoginMapper logins, UserInfoMapper users, SystemCatalog catalog) {
        this.logins = logins;
        this.users = users;
        this.catalog = catalog;
    }

    public AccessTokenSubject resolve(OAuth2Authorization authorization, String clientId) {
        if (authorization == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }
        var user = users.findByIdForUpdate(Long.parseLong(authorization.getPrincipalName()))
            .filter(value -> value.isEnabled())
            .orElseThrow(() -> new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT));
        var ids = logins.findActiveIdsByAuthorization(authorization.getId());
        if (ids.size() != 1) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }
        return new AccessTokenSubject(Long.toString(ids.get(0)), clientId,
            new java.util.ArrayList<>(catalog.effectiveGrants(
                PermissionPersistenceConverter.decode(user.getUserPermissions())
            )));
    }

}
