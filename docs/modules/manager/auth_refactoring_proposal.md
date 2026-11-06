# 内部认证流程改进方案（实施归档）

**简体中文** | [English](auth_refactoring_proposal.en.md)

状态：四项改进已实现，当前行为以关联主题文档为准。日期：2026-10-30。

内部继续使用用户名密码登录；第三方授权码流程按需使用，PKCE 按客户端配置，不全局强制。API、Cookie 和 JWT 主体契约保持不变。

| 改进项 | 实施结果 |
|---|---|
| 账户切换锁顺序 | AccountAuthenticator 在事务外校验密码；BrowserLoginTransaction 经独立代理开启事务；存储适配器发现新旧账户后按 ID 升序加锁，复查凭据后原子替换授权 |
| 跨标签页刷新 | authCoordinator 用 Web Locks 串行协调同源登录/刷新/退出，非敏感版本标记避免重复刷新与旧账户请求重放；无相关能力时降级页内协调 |
| 会话期限与清理 | 内部保留初次绝对期限；按 expires_at 清理，先锁账户、跳过忙碌账户，每批最多 100 个账户；调度示例每五分钟执行 |
| 职责整理 | 应用服务负责用例，独立事务组件负责持久化编排；密码认证、内部身份认证、签发主体解析、HTTP 元数据分别处理；登录投影 SQL 归入 UserLoginMapper |

OAuth 无 Refresh Token 的授权投影已区分授权码阶段和实际 Access/ID Token 期限。账户下线操作也统一先锁账户。仓库明确尚未部署，因此调整 V1 初始化 SQL；已部署环境不能直接修改已执行迁移。

验证包括数据库并发及生命周期测试、真实多标签页浏览器测试、前端类型检查与构建。浏览器测试 HTTP 端点使用替身；跨端口协调、刷新响应丢失恢复及离线 JWT 即时撤销不属于已解决范围。

为避免方案与实现说明长期分叉，本文件仅保留实施归档及导航，完整规则见：

- [认证与会话](auth_and_session.md)
- [浏览器认证时序](browser_auth_sequence.md)
- [数据库设计](database_design.md)
- [模块职责与事务](manager_module_design.md)
- [运行与验证命令](../../development.md)
