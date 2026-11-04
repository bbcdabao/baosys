package baosys.manager.infra.security;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 将可选 HTTP 上下文转换为持久化元数据；后台调用不要求存在请求。
 */
@Component
public class LoginRequestMetadata {
    public record Metadata(String sessionId, String agent) {}

    public Metadata current() {
        var attributes = RequestContextHolder.getRequestAttributes();
        var request = attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
        var session = request == null ? null : request.getSession(false);
        String agent = request == null ? null : request.getHeader("User-Agent");
        return new Metadata(session == null ? null : session.getId(),
            agent == null ? "OAuth2" : agent.substring(0, Math.min(agent.length(), 512)));
    }
}
