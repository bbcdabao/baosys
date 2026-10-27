package baosys.common.security.resource;

import baosys.common.security.jwt.JwtConstants;

import org.springframework.security.oauth2.server.resource.authentication.*;

/**
 * JWT 权限转换工具类
 *
 * 作用：将 JWT（JSON Web Token）中的 Claims（载荷数据）转换成 Spring Security 内部的 Authentication 认证对象。
 * 将 permissions 中的系统入口（MGR）和接口权限（MGR:user:list）原样转换为 authority。
 */
public final class JwtPermissionConverter {

    private JwtPermissionConverter() {}

    public static JwtAuthenticationConverter create() {
        var permissions = new JwtGrantedAuthoritiesConverter();
        permissions.setAuthoritiesClaimName(JwtConstants.PERMISSIONS_CLAIM);
        permissions.setAuthorityPrefix("");
        var converter = new JwtAuthenticationConverter();
        // 系统入口与接口权限均以 permissions 为唯一来源。
        converter.setJwtGrantedAuthoritiesConverter(permissions);
        return converter;
    }
}
