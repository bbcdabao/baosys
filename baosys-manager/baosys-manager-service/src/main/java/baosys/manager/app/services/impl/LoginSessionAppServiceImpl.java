package baosys.manager.app.services.impl;

import baosys.common.api.PageResponse;
import baosys.manager.api.module.responses.LoginSessionResponse;
import baosys.manager.app.services.LoginSessionAppService;
import baosys.manager.infra.database.converter.PermissionPersistenceConverter;
import baosys.manager.infra.database.entity.LoginSessionRow;
import baosys.manager.infra.database.mapper.UserLoginMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        int size
    ) {
        String normalizedName = userName == null ? "" : userName.trim();
        var result = loginMapper.selectActiveSessions(
            Page.of(page + 1L, size),
            userId,
            normalizedName,
            OffsetDateTime.now(ZoneOffset.UTC)
        );
        var items = result
            .getRecords()
            .stream()
            .map(LoginSessionAppServiceImpl::toResponse)
            .toList();
        return PageResponse.of(items, result.getTotal(), page, size);
    }

    private static LoginSessionResponse toResponse(LoginSessionRow row) {
        return new LoginSessionResponse(
            row.getId(),
            row.getUserId(),
            row.getUserName(),
            PermissionPersistenceConverter.decode(row.getUserPermissions()).stream().sorted().toList(),
            row.getLoginClient(),
            row.getLoginAt(),
            row.getExpiresAt()
        );
    }
}
