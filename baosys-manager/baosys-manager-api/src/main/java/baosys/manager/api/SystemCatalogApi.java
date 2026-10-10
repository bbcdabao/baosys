package baosys.manager.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import baosys.common.api.ApiResponse;
import baosys.manager.api.dto.SystemDefinition;

public interface SystemCatalogApi {
    String PREFIX = "/v1/systems";

    @GetMapping
    ApiResponse<List<SystemDefinition>> list();
}
