package szs.baosys.common.security.resource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * JWT 权限转换器工厂。
 *
 * <p>平台不再使用角色 claim，而是直接读取 {@code permissions} 权限编码列表，
 * 将每个权限编码转换成 Spring Security 的 {@link GrantedAuthority}。这样
 * {@code @PreAuthorize("hasAuthority('user:list')")} 可以直接使用平台权限编码。</p>
 */
public final class JwtPermissionConverter {

    private JwtPermissionConverter() {
    }

    /**
     * 创建 JWT 到 Spring Security 认证信息的转换器。
     *
     * <p>认证主体固定使用 JWT {@code sub}，也就是 token 签发时写入的用户主键；
     * 用户名等展示字段只作为普通 claim 保留，不参与权限判断。</p>
     *
     * @param claimName 保存权限列表的 JWT claim 名称
     * @param authorityPrefix 权限前缀，默认空字符串
     */
    public static JwtAuthenticationConverter create(String claimName, String authorityPrefix) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("sub");
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> permissions = jwt.getClaimAsStringList(claimName);
            if (permissions == null) {
                permissions = List.of();
            }
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            for (String permission : permissions) {
                if (permission != null && !permission.isBlank()) {
                    // 过滤空权限，避免生成无法匹配业务表达式的 GrantedAuthority。
                    authorities.add(new SimpleGrantedAuthority(authorityPrefix + permission));
                }
            }
            return authorities;
        });
        return converter;
    }
}
