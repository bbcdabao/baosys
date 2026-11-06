package baosys.managerweb;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.resource.PathResourceResolver;
import org.springframework.web.reactive.resource.ResourceResolverChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Serves static assets and falls back to the SPA entry point for client routes.
 */
@Configuration
public class SpaWebConfiguration implements WebFluxConfigurer {

    @org.springframework.context.annotation.Bean
    org.springframework.web.server.WebFilter normalizeUiBase() {
        return (exchange, chain) -> {
            if (exchange.getRequest().getURI().getRawPath().equals("/baosys-manager")) {
                String query = exchange.getRequest().getURI().getRawQuery();
                exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.PERMANENT_REDIRECT);
                exchange.getResponse().getHeaders().setLocation(java.net.URI.create(
                    "/baosys-manager/" + (query == null ? "" : "?" + query)));
                return exchange.getResponse().setComplete();
            }
            return chain.filter(exchange);
        };
    }

    @org.springframework.context.annotation.Bean
    org.springframework.web.reactive.function.server.RouterFunction<org.springframework.web.reactive.function.server.ServerResponse> uiIndex() {
        return org.springframework.web.reactive.function.server.RouterFunctions.route()
            .GET("/baosys-manager/", request -> org.springframework.web.reactive.function.server.ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML).bodyValue(new ClassPathResource("static/index.html")))
            .GET("/", request -> org.springframework.web.reactive.function.server.ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML).bodyValue(new ClassPathResource("static/index.html")))
            .build();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
            .addResourceHandler("/baosys-manager/**", "/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(false)
            .addResolver(new SpaPathResourceResolver());
    }

    private static final class SpaPathResourceResolver extends PathResourceResolver {

        private static final Resource INDEX = new ClassPathResource("static/index.html");

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
                        isClientRoute(exchange, requestPath) && INDEX.isReadable()
                            ? Mono.just(INDEX)
                            : Mono.empty()
                    )
                );
        }

        private static boolean isClientRoute(ServerWebExchange exchange, String requestPath) {
            String path = StringUtils.trimLeadingCharacter(requestPath, '/');

            return (
                isSafeMethod(exchange) &&
                acceptsHtml(exchange) &&
                !isPathUnder(path, "api") &&
                !isPathUnder(path, "actuator") &&
                !isPathUnder(path, "assets") &&
                !isPathUnder(path, "oauth2") &&
                !isPathUnder(path, "login/oauth2") &&
                !isPathUnder(path, "logout") &&
                !isPathUnder(path, "csrf") &&
                !path.startsWith("baosys-") &&
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
