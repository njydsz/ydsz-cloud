# ydsz-gateway 全面优化建议报告（2026 Q3）

> 分析基准：最新代码（2026-08-15，14 个配置类 / 13 个过滤器 / 4 个插件类 / 2 个负载均衡类）
> 对标对象：Kong、Apache APISIX、Apache ShenYu、Spring Cloud Gateway 官方最佳实践、阿里巴巴 Sentinel、美团 Shepherd、Netflix Zuul 2、OWASP 网关安全清单、W3C Trace Context 规范、RFC 8594 / RFC 5988
> 关联规范：`docs/云顶编码规范.md`、`docs/checkstyle.xml`

---

## 0. 结论摘要

**模块成熟度**：`ydsz-gateway` 已是一套**功能完备、安全意识和工程化程度都明显高于一般开源项目**的网关实现。横切关注点（鉴权、限流、黑白名单、灰度、链路追踪、可观测性、优雅停机、TLS）覆盖齐全，降级兜底（Redis 熔断、本地限流兜底、JWT 缓存广播失效）意识到位，注释与 Javadoc 详尽到几乎每个字段都标注了 P0/P1 编号。

**核心短板**：当前处于"功能完备 → 生产级高可用"的进阶临界点。主要问题不是"缺功能"，而是：

1. **若干正确性 / 安全缺陷**（🔴）：traceId 生成不符合 W3C 规范导致 OpenTelemetry 全链路追踪实际断裂、两个过滤器 Order 冲突、下游超时无法映射为 504、CORS 双配置 + 默认 `*` 存在旁路风险、响应缓存编码/大小正确性问题。
2. **一处明显的"空壳"过度设计**：Groovy 插件热加载系统有 WatchService 骨架但无任何实际加载逻辑。
3. **缺失熔断器**：README 宣称 Sentinel 熔断，但代码与依赖中均无熔断实现，重试（Retry 3 次）无熔断配合会在下游故障时放大流量。

建议按 **P0（正确性/安全，1-2 周）→ P1（架构/功能，1 个迭代）→ P2（性能/体验，持续）** 三档落地，见第 8 节路线图。

---

## 1. 现状总评（做得好的地方）

以下能力已达行业标杆水平，建议保留并作为其它模块的对标样板：

| 能力 | 实现要点 | 对标水平 |
|---|---|---|
| 内部头防伪 | HMAC-SHA256 签名 + 密钥与 JWT 分离 + 恒定时间比较（`InternalHeaderSigner`） | 大厂级 |
| JWT 校验缓存 | Caffeine + 防击穿/防穿透 + Redis Pub/Sub 多实例广播失效（`CachedJwtValidator`） | 大厂级 |
| 多维限流 | Redis Lua 令牌桶 + IP/用户/租户三维 + Redis 故障本地兜底 + 按实例数分摊配额（`RateLimitFilter`） | 优于多数开源 |
| IP 提取 | 可信代理链校验，拒绝外部伪造 X-Forwarded-For（`GatewayIpUtils`） | OWASP 推荐 |
| 敏感信息治理 | 日志脱敏（`AccessLogGlobalFilter`）、Token 脱敏（`SensitiveUtil`）、审计路径脱敏 | 等保/GDPR 友好 |
| 灰度路由 | 头/参数/路径/比例四源 + 一致性哈希粘性 + Alias Method O(1) 加权轮询（`GrayLoadBalancer`） | 大厂级 |
| 可观测性 | Prometheus 指标 + 结构化访问日志 + 审计日志 + 健康检查（`GatewayMetrics`/`AccessLogGlobalFilter`） | 大厂级 |
| 配置热更新 | Nacos 动态路由 + 内存缓存 + 监听刷新（`NacosRouteDefinitionRepository`） | 大厂级 |

---

## 2. 关键缺陷（🔴 正确性 / 安全，建议最高优先级修复）

### 2.1 🔴 traceId 生成不符合 W3C 规范 → 全链路追踪实际断裂

**位置**：`W3CTraceContextFilter.java:166-177`

```java
private String generateTraceId() {
    return String.valueOf(snowflakeIdGenerator.nextId()).replace("-", "");
}
private String generateSpanId() {
    return String.valueOf(snowflakeIdGenerator.nextId()).replace("-", "").substring(0, 16);
}
```

