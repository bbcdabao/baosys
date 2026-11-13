package baosys.common.security.gateway;

import baosys.common.security.config.SecurityProperties;
import java.util.Map;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Materializes Spring's deferred, BREACH-masked token for the same-origin SPA.
 * CsrfController
 */
@RestController
public class CsrfController {

    private final SecurityProperties properties;

    public CsrfController(SecurityProperties properties) {
        this.properties = properties;
    }

    @GetMapping("${spring.security.gateway.csrf-path:/csrf}")
    public Mono<?> csrf(ServerWebExchange exchange) {
        if (!properties.getGateway().isCsrfEnabled()) {
            return Mono.just(Map.of("enabled", false));
        }
        return exchange.getRequiredAttribute(CsrfToken.class.getName());
    }
}
