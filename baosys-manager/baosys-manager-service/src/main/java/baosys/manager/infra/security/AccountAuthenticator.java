package baosys.manager.infra.security;

import baosys.manager.infra.database.mapper.UserInfoMapper;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 密码校验不持有事务或账户行锁；快照仅供锁内再次校验。
 */
@Component
public class AccountAuthenticator {
    private final UserInfoMapper users;
    private final PasswordEncoder passwords;
    private final String dummyPassword;

    public AccountAuthenticator(UserInfoMapper users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
        this.dummyPassword = passwords.encode(UUID.randomUUID().toString());
    }

    public record VerifiedAccount(long id, String username, String passwordHash) {
        @Override
        public String toString() { return "VerifiedAccount[redacted]"; }
    }

    public VerifiedAccount authenticate(String username, String password) {
        var found = users.findByUserName(username.trim());
        boolean matches = passwords.matches(password,
            found.map(user -> user.getUserPass()).orElse(dummyPassword));
        if (!matches || found.isEmpty() || !found.get().isEnabled()) {
            throw new BadCredentialsException("Invalid credentials");
        }
        var user = found.get();
        return new VerifiedAccount(user.getId(), user.getUserName(), user.getUserPass());
    }
}
