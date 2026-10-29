package baosys.manager.infra.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 将框架的令牌处理和账户锁置于同一事务中，保持授权状态更新的一致性。
 * AuthorizationTransactionFilter
 */
final class AuthorizationTransactionFilter extends OncePerRequestFilter {

    private final TransactionTemplate transactions;

    AuthorizationTransactionFilter(PlatformTransactionManager manager) {
        transactions = new TransactionTemplate(manager);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return (
            !"POST".equals(request.getMethod()) ||
            !(
                "/oauth2/token".equals(request.getServletPath()) ||
                "/oauth2/revoke".equals(request.getServletPath())
            )
        );
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws IOException, ServletException {
        try {
            transactions.executeWithoutResult(status -> {
                try {
                    chain.doFilter(request, response);
                } catch (IOException | ServletException exception) {
                    throw new Failure(exception);
                }
            });
        } catch (Failure failure) {
            if (failure.getCause() instanceof IOException exception) {
                throw exception;
            }
            throw (ServletException) failure.getCause();
        }
    }

    private static final class Failure extends RuntimeException {

        Failure(Exception cause) {
            super(cause);
        }
    }
}
