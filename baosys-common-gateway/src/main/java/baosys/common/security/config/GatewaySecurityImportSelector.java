package baosys.common.security.config;

import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotationMetadata;

/**
 * 配置网关安全相关的导入选择器，后续根据配置加载不同安全策略可以从这里开始
 * GatewaySecurityImportSelector
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class GatewaySecurityImportSelector implements DeferredImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
        return new String[] { AuthenticationCheckConfiguration.class.getName() };
    }
}
