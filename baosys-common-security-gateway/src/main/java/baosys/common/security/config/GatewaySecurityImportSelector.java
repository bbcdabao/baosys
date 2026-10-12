package baosys.common.security.config;

import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;

/** Evaluate defaults after application-provided decoder and chain definitions. */
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class GatewaySecurityImportSelector implements DeferredImportSelector {
    @Override public String[] selectImports(AnnotationMetadata metadata) {
        return new String[] { AuthenticationCheckConfiguration.class.getName() };
    }
}
