package baosys.common.security.jwt;

import java.util.List;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.util.StringUtils;

/**
 * 网关和资源服务共用的 JWT 访问令牌校验规则。
 *
 * <p>校验分为两层：Spring Security 检查标准访问令牌字段，本类补充 BaoSys
 * 的密钥标识和权限字段约束。两层均通过，令牌才有效。</p>
 *
 * <p>本类只校验已解码 JWT 的头部和声明；签名验证、算法限制及验签密钥选择由
 * 配置本校验器的 JWT decoder 负责。校验通过也不代表具备某个接口的访问权限，
 * 具体授权仍由后续安全规则处理。</p>
 */
public final class JwtVerification {

    private JwtVerification() {}

    /**
     * 创建标准校验器与平台契约校验器的组合。
     * 受众统一使用 {@link JwtConstants#ACCESS_TOKEN_AUDIENCE}，由框架检查令牌的 aud 是否包含该值。
     *
     * @param issuer 可信签发者，必须是非空白文本
     * @return 所有规则均须通过的组合校验器
     * @throws IllegalArgumentException 配置缺失或不符合平台约定时抛出，使启动尽早失败
     */
    public static OAuth2TokenValidator<Jwt> validator(String issuer) {
        OAuth2TokenValidator<Jwt> standardValidator = standardValidator(issuer);
        OAuth2TokenValidator<Jwt> contractValidator = JwtVerification::validateAccessTokenContract;
        return new DelegatingOAuth2TokenValidator<>(standardValidator, contractValidator);
    }

    /**
     * 框架负责 iss、aud、exp、nbf 等标准规则；此处收紧类型和必要文本字段要求。
     * iat 的必填要求由框架保留，不额外限制它相对当前时间的偏移。
     * typ 必须为 at+jwt；sub（主体）、jti（令牌标识）、client_id（客户端标识）
     * 都必须是非空白字符串，不能仅仅存在。
     */
    private static OAuth2TokenValidator<Jwt> standardValidator(String issuer) {
        return JwtValidators.createAtJwtValidator()
            .issuer(issuer)
            .audience(JwtConstants.ACCESS_TOKEN_AUDIENCE)
            .validators(validators -> {
                validators.put("typ", new JwtTypeValidator("at+jwt"));
                for (String claim : List.of("sub", "jti", "client_id")) {
                    validators.put(
                        claim,
                        new JwtClaimValidator<Object>(claim, JwtVerification::isNonBlankString)
                    );
                }
            })
            .build();
    }

    /**
     * 检查平台自定义契约，用提前返回分别表达每一项拒绝条件。
     * 对外保持统一的 invalid_token 错误，不暴露内部校验细节。
     */
    private static OAuth2TokenValidatorResult validateAccessTokenContract(Jwt jwt) {
        try {
            // permissions 必须是字符串列表；空列表合法，缺失、字符串或混合类型均非法。
            Object rawPermissions = jwt.getClaims().get(JwtConstants.PERMISSIONS_CLAIM);
            if (
                !(rawPermissions instanceof List<?> permissions) ||
                !permissions.stream().allMatch(String.class::isInstance)
            ) {
                return invalid();
            }

            // kid 必须是有效文本；是否对应可信密钥由 decoder 验签时确认。
            if (!isNonBlankString(jwt.getHeaders().get("kid"))) {
                return invalid();
            }

            return OAuth2TokenValidatorResult.success();
        } catch (IllegalArgumentException exception) {
            // 声明读取或转换产生的参数异常统一视为认证校验失败。
            return invalid();
        }
    }

    /**
     * 同时限制字段类型和文本内容，不把数字等值隐式转换成字符串后接受。
     * @param value
     * @return
     */
    private static boolean isNonBlankString(Object value) {
        return value instanceof String text && StringUtils.hasText(text);
    }

    /**
     * 保持错误码和描述一致，供 Spring Security 处理认证失败。
     * @return
     */
    private static OAuth2TokenValidatorResult invalid() {
        return OAuth2TokenValidatorResult.failure(
            new OAuth2Error("invalid_token", "Invalid access token contract", null)
        );
    }
}