**问题**：W3C Trace Context 要求 `traceparent = 00-{32位hex}-{16位hex}-{flags}`。而 `SnowflakeIdGenerator.nextId()` 产生的是**十进制数字**（约 19 位），`String.valueOf` + `replace("-")` 后仍是十进制，既不是 32 位、也不是 hex。`generateSpanId()` 同理是 16 位十进制。

**自相矛盾**：同一个类的 `isValidTraceparent()`（`W3CTraceContextFilter.java:124-143`）严格要求上游 `traceId` 必须是 32 hex、`spanId` 必须是 16 hex。也就是说——**网关自己生成的 traceparent 连自己的校验都过不了**，下游 OpenTelemetry / SkyWalking Agent 会判定非法并丢弃，W3C 全链路追踪链路实际是断的。

**连带问题**：全模块存在三套 traceId 生成器不统一——`W3CTraceContextFilter` 用 Snowflake、`AccessLogGlobalFilter`/`AuthGlobalFilter` 用 `TraceIdGenerator.generateSortableTraceId()`。虽然 W3C 过滤器 Order 最靠前会先注入，但其产物是十进制，导致 `X-Trace-Id` 在日志里是可用的唯一串，但 `traceparent` 对外不可用。

**建议（P0）**：
1. 统一 traceId 生成：新增 `TraceIdGenerator.generateW3CTraceId()`（用 `SecureRandom` 生成 32 位 hex），spanId 生成 16 位 hex。
2. 若需保留 Snowflake 语义，将雪花 ID 转为 32 位 hex（`String.format("%032x", id)`），但需保证可排序性与 W3C 兼容性兼得——简单起见直接随机 32 hex 即可。
3. 消除三套生成器，`AccessLogGlobalFilter`/`AuthGlobalFilter` 全部读取 W3C 注入的 `X-Trace-Id`，不再自行生成（`AuthGlobalFilter.java:204-208` 已朝此方向，需彻底收口）。

### 2.2 🔴 过滤器 Order 冲突（`PayloadValidationFilter` vs `IpBlacklistFilter`）

**位置**：`PayloadValidationFilter.java:161` 与 `IpBlacklistFilter.java:183` 均返回 `Ordered.HIGHEST_PRECEDENCE + 3`。

**问题**：Spring 对相同 `getOrder()` 的过滤器按 Bean 注册顺序执行，顺序不确定。此处虽碰巧都只做"放行或拒绝"（无状态依赖），暂未暴露问题，但属于**隐性顺序炸弹**，后续任何一方引入状态/前置依赖都会踩坑。`GatewayApplication.java:42-57` 的 Javadoc 顺序表本身也已与实际不符（表中未列出 `W3CTraceContextFilter`(0)，且 `+3` 写了两项）。

**建议（P0）**：
1. 引入 `GatewayFilterOrder` 枚举/常量类，集中定义所有过滤器 Order（如 `TRACE=HIGHEST_PRECEDENCE`、`ACCESS_LOG=+1`、`IP_BLACKLIST=+3`、`PAYLOAD=+4`、`IP_WHITELIST=+5`...），过滤器引用常量而非魔法数字。
2. 同步修正 `GatewayApplication` 的 Javadoc 顺序表，并在 CI 增加"Order 唯一性"静态检查（可用 ArchUnit 或自定义 Checkstyle 规则）。

### 2.3 🔴 下游超时/连接异常无法正确映射为 504/502

**位置**：`GatewayErrorConfig.java:126-144`

```java
if (ex instanceof ConnectException) return HttpStatus.BAD_GATEWAY;      // java.net.ConnectException
if (ex instanceof TimeoutException) return HttpStatus.GATEWAY_TIMEOUT;  // java.util.concurrent.TimeoutException
...
if ("NotFoundException".equals(className)) return HttpStatus.NOT_FOUND; // 字符串匹配
```

