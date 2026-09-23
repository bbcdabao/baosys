package baosys.common.security.gateway;

import java.util.List;

import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * 使用 Spring PathPattern 语义匹配已配置的公开路径。
 *
 * <p>网关和 Servlet 资源服务的公开路径配置都采用 Spring PathPattern 语法，例如
 * {@code /actuator/health/**}。提前解析成 {@link PathPattern} 可以避免每次请求
 * 重复解析字符串。</p>
 */
public final class PermitAllPathMatcher {

    private final List<PathPattern> patterns;

    /**
     * 创建公开路径匹配器。
     *
     * <p>空值和空白字符串会被忽略，配置为空时表示没有额外公开路径。</p>
     */
    public PermitAllPathMatcher(List<String> patterns) {
        this.patterns = patterns == null
                ? List.of()
                : patterns.stream()
                        .filter(pattern -> pattern != null && !pattern.isBlank())
                        .map(PathPatternParser.defaultInstance::parse)
                        .toList();
    }

    /**
     * 判断请求路径是否命中公开路径配置。
     */
    public boolean matches(String path) {
        if (patterns.isEmpty()) {
            return false;
        }
        PathContainer pathContainer = PathContainer.parsePath(path);
        return patterns.stream().anyMatch(pattern -> pattern.matches(pathContainer));
    }
}
