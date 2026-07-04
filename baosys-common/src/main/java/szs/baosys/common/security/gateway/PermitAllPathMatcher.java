package szs.baosys.common.security.gateway;

import java.util.List;

import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * 使用 Spring PathPattern 语义匹配已配置的公开路径。
 */
public final class PermitAllPathMatcher {

    private final List<PathPattern> patterns;

    public PermitAllPathMatcher(List<String> patterns) {
        this.patterns = patterns == null
                ? List.of()
                : patterns.stream()
                        .filter(pattern -> pattern != null && !pattern.isBlank())
                        .map(PathPatternParser.defaultInstance::parse)
                        .toList();
    }

    public boolean matches(String path) {
        if (patterns.isEmpty()) {
            return false;
        }
        PathContainer pathContainer = PathContainer.parsePath(path);
        return patterns.stream().anyMatch(pattern -> pattern.matches(pathContainer));
    }
}