**问题**：
1. Reactor Netty 下游读超时抛的是 `reactor.netty.http.client.PrematureCloseException` / `io.netty.handler.timeout.ReadTimeoutException` / `ConnectTimeoutException`，而 `spring.cloud.gateway.httpclient.response-timeout: 30s` 触发的是包装后的 `ResponseStatusException(504)`，**几乎不会出现裸 `java.util.concurrent.TimeoutException`**，导致 504 映射形同虚设，最终落到 default 的 500。
2. `ConnectException` 只覆盖了裸连接拒绝；连接池 `PoolAcquireTimeoutException`、`WebClientRequestException` 等未被覆盖。
3. 用 `getClass().getSimpleName().equals("NotFoundException")` 字符串匹配极其脆弱，`spring-cloud-gateway` 的 `org.springframework.cloud.gateway.support.NotFoundException` 是公开类，应直接 `instanceof`。

**建议（P0）**：改为按具体异常类型（含 reactor.netty 异常、`ResponseStatusException` 的 statusCode、`WebClientRequestException`）做映射，全部用 `instanceof`，并补充 `PoolAcquireTimeoutException` → 503、`ReadTimeoutException`/`ConnectTimeoutException` → 504。建议写一个单测矩阵固化映射关系（现有 `GatewayErrorConfigTest` 可扩展）。

### 2.4 🔴 CORS 双配置 + 默认 `*` 存在旁路风险

**位置**：`CorsProperties.java:38` 默认 `allowedOrigins = ["*"]`、`allowCredentials = true`；`GatewayCorsConfig.java:48-64` 注册了 `CorsWebFilter`；同时 `ydsz-gateway-prod.yaml:52-64` 又配置了 `spring.cloud.gateway.globalcors`。

**问题**：
1. **两套 CORS 并存**：`CorsWebFilter`（WebFilter，优先级最高）与 SCG 的 `globalcors`（GlobalFilter）都会处理预检/跨域，语义与配置来源割裂，运维极易只改一处导致另一处仍是旧值。
2. **默认 `*` 是旁路**：`CorsProperties` 默认 `["*"]`，若生产环境只按 prod yaml 配了 `globalcors.allowedOriginPatterns`、却遗漏 `ydsz.gateway.cors.allowed-origins`，`CorsWebFilter` 会以 `*` 反射任意 Origin，`globalcors` 的严格白名单被旁路。且 `allowedOrigins="*"` 与 `allowCredentials=true` 的组合本身违反浏览器规范（Spring 对 `allowedOrigins` 配 `*` + credentials 会抛异常，但此处用的是 `setAllowedOriginPatterns`，`*` 会被当作 Pattern 反射具体 Origin，安全语义已被削弱）。

**建议（P0）**：
1. 保留单一 CORS 来源：删除 `globalcors`，统一由 `CorsWebFilter` + `CorsProperties` 管理。
2. `CorsProperties` 默认值改为**空列表**（`Collections.emptyList()`），并在 `GatewayCorsConfig` 中加启动校验：生产 Profile 下 `allowedOrigins` 为空则启动失败（fail-fast），杜绝静默 `*`。
3. 补 `allowCredentials=true` 时禁止 `*` 的显式校验。

### 2.5 🟡 响应缓存正确性缺陷（内容编码 / 大小上限 / 响应头透传）

**位置**：`ResponseCacheFilter.java`（`serveFromCache` 152-165、`cacheResponseBody` 282-286、`buildCacheKey` 296-317）

**问题**：
1. **无缓存大小上限**：`cacheResponse`/`cacheResponseFromBuffer` 将整个响应体读为 `String` 存入 Redis，若下游返回超大响应（如 100MB 文件/报表），会被全量读入网关内存，存在 OOM 风险。请求侧有 10MB 限制（`RequestSize`），响应侧却无对应保护。
2. **内容编码错乱**：`serveFromCache` 只设置 `Content-Type` 和 `X-Cache`，若原响应带 `Content-Encoding: gzip`（prod yaml 开启了 `server.compression`），缓存的压缩字节会被当作明文 JSON 返回给客户端，浏览器解压失败；反之亦然。
3. **响应头丢失**：`Cache-Control` / `ETag` / `Last-Modified` / `Vary` 未透传，客户端缓存协商失效。
4. **缓存键不含 `Accept-Language` / API 版本**：多语言场景会命中他语言缓存（`buildCacheKey` 仅 method+path+query+userId）。

