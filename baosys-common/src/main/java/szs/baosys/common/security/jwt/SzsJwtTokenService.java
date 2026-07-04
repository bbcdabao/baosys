package szs.baosys.common.security.jwt;

import java.util.List;
import java.util.Map;

/**
 * 平台 JWT 访问令牌签发服务。
 */
public interface SzsJwtTokenService {

    /**
     * 生成访问令牌。
     *
     * @param userId 用户主键，写入 JWT subject
     * @param username 登录名，写入 {@code username} claim，仅用于日志、展示等上下文
     * @param permissions 权限编码列表，写入配置指定的 permissions claim
     * @param extraClaims 额外业务 claim，例如展示名
     * @return 编码后的 JWT 字符串
     */
    String generateAccessToken(
            String userId,
            String username,
            List<String> permissions,
            Map<String, Object> extraClaims);
}
