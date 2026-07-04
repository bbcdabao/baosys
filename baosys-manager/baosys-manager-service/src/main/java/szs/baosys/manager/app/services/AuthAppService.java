package szs.baosys.manager.app.services;

import szs.baosys.manager.api.dto.LoginRequest;

public interface AuthAppService {

    LoginResult login(LoginRequest request);

    LoginResult refresh(String refreshToken);

    void logout(String refreshToken);
}
