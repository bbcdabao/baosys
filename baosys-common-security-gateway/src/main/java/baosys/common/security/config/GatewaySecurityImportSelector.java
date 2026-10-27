package baosys.common.security.config;

import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotationMetadata;

@Order(Ordered.HIGHEST_PRECEDENCE)
public final class GatewaySecurityImportSelector implements DeferredImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
        return new String[] { AuthenticationCheckConfiguration.class.getName() };
    }
}
