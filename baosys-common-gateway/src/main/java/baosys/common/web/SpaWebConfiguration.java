package baosys.common.web;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
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
@EnableConfigurationProperties(SpaProperties.class)
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
    private final Resource index;

    public SpaWebConfiguration(SpaProperties properties, ResourceLoader loader) {
        this.properties = properties;
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
            .GET(properties.getBasePath() + "/", request -> ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML).bodyValue(index))
            .GET("/", request -> ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML).bodyValue(index))
            .build();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        var registration = registry
            .addResourceHandler(properties.getBasePath() + "/**", "/**")
            .addResourceLocations(properties.getResourceLocation());
        if (properties.getFallback().isEnabled()) {
            registration
                .resourceChain(false)
                .addResolver(new SpaPathResourceResolver(index, excludedPaths(), excludedPrefixes()));
        }
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
                    Mono.defer(() ->
                        isClientRoute(exchange, requestPath) && index.isReadable()
                            ? Mono.just(index)
                            : Mono.empty()
                    )
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
