package baosys.manager.api.dto;

import java.util.List;

/**
 * 当前登录用户的可信快照。
 *
 * <p>服务端由访问令牌中的登录记录标识定位账号，重新查询当前信息。
 * permissions 同时包含系统入口和接口权限，不再重复返回派生的系统列表。
 */
public record CurrentUserResponse(
    long userId,
    String username,
    String displayName,
    List<String> permissions
) {}