**建议（P1）**：
1. 增加 `max-cacheable-response-size`（如 1MB），超限 `BYPASS` 不缓存。
2. 缓存时记录 `Content-Type`/`Content-Encoding`/`Cache-Control`/`ETag`，命中时原样回放；或对带 `Content-Encoding` 的响应直接 `BYPASS`（最简单）。
3. 将 `Accept-Language`、`X-API-Version` 纳入缓存键。
4. 仅缓存 `Content-Type ∈ {json, xml, text}` 已具备（`CACHEABLE_CONTENT_TYPES`），但需补 `Content-Encoding` 判定。

### 2.6 🟡 nonce 防重放语义不一致

**位置**：`AuthGlobalFilter.java:273-279`（网关侧 `nonceCache.verifyAndConsume(nonce)`）、`WebSocketAuthFilter.java:163-167`（生成 nonce 但**不** `verifyAndConsume`）。

**问题**：
1. `NonceCache.verifyAndConsume` 是"校验即消费"原子操作。`GatewayApplication.java:148-151` 里 `new NonceCache()` 是**网关本地内存实例**，与下游服务各自的 `NonceCache` 是不同进程独立缓存——语义上虽可各自消费，但网关侧"防重放"的真实收益存疑：重放攻击需同时伪造 HMAC 签名 + 原 nonce，而签名本身含时间戳+nonce，攻击者在 60s 窗口内重放原请求会被网关侧 nonce 拦截。逻辑成立，但**跨进程/单实例语义需在文档中明确**，否则容易误以为 nonce 是跨服务共享的。
2. `WebSocketAuthFilter` 生成了 nonce 却未写入 `NonceCache`，导致 WebSocket 握手路径的 nonce 只发不存，与 HTTP 路径行为不一致（防重放形同虚设）。

**建议（P1）**：统一 nonce 处理逻辑，将"生成 nonce → 存 NonceCache → 签名"抽为公共方法供 `AuthGlobalFilter`/`WebSocketAuthFilter` 复用；在 Javadoc 明确 `NonceCache` 为进程内缓存及防重放边界。

---

## 3. 架构优化

### 3.1 引入熔断器，与重试闭环（README 承诺但未实现）

README（第 5、21 行）宣称"Sentinel 熔断（端口 8719）"，但 `pom.xml` **无 Sentinel 依赖**，prod yaml 无 Sentinel 配置，代码仅有 `setCircuitBreakerState` 指标占位。而 `ydsz-gateway-prod.yaml:70-78` 配了 `Retry`（GET 3 次）。**重试无熔断配合**：下游持续 502/504 时，重试会将流量放大 3 倍，加剧雪崩。

**建议（P1）**：引入 Resilience4j（reactive `ReactiveCircuitBreaker`）或 Sentinel，按路由维度熔断；重试的 `statuses` 与熔断判定联动，熔断 OPEN 时直接短路返回 503（`CIRCUIT_BREAKER_OPEN` 错误码已定义但未使用）。若团队已定 Sentinel，则补 `spring-cloud-starter-alibaba-sentinel` 依赖与 `GatewayFlowRule`/`DegradeRule` 配置，并移除"已对接 Sentinel"的错误文档描述或补齐实现。

### 3.2 过滤器 Order 治理（承接 2.2）

将 13 个过滤器的魔法数字收口为 `GatewayFilterOrder` 常量枚举，并提供 ArchUnit 测试校验"Order 唯一 + 区间不重叠"。

### 3.3 配置注入风格统一（`@Value` 收敛为 `@ConfigurationProperties`）

`ApiKeyAuthFilter`、`PayloadValidationFilter`、`WebSocketAuthFilter`、`AuthGlobalFilter`（`internalSignSecret`）、`GrayLoadBalancerRequestFilter`（`grayRatioPercent`）等大量使用散落 `@Value`，而 `RateLimitProperties`/`SecurityHeadersProperties`/`CorsProperties` 已用 `@ConfigurationProperties`。**风格割裂**导致：无法 `@Validated` 校验、无 IDE 补全、配置项分散难治理。

