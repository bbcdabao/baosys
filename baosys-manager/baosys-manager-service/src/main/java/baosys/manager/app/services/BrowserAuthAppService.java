package baosys.manager.app.services;

import baosys.manager.app.error.BusinessException;
import baosys.manager.app.services.impl.BrowserLoginTransaction;
import baosys.manager.infra.security.AccountAuthenticator;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;

/** 浏览器认证用例入口；事务提交后才向 Controller 返回令牌。 */
@Service
public class BrowserAuthAppService {
    private final AccountAuthenticator accounts;
    private final BrowserLoginTransaction transactions;

    public BrowserAuthAppService(AccountAuthenticator accounts, BrowserLoginTransaction transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    public record Tokens(String accessToken, String refreshToken, Instant accessExpiry, Instant refreshExpiry) {
        @Override
        public String toString() { return "Tokens[redacted]"; }
    }

    public Tokens login(String username, String password, String previousRefresh) {
        try {
            return transactions.completeLogin(accounts.authenticate(username, password), previousRefresh);
        } catch (BadCredentialsException | OAuth2AuthenticationException exception) {
            throw unauthorized();
        }
    }

    public Tokens refresh(String refresh) {
        try {
            return transactions.refresh(refresh);
        } catch (BadCredentialsException | OAuth2AuthenticationException exception) {
            throw unauthorized();
        }
    }

    public void revoke(String refresh) { transactions.revoke(refresh); }

    private static BusinessException unauthorized() {
        return new BusinessException("AUTH_401_001", HttpStatus.UNAUTHORIZED, "账户凭证不正确或登录已失效");
    }
}
