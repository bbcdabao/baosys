package szs.baosys.manager.app.services.impl;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import szs.baosys.common.api.PageResponse;
import szs.baosys.manager.api.dto.LoginSessionResponse;
import szs.baosys.manager.app.services.LoginSessionAppService;
import szs.baosys.manager.infra.database.entity.LoginSessionRow;
import szs.baosys.manager.infra.database.mapper.UserLoginMapper;

@Service
public class LoginSessionAppServiceImpl implements LoginSessionAppService {

    private final UserLoginMapper loginMapper;

    public LoginSessionAppServiceImpl(UserLoginMapper loginMapper) {
        this.loginMapper = loginMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LoginSessionResponse> list(
            Long userId,
            String userName,
            int page,
            int size) {
        String normalizedName = userName == null ? "" : userName.trim();
        var result = loginMapper.selectActiveSessions(
                Page.of(page + 1L, size),
                userId,
                normalizedName,
                OffsetDateTime.now(ZoneOffset.UTC));
        var items = result.getRecords().stream()
                .map(LoginSessionAppServiceImpl::toResponse)
                .toList();
        return new PageResponse<>(
                items,
                result.getTotal(),
                page,
                size,
                Math.toIntExact(result.getPages()));
    }

    private static LoginSessionResponse toResponse(LoginSessionRow row) {
        return new LoginSessionResponse(
                row.getId(),
                row.getUserId(),
                row.getUserName(),
                row.getSystemType(),
                row.getLoginClient(),
                row.getLoginAt(),
                row.getExpiresAt());
    }
}
