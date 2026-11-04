package baosys.manager.infra.security;

import org.springframework.security.oauth2.core.AuthorizationGrantType;

/**
 * 内部授权标识，不注册为 OAuth token 端点的密码授权。
 */
public final class SharedLoginGrant {
    public static final AuthorizationGrantType TYPE =
        new AuthorizationGrantType("urn:baosys:grant:shared-login");
    private SharedLoginGrant() {}
}
