package baosys.manager.api.dto;

import java.util.List;


/**
 * 当前登录用户的可信快照。
 *
 * <p>账号信息由服务端根据访问令牌中的用户标识重新查询，避免前端依赖可能已过期的令牌声明。
 */
public record CurrentUserResponse(
        long userId,
        String username,
        String displayName,
        String systemType,
        List<String> permissions) {
}
