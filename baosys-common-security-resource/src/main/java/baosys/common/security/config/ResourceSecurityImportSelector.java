package baosys.common.security.config;

import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/** 在用户配置之后加载默认配置，使自定义解码器和安全过滤链优先生效。 */
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public final class ResourceSecurityImportSelector implements DeferredImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
        return new String[] { UltraViresCheckConfiguration.class.getName() };
    }
}
