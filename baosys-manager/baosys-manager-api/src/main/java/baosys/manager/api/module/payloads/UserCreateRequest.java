package baosys.manager.api.module.payloads;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UserCreateRequest(
    @NotBlank(message = "账户名称不能为空")
    @Size(max = 128, message = "账户名称不能超过 128 个字符")
    String name,
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 128, message = "密码长度必须为 8 到 128 个字符")
    String pass,
    @NotNull(message = "权限配置不能为空") Set<String> permissions,
    Boolean enabled
) {}
