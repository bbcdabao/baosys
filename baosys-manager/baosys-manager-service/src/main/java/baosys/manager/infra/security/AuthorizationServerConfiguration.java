package baosys.manager.infra.security;

import baosys.common.security.jwt.JwtConstants;

import baosys.common.security.resource.PermissionSet;
import baosys.manager.app.config.SystemCatalog;
import baosys.manager.infra.database.converter.PermissionPersistenceConverter;
import baosys.manager.infra.database.mapper.UserInfoMapper;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.security.web.authentication.logout.LogoutFilter;

/**
 * 配置标准 OAuth2/OIDC 端点、账户认证和平台访问令牌声明。
 * AuthorizationServerConfiguration
 */
@Configuration(proxyBeanMethods = false)
public class AuthorizationServerConfiguration {

    /**
     * 优先匹配授权服务器端点，令牌签发与撤销在同一事务内处理。
     * @param http
     * @param transactions
     * @return
     * @throws Exception
     */
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerChain(
        HttpSecurity http,
        PlatformTransactionManager transactions
    ) throws Exception {
        var server = OAuth2AuthorizationServerConfigurer.authorizationServer();
        return http
            .securityMatcher(server.getEndpointsMatcher())
            .with(server, config -> config.oidc(Customizer.withDefaults()))
            .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
            .exceptionHandling(errors ->
                errors.defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                )
            )
            .addFilterBefore(
                new AuthorizationTransactionFilter(transactions),
                LogoutFilter.class
            )
            .build();
    }

    /**
     * 为授权码流程提供表单登录入口。
     * @param http
     * @return
     * @throws Exception
     */
    @Bean
    @Order(2)
    SecurityFilterChain loginChain(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/login", "/logout", "/error")
            .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
            .formLogin(form -> form.permitAll())
            .build();
    }

    @Bean
    UserDetailsService accountUsers(UserInfoMapper users) {
        return username ->
            users
                .findByUserName(username.trim())
                .map(user ->
                    User.withUsername(Long.toString(user.getId()))
                        .password(user.getUserPass())
                        .disabled(!user.isEnabled())
                        .authorities("ACCOUNT")
                        .build()
                )
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        var encoder =
            (DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
        // 兼容历史账户无前缀的 BCrypt 密码；新客户端密钥使用 {bcrypt} 前缀。
        encoder.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
        return encoder;
    }

    @Bean
    OAuth2AuthorizationService authorizationService(
        JdbcTemplate jdbc,
        RegisteredClientRepository clients,
        UserInfoMapper users
    ) {
        return new AccountAuthorizationService(jdbc, clients, users);
    }

    @Bean
    OAuth2AuthorizationConsentService consentService(
        JdbcTemplate jdbc,
        RegisteredClientRepository clients
    ) {
        return new JdbcOAuth2AuthorizationConsentService(jdbc, clients);
    }

    /**
     * 仅定制访问令牌：使用业务会话 ID 作为主体，并写入当前有效权限。
     * @param jdbc
     * @param users
     * @param catalog
     * @return
     */
    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> accountClaims(
        JdbcTemplate jdbc,
        UserInfoMapper users,
        SystemCatalog catalog
    ) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }
            var authorization = context.getAuthorization();
            if (authorization == null) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
            }
            var user = users
                .findByIdForUpdate(Long.parseLong(authorization.getPrincipalName()))
                .filter(u -> u.isEnabled())
                .orElseThrow(() ->
                    new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT)
                );
            var loginIds = jdbc.queryForList(
                "SELECT id FROM user_login WHERE authorization_id=? AND expires_at>now()",
                Long.class,
                authorization.getId()
            );
            if (loginIds.size() != 1) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
            }
            var grants = PermissionSet.normalize(
                catalog.effectiveGrants(
                    PermissionPersistenceConverter.decode(user.getUserPermissions())
                )
            );
            context
                .getJwsHeader()
                .type("at+jwt")
                .algorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256);
            context
                .getClaims()
                .subject(Long.toString(loginIds.get(0)))
                .audience(new java.util.ArrayList<>(List.of(JwtConstants.ACCESS_TOKEN_AUDIENCE)))
                .claim("client_id", context.getRegisteredClient().getClientId())
                .claim(JwtConstants.PERMISSIONS_CLAIM, new java.util.ArrayList<>(grants))
                // 移除框架预填的 scope，业务访问令牌仅用 permissions 表达权限。
                .claims(claims -> claims.remove("scope"));
        };
    }
}
