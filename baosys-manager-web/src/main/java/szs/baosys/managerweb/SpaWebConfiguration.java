package szs.baosys.managerweb;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
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

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(false)
                .addResolver(new SpaPathResourceResolver());
    }

    private static final class SpaPathResourceResolver extends PathResourceResolver {

        private static final Resource INDEX =
                new ClassPathResource("static/index.html");

        @Override
        protected Mono<Resource> resolveResourceInternal(
                ServerWebExchange exchange,
                String requestPath,
                List<? extends Resource> locations,
                ResourceResolverChain chain) {
            return super.resolveResourceInternal(
                            exchange,
                            requestPath,
                            locations,
                            chain)
                    .switchIfEmpty(Mono.defer(() ->
                            isClientRoute(exchange, requestPath)
                                            && INDEX.isReadable()
                                    ? Mono.just(INDEX)
                                    : Mono.empty()));
        }

        private static boolean isClientRoute(
                ServerWebExchange exchange,
                String requestPath) {
            String path = StringUtils.trimLeadingCharacter(requestPath, '/');
            // API、运维端点和带扩展名的静态资源失败时应保留 404，不能伪装成 SPA 页面。
            return acceptsHtml(exchange)
                    && !isPathUnder(path, "api")
                    && !isPathUnder(path, "actuator")
                    && !StringUtils.hasText(StringUtils.getFilenameExtension(path));
        }

        private static boolean acceptsHtml(ServerWebExchange exchange) {
            return exchange.getRequest()
                    .getHeaders()
                    .getAccept()
                    .stream()
                    .anyMatch(mediaType ->
                            mediaType.isCompatibleWith(MediaType.TEXT_HTML));
        }

        private static boolean isPathUnder(String path, String prefix) {
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }
    }
}
