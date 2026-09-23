/**
 * BaoSys 的认证与授权基础设施。
 *
 * <p>子包分别负责 JWT、密码、网关 Cookie 适配、资源服务权限转换和按需启用配置。
 * 业务服务应依赖这里提供的抽象，而不是自行解析令牌或散落安全规则。</p>
 */
package baosys.common.security;
