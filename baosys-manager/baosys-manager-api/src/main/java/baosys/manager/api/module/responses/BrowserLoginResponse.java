package baosys.manager.api.module.responses;

/**
 * 登录成功后经过服务端校验的返回地址。
 * @param redirectUrl
 */
public record BrowserLoginResponse(String redirectUrl) {}
