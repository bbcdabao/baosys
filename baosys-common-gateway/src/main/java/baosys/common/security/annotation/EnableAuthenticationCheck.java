package baosys.common.security.annotation;

import baosys.common.security.config.GatewaySecurityImportSelector;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * 开启网关侧认证检查。
 *
 * <p>用于 {@code baosys-manager-web} 这类 Spring Cloud Gateway 服务。
 * 开启后网关会校验访问 {@code /api/**} 的 JWT，并把 {@code access_token}
 * Cookie 转换成下游服务可识别的 {@code Authorization: Bearer ...} 请求头。</p>
 *
 * <p>该注解适合放在网关应用启动类上。启用后仍需配置
 * 本地公钥或 {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri} 和可信 issuer，才能完成 token 签名和签发方校验。</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(GatewaySecurityImportSelector.class)
public @interface EnableAuthenticationCheck {}
