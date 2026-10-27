package baosys.manager.app.services;

import baosys.common.api.PageResponse;
import baosys.manager.api.dto.LoginSessionResponse;

public interface LoginSessionAppService {
    PageResponse<LoginSessionResponse> list(Long userId, String userName, int page, int size);
}
