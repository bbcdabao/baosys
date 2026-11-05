package baosys.manager.api.module.contract;

import baosys.common.api.ApiResponse;
import baosys.manager.api.module.responses.SystemDefinition;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;

public interface SystemCatalogApi {
    String PREFIX = "/v1/systems";

    @GetMapping
    ApiResponse<List<SystemDefinition>> list();
}
