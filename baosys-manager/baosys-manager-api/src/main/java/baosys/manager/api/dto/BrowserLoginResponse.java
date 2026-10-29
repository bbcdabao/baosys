package baosys.manager.api.dto;

/** 登录成功后经过服务端校验的返回地址。 */
public record BrowserLoginResponse(String redirectUrl) {}
