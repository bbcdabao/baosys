package baosys.common.security.config;

import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/** Evaluate defaults after user configuration so custom decoders/chains take precedence. */
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public final class ResourceSecurityImportSelector implements DeferredImportSelector {
    @Override public String[] selectImports(AnnotationMetadata metadata) {
        return new String[] { UltraViresCheckConfiguration.class.getName() };
    }
}
