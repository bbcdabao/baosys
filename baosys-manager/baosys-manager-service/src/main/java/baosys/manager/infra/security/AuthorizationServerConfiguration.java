package baosys.manager.infra.security;

import baosys.common.security.jwt.TokenIssuer;
import baosys.manager.infra.database.mapper.UserInfoMapper;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import java.time.Clock;
import baosys.manager.infra.database.mapper.UserLoginMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.web.SecurityFilterChain;
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
        PlatformTransactionManager transactions,
        org.springframework.security.oauth2.jwt.JwtDecoder decoder,
        InternalLoginAuthenticator subjects,
        SharedLoginNavigation navigation,
        org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings settings
    ) throws Exception {
        var server = OAuth2AuthorizationServerConfigurer.authorizationServer();
        return http
            .securityMatcher(server.getEndpointsMatcher())
            .with(server, config -> config.oidc(Customizer.withDefaults()))
            .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
            .exceptionHandling(errors ->
                errors.defaultAuthenticationEntryPointFor(
                    navigation,
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                )
            )
            .addFilterBefore(
                new SharedLoginAuthorizationFilter(decoder, subjects, settings, navigation),
                LogoutFilter.class
            )
            .addFilterBefore(
                new AuthorizationTransactionFilter(transactions),
                LogoutFilter.class
            )
            .build();
    }

    /**
     * 旧登录地址跳转到共用 Vue 登录页，不再生成框架默认表单。
     * @param http
     * @return
     * @throws Exception
     */
    @Bean
    @Order(2)
    SecurityFilterChain loginChain(HttpSecurity http, SharedLoginNavigation navigation) throws Exception {
        return http
            .securityMatcher("/login", "/logout", "/error")
            .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
            .exceptionHandling(errors -> errors.authenticationEntryPoint(navigation))
            .build();
    }

    @Bean
    Clock authClock() { return Clock.systemUTC(); }

    @Bean
    PasswordEncoder passwordEncoder() {
        var encoder =
            (DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
        // 兼容历史账户无前缀的 BCrypt 密码；新客户端密钥使用 {bcrypt} 前缀。
        encoder.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
        return encoder;
    }

    @Bean
    AccountAuthorizationService authorizationService(
        JdbcTemplate jdbc,
        RegisteredClientRepository clients,
        UserInfoMapper users, UserLoginMapper logins, LoginRequestMetadata metadata, Clock clock
    ) {
        return new AccountAuthorizationService(jdbc, clients, users, logins, metadata, clock);
    }

    @Bean
    OAuth2AuthorizationConsentService consentService(
        JdbcTemplate jdbc,
        RegisteredClientRepository clients
    ) {
        return new JdbcOAuth2AuthorizationConsentService(jdbc, clients);
    }

    @Bean
    TokenIssuer tokenIssuer(org.springframework.security.oauth2.jwt.JwtEncoder encoder) {
        return new TokenIssuer(encoder);
    }

    @Bean
    OAuth2TokenGenerator<?> tokenGenerator(
        TokenIssuer issuer,
        AccountTokenSubjectResolver subjects,
        org.springframework.security.oauth2.jwt.JwtEncoder encoder, Clock clock
    ) {
        // 业务 Access/Refresh Token 统一签发；OIDC ID Token 保留框架实现。
        return new DelegatingOAuth2TokenGenerator(
            new AccountTokenGenerator(issuer, subjects, clock),
            new JwtGenerator(encoder)
        );
    }
}