**建议（P1）**：新增 `ApiKeyProperties`、`PayloadValidationProperties`、`WebSocketProperties`、`GrayProperties`、`InternalSignProperties`，统一走 `@ConfigurationProperties + @Validated`（配合已有 `additional-spring-configuration-metadata.json`）。内部签名密钥长度校验（`AuthGlobalFilter.java:159-177` 的 `@PostConstruct`）可上移到 `@Validated` 的 `@Size(min=32)`。

### 3.4 异常处理收口（承接 2.3）

将 `GatewayErrorConfig` 的异常→状态码→业务码映射收敛为单一来源，`resolveBizCode` 的 `default: httpStatus.value()*100` 会产生不在 `GatewayErrorCode` 枚举内的野值（如 415→41500），`fromCode` 回退 `INTERNAL_ERROR` 导致 help URL 错位。建议 `default` 一律回退 `INTERNAL_ERROR.getCode()`，并保证任意 status 都有闭合映射。

### 3.5 HttpClient 集成方式核验

`GatewayHttpClientConfig.java:131-134` 定义 `HttpClient` Bean 期望被 SCG 使用，但 SCG 4.x 的 Netty 客户端由 `HttpClientProperties` + `HttpClientCustomizer` 装配，直接暴露 `HttpClient` Bean 未必被 `ReactorNettyHttpClient` 采纳。**建议（P1）**：改用 `HttpClientCustomizer` 或验证 Bean 是否真正生效（可通过压测观察连接复用/日志确认），避免"配置了却不生效"。

---

## 4. 功能增强

### 4.1 API Key 认证升级（对标 Kong key-auth + ACL）

`ApiKeyAuthFilter.java:152-158` 每次请求 `Set.of(validKeys.split(","))` 重建集合（性能浪费），且 Key 无元数据、无独立限流、无过期/吊销、无身份映射。

**建议（P1/P2）**：
1. 启动时解析 Key 到内存（或 Redis），`isValidApiKey` 用 `O(1)` 查找。
2. 升级为 Key → 身份（appId）映射 + 独立速率配额，注入下游 `X-API-Key-User` 携带 appId 而非掩码字符串。
3. 支持 Key 过期 / 吊销（存 Redis，`TTL` + 黑名单）。

### 4.2 限流补充 per-route 精细化 + 动态规则

当前限流仅有 IP/用户/租户三个全局维度（`RateLimitProperties`），无法针对热点接口（如登录、导出）单独限流。建议（P1）：增加 `per-route` 维度（routeId/path 粒度），并支持从 Nacos 动态刷新阈值（复用已有 `refresh-enabled` 能力）。

### 4.3 灰度全链路透传 + 比例灰度机制统一

1. `GrayLoadBalancerRequestFilter.java:71` 用配置 `ydsz.gateway.gray.ratio-percent`，而 `GrayLoadBalancer.java:79` 定义了 `METADATA_GRAY_RATIO`（metadata 中的 `grayRatio`）——**两套比例灰度机制未打通**，易误解。
2. 灰度标识仅作用于"网关 → 首跳服务"，下游 Feign 调用其它服务时 `X-Gray-Tag` 未透传（需 `RequestInterceptor` 透传灰度头），灰度链路不完整。

**建议（P1）**：统一比例灰度配置源；提供 Feign `RequestInterceptor` 或公共拦截器透传 `X-Gray-Tag`，实现全链路灰度。

### 4.4 IP 黑/白名单动态管理

`IpBlacklistFilter` 的写入方式目前仅有"Redis CLI 手动 SET"。建议（P2）：提供受控的管理端点或对接安全系统（限流/风控）自动写入，并配套 `IpWhitelistFilter` 的对称能力。

---

## 5. 性能提升

### 5.1 限流三维度合并为单次 Redis 调用

`RateLimitFilter.java:191-209` 用 `Mono.zip` 并行执行 3 次 Redis Lua（IP/用户/租户），每次都是独立网络往返。**建议（P1）**：将三维度合并进同一 Lua 脚本（一次 `EVAL` 传入 3 组 key/rate/capacity），减少 2 次 RT，高 QPS 下收益明显。

### 5.2 预编译正则 / 复用集合

- `AuditLogFilter.java:272-275` 的 `sanitizePath` 用 `String.replaceAll`（每次编译 Pattern）。改为 `static final Pattern`。
- `ApiKeyAuthFilter` 的 Key 集合启动时解析（见 4.1）。
- `AccessLogGlobalFilter` 的 `sanitizeQuery` 仅在 4xx/5xx 执行（已优化），可保持。

