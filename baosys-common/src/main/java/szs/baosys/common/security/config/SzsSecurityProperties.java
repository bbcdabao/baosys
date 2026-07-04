package szs.baosys.common.security.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 平台安全配置属性。
 *
 * <p>所有配置统一使用 {@code szs.security} 前缀，网关、认证中心和资源服务共享同一组
 * Cookie 名称、JWT 签发者、权限 claim 名称等约定。</p>
 */
@ConfigurationProperties(prefix = "szs.security")
public class SzsSecurityProperties {

    /**
     * JWT 签发与校验配置。
     */
    private Jwt jwt = new Jwt();
    /**
     * 前端和网关之间传递访问令牌的 Cookie 名称。
     */
    private String accessTokenCookieName = "access_token";
    /**
     * JWT 中保存权限编码列表的 claim 名称。
     */
    private String permissionsClaimName = "permissions";
    /**
     * 转换为 Spring Security 权限时追加的前缀；平台默认直接使用权限编码。
     */
    private String authorityPrefix = "";
    /**
     * 网关安全配置。
     */
    private Gateway gateway = new Gateway();
    /**
     * Servlet 资源服务安全配置。
     */
    private EndpointAccess resource = new EndpointAccess();

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public String getAccessTokenCookieName() {
        return accessTokenCookieName;
    }

    public void setAccessTokenCookieName(String accessTokenCookieName) {
        this.accessTokenCookieName = accessTokenCookieName;
    }

    public String getPermissionsClaimName() {
        return permissionsClaimName;
    }

    public void setPermissionsClaimName(String permissionsClaimName) {
        this.permissionsClaimName = permissionsClaimName;
    }

    public String getAuthorityPrefix() {
        return authorityPrefix;
    }

    public void setAuthorityPrefix(String authorityPrefix) {
        this.authorityPrefix = authorityPrefix;
    }

    public Gateway getGateway() {
        return gateway;
    }

    public void setGateway(Gateway gateway) {
        this.gateway = gateway == null ? new Gateway() : gateway;
    }

    public EndpointAccess getResource() {
        return resource;
    }

    public void setResource(EndpointAccess resource) {
        this.resource = resource == null ? new EndpointAccess() : resource;
    }

    public static class Gateway extends EndpointAccess {

        /**
         * 网关 CSRF 校验配置。
         */
        private Csrf csrf = new Csrf();

        public Csrf getCsrf() {
            return csrf;
        }

        public void setCsrf(Csrf csrf) {
            this.csrf = csrf == null ? new Csrf() : csrf;
        }
    }

    public static class EndpointAccess {

        /**
         * 无需认证的路径，支持 Spring PathPattern 格式，例如 {@code /actuator/health/**}。
         */
        private List<String> permitAllPaths = new ArrayList<>();

        public List<String> getPermitAllPaths() {
            return permitAllPaths;
        }

        public void setPermitAllPaths(List<String> permitAllPaths) {
            this.permitAllPaths = permitAllPaths == null ? new ArrayList<>() : new ArrayList<>(permitAllPaths);
        }
    }

    public static class Csrf {

        /**
         * 是否启用网关 CSRF 校验。内网系统默认关闭，需要时显式打开。
         */
        private boolean enabled;
        /**
         * 前端和网关之间传递 CSRF token 的 Cookie 名称。
         */
        private String cookieName = "BAOSYS_CSRF_TOKEN";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCookieName() {
            return cookieName;
        }

        public void setCookieName(String cookieName) {
            this.cookieName = cookieName;
        }
    }

    public static class Jwt {

        /**
         * JWT 签发者。当前平台使用稳定字符串，不要求 issuer 必须是 URL。
         */
        private String issuer = "szs-auth-server";
        /**
         * 访问令牌有效期，单位秒。
         */
        private long accessTokenExpireSeconds = 7200;
        /**
         * X.509 RSA 公钥，支持 PEM 格式或去掉头尾后的裸 Base64。
         */
        private String publicKey;
        /**
         * PKCS#8 RSA 私钥，仅认证中心需要配置。
         */
        private String privateKey;

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public long getAccessTokenExpireSeconds() {
            return accessTokenExpireSeconds;
        }

        public void setAccessTokenExpireSeconds(long accessTokenExpireSeconds) {
            this.accessTokenExpireSeconds = accessTokenExpireSeconds;
        }

        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        public void setPrivateKey(String privateKey) {
            this.privateKey = privateKey;
        }
    }
}
