package baosys.manager.infra.security;
import baosys.common.security.jwt.JwtConstants;
import baosys.manager.infra.database.mapper.UserLoginMapper;
import org.springframework.stereotype.Component;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/** 已验签内部 JWT 到认证中心账户身份的适配。 */
@Component
public class InternalLoginAuthenticator {
    private final UserLoginMapper logins;
    public InternalLoginAuthenticator(UserLoginMapper logins) { this.logins = logins; }
    /** JWT 已由框架验签；仅内部共享登录可以转换为认证中心用户身份。 */
    public UsernamePasswordAuthenticationToken authenticateInternalLogin(Jwt jwt) {
        long loginId;
        try {
            loginId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new InvalidBearerTokenException("Invalid internal login", exception);
        }
        var accounts = logins.findInternalAccountIds(loginId, jwt.getClaimAsString("client_id"),
            JwtConstants.BROWSER_CLIENT_ID, SharedLoginGrant.TYPE.getValue());
        if (accounts.size() != 1) throw new InvalidBearerTokenException("Invalid internal login");
        var principal = User.withUsername(Long.toString(accounts.get(0)))
            .password("").authorities("ACCOUNT").build();
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
}
