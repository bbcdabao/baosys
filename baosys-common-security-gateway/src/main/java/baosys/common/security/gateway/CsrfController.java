package baosys.common.security.gateway;

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

    @GetMapping("/csrf")
    public Mono<CsrfToken> csrf(ServerWebExchange exchange) {
        return exchange.getRequiredAttribute(CsrfToken.class.getName());
    }
}
