package baosys.manager.api.module.payloads;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 浏览器登录凭据与可选返回地址。
 * @param username
 * @param password
 * @param redirectUrl
 */
public record BrowserLoginRequest(
    @NotBlank @Size(max = 128) String username,
    @NotBlank @Size(max = 256) String password,
    @Size(max = 8192) String redirectUrl
) {}
