package baosys.manager.infra.security;

import baosys.common.security.jwt.TokenIssuer;
import java.time.Clock;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.token.*;

/** 将授权服务器的令牌上下文适配到公共签发器，协议校验仍交由框架执行。 */
public final class AccountTokenGenerator implements OAuth2TokenGenerator<OAuth2Token> {
    private final TokenIssuer issuer;
    private final AccountTokenSubjectResolver subjects;

    private final Clock clock;

    public AccountTokenGenerator(TokenIssuer issuer, AccountTokenSubjectResolver subjects, Clock clock) {
        this.clock = clock;
        this.issuer = issuer;
        this.subjects = subjects;
    }

    @Override
    public OAuth2Token generate(OAuth2TokenContext context) {
        var client = context.getRegisteredClient();
        var now = clock.instant();
        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            return issuer.issueAccessToken(context.getAuthorizationServerContext().getIssuer(),
                subjects.resolve(context.getAuthorization(), client.getClientId()),
                now, now.plus(client.getTokenSettings().getAccessTokenTimeToLive()));
        }
        if (OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())) {
            // 保持框架默认限制：公共客户端的授权码兑换不签发 Refresh Token。
            if (AuthorizationGrantType.AUTHORIZATION_CODE.equals(context.getAuthorizationGrantType())
                && context.getAuthorizationGrant() instanceof OAuth2AuthorizationCodeAuthenticationToken grant
                && grant.getPrincipal() instanceof OAuth2ClientAuthenticationToken principal
                && ClientAuthenticationMethod.NONE.equals(principal.getClientAuthenticationMethod())) {
                return null;
            }
            return issuer.issueRefreshToken(now,
                now.plus(client.getTokenSettings().getRefreshTokenTimeToLive()));
        }
        return null;
    }
}
