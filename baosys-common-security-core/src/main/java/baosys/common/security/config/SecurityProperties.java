package baosys.common.security.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 网关与资源服务的安全策略配置，绑定 {@code spring.security} 下的项目扩展属性。
 *
 * <p>网关使用 {@code gateway}，资源服务使用 {@code resource}，由各自的安全配置类明确读取，
 * 不根据配置内容自动选择角色。两个对象都会初始化，未配置的字段保留默认值。
 * 标准 OAuth2 参数由 Spring Boot 自身绑定，不由本类管理。</p>
 * <p>网关先匹配公开路径，再按顺序匹配访问规则；规则未指定权限时仅要求登录。
 * 资源服务对非公开路径要求有效认证，并通过接口上的 {@code @PreAuthorize} 校验具体 API 权限，
 * 不再统一检查系统入口权限。接口内部仍需执行数据归属和业务权限校验。</p>
 */
@ConfigurationProperties("spring.security")
public class SecurityProperties {

    /**
     * 网关入口策略，对应 {@code spring.security.gateway}。
     * 路径使用网关接收到的对外请求路径，例如 {@code /api/baosys-manager/v1/users/me}。
     */
    private Gateway gateway = new Gateway();
    /**
     * 资源服务入口策略，对应 {@code spring.security.resource}。
     * 路径使用服务内部路径，不包含 Servlet context-path，例如 {@code /v1/users/me}。
     */
    private Resource resource = new Resource();

    public Gateway getGateway() {
        return gateway;
    }

    public void setGateway(Gateway value) {
        gateway = value;
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource value) {
        resource = value;
    }

    /**
     * 网关与资源服务共用的路径访问规则，配置前缀由所属的 gateway 或 resource 决定。
     */
    public static class EndpointAccess {

        /**
         * 无需登录即可访问的路径模式，对应 {@code permit-all-paths}，默认空列表。
         * 例如 {@code /actuator/health/**}；支持对应 Spring Security 路径匹配器的模式。
         * 优先于后续的认证和授权规则，重叠时以公开规则为准。
         * 此处仅放开路径授权，不代表跳过所有安全过滤器，也不会自动豁免网关 CSRF 校验。
         */
        private List<String> permitAllPaths = new ArrayList<>();

        public List<String> getPermitAllPaths() {
            return permitAllPaths;
        }

        public void setPermitAllPaths(List<String> value) {
            permitAllPaths = value;
        }

        /**
         * 校验并规范化 {@code permit-all-paths}，拒绝空白项并去除首尾空白。
         */
        public List<String> normalizedPermitAllPaths(String propertyName) {
            if (permitAllPaths == null) {
                return new ArrayList<>();
            }
            var normalized = new ArrayList<String>(permitAllPaths.size());
            for (String raw : permitAllPaths) {
                if (!StringUtils.hasText(raw)) {
                    throw new IllegalArgumentException(propertyName + " entries must not be blank");
                }
                normalized.add(raw.trim());
            }
            return normalized;
        }
    }

    /**
     * 资源服务策略：公开路径之外的请求必须通过 JWT 认证。
     * 具体 API 权限由 {@code @PreAuthorize} 等方法级规则声明；未声明额外规则的接口仅要求认证。
     * 公开运维接口由 {@code permit-all-paths} 配置；未公开的接口仅要求认证。
     */
    public static class Resource extends EndpointAccess {}

    /**
     * 网关策略：按对外请求路径检查目标子系统的入口权限。
     */
    public static class Gateway extends EndpointAccess {
        /**
         * 要清理的完整请求头名，对应 {@code identity-header-names}，不区分大小写。
         * 配置后替换默认列表；空列表表示不按完整头名清理。
         */
        private List<String> identityHeaderNames = new ArrayList<>(
            List.of("UserId", "TenantId", "X-User", "X-Tenant")
        );

        /**
         * 要清理的请求头名前缀，对应 {@code identity-header-prefixes}，不区分大小写。
         * 使用普通前缀（如 X-User-），无需添加通配符。
         * 配置后替换默认列表；空列表表示不按前缀清理。空白项忽略，避免匹配所有头。
         */
        private List<String> identityHeaderPrefixes = new ArrayList<>(
            List.of("X-User-", "X-Tenant-")
        );

        public List<String> getIdentityHeaderNames() { return identityHeaderNames; }
        public void setIdentityHeaderNames(List<String> value) { identityHeaderNames = value; }
        public List<String> getIdentityHeaderPrefixes() { return identityHeaderPrefixes; }
        public void setIdentityHeaderPrefixes(List<String> value) { identityHeaderPrefixes = value; }

        /** 网关 CSRF 校验开关，对应 csrf-enabled，默认关闭。 */
        private boolean csrfEnabled = false;
        public boolean isCsrfEnabled() { return csrfEnabled; }
        public void setCsrfEnabled(boolean value) { csrfEnabled = value; }

        private String csrfPath;
        private String csrfCookieName;
        public String getCsrfPath() { return csrfPath; }
        public void setCsrfPath(String value) { csrfPath = value; }
        public String getCsrfCookieName() { return csrfCookieName; }
        public void setCsrfCookieName(String value) { csrfCookieName = value; }


        /**
         * 路径与登录/系统入口权限要求的映射，对应 {@code spring.security.gateway.access-rules}，默认空列表。
         * 在公开路径之后按列表顺序匹配，首次命中的规则生效。
         * 共享接口的具体路径应放在子系统的宽泛路径之前；permission 为空时仍要求登录。
         * 未匹配任何规则的 {@code /api/**} 请求被拒绝，其他未匹配路径由当前网关安全配置放行。
         * 此列表仅控制访问权限，实际转发地址仍由 Spring Cloud Gateway 路由配置决定。
         */
        private List<RoutePermission> accessRules = new ArrayList<>();

        public List<RoutePermission> getAccessRules() {
            return accessRules;
        }

        public void setAccessRules(List<RoutePermission> value) {
            accessRules = value;
        }
    }

    /**
     * 一条网关路径授权规则，对应 {@code spring.security.gateway.access-rules} 中的一个列表项。
     */
    public static class RoutePermission {

        /**
         * 需要检查权限的对外请求路径模式，对应列表项的 {@code path}。
         * 例如 {@code /api/baosys-manager/**}；每条规则应提供有效路径模式，默认未设置。
         */
        private String path;
        /**
         * 命中该路径时要求的子系统编码，对应列表项的 {@code permission}。
         * 未配置、空字符串或仅含空白字符时，只要求有效登录，不检查系统入口权限。
         * 适用于全局子系统共用接口；下游的 API 权限和数据归属检查仍然生效。
         * 例如 {@code MGR}；约定使用 3～8 个大写字母，配置值即为入口权限。
         * 网关将编码转换为 {@code MGR} 等 authority，与 JWT 中的系统入口权限匹配。
         * 命名由配置维护，不进行格式校验。
         */
        private String permission;

        public String getPath() {
            return path;
        }

        public void setPath(String value) {
            path = value;
        }

        public String getPermission() {
            return permission;
        }

        public void setPermission(String value) {
            permission = value;
        }
    }
}
