package szs.baosys.manager.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import szs.baosys.manager.api.enums.SystemType;

public record LoginRequest(
        @NotBlank(message = "用户名不能为空")
        String username,
        @NotBlank(message = "密码不能为空")
        String password,
        @NotNull(message = "系统类型不能为空")
        SystemType systemType,
        @Size(max = 512, message = "客户端标识不能超过 512 个字符")
        String loginClient) {
}
