package baosys.manager.infra.security;

import baosys.common.security.jwt.JwtConstants;

import baosys.common.security.resource.PermissionSet;
import baosys.manager.app.config.SystemCatalog;
import baosys.manager.app.error.BusinessException;
import baosys.manager.infra.database.converter.PermissionPersistenceConverter;
import baosys.manager.infra.database.mapper.UserInfoMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 浏览器共享登录服务，与 OAuth2 共用持久化授权和会话撤销流程。
 * SharedLoginService
 */
@Service
public class SharedLoginService {

    public static final AuthorizationGrantType GRANT = new AuthorizationGrantType(
        "urn:baosys:grant:shared-login"
    );
    private final UserInfoMapper users;
    private final PasswordEncoder passwords;
    private final OAuth2AuthorizationService authorizations;
    private final RegisteredClientRepository clients;
    private final JwtEncoder encoder;
    private final JdbcTemplate jdbc;
    private final SystemCatalog catalog;
    private final AuthorizationServerSettings settings;
    private final String dummyPassword;

    public SharedLoginService(
        UserInfoMapper users,
        PasswordEncoder passwords,
        OAuth2AuthorizationService authorizations,
        RegisteredClientRepository clients,
        JwtEncoder encoder,
        JdbcTemplate jdbc,
        SystemCatalog catalog,
        AuthorizationServerSettings settings
    ) {
        this.users = users;
        this.passwords = passwords;
        this.authorizations = authorizations;
        this.clients = clients;
        this.encoder = encoder;
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.settings = settings;
        this.dummyPassword = passwords.encode(UUID.randomUUID().toString());
    }

    /** 本次签发的令牌及各自到期时间，供控制器设置 Cookie 有效期。 */
    public record Tokens(
        String accessToken,
        String refreshToken,
        Instant accessExpiry,
        Instant refreshExpiry
    ) {}

    /** 校验账户凭证，撤销浏览器旧授权，并为新登录创建独立会话。 */
    @Transactional
    public Tokens login(String username, String password, String previousRefresh) {
        var found = users.findByUserName(username.trim());
        // 账户不存在时也执行密码校验，降低通过校验耗时枚举账户的风险。
        boolean matches = passwords.matches(
            password,
            found.map(user -> user.getUserPass()).orElse(dummyPassword)
        );
        if (!matches || found.isEmpty() || !found.get().isEnabled()) {
            throw unauthorized();
        }
        // 先撤销浏览器旧授权，再锁定新账户，保持与刷新、注销流程一致的加锁顺序。
        revoke(previousRefresh);
        var user = users
            .findByIdForUpdate(found.get().getId())
            .filter(value -> value.isEnabled())
            .orElseThrow(SharedLoginService::unauthorized);
        if (!Objects.equals(user.getUserPass(), found.get().getUserPass())) {
            throw unauthorized();
        }
        var client = Objects.requireNonNull(clients.findByClientId(JwtConstants.BROWSER_CLIENT_ID));
        var authorization = OAuth2Authorization.withRegisteredClient(client)
            .principalName(user.getId().toString())
            .authorizationGrantType(GRANT)
            .authorizedScopes(Set.of())
            .build();
        authorizations.save(authorization); // 先创建业务会话，再将会话 ID 写入访问令牌的 sub。
        return issue(
            authorization,
            Instant.now().plus(client.getTokenSettings().getRefreshTokenTimeToLive())
        );
    }

    /** 轮换刷新令牌，但保留最初的刷新到期时间，不无限延长会话。 */
    @Transactional
    public Tokens refresh(String refresh) {
        var authorization = find(refresh);
        if (authorization == null || !authorization.getRefreshToken().isActive()) {
            throw unauthorized();
        }
        return issue(authorization, authorization.getRefreshToken().getToken().getExpiresAt());
    }

    /** 撤销本登录模式的授权；缺失或已失效的令牌无需额外处理。 */
    @Transactional
    public void revoke(String refresh) {
        var authorization = find(refresh);
        if (authorization != null) {
            authorizations.remove(authorization);
        }
    }

    /** 仅接受共享登录授权的刷新令牌，避免混用标准 OAuth2 授权。 */
    private OAuth2Authorization find(String refresh) {
        if (refresh == null || refresh.isBlank()) {
            return null;
        }
        var value = authorizations.findByToken(refresh, OAuth2TokenType.REFRESH_TOKEN);
        return value != null && GRANT.equals(value.getAuthorizationGrantType()) ? value : null;
    }

    /** 在账户锁内读取最新权限，签发访问令牌并保存轮换后的刷新令牌。 */
    private Tokens issue(OAuth2Authorization authorization, Instant refreshExpiry) {
        var user = users
            .findByIdForUpdate(Long.parseLong(authorization.getPrincipalName()))
            .filter(value -> value.isEnabled())
            .orElseThrow(SharedLoginService::unauthorized);
        var id = jdbc.queryForObject(
            "SELECT id FROM user_login WHERE authorization_id=? AND expires_at>now()",
            Long.class,
            authorization.getId()
        );
        var grants = PermissionSet.normalize(
            catalog.effectiveGrants(
                PermissionPersistenceConverter.decode(user.getUserPermissions())
            )
        );
        var client = clients.findById(authorization.getRegisteredClientId());
        Instant now = Instant.now();
        Instant expiry = now.plus(client.getTokenSettings().getAccessTokenTimeToLive());
        // 访问令牌的有效期不能超过当前会话的刷新期限。
        if (expiry.isAfter(refreshExpiry)) {
            expiry = refreshExpiry;
        }
        var claims = JwtClaimsSet.builder()
            .issuer(settings.getIssuer())
            .subject(String.valueOf(id))
            .audience(new ArrayList<>(List.of(JwtConstants.ACCESS_TOKEN_AUDIENCE)))
            .issuedAt(now)
            .expiresAt(expiry)
            .id(UUID.randomUUID().toString())
            .claim("client_id", client.getClientId())
            .claim(JwtConstants.PERMISSIONS_CLAIM, new ArrayList<>(grants))
            .build();
        var jwt = encoder.encode(
            JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).type("at+jwt").build(),
                claims
            )
        );
        byte[] random = new byte[32];
        new java.security.SecureRandom().nextBytes(random);
        var refresh = new OAuth2RefreshToken(
            Base64.getUrlEncoder().withoutPadding().encodeToString(random),
            now,
            refreshExpiry
        );
        authorizations.save(
            OAuth2Authorization.from(authorization)
                .token(
                    new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        jwt.getTokenValue(),
                        now,
                        expiry
                    ),
                    metadata ->
                        metadata.put(
                            OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                            claims.getClaims()
                        )
                )
                .refreshToken(refresh)
                .build()
        );
        return new Tokens(jwt.getTokenValue(), refresh.getTokenValue(), expiry, refreshExpiry);
    }

    private static BusinessException unauthorized() {
        return new BusinessException(
            "AUTH_401_001",
            HttpStatus.UNAUTHORIZED,
            "账户凭证不正确或登录已失效"
        );
    }
}
