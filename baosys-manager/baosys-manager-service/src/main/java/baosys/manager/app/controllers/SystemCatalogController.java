package baosys.manager.app.controllers;

import baosys.common.api.ApiResponse;
import baosys.manager.api.module.contract.SystemCatalogApi;
import baosys.manager.api.module.responses.SystemDefinition;
import baosys.manager.app.config.SystemCatalog;
import baosys.manager.app.config.web.TraceIds;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 由现有资源服务器保护，登录账户可读取，用于显示已授权系统。
 * SystemCatalogController
 */
@RestController
@RequestMapping(SystemCatalogApi.PREFIX)
public class SystemCatalogController implements SystemCatalogApi {

    private final SystemCatalog catalog;

    public SystemCatalogController(SystemCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<SystemDefinition>> list() {
        return ApiResponse.success(catalog.systems(), TraceIds.current());
    }
}
