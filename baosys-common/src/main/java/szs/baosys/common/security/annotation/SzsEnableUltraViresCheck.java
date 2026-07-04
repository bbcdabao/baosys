package szs.baosys.common.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import szs.baosys.common.security.config.SzsUltraViresCheckConfiguration;

/**
 * 开启资源服务越权检查。
 *
 * <p>用于认证中心或后续受保护的业务服务。开启后服务会按资源服务器方式解析 JWT，
 * 并把 token 中的 {@code permissions} claim 转换成 Spring Security 权限，
 * 从而支持 {@code @PreAuthorize("hasAuthority('user:list')")} 这类方法级鉴权。</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(SzsUltraViresCheckConfiguration.class)
public @interface SzsEnableUltraViresCheck {
}
