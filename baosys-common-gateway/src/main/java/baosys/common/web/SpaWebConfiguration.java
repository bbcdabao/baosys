package baosys.common.web;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.resource.PathResourceResolver;
import org.springframework.web.reactive.resource.ResourceResolverChain;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/**
 * Serves static assets and optionally falls back to the SPA entry point for client routes.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(prefix = "common.spa", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({SpaProperties.class, WebProperties.class})
public class SpaWebConfiguration implements WebFluxConfigurer {

    private static final List<String> BUILTIN_EXCLUDED_PATHS = List.of(
        "api",
        "actuator",
        "assets",
        "oauth2",
        "login/oauth2",
        "logout",
        "csrf"
    );

    private final SpaProperties properties;
    private final WebProperties webProperties;
    private final Resource index;

    public SpaWebConfiguration(SpaProperties properties, WebProperties webProperties, ResourceLoader loader) {
        this.properties = properties;
        this.webProperties = webProperties;
        this.index = loader.getResource(properties.getResourceLocation() + "index.html");
    }

    @Bean
    WebFilter normalizeUiBase() {
        return (exchange, chain) -> {
            if (exchange.getRequest().getURI().getRawPath().equals(properties.getBasePath())) {
                String query = exchange.getRequest().getURI().getRawQuery();
                exchange.getResponse().setStatusCode(HttpStatus.PERMANENT_REDIRECT);
                exchange.getResponse().getHeaders().setLocation(URI.create(
                    properties.getBasePath() + "/" + (query == null ? "" : "?" + query)));
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        };
    }

    @Bean
    RouterFunction<ServerResponse> uiIndex() {
        return RouterFunctions.route()
            .GET(properties.getBasePath() + "/", request -> indexResponse())
            .GET(properties.getBasePath() + "/index.html", request -> indexResponse())
            .GET("/", request -> indexResponse())
            .GET("/index.html", request -> indexResponse())
            .build();
    }

    private Mono<ServerResponse> indexResponse() {
        if (!index.isReadable()) {
            return ServerResponse.notFound().build();
        }
        return ServerResponse.ok()
            .contentType(MediaType.TEXT_HTML)
            .cacheControl(CacheControl.noCache())
            .bodyValue(index);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        var registration = registry
            .addResourceHandler(properties.getBasePath() + "/**", "/**")
            .addResourceLocations(properties.getResourceLocation());
        var cache = webProperties.getResources().getCache();
        CacheControl cacheControl = resolveCacheControl(cache);
        if (cacheControl != null) {
            registration.setCacheControl(cacheControl);
        }
        registration.setUseLastModified(cache.isUseLastModified());
        if (properties.getFallback().isEnabled()) {
            registration
                .resourceChain(false)
                .addResolver(new SpaPathResourceResolver(index, excludedPaths(), excludedPrefixes()));
        }
    }

    private static CacheControl resolveCacheControl(WebProperties.Resources.Cache cache) {
        CacheControl cacheControl = cache.getCachecontrol().toHttpCacheControl();
        if (cacheControl != null && cacheControl.getHeaderValue() != null) {
            return cacheControl;
        }
        Duration period = cache.getPeriod();
        return period != null ? CacheControl.maxAge(period) : null;
    }

    private List<String> excludedPaths() {
        var merged = new LinkedHashSet<String>(BUILTIN_EXCLUDED_PATHS);
        merged.addAll(properties.getFallback().getExcludedPaths());
        return List.copyOf(merged);
    }

    private List<String> excludedPrefixes() {
        return List.copyOf(properties.getFallback().getExcludedPrefixes());
    }

    private static final class SpaPathResourceResolver extends PathResourceResolver {

        private final Resource index;
        private final List<String> excludedPaths;
        private final List<String> excludedPrefixes;

        SpaPathResourceResolver(
            Resource index,
            List<String> excludedPaths,
            List<String> excludedPrefixes
        ) {
            this.index = index;
            this.excludedPaths = excludedPaths;
            this.excludedPrefixes = excludedPrefixes;
        }

        @Override
        protected Mono<Resource> resolveResourceInternal(
            ServerWebExchange exchange,
            String requestPath,
            List<? extends Resource> locations,
            ResourceResolverChain chain
        ) {
            return super
                .resolveResourceInternal(exchange, requestPath, locations, chain)
                .switchIfEmpty(
                    Mono.defer(() -> {
                        if (isClientRoute(exchange, requestPath) && index.isReadable()) {
                            exchange.getResponse().beforeCommit(() -> {
                                exchange.getResponse().getHeaders().setCacheControl(CacheControl.noCache());
                                return Mono.empty();
                            });
                            return Mono.just(index);
                        }
                        return Mono.empty();
                    })
                );
        }

        private boolean isClientRoute(ServerWebExchange exchange, String requestPath) {
            String path = StringUtils.trimLeadingCharacter(requestPath, '/');

            return (
                isSafeMethod(exchange) &&
                acceptsHtml(exchange) &&
                excludedPaths.stream().noneMatch(prefix -> isPathUnder(path, prefix)) &&
                excludedPrefixes.stream().noneMatch(path::startsWith) &&
                !StringUtils.hasText(StringUtils.getFilenameExtension(path))
            );
        }

        private static boolean isSafeMethod(ServerWebExchange exchange) {
            HttpMethod method = exchange.getRequest().getMethod();
            return method == HttpMethod.GET || method == HttpMethod.HEAD;
        }

        private static boolean acceptsHtml(ServerWebExchange exchange) {
            return exchange
                .getRequest()
                .getHeaders()
                .getAccept()
                .stream()
                .anyMatch(mediaType -> mediaType.isCompatibleWith(MediaType.TEXT_HTML));
        }

        private static boolean isPathUnder(String path, String prefix) {
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }
    }
}
