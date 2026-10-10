package baosys.common.security.config;

import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/** Evaluate defaults after application-provided decoder and chain definitions. */
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public final class GatewaySecurityImportSelector implements DeferredImportSelector {
    @Override public String[] selectImports(AnnotationMetadata metadata) {
        return new String[] { AuthenticationCheckConfiguration.class.getName() };
    }
}
