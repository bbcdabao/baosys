package baosys.manager.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UserUpdateRequest(
    @NotNull(message = "账户 ID 不能为空") Long id,
    @NotBlank(message = "账户名称不能为空")
    @Size(max = 128, message = "账户名称不能超过 128 个字符")
    String name,
    String pass,
    @NotNull(message = "权限配置不能为空") Set<String> permissions,
    Boolean enabled
) {}
