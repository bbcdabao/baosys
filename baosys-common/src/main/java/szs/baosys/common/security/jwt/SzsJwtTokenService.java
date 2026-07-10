package szs.baosys.common.security.jwt;

import java.util.List;
import java.util.Map;

/**
 * 平台 JWT 访问令牌签发服务。
 *
 * <p>调用方只需要提供业务身份、展示用用户名、子系统类型、权限编码和扩展 claim。
 * issuer、过期时间、权限 claim 名称等跨服务约定由 common 模块统一写入，避免各业务
 * 服务各自拼装 token。</p>
 */
public interface SzsJwtTokenService {

    /**
     * 生成访问令牌。
     *
     * @param userId 用户主键，写入 JWT {@code sub}，作为后续鉴权的业务主体
     * @param username 登录名，写入 {@code username} claim，仅用于日志、展示等上下文
     * @param systemType 用户登录的子系统类型，写入 {@code systemType} claim
     * @param permissions 权限编码列表，写入配置指定的权限 claim
     * @param extraClaims 额外业务 claim，例如展示名；不应依赖它覆盖平台保留声明
     * @return 已签名的 JWT access token
     */
    String generateAccessToken(
            String userId,
            String username,
            String systemType,
            List<String> permissions,
            Map<String, Object> extraClaims);
}
