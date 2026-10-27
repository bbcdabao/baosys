package baosys.common.security.resource;

/**
 * TOKEN Cookie 名称常量
 */
public final class SharedTokenCookies {

    /**
     * 访问令牌随业务请求发送，由网关进行认证。
     */
    public static final String ACCESS = "BAOSYS_ACCESS_TOKEN";
    public static final String REFRESH = "BAOSYS_REFRESH_TOKEN";

    /**
     * 刷新令牌仅发送给共享登录接口，缩小浏览器携带范围。
     */
    public static final String REFRESH_PATH = "/api/baosys-manager/v1/browser-auth";

    private SharedTokenCookies() {}
}