### 5.3 访问日志异步化 / 采样

`AccessLogGlobalFilter.java:152-207` 在 `doFinally` 中同步执行 StringBuilder 拼接 + `log.info`。10K QPS 下同步拼接会占用 Netty EventLoop。**建议（P2）**：日志走异步队列（配合 logback async appender），或对 2xx 做采样（如 1% 采样 + 全量错误），降低日志成本。

### 5.4 `CachedJwtValidator` 二次查缓存微调

`validateAndParse`（`CachedJwtValidator.java:229-254`）先 `getIfPresent` 判 hit，miss 后再 `getWithProtection`（内部还会再查一次）。可接受，若追求极致可让 `getWithProtection` 直接返回"hit/miss"标志，省一次查找。

---

## 6. 体验改善

### 6.1 错误响应 message 风格统一（i18n key vs 中文混杂）

`AuthGlobalFilter` 返回 `"error.TOKEN_INVALID"` 等 i18n key，`RateLimitFilter.rejectWithRateLimit` 返回中文 `"请求过于频繁，请稍后重试"`，`ApiKeyAuthFilter` 返回 `"API Key 无效"`。**三种风格并存**，前端无法统一处理。

**建议（P1）**：统一为"messageKey + 前端翻译"或"网关直接返回可读 message"二者其一。若保留 i18n key，则所有 filter 的拒绝分支都必须返回 key（含 `RateLimitFilter`/`ApiKeyAuthFilter`/`PayloadValidationFilter`），并补充 i18n 资源文件映射。

### 6.2 健康检查 `block()` 无超时

`GatewayHealthIndicator.java:90` 的 `redisTemplate...ping().block()` 无超时参数，Redis 挂起时健康检查会阻塞，进而影响 K8s 探针。

**建议（P1）**：改为 `block(Duration.ofSeconds(2))`，超时即标记 `DOWN`。

### 6.3 移除过时 / 无效安全头

`AuthGlobalFilter.java:447` 注入 `X-XSS-Protection: 1; mode=block`，该头已被 Chrome/Edge/Firefox **废弃**，甚至可能引入 XSS 审计绕过问题（CSP 才是主流）。`AuthGlobalFilter.java:445` 注入 `X-CSRF-Protection: 1`，这是**非标准头、无任何浏览器实现**，纯声明无防护作用（CSRF 需 token 或 `SameSite` Cookie）。

**建议（P2）**：移除 `X-XSS-Protection` 与 `X-CSRF-Protection`，保留 `CSP` + `frame-ancestors` + `SameSite` 策略即可。

### 6.4 告警渠道硬编码钉钉

`GatewayAlertService.java:140-141` 硬编码 `NotifyChannel.DINGTALK`。建议（P2）：渠道改为可配置（`ydsz.gateway.alert.channels`），复用 `NotifyService` 的多渠道能力（`ydsz-common-notify` 已支持飞书/邮件/短信）。

### 6.5 补充压测基准与容量模型

README 有连接池容量估算（4C8G → 5000 QPS），建议（P2）补一份 JMeter/Gatling 压测基线（含过滤器全开/限流开启的 P99），并固化到 CI，防止后续过滤器叠加导致性能回退。

---

## 7. 过度设计

### 7.1 🔴 Groovy 插件热加载是"空壳"（最典型的过度设计）

`PluginManager.java:200-202` 的 `WatchService` 检测到 `.groovy` 变更后仅 `log.info`，注释明写"Groovy 加载需引入 groovy-jsr223 依赖，当前版本预留扩展点"；`pom.xml` 无 Groovy 依赖；全模块**无任何 `GatewayPlugin` 实现类**。`PluginGlobalFilter` 的 PRE/POST 插件执行点也无人消费。

**建议（P0/P1）**：二选一——（1）删除 `plugin` 包与 `PluginGlobalFilter`，减少维护面与每次请求的空遍历；（2）若确需扩展点，改为务实的 Java SPI（`ServiceLoader`）或直接依赖 Spring 的 `GlobalFilter` 组合，放弃 Groovy 热加载这一"重且未落地"的机制。

