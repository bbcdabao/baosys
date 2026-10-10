package baosys.manager.app.controllers;

import java.util.List;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import baosys.common.api.ApiResponse;
import baosys.manager.api.SystemCatalogApi;
import baosys.manager.api.dto.SystemDefinition;
import baosys.manager.app.config.SystemCatalog;
import baosys.manager.app.config.web.TraceIds;

/** 由现有资源服务器保护，仅登录后的 manager 用户可读取。 */
@RestController
@RequestMapping(SystemCatalogApi.PREFIX)
public class SystemCatalogController implements SystemCatalogApi {
    private final SystemCatalog catalog;

    public SystemCatalogController(SystemCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    @PreAuthorize("authentication.token.claims['systemType'] == 'MANAGER'")
    public ApiResponse<List<SystemDefinition>> list() {
        return ApiResponse.success(catalog.systems(), TraceIds.current());
    }
}
