package baosys.common.security.jwt;

/**
 * 平台 JWT 签发、校验和权限转换共用的约定。
 * JwtConstants
 */
public final class JwtConstants {

    /**
     * JWT 中承载业务权限的字段名。
     */
    public static final String PERMISSIONS_CLAIM = "permissions";

    /**
     * 平台访问令牌的受众。
     */
    public static final String ACCESS_TOKEN_AUDIENCE = "def";

    /**
     * 平台浏览器客户端的标识。
     */
    public static final String BROWSER_CLIENT_ID = "baosys-web";

    private JwtConstants() {}
}
