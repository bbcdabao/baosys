package baosys.common.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * UI hosting configuration. Root aliases support reverse proxies stripping the UI prefix.
 */
@ConfigurationProperties("common.spa")
public class SpaProperties {
    private String basePath = "/ui";
    private String resourceLocation = "classpath:/static/";
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
}
