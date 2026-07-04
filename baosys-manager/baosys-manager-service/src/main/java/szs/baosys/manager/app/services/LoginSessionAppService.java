package szs.baosys.manager.app.services;

import szs.baosys.common.api.PageResponse;
import szs.baosys.manager.api.dto.LoginSessionResponse;

public interface LoginSessionAppService {

    PageResponse<LoginSessionResponse> list(
            Long userId,
            String userName,
            int page,
            int size);
}
