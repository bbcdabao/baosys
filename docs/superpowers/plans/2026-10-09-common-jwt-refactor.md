# common 模块拆分与 JWT 启动配置方案

日期：2026-10-09  
校验规则更新：2026-10-10，子系统隔离统一使用现有的 `systemType` 机制。  
状态：核心拆分已实施；本文保留设计背景，部署操作以当前接入指南为准。

> 2026-10-10 更新：子系统及接口权限目录统一由工程根目录 `baosys-systems.yml` 声明，替代 manager 固定枚举和前端目录；JWT 继续使用 `systemType`。配置、构建、外部文件覆盖和数据库迁移见[新增子系统接入指南](../../subsystem-onboarding.md)。本文将“越权”理解为“防止越权的权限校验”。

## 1. 目标与关键决策

将用户中心签发 Token、业务微服务认证授权、网关鉴权分离，避免引入 common 就加载所有 JWT 配置。

1. 按依赖边界拆为独立 Maven/Gradle 子模块；仅拆 Java 包不能隔离自动配置与依赖。
2. manager 管理签名密钥、签发 Token，并提供标准 JWKS 公钥接口。
3. 业务服务与网关都验证 JWT，不持有签名私钥。
4. 公钥来源统一规则：**显式配置的本地公钥优先；未配置本地公钥时，使用配置的 JWKS 接口。** 可扩展支持通过 issuer 元数据发现 JWKS。
5. 显式配置公钥后始终使用配置公钥，不启用 JWKS 获取、远程发现、缓存刷新或失败回退；公钥无效时明确失败。
6. 解码必须包括签名和必要声明校验；解析 JWT 内容不等于完成认证，更不等于通过业务权限校验。
7. JWKS 仅使用各实例内存缓存，不使用 Redis；采用请求触发的过期懒刷新，不使用定时刷新，未知 kid 不提前刷新。接受轮换后缓存更新前部分请求被拒绝。
8. 子系统隔离沿用现有的 `systemType`：manager 按登录与授权流程签发该声明；网关或业务服务配置 `security.system-type` 后要求其匹配，具体操作权限继续由 `permissions` 和业务授权控制。

## 2. 模块划分

三类使用场景对应三个安全功能模块，外加轻量共享模块。无需把整个 common 强行归入三个安全模块。

| 模块 | 职责 | 主要使用方 |
| --- | --- | --- |
| common-core | 通用异常、基础结果模型、无安全框架依赖的工具；具体移动清单待源码盘点 | 各服务按需使用 |
| common-security-core | claim 常量、身份模型、权限标识、公钥来源配置模型及选择规则 | 下列三个安全模块 |
| common-security-issuer | 签名密钥材料、JwtEncoder 自动配置、与 JWKS 发布的集成 | manager |
| common-security-resource | Servlet JwtDecoder、身份和权限映射、方法授权支撑、认证异常适配 | Spring MVC 业务微服务 |
| common-security-gateway | ReactiveJwtDecoder、响应式权限映射、网关安全链及路由规则适配 | WebFlux 网关 |

约束：

- manager 的用户管理、登录业务、角色分配、数据库访问留在 manager，不放入通用安全模块。
- 业务资源归属、租户隔离和数据权限留在对应业务服务；common 只提供可复用接口和上下文。
- security-core 不引入 Servlet、WebFlux、安全过滤链或 manager 的业务实体。
- resource 与 gateway 不互相依赖，也不依赖 issuer；避免通过聚合 starter 把三个模块重新全部引入。
- 密码编码若只由 manager 使用，可直接归入 manager；若多处复用，可保留独立 password 模块及 PasswordAutoConfiguration。
- 如果存在 WebFlux 业务服务，应复用响应式验证适配代码，而非为了 Decoder 引入整套网关功能；届时可再抽取 reactive-resource 模块。

建议依赖关系：

```text
manager          -> common-security-issuer   -> common-security-core
业务微服务        -> common-security-resource -> common-security-core
WebFlux 网关     -> common-security-gateway  -> common-security-core
```

## 3. manager 的公钥接口

### 3.1 使用标准 JWKS

建议地址示例：`https://manager.example.com/oauth2/jwks`。实际地址由部署配置决定。

