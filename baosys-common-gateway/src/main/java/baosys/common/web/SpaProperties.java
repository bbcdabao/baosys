package baosys.common.web;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * UI hosting configuration. Root aliases support reverse proxies stripping the UI prefix.
 */
@ConfigurationProperties("common.spa")
public class SpaProperties {
    private String basePath = "/ui";
    private String resourceLocation = "classpath:/static/";
    private Fallback fallback = new Fallback();

    public String getBasePath() { return basePath; }
    public void setBasePath(String value) {
        if (value == null || !value.matches("/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*")) {
            throw new IllegalArgumentException("common.spa.base-path must be an absolute non-root path without trailing slash");
        }
        basePath = value;
    }

    public String getResourceLocation() { return resourceLocation; }
    public void setResourceLocation(String value) {
        if (value == null || !(value.startsWith("classpath:/") || value.startsWith("file:/"))) {
            throw new IllegalArgumentException("SPA resources must use classpath:/ or file:/");
        }
        resourceLocation = value.endsWith("/") ? value : value + "/";
    }

    public Fallback getFallback() { return fallback; }
    public void setFallback(Fallback value) {
        fallback = value == null ? new Fallback() : value;
    }

    public static class Fallback {
        private boolean enabled = false;
        private List<String> excludedPaths = List.of();
        private List<String> excludedPrefixes = List.of();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }

        public List<String> getExcludedPaths() { return excludedPaths; }
        public void setExcludedPaths(List<String> values) {
            excludedPaths = normalizePaths(values);
        }

        public List<String> getExcludedPrefixes() { return excludedPrefixes; }
        public void setExcludedPrefixes(List<String> values) {
            excludedPrefixes = normalizePrefixes(values);
        }

        private static List<String> normalizePaths(List<String> values) {
            if (values == null) {
                return List.of();
            }
            List<String> normalized = new ArrayList<>(values.size());
            for (String raw : values) {
                if (raw == null) {
                    throw new IllegalArgumentException("common.spa.fallback.excluded-paths entries must not be blank");
                }
                String value = StringUtils.trimTrailingCharacter(
                    StringUtils.trimLeadingCharacter(raw.trim(), '/'),
                    '/'
                );
                if (!StringUtils.hasText(value) || value.contains("..") || value.contains("//")) {
                    throw new IllegalArgumentException(
                        "common.spa.fallback.excluded-paths entry is invalid: " + raw
                    );
                }
                normalized.add(value);
            }
            return List.copyOf(normalized);
        }

        private static List<String> normalizePrefixes(List<String> values) {
            if (values == null) {
                return List.of();
            }
            List<String> normalized = new ArrayList<>(values.size());
            for (String raw : values) {
                if (raw == null) {
                    throw new IllegalArgumentException("common.spa.fallback.excluded-prefixes entries must not be blank");
                }
                String value = StringUtils.trimLeadingCharacter(raw.trim(), '/');
                if (!StringUtils.hasText(value) || value.contains("..") || value.contains("//")) {
                    throw new IllegalArgumentException(
                        "common.spa.fallback.excluded-prefixes entry is invalid: " + raw
                    );
                }
                normalized.add(value);
            }
            return List.copyOf(normalized);
        }
    }
}
