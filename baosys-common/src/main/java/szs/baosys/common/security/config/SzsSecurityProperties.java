package szs.baosys.common.security.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 平台安全配置属性。
 *
 * <p>所有配置统一使用 {@code szs.security} 前缀，网关、认证中心和资源服务共享同一组
 * Cookie 名称、JWT 签发者、权限 claim 名称等约定。这样认证链路中的各服务可以通过
 * 配置保持协议一致，而不是把这些约定散落在业务代码中。</p>
 */
@ConfigurationProperties(prefix = "szs.security")
public class SzsSecurityProperties {

    /**
     * JWT 签发与校验配置。
     */
    private Jwt jwt = new Jwt();
    /**
     * 前端和网关之间传递访问令牌的 Cookie 名称。
     *
     * <p>网关会从该 Cookie 中读取 access token，并在转发到下游服务前补齐
     * {@code Authorization: Bearer ...} 请求头。</p>
     */
    private String accessTokenCookieName = "access_token";
    /**
     * JWT 中保存权限编码列表的 claim 名称。
     *
     * <p>资源服务会读取这个 claim，并把其中的权限编码转换为 Spring Security
     * {@code GrantedAuthority}。</p>
     */
    private String permissionsClaimName = "permissions";
    /**
     * 当前网关允许访问的子系统类型。
     *
     * <p>该值只建议配置在网关应用中。Manager 网关使用 {@code MANAGER}。
     * 配置后 JWT 解码器会要求 token 中的 {@code systemType} claim 与它一致；未配置时
     * 跳过该校验，便于认证中心和业务服务继续只做身份、签名和功能权限校验。</p>
     */
    private String systemType;
    /**
     * 转换为 Spring Security 权限时追加的前缀。
     *
     * <p>平台默认直接使用权限编码，因此不追加 {@code ROLE_} 等前缀。</p>
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

    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
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

    /**
     * 网关侧安全配置。
     *
     * <p>继承公开路径配置，并额外提供 Cookie 场景下的 CSRF 保护开关。</p>
     */
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

    /**
     * 端点访问控制配置。
     *
     * <p>网关和 Servlet 资源服务都使用这组白名单语义，避免同一类公开路径在不同
     * 技术栈下出现理解不一致。</p>
     */
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

    /**
     * 网关 CSRF 校验配置。
     */
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

    /**
     * JWT 签发与校验配置。
     */
    public static class Jwt {

        /**
         * JWT 签发者，也就是平台约定的鉴权中心名称。
         *
         * <p>签发 access token 时会写入 {@code iss} claim，解码校验时也会要求
         * token 中的 {@code iss} 与这里配置的值一致。这样即使存在多个鉴权中心或
         * 多套密钥，也能通过 issuer 明确区分 token 的来源，避免误信其他签发方。</p>
         *
         * <p>当前平台使用稳定服务名，不要求 issuer 必须是 URL。</p>
         */
        private String issuer = "szs-auth-server";
        /**
         * 访问令牌有效期，单位秒。
         */
        private long accessTokenExpireSeconds = 7200;
        /**
         * X.509 RSA 公钥。
         *
         * <p>网关和资源服务用它校验 JWT 签名。支持完整 PEM 文本，或去掉 PEM
         * 头尾后的裸 Base64。</p>
         */
        private String publicKey;
        /**
         * PKCS#8 RSA 私钥。
         *
         * <p>仅认证中心需要配置，用于签发 access token；网关和资源服务不应持有
         * 私钥。</p>
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