返回标准 JSON Web Key Set，顶层字段为 keys，不使用项目通用的 code/data/message 包装。RSA 公钥示意如下，n 为占位内容，不可直接使用：

```json
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "manager-signing-2026-01",
      "use": "sig",
      "alg": "RS256",
      "n": "<base64url编码的RSA模数>",
      "e": "AQAB"
    }
  ]
}
```

JWT header 的 kid 与 JWKS 的 kid 对应。接口只输出公钥字段，不能输出 d、p、q 等私钥参数。签发器和接口必须来自同一套受管理的密钥材料，不应各自生成一套密钥。

如 manager 已采用 Spring Authorization Server，应优先使用其 JWKSource 与内置 JWKS 端点；默认路径为 `/oauth2/jwks`，端点需要相应的 JWKSource Bean。参见[授权服务器配置模型](https://docs.spring.io/spring-authorization-server/reference/configuration-model.html)和[端点设置 API](https://docs.spring.io/spring-authorization-server/reference/api/java/org/springframework/security/oauth2/server/authorization/settings/AuthorizationServerSettings.html)。

如 manager 尚未实现完整 OAuth2/OIDC 授权服务器，可以先提供标准 JWKS 接口，资源服务直接配置 jwk-set-uri。单独提供 JWKS 并不意味着已实现完整 OAuth2 登录或授权流程。

### 3.2 访问与密钥轮换

- JWKS 是公开验证材料，建议允许无需 Bearer Token 的 GET 请求；避免获取验签公钥本身依赖尚未验证的 Token。
- 如果经过网关访问，该端点也必须有对应放行规则，避免循环依赖；服务端直连可信 manager 地址通常更简单。
- 使用 HTTPS 和受信任的固定配置地址，不能依据未验证 Token 中的任意 URL 下载密钥。
- 先发布新公钥，再用对应私钥签发；旧公钥至少覆盖旧 Token 最大剩余有效期与时钟偏差后再移除。
- manager 多实例共享一致的签名密钥及 kid；不能每次重启生成并替换生产密钥。
- 本地公钥模式不会自动跟随远程轮换，需要同步更新配置并重新加载或重启；希望自动轮换的服务采用 JWKS 模式。

### 3.3 业界依据与项目选择

“发布新公钥、通过 kid 区分密钥、保留退役公钥支持过渡”的方式有标准依据；不能表述为所有系统的缓存、轮换和清理细节都相同。[OpenID Connect Core 10.1.1](https://openid.net/specs/openid-connect-core-1_0-18.html#RotateSigKeys) 描述了此机制，并指出验证方遇到未知 kid 时重新获取 JWKS。Auth0 也建议在撤销旧密钥前，确保客户端有时间缓存新公钥且旧 Token 已过期，参见[密钥轮换说明](https://support.auth0.com/center/s/article/Tenant-Signing-Key-Rotation)。

本项目采用上述公钥生命周期，但主动选择较简单的缓存策略：未知 kid 不触发刷新，仅在无缓存或缓存到期且有验证请求时获取。该策略接受临时认证失败，不宣称与标准所述刷新行为完全一致，也不保证轮换零中断。

### 3.4 第二次及后续轮换：按有效期清理，不无限累加

kid 是密钥标识，不是密钥本身。每一代新密钥使用不同 kid，不将同一 kid 重新分配给另一把密钥。manager 记录当前签发密钥，以及每把退役密钥对应 Token 的最晚过期时间。

正常轮换步骤：

1. 生成新密钥 B 并发布公钥，保留 A 公钥；随后切换签发密钥至 B，所有 manager 实例停止用 A 签发。
2. A 的私钥退出在线签发路径，A 公钥继续用于验证尚未过期的旧 Token。
3. 当 A 签发的全部相关 Token 已超过有效期和验证容差，删除 JWKS 中的 A 公钥。
4. 下次从 B 轮换到 C 时重复此流程，无需保留已过期的 A。

```text
旧公钥最早清理时间
= 该密钥签发的所有相关 Token 的最大 exp
+ 各验证方允许的最大时钟偏差
+ 可选运维安全余量
```

如果不记录每个 Token 的 exp，可保守使用“所有实例停止使用旧密钥的时间 + 该密钥所签 Token 的最大允许有效期 + 时钟偏差 + 安全余量”。同一密钥若还签发更长生命周期的其他 JWT，也必须计入；不能只计算访问令牌。如果 Token 没有有限有效期，无法直接按此规则清理，需先明确有效期或撤销方案。

示例：Token 最大有效期 2 小时，时钟偏差 60 秒，忽略额外余量：

| 操作 | 当前签发密钥 | JWKS 公钥 |
| --- | --- | --- |
| 初始 | A | A |
| 第一次轮换，停止 A 签发 | B | A、B |
| A 最晚 Token 过期再加 60 秒后清理 | B | B |
| 第二次轮换，停止 B 签发 | C | B、C |
| B 最晚 Token 过期再加 60 秒后清理 | C | C |

若轮换间隔短于 Token 有效期，可以暂时存在 A、B、C 等多代公钥。按过期时间清理，不固定为“最近两把”。可预发布下一代公钥，但本项目不要求轮换零中断。

各服务在下次过期懒刷新时同步清理结果，缓存中的旧公钥不会让已过期 Token 通过时间校验。密钥泄露等紧急撤销不属于正常过期清理流程：仅从 JWKS 删除公钥不会立即清除各实例缓存；必要时需要额外失效或停用措施。

## 4. 公钥来源选择规则

以下为项目自定义策略，需要实现，不能假定 Spring Boot 多个属性同时存在时天然遵循此优先级。

| 输入配置 | 选择结果 | 行为 |
| --- | --- | --- |
| public-key 或 public-key-location | LOCAL | 使用显式配置的公钥，不访问 JWKS 或发现接口 |
| 本地公钥 + jwk-set-uri | LOCAL | 本地优先，日志记录选择结果，不输出密钥内容 |
| 无本地公钥，有 jwk-set-uri | JWKS | 使用指定接口获取并缓存验证公钥 |
| 无本地公钥及 jwk-set-uri，有 issuer-uri | DISCOVERY，可选能力 | 从标准元数据发现 jwks_uri |
| 已启用 JWT，但上述来源全无 | 配置错误 | 启动失败并提示缺失配置 |

细则：

- public-key 与 public-key-location 同时填写时，建议报配置冲突，避免选错本地材料。
- LOCAL 模式在应用启动及整个运行期间均不初始化远程取钥流程，不访问 JWKS/发现接口，不启动定时任务；即使配置了 jwk-set-uri，也不会在未知 kid、验签失败、manager 轮换或运行时间变长时切换到远程模式。只有显式修改公钥配置并重新加载/重启才改变所用公钥。
- 显式设置但内容为空、格式错误、文件不存在，视为配置错误，不按“未配置”悄悄回退。
- issuer-uri 同时承担预期签发者配置。即使 LOCAL 或指定 JWKS 已选中，仍应校验 iss；此时不需要做元数据发现。
- 推荐生产显式配置 jwk-set-uri + issuer-uri，分别明确密钥获取地址与预期签发者。
- 服务自行声明 JwtDecoder 或 ReactiveJwtDecoder 时，common 自动配置退让；此类自定义 Bean 必须自行承担来源策略和声明校验。
- 不允许因 JWKS 故障退化为不验签、不校验 issuer 或信任客户端用户头。

## 5. 配置属性与示例

为兼容现有 `security.jwt.private-key`，建议第一阶段沿用 security.jwt 前缀并建立统一属性模型。下列新增属性为方案约定，并非全部为 Spring Boot 原生属性。

| 属性 | 用途 |
| --- | --- |
| security.jwt.public-key | 内联 PEM 公钥，兼容需要直接配置公钥的模块 |
| security.jwt.public-key-location | 公钥文件位置，如 classpath: 或 file: |
| security.jwt.jwk-set-uri | manager 标准 JWKS 地址 |
| security.jwt.issuer-uri | 预期 iss；未提供其他公钥来源时可用于发现 |
| security.system-type | 可选的子系统隔离配置，要求 JWT systemType 与其一致 |
| security.jwt.allowed-algorithms | 验签算法允许列表，初期建议限定 RS256 |
| security.jwt.clock-skew | 时间声明允许偏差，建议初始值 60s |
| security.jwt.jwks.cache-ttl | JWKS 内存缓存有效期，建议初始值 5m；成功获取后开始计时，读取不续期 |
| security.jwt.jwks.failure-backoff | 获取失败后的请求重试间隔，建议初始值 5s；无后台重试 |
| security.jwt.private-key | 仅 issuer 使用，保留现有属性兼容；具体格式按源码核对 |

### 5.1 业务服务：远程 JWKS

```yaml
security:
  system-type: MANAGER
  jwt:
    jwk-set-uri: https://manager.example.com/oauth2/jwks
    issuer-uri: https://manager.example.com
    allowed-algorithms:
      - RS256
    clock-skew: 60s
```

### 5.2 网关：远程 JWKS

```yaml
security:
  system-type: MANAGER
  jwt:
    jwk-set-uri: https://manager.example.com/oauth2/jwks
    issuer-uri: https://manager.example.com
    allowed-algorithms:
      - RS256
    clock-skew: 60s
```

上例以 MANAGER 子系统为例。需要隔离子系统的网关或业务服务配置 `security.system-type`，要求令牌中的 `systemType` 与配置一致；声明缺失或不匹配时拒绝认证。未配置或配置为空时跳过此项检查，签名、issuer 和时间校验仍生效。manager 根据现有登录与授权流程签发该声明，具体操作权限仍由业务服务检查。

### 5.3 显式公钥优先

```yaml
security:
  system-type: MANAGER
  jwt:
    public-key-location: file:/etc/baosys/keys/manager-public.pem
    jwk-set-uri: https://manager.example.com/oauth2/jwks
    issuer-uri: https://manager.example.com
    allowed-algorithms:
      - RS256
```

此配置选择 LOCAL。保留 jwk-set-uri 不会触发远程请求，包括启动、长时间运行、未知 kid 和验签失败等情况。issuer-uri 仅用于本地比较 iss，不调用发现接口。jwks 缓存相关属性在 LOCAL 模式不生效。

内联配置可替换 public-key-location：

```yaml
security:
  jwt:
    public-key: |
      -----BEGIN PUBLIC KEY-----
      <实际公钥内容>
      -----END PUBLIC KEY-----
```

这是公钥写法片段，实际服务还需配置 issuer，需要隔离子系统时配置 security.system-type。本方案初期按 RSA 公钥设计，其他算法需相应扩展密钥解析与 Decoder 构建方式。

### 5.4 与 Spring Boot 原生属性的关系

原生资源服务器常用属性位于 `spring.security.oauth2.resourceserver.jwt` 下，包括 public-key-location、jwk-set-uri 和 issuer-uri。为避免两套配置互相覆盖，第一阶段由 common 统一读取 security.jwt 并创建 Decoder；不要再用另一套属性或安全 DSL 指定不同的 Decoder 来源。

若现有服务已经使用原生属性，迁移时应先盘点并映射，不可直接忽略；必要时增加过渡适配，并对两套属性冲突报错。后续统一命名必须单独制定兼容策略。

## 6. Decoder 实现与校验

共享来源选择规则和声明验证规则，分别实现两套运行时适配：

| 场景 | Bean 类型 | LOCAL 构建入口 | JWKS 构建入口 |
| --- | --- | --- | --- |
| Servlet 业务服务 | JwtDecoder | NimbusJwtDecoder.withPublicKey(...) | NimbusJwtDecoder.withJwkSetUri(...) |
| WebFlux 网关 | ReactiveJwtDecoder | NimbusReactiveJwtDecoder.withPublicKey(...) | NimbusReactiveJwtDecoder.withJwkSetUri(...) |

这些入口由 Spring Security 提供，参见 [Servlet JWT 文档](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)和[响应式 JWT 文档](https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/jwt.html)。

实现步骤：

1. 绑定并验证属性，选择且只选择一个公钥来源。
2. 构建对应 Decoder，固定算法允许列表，不直接信任 Token 的 alg。
3. 组合默认时间校验、预期 issuer 校验及现有的可选 systemType 校验；不得因添加校验而覆盖掉其他校验器。
4. 按系统访问令牌契约要求 exp、sub 等必要声明存在，验证 exp/nbf 和可配置时钟偏差；不能仅校验有值的时间字段而接受缺失 exp 的无限期 Token。
5. 统一 claims 到权限的转换规则，明确 scope/roles/permissions 与 SCOPE_/ROLE_ 前缀约定。
6. 将 Decoder 接入各自的 Resource Server 安全链。只注册 Decoder Bean 并不等于请求已经受到保护。

本地公钥和 JWKS 模式必须具有相同的 issuer、systemType、时间与算法验证效果。自建 Decoder 时显式附加验证器，不能假设配置了属性就会自动应用到自定义 Bean。

网关不得在事件循环中调用 Servlet Decoder、阻塞式 HTTP 客户端或通过 block() 包装同步验签流程；使用响应式适配。

上表列出的是框架构建入口，不保证其默认行为符合本项目的严格 TTL 策略。必须核对当前版本并控制底层未知 kid 刷新、预取、重试及缓存层行为；若直接 withJwkSetUri 无法满足，应适配密钥来源或 JWTProcessor，使验签器只使用当前缓存快照，网络获取仅由第 9 节流程触发。不能只在外层增加缓存，而让内层仍绕过 TTL 请求 JWKS。

## 7. 自动配置与启用边界

### 7.1 过渡阶段：保留已确认的导入链

在当前设计中，业务 Decoder 已由自定义注解的 @Import 导入，因此：

- 从原 common 的 AutoConfiguration.imports 移除 JwtDecoderConfiguration。
- 若确定其不再作为自动配置候选，将其改为普通 @Configuration(proxyBeanMethods = false)。
- 确保不被业务组件扫描意外发现；否则不加启用注解也可能加载。
- 保留 JwtEncoderConfiguration 的自动配置注册及 private-key 条件，因为它是按签发配置启用，而非由同一个注解导入。

过渡 imports 内容：

```text
baosys.common.autoconfigure.PasswordAutoConfiguration
baosys.common.security.config.JwtEncoderConfiguration
```

这只是现有组织结构的过渡状态，不代表 Encoder 最终仍应放在所有服务共享的基础 common 中。

### 7.2 拆分后的目标状态

| 模块 | 注册和启用方式 |
| --- | --- |
| security-core | 不注册 Decoder、Encoder 或安全过滤链 |
| security-issuer | 自己的 imports 注册 Encoder 自动配置，保留 private-key 条件；业务自定义 JwtEncoder 时退让 |
| security-resource | 保留现有启用注解，显式导入 Servlet 验证配置，不在基础 common imports 重复注册 |
| security-gateway | 网关专用启用注解导入响应式配置，并限定响应式应用；不复用 Servlet 配置 |
| password 功能 | PasswordAutoConfiguration 仅在实际所属模块注册 |

启用注解名称以现有源码为准，不在迁移时无故重命名。若未来改用 starter 自动配置，应整体迁移入口，避免同一配置同时走组件扫描、@Import 和 imports 三条路径。

private-key 的 @ConditionalOnProperty 默认只判断属性存在且值不为 false，不验证密钥格式或非空；应额外校验密钥内容及配对公钥。启用签发但配置无效时明确失败。

### 7.3 移除 Encoder 对 Decoder 的错误耦合

`@AutoConfiguration(after = JwtDecoderConfiguration.class)` 只影响共同参与自动配置的候选顺序，不会导入 Decoder，也不代表 Bean 实例化依赖。

目标设计中，Encoder 从 issuer 自己的签名密钥提供器或 JWKSource 获取材料，不能依赖 Decoder 创建签名密钥。通过参数注入声明实际 Bean 依赖。

如果源码发现两者共享 Bean，应先把共享材料移至明确的配置/提供器，再移除 after。若 @ConditionalOnBean/@ConditionalOnMissingBean 依赖原有注册顺序，需一起调整并做上下文测试，不能只删除注解。manager 如果也需要验证入站 Token，可单独启用资源服务功能。

## 8. 认证与防越权职责

| 层次 | 应承担的检查 |
| --- | --- |
| manager | 验证登录身份、确定 Token 的 systemType 和权限、签发令牌 |
| 网关 | Token 签名及声明、路由级公开/保护规则、粗粒度 scope；清理客户端伪造的身份头 |
| 业务服务 | 再次验签与声明校验、接口/方法权限、资源归属、租户边界和数据权限 |

网关通过不代表业务授权通过。例如：拥有 order:write 权限，仍需在订单服务检查订单是否属于当前用户/租户，以防水平越权；普通用户不能通过修改请求参数取得管理权限，以防垂直越权。

默认透传原 Bearer Token，让下游自行验证。用户身份从验证后的 SecurityContext 获取，不信任客户端传入的 userId、tenantId 或未经保护的 X-User-* 头。验签失败返回 401；身份有效但权限不足返回 403；基础设施取钥故障可统一定义为受控 503，但始终拒绝放行。

## 9. 缓存、故障与可观测性

### 9.1 已确认策略：内存缓存 + 过期懒刷新

- 不使用 Redis；缓存按实例和可信 JWKS 地址隔离，不跨 issuer 混用 kid。
- 不在启动时取钥，不使用定时任务、后台预取或自动周期刷新。
- 第一次需要验证 JWT 时，无缓存则获取 JWKS；有缓存则先检查是否过期，再按 kid 查找公钥。
- 缓存仍有效：使用快照验签；未知 kid 或签名不匹配直接拒绝，不触发刷新。
- 缓存已过期：当前请求触发获取并等待结果，成功后用新快照验证当前 Token。无验证请求时保持过期状态，不主动获取。
- TTL 建议初始值 5 分钟，从成功获取且解析有效的 JWKS 后计时；每次读取不延长 TTL，失败不重置成功时间。
- 对同一实例、同一地址的并发刷新合并为一次，其他请求等待同一结果；网关用响应式等待，不能阻塞事件循环。
- 整体替换成功获取的新快照，不永久合并历史 keys，否则 manager 删除的旧公钥无法从客户端淘汰。

```text
需要验证 JWT
  ├─ LOCAL：始终使用配置公钥 → 验签及声明校验；不进入远程分支
  └─ JWKS：先检查缓存状态
       ├─ 缓存存在且未过期 → 按 kid 选钥 → 验签及声明校验
       │                     找不到或验签失败 → 拒绝，不刷新
       └─ 无缓存或已过期 → 请求触发合并获取
                             成功 → 替换快照 → 验证当前 Token
                             失败 → 拒绝并记录重试退避时间
```

若以后启用 DISCOVERY，元数据发现也必须按请求懒执行，并遵守相同网络边界；首期优先配置明确的 jwk-set-uri。

### 9.2 轮换期间的可接受行为

假设 10:00 缓存 A，TTL 为 5 分钟，10:01 manager 改用 B 签发且 JWKS 已含 A、B：10:02 携带 B Token 的请求可能失败；10:05 或之后的第一个验签请求刷新成功后恢复。manager 后续轮换到 C 时重复同样过程。

自然恢复需要同时满足：缓存到期、存在验证请求、远程接口可用且包含目标公钥。各实例恢复时间不同；若无流量则不刷新。接受缓存窗口内的认证失败，不增加未知 kid 强制刷新补救。

LOCAL 模式不会因等待而恢复到新密钥：必须显式更新配置公钥；这是固定公钥模式的预期行为。

### 9.3 获取失败与观测

- 配置连接及读取超时。获取失败、空集合或无可用签名公钥视为失败，不将失败结果当作有效快照。
- 建议失败后退避 5 秒，仅由退避结束后的下一次验证请求重试；无后台重试。数字为初始建议，不是框架默认值。
- 未过期且匹配的缓存公钥可正常使用；缓存过期后刷新失败则拒绝，本方案不启用过期缓存兜底，也不跳过签名或声明校验。
- 记录模式、缓存年龄、获取耗时、失败及未知 kid 指标；不记录完整 Token、私钥或敏感 claims。
- 必须用 HTTP 调用计数验证 Servlet 与 Reactive 两侧的真实行为，尤其确认未知 kid 不提前获取。

## 10. 迁移实施顺序

1. 盘点 common 所有类、依赖、自动配置文件、自定义启用注解和组件扫描范围；确认网关技术栈及各服务实际属性。
2. 建立类迁移清单，先拆出 security-core，保持现有业务接口兼容。
3. 将 Encoder 与签名密钥材料迁至 issuer 或 manager，解除对 Decoder 的依赖。
4. 在 manager 发布 JWKS，验证与现有签发 Token 的 kid、算法和公钥一致。
5. 为业务服务实现统一来源策略，先在一个服务验证 LOCAL 与 JWKS 两条路径，再迁移其余服务。
6. 为网关实现响应式 Decoder 与相同策略，补齐路由和身份头处理。
7. 调整 imports 与注解导入链，清除重复来源和意外组件扫描；必要时暂留薄兼容模块，但不能让其再次传递依赖所有安全模块。
8. 完成联调、密钥轮换及故障演练后，移除旧类和旧配置适配。

## 11. 验收清单

| 场景 | 预期结果 |
| --- | --- |
| 只依赖基础 common/core | 不创建 JWT 编解码器或安全链 |
| 无 private-key | 不自动创建 Encoder；普通资源服务启动不依赖私钥 |
| 有合法签名材料 | manager 可签发，JWKS 公钥可验证其 Token |
| LOCAL 与 JWKS 同时配置 | 选 LOCAL，JWKS/发现接口请求次数为 0 |
| 本地公钥损坏或路径错误 | 明确配置失败，不回退 JWKS |
| 本地公钥有效但 Token 不匹配 | 拒绝 Token，不尝试远程来源 |
| 只有 JWKS | 按 kid 选钥验签，缓存后重复请求不逐次取钥 |
| 错误签名、过期、未来 nbf、错误 iss/alg、缺必要声明；已配置 system-type 时缺失或不匹配 systemType | 网关与业务服务均拒绝 |
| manager 暂时不可用 | 缓存与无缓存路径符合明确策略，不能绕过验证 |
| 缓存有效时收到未知 kid | 拒绝请求，JWKS 请求次数不增加 |
| 缓存到期但无请求 | 不主动获取 JWKS |
| 缓存到期后的首个验证请求 | 获取成功后使用新快照验证当前请求；并发请求合并获取 |
| 新旧密钥轮换 | 未刷新实例允许暂时拒绝新 Token；过期刷新成功后，可验证快照中对应的新旧合法 Token |
| 第二次及后续轮换 | 重复生命周期，按最大 exp 与容差清理历史公钥，不无限累加、不固定仅保留两把 |
| manager 删除已退役公钥 | 服务成功刷新后旧 key 从快照删除，不与历史缓存永久合并 |
| 显式公钥 + manager 连续轮换 | 始终用配置公钥，启动及运行期间 JWKS/发现请求均为 0；不会等待后自动切换 |
| 过期刷新失败 | 拒绝访问，TTL 不续期，退避期间不重复获取；退避后有请求才重试 |
| 绕过网关直接调用业务服务 | 仍需通过服务自身认证授权 |
| 普通用户操作其他用户/租户资源 | 被业务层拒绝 |
| Servlet/Reactive 分别启用 | 仅存在适用的 Decoder 与安全链，不交叉加载 |
| 业务提供自定义 Decoder/Encoder | 无重复 Bean，common 按约定退让 |
| 网关公开路由与 JWKS 路由 | 放行范围准确，其他路由仍受保护 |

建议使用配置上下文测试验证 Bean 装配，使用可控 JWKS HTTP 服务验证请求次数、缓存和轮换，再做 manager—网关—业务服务端到端联调。

## 12. 实施前待核实

- common 中是否还包含数据库、缓存、Feign、日志等非安全功能，其拆分不在本方案中凭空假定。
- JwtDecoderConfiguration 当前由哪些注解/配置导入；是否声明 Encoder 所需的共享 Bean。
- 现有 Token 的算法、kid、iss、systemType、scope/roles/permissions 格式及有效期。
- manager 是否已经使用 Spring Authorization Server，是否已有 JWKS/元数据端点。
- 当前公私钥属性的实际类型、存储方式和部署注入方法。
- 各服务的安全链所有权与 @ConditionalOnMissingBean 行为；自定义安全链是否已显式配置资源服务器。

## 13. 参考资料

- [Spring Boot 自定义自动配置](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html)
- [Spring Security Servlet JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Spring Security Reactive JWT Resource Server](https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/jwt.html)
- [Spring Authorization Server 配置模型](https://docs.spring.io/spring-authorization-server/reference/configuration-model.html)
- [OpenID Connect Core 10.1.1：非对称签名密钥轮换](https://openid.net/specs/openid-connect-core-1_0-18.html#RotateSigKeys)
- [Auth0：签名密钥轮换与旧密钥撤销](https://support.auth0.com/center/s/article/Tenant-Signing-Key-Rotation)

本方案中的模块划分、公钥优先级、自定义属性、故障策略和迁移计划属于项目设计决策；框架支持的 API 应以项目锁定版本为准。