### 7.2 nonce 三重防重放对内网内部头偏重（承接 2.6）

内部头（网关→下游）本身处于内网信任域，已具备"HMAC 签名 + 60s 时间戳窗口"。再叠加 nonce 防重放，收益有限却引入 `NonceCache` 存储与跨进程语义复杂度。**建议（P2）**：保留签名+时间戳即可，nonce 机制降级为可选（开关控制），或仅在对外暴露面（若未来网关对外）启用。

### 7.3 注释通胀

每个字段/方法都带大段 Javadoc + P0/P1 编号，注释量已逼近代码量。这是"团队持续 review"的副产品（值得肯定），但建议（P2）后续只保留"为什么"（设计意图、安全边界、坑），删除"是什么"（可从代码自明），避免注释与实现漂移（如 2.2 的 Order 表已与实现不符）。

### 7.4 响应缓存收益存疑（内网低延迟场景）

内网 RPC 延迟通常 < 10ms，网关缓存 60s 的收益有限，却带来 2.5 节一串正确性成本（编码/大小/透传/键完整性）。**建议（P2）**：评估真实命中场景；若仅少数读多写少接口（字典、配置），改用"下游服务自身加 `@Cacheable` + `Cache-Control`"更简单，网关缓存可默认关闭（`matchIfMissing=true` 目前默认开启，建议改为默认关）。

### 7.5 `ApiVersionHeaderFilter` 收益有限

API 版本已在路径 `/api/v1/**` 明文可见，`X-API-Version` 响应头属锦上添花。`Sunset` 头虽符合 RFC 8594，但未与 `ydsz-common-web` 的 `@ApiVersion` 注解/废弃管理打通（过滤器仅正则提取版本段）。**建议（P2）**：若前端未实际消费该头，可下线；若保留，需与 `@ApiVersion` 的废弃配置打通才算真正实现其 Javadoc 承诺。

---

## 8. 落地优先级路线图

| 优先级 | 条目 | 工作量 | 说明 |
|---|---|---|---|
| **P0** | 2.1 traceId/W3C 修复 | 0.5d | 统一 32-hex 生成器，收口三套生成逻辑 |
| **P0** | 2.2 Order 冲突治理 | 0.5d | 引入 `GatewayFilterOrder` 常量 + ArchUnit 校验 |
| **P0** | 2.3 异常→状态码映射修复 | 0.5d | `instanceof` + reactor.netty 异常 + 单测矩阵 |
| **P0** | 2.4 CORS 单一来源 + 默认收紧 | 0.5d | 删 globalcors，默认空列表 + 生产 fail-fast |
| **P0** | 7.1 插件空壳处理 | 0.5d | 删或改 Java SPI |
| **P1** | 3.1 引入熔断器（+重试闭环） | 3d | Resilience4j / Sentinel，按路由熔断 |
| **P1** | 2.5 响应缓存正确性 | 2d | 大小上限 + Content-Encoding 判定 + 头透传 + 键补全 |
| **P1** | 3.3 配置 `@ConfigurationProperties` 收口 | 2d | 新增 5 个 Properties 类 + `@Validated` |
| **P1** | 5.1 限流三维度合并 Lua | 1d | 单次 EVAL 降 2 次 RT |
| **P1** | 6.1 错误 message 统一 + 6.2 health 超时 | 1d | 风格统一 + block 超时 |
| **P1** | 4.1 API Key 升级 + 4.3 灰度全链路 | 3d | Key 元数据 + Feign 透传 |
| **P2** | 3.5 HttpClient 生效核验 | 0.5d | HttpClientCustomizer 改造 |
| **P2** | 5.3 日志异步/采样 + 6.3 移除过时安全头 | 1d | 降低成本 + 消除无效头 |
| **P2** | 7.2/7.4/7.5 简化（nonce/缓存/版本头） | 1d | 评估后开关化或下线 |
| **P2** | 6.5 压测基线固化 | 2d | JMeter + CI 防回退 |

> 建议先集中清完 **P0（约 2.5 人日）**，这 5 项都属于"不改会埋雷"的正确性/安全缺陷；P1 在一个迭代内完成，即可将网关从"功能完备"推进到"生产级高可用"。
