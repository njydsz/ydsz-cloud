# ydsz-gateway

> API 网关（Spring Cloud Gateway + WebFlux）

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9000**（按构建顺序 1/10） |
| **服务名** | `ydsz-gateway` |
| **构建顺序** | 1/10（Maven 构建第一个部署单元） |
| **Nacos 注册** | 是（注册中心 + 配置中心） |
| **数据库** | 不直接访问 |
| **作用** | 统一入口、路由分发、限流、CORS、认证、灰度、API 版本协商 |

## 核心职责

1. **路由分发**：Nacos 动态路由为唯一入口（`gateway-routes.json` JSON 数组格式，8 条路由）
2. **鉴权拦截**：解析 JWT（本地缓存防击穿/穿透 + 自适应 TTL，验签切出事件循环到 `boundedElastic`）、Token 黑名单校验（Redis）、转发 `X-User-Id` / `X-Tenant-Id` / `X-Trace-Id` 等内部头
3. **限流**：Redis + Lua 令牌桶二维限流（IP / 用户），Redis 不可用时降级放行
4. **熔断**：自研熔断引擎（common-safe）按路由隔离熔断（防下游雪崩）
5. **CORS**：按环境单一可信 Origin 放行（生产必须显式域名，拒绝凭据+通配符组合）
6. **IP 访问控制**：`ydsz.gateway.ip-control.*` 统一黑白名单（Redis 动态黑名单 + 本地缓存）
7. **灰度路由**：基于 `X-Gray-Tag` 头 + Nacos `metadata.version` 元数据 + `weight` 权重加权随机（Alias Method O(1)）+ `grayRatio` 比例分流
8. **WebSocket**：握手认证 + Origin 校验 + 连接数限制（Redis 原子计数，用户/IP 二维度）
9. **API 版本协商**：`X-API-Version` 头（Path > Header > Query 优先级）+ 弃用版本 Sunset 头（RFC 8594）
10. **安全审计**：双轨制（SLF4J 结构化日志 + 审计事件桥接 `sys_audit_log`）
11. **可观测性**：Prometheus 指标（请求延迟/总数/限流/JWT 缓存/熔断状态）+ W3C Trace Context

## 数据库表设计

本模块为**纯路由网关**，**不直接访问任何业务数据库**，仅作为流量入口与横切关注点（限流/熔断/灰度）的执行点。

- 注册中心：Nacos（仅做服务发现 + 配置中心）
- 缓存：Redis（限流计数 / IP 白名单缓存 / Token 黑名单 / WebSocket 连接计数）
- 业务 DB：**不持有任何 `ydsz_*` 表**
- 业务实体：模块内**不定义 `*DO.java`**，所有业务数据均通过路由转发到下游服务

> **设计原则**：
> - 网关注入业务表会带来分布式事务与数据一致性风险，违反"网关无状态"约束；
> - 所有审计 / 操作日志下沉到 `ydsz-system` 的 `sys_audit_log`（由 `GatewayAuditEventBridge` 桥接）；
> - 限流统计写入 Redis（`ydsz:ratelimit:*`），不落库；
> - 灰度标签仅作为请求头/Metadata 透传，不持久化。

> **架构约束**：`ydsz-gateway` 为 reactive 栈（WebFlux + Netty），**不依赖** `ydsz-common-web`（servlet 栈），按需挑选 9 个细粒度子模块（core / util / exception / auth / safe / cache / sentry / audit / thread）。

## 启动顺序

```
gateway (9000) ─── 入口，必须最先启动
   ↓
system (9001) ──┐
userinfo (9002) ─┼─→ 可并行启动
nextwiki (9003) ─┘
   ↓
message (9004) ──┐
workflow (9005) ─┼─→ 可并行启动
cronjob (9006) ─┤
literule (9007) ─┤
agent (9008) ────┘
```

## 目录结构

```
ydsz-gateway/
├── pom.xml
└── src/main/
    ├── java/com/njydsz/gateway/
    │   ├── GatewayApplication.java              # 启动类 + @EnableConfigurationProperties + Token 黑名单服务注册 + 健康指标
    │   ├── config/
    │   │   ├── GatewayConstants.java            # 内部头常量（委托各 common 模块常量）
    │   │   ├── GatewayErrorCode.java            # 网关错误码（5 位业务码，HTTP 状态码 + 分类）
    │   │   ├── GatewayErrorWriter.java          # 统一错误响应写出器（bizCode + ProblemDetail + traceId + RFC 5988 Link）
    │   │   ├── GatewayFilterConfig.java         # 过滤器配置（CorsWebFilter + 全局异常处理器 GatewayExceptionHandler）
    │   │   ├── GatewayFilterOrder.java          # 过滤器执行顺序统一常量（enum，基于 HIGHEST_PRECEDENCE 偏移）
    │   │   ├── GatewayHealthIndicator.java      # 网关健康指标（Redis / 安全头 / 限流 / IP 控制 / 鉴权 / 指标）
    │   │   ├── GatewayHttpClientConfig.java     # HttpClient 连接池配置（Reactor Netty + Micrometer 指标）
    │   │   ├── GatewayIpUtils.java              # IP 工具类（可信代理链解析）
    │   │   ├── GatewayMetrics.java              # Prometheus 指标（请求延迟/总数/灰度/限流/JWT/熔断）
    │   │   ├── GatewayRouteConfig.java          # Nacos 动态路由装配（@Primary 覆盖默认路由仓库）
    │   │   ├── InternalHeaderSigner.java        # 内部头 HMAC-SHA256 签名（防伪造）
    │   │   ├── NacosRouteDefinitionRepository.java  # Nacos 路由仓库（内存缓存 + 配置变更监听 + RefreshRoutesEvent）
    │   │   ├── PathGuard.java                   # 路径安全防护（双层 URL 编码检测 + null 字节注入 + 白名单匹配）
    │   │   ├── RateLimitProperties.java         # 限流配置属性（prefix ydsz.gateway.ratelimit）
    │   │   ├── IpAccessControlProperties.java   # IP 黑白名单配置（prefix ydsz.gateway.ip-control）
    │   │   ├── ApiVersionProperties.java        # API 版本协商配置（prefix ydsz.gateway.api-version）
    │   │   ├── CorsProperties.java              # CORS 配置（prefix ydsz.gateway.cors）
    │   │   ├── WebSocketConnectionLimiter.java  # WebSocket 连接数限制器（Redis 原子计数，用户/IP 二维度）
    │   │   └── CachedJwtValidator.java          # JWT 校验缓存（防击穿/穿透 + 自适应 TTL + 命中率指标）
    │   ├── filter/
    │   │   ├── AccessLogGlobalFilter.java       # 访问日志（JSON 转义 + 采样 + 敏感参数脱敏 + Prometheus 指标）
    │   │   ├── ApiKeyAuthFilter.java            # API Key 认证（SHA-256 摘要比对，备选 JWT）
    │   │   ├── ApiVersionHeaderFilter.java      # API 版本协商（X-API-Version / Sunset 头）
    │   │   ├── AuditLogFilter.java              # 审计日志（双轨制：SLF4J + 审计事件桥接 sys_audit_log）
    │   │   ├── AuthGlobalFilter.java            # JWT 解析 + 内部头注入（验签切出事件循环）+ Token 黑名单 + 路径穿越拦截
    │   │   ├── CircuitBreakerGlobalFilter.java  # 熔断（common-safe 自研引擎，按路由隔离 + 状态指标）
    │   │   ├── GrayLoadBalancerRequestFilter.java  # 灰度路由请求过滤器（注入 X-Gray-Tag）
    │   │   ├── GrayResponseHeaderFilter.java    # 灰度路由响应头（X-Gray-Hit）
    │   │   ├── IpAccessControlFilter.java       # IP 黑白名单统一过滤（Redis 动态黑名单 + 本地缓存）
    │   │   ├── PayloadValidationFilter.java     # 请求体安全校验（大小 + Content-Type + JSON 深度）
    │   │   ├── RateLimitFilter.java             # Redis 令牌桶二维限流（IP/用户，Lua 脚本原子操作）
    │   │   ├── W3CTraceContextFilter.java       # W3C 链路追踪（traceparent）
    │   │   └── WebSocketAuthFilter.java         # WebSocket 握手认证
    │   ├── loadbalancer/
    │   │   ├── GrayLoadBalancer.java            # 灰度负载均衡器（权重加权随机 Alias Method O(1) + 灰度降级）
    │   │   └── GrayLoadBalancerConfig.java      # 负载均衡器配置（主动健康检查 + 灰度 LB 注册）
    │   └── constant/
    │       └── InternalSignatureHeaderConstants.java  # 内部签名头常量（X-Internal-Sig）
    └── resources/
        ├── bootstrap.yml                       # Nacos 连接 + 端口（9000）+ 共享配置引入
        ├── routes-nacos.yaml                   # Nacos 动态路由模板（JSON 数组格式，8 条路由）
        ├── config/                             # 环境配置（Nacos DataId）
        │   ├── ydsz-gateway-common.yaml          # 跨环境共享配置（shared-configs 引入）
        │   ├── ydsz-gateway-dev.yaml
        │   ├── ydsz-gateway-sit.yaml
        │   ├── ydsz-gateway-uat.yaml
        │   └── ydsz-gateway-prod.yaml
        └── META-INF/
            ├── additional-spring-configuration-metadata.json  # IDE 配置补全
            ├── spring-configuration-metadata.json
            └── spring/
                └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## 配置项

### 核心配置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `server.port` | 9000 | 网关端口 |
| `ydsz.gateway.internal-secret` | （空） | 内部头签名密钥（HMAC-SHA256，生产必须 >= 32 字节） |
| `ydsz.gateway.websocket.allowed-origins` | （空） | WebSocket Origin 白名单 |
| `ydsz.gateway.websocket.max-connections-per-user` | 5 | 单用户最大 WebSocket 连接数 |
| `ydsz.gateway.websocket.max-connections-per-ip` | 20 | 单 IP 最大 WebSocket 连接数 |
| `ydsz.gateway.websocket.counter-ttl-seconds` | 3600 | WebSocket 连接计数器 TTL（秒） |
| `ydsz.gateway.jwt.cache-ttl-seconds` | 10 | JWT 校验结果本地缓存 TTL（秒） |

### HttpClient 连接池

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.gateway.httpclient.pool.max-connections` | 500 | 最大连接数 |
| `ydsz.gateway.httpclient.pool.pending-acquire-timeout-ms` | 45000 | 获取连接超时（ms） |
| `ydsz.gateway.httpclient.pool.max-idle-time-seconds` | 30 | 最大空闲时间（秒） |
| `ydsz.gateway.httpclient.pool.max-life-time-seconds` | 60 | 最大生命周期（秒） |
| `ydsz.gateway.httpclient.pool.eviction-interval-seconds` | 60 | 驱逐检查间隔（秒） |

> 连接池启用 Reactor Netty Micrometer 指标（`metrics(true)`），指标名前缀 `reactor_netty_connection_provider_*`。

### 限流配置（二维度：IP / 用户）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.gateway.ratelimit.enabled` | true | 限流总开关 |
| `ydsz.gateway.ratelimit.per-ip.enabled` | true | IP 级限流 |
| `ydsz.gateway.ratelimit.per-ip.default-qps` | 30 | IP 默认 QPS |
| `ydsz.gateway.ratelimit.per-ip.burst-capacity` | 60 | IP 突发容量 |
| `ydsz.gateway.ratelimit.per-ip.whitelist` | （空） | IP 白名单（不限流） |
| `ydsz.gateway.ratelimit.per-user.enabled` | true | 用户级限流 |
| `ydsz.gateway.ratelimit.per-user.default-qps` | 50 | 用户默认 QPS |
| `ydsz.gateway.ratelimit.per-user.burst-capacity` | 100 | 用户突发容量 |
| `ydsz.gateway.ratelimit.response-headers.enabled` | true | 限流响应头 |
| `ydsz.gateway.ratelimit.response-headers.retry-after` | 5 | Retry-After 头值（秒） |

> 限流触发时响应头包含：`X-RateLimit-Limit`、`X-RateLimit-Remaining`、`X-RateLimit-Reset`、`Retry-After`、`X-RateLimit-Reset-Time`。

### CORS 配置（prefix `ydsz.gateway.cors`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.gateway.cors.enabled` | true | CORS 过滤器开关 |
| `ydsz.gateway.cors.allowed-origin` | `https://ydsz.example.com` | 单一可信 Origin（生产必须替换） |
| `ydsz.gateway.cors.allowed-methods` | GET,POST,PUT,DELETE,OPTIONS,PATCH | 允许的 HTTP 方法 |
| `ydsz.gateway.cors.allowed-headers` | `*` | 允许的请求头 |
| `ydsz.gateway.cors.exposed-headers` | X-Trace-Id,X-Request-Id,X-RateLimit-*,Retry-After,X-API-Version | 暴露给浏览器的响应头 |
| `ydsz.gateway.cors.allow-credentials` | true | 是否允许携带凭据 |
| `ydsz.gateway.cors.max-age-seconds` | 3600 | 预检请求缓存时间（秒） |

> 凭据模式与通配符互斥校验：`allowCredentials=true` 时 `allowedOrigin` 不能为 `*`，启动时校验失败会阻止启动。

### 安全响应头

> 安全响应头由 `ydsz-common-safe` 模块统一管理（prefix `ydsz.safe.security-headers`），Gateway 不再单独配置。
> 详见 `SecurityHeaderProperties` / `SecurityHeaderConfigurer`。

### IP 访问控制（统一黑白名单，prefix `ydsz.gateway.ip-control`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.gateway.ip-control.whitelist-enabled` | false | 白名单开关 |
| `ydsz.gateway.ip-control.whitelist` | （空） | IP 白名单（逗号分隔，支持 CIDR/单 IP） |
| `ydsz.gateway.ip-control.whitelist-skip-paths` | （空） | 白名单跳过路径 |
| `ydsz.gateway.ip-control.blacklist-enabled` | true | 黑名单开关（支持 Redis 动态更新） |
| `ydsz.gateway.ip-control.blacklist-ttl-seconds` | 10 | 本地缓存 TTL（秒） |
| `ydsz.gateway.ip-control.blacklist-max-size` | 50000 | 本地缓存最大容量 |
| `ydsz.gateway.ip-control.blacklist-fail-mode` | fail-open | Redis 异常时降级策略（fail-open 放行 / fail-closed 拒绝） |

### 动态路由（唯一路由入口）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.gateway.dynamic-routes.enabled` | true | Nacos 动态路由开关（默认启用） |
| `ydsz.gateway.dynamic-routes.data-id` | gateway-routes.json | 路由配置 DataId（JSON 数组格式，Group=当前 profile） |

### 熔断（自研引擎，prefix `ydsz.gateway.circuit-breaker`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.gateway.filter.circuit-breaker` | true | 熔断过滤器开关 |
| `ydsz.gateway.circuit-breaker.failure-rate-threshold` | 50 | 失败率阈值（%） |
| `ydsz.gateway.circuit-breaker.wait-duration-in-open-state-ms` | 10000 | OPEN 状态持续时间（ms） |
| `ydsz.gateway.circuit-breaker.sliding-window-size` | 10 | 滑动窗口大小（次数） |
| `ydsz.gateway.circuit-breaker.minimum-number-of-calls` | 5 | 最少调用次数 |
| `ydsz.gateway.circuit-breaker.permitted-number-of-calls-in-half-open-state` | 2 | HALF_OPEN 状态下允许的探测调用数 |

### 灰度路由

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.gray-loadbalancer.enabled` | true | 灰度负载均衡器开关 |
| `spring.cloud.loadbalancer.configurations` | gray | 激活灰度负载均衡器配置 |

> Nacos 实例 metadata 支持字段：`version`（gray/stable）、`weight`（权重，默认 1）、`grayRatio`（灰度流量比例）。

### 访问日志采样

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.gateway.access-log.sample-rate` | 100 | 采样率（0-100，4xx/5xx 全量） |

### API 版本协商（prefix `ydsz.gateway.api-version`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.gateway.api-version.enabled` | true | 版本协商开关 |
| `ydsz.gateway.api-version.supported-versions` | [v1, v2] | 支持的版本列表 |
| `ydsz.gateway.api-version.default-version` | v2 | 默认版本（Path > Header > Query 优先级） |
| `ydsz.gateway.api-version.deprecated-versions` | （空） | 弃用版本配置（key = 版本号，含 sunset / replacement / message） |
| `ydsz.gateway.api-version.header-negotiation.enabled` | true | Header 协商开关 |
| `ydsz.gateway.api-version.header-negotiation.header-name` | X-API-Version | 版本请求头名称 |
| `ydsz.gateway.api-version.query-negotiation.enabled` | true | Query 协商开关 |
| `ydsz.gateway.api-version.query-negotiation.param-name` | api-version | 版本参数名称 |

> 弃用版本配置示例：
> ```yaml
> ydsz:
>   gateway:
>     api-version:
>       deprecated-versions:
>         v1:
>           sunset: "2026-12-31T23:59:59Z"
>           replacement: /api/v2
>           message: "v1 将于 2026 年底下线，请迁移至 v2"
> ```

### API Key 认证（prefix `ydsz.gateway.api-key`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.gateway.filter.api-key-auth` | false | API Key 过滤器开关 |
| `ydsz.gateway.api-key.enabled` | false | API Key 认证总开关 |
| `ydsz.gateway.api-key.keys` | （空） | 有效 API Key 列表（逗号分隔，SHA-256 摘要存储） |
| `ydsz.gateway.api-key.protected-paths` | /api/project/**,/api/workflow/** | 受保护路径（Ant 风格） |

### 请求体校验（prefix `ydsz.gateway.payload-validation`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.gateway.filter.payload-validation` | true | 请求体校验过滤器开关 |
| `ydsz.gateway.payload-validation.enabled` | true | 请求体校验总开关 |
| `ydsz.gateway.payload-validation.max-body-size-mb` | 10 | 最大请求体大小（MB） |
| `ydsz.gateway.payload-validation.max-json-depth` | 50 | 最大 JSON 嵌套深度 |
| `ydsz.gateway.payload-validation.strict-content-type` | true | 强制校验 Content-Type |

> 网关层仅做传输层防护（大小 + Content-Type + JSON 深度），JSON 深度/内容校验由下游解析器负责。

### 过滤器总开关（prefix `ydsz.gateway.filter`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.gateway.filter.access-log` | true | 访问日志过滤器 |
| `ydsz.gateway.filter.audit-log` | true | 安全审计日志过滤器 |
| `ydsz.gateway.filter.rate-limit` | true | 令牌桶限流过滤器 |
| `ydsz.gateway.filter.circuit-breaker` | true | 熔断过滤器 |
| `ydsz.gateway.filter.gray-loadbalancer` | true | 灰度路由过滤器 |
| `ydsz.gateway.filter.w3c-trace` | true | W3C Trace Context 过滤器 |
| `ydsz.gateway.filter.ip-access-control` | true | IP 黑白名单过滤器 |
| `ydsz.gateway.filter.auth` | true | JWT 鉴权过滤器 |
| `ydsz.gateway.filter.api-key-auth` | false | API Key 认证过滤器 |
| `ydsz.gateway.filter.websocket-auth` | true | WebSocket 鉴权过滤器 |
| `ydsz.gateway.filter.payload-validation` | true | 请求体校验过滤器 |

### 健康探针路径

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.gateway.health-probe.paths` | /actuator/health, /actuator/health/liveness, /actuator/health/readiness, /actuator/info | K8s 健康探针放行路径 |

### Nacos 路由监听线程池

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.thread.pools.nacosRouteListener.core-size` | 1 | 核心线程数 |
| `ydsz.thread.pools.nacosRouteListener.max-size` | 1 | 最大线程数 |
| `ydsz.thread.pools.nacosRouteListener.queue-capacity` | 1024 | 队列容量 |
| `ydsz.thread.pools.nacosRouteListener.thread-name-prefix` | nacos-route-listener- | 线程名前缀 |
| `ydsz.thread.pools.nacosRouteListener.reject-policy` | CALLER_RUNS | 拒绝策略 |

## 过滤器执行顺序

```
  偏移量   过滤器类                               职责
  ──────   ─────────────────────────────────────  ──────────────────────────
  0        W3CTraceContextFilter                  生成 traceparent（最先）
  1        AccessLogGlobalFilter                   结构化访问日志（含采样 + 脱敏）
  3        IpAccessControlFilter                   IP 黑名单 + 白名单
  4        PayloadValidationFilter                 请求体大小 + Content-Type + JSON 深度校验
  8        WebSocketAuthFilter                     WebSocket 独立鉴权
  10       AuthGlobalFilter                        主鉴权 + 内部头注入 + Token 黑名单 + 路径穿越拦截
  15       ApiKeyAuthFilter                        API Key 备选认证（SHA-256 摘要比对）
  20       GrayLoadBalancerRequestFilter           灰度标识注入
  30       RateLimitFilter                        令牌桶限流（Redis + Lua）
  35       AuditLogFilter                          审计日志（双轨制）
  45       CircuitBreakerGlobalFilter              自研引擎熔断
  150      GrayResponseHeaderFilter                灰度路由响应头（X-Gray-Hit）
  200      ApiVersionHeaderFilter                  API 版本响应头（Sunset 头）
  ───      ─────────────────────────────────────  ──────────────────────────
  +100     ReactiveLoadBalancerClientFilter       Spring Cloud 负载均衡（含灰度 LB）
```

> 所有过滤器顺序由 `GatewayFilterOrder` 枚举统一管理，偏移量基于 `Ordered.HIGHEST_PRECEDENCE`。

## 统一错误码

> 完整错误码详见 `GatewayErrorCode` 枚举。结构：HTTP 状态码（3 位）+ 错误分类（2 位）= 5 位业务码。

| 分类 | 错误码范围 | 说明 |
|---|---|---|
| 请求参数错误 | 400xx | `BAD_REQUEST` / `PATH_TRAVERSAL` / `PAYLOAD_TOO_LARGE` / `CONTENT_TYPE_MISSING` / `INVALID_PARAMETER` |
| 认证失败 | 401xx | `UNAUTHORIZED` / `TOKEN_INVALID` / `TOKEN_EXPIRED` / `TOKEN_BLACKLISTED` / `REPLAY_DETECTED` / `API_KEY_MISSING` / `API_KEY_INVALID` |
| 权限不足 | 403xx | `FORBIDDEN` / `IP_FORBIDDEN` / `IP_BLACKLISTED` / `ORIGIN_FORBIDDEN` |
| 路由不存在 | 404xx | `ROUTE_NOT_FOUND` |
| 请求超时 | 408xx | `REQUEST_TIMEOUT` |
| 限流触发 | 429xx | `RATE_LIMITED` / `RATE_LIMITED_IP` / `RATE_LIMITED_USER` |
| 网关内部错误 | 500xx | `INTERNAL_ERROR` |
| 下游服务异常 | 502xx | `BAD_GATEWAY` |
| 熔断/服务不可用 | 503xx | `SERVICE_UNAVAILABLE` / `CIRCUIT_BREAKER_OPEN` |
| 下游响应超时 | 504xx | `GATEWAY_TIMEOUT` |

> 错误响应支持 RFC 7807 ProblemDetail 格式（Accept 协商）和 ydsz 标准 JSON 双轨输出，含 RFC 5988 Link 帮助文档头。

## Prometheus 指标

| 指标名 | 类型 | 说明 |
|---|---|---|
| `ydsz_gateway_request_duration_seconds` | Timer | 按路由分桶的请求延迟（route/method/status 标签） |
| `ydsz_gateway_request_total` | Counter | 请求总数计数器（route/method/status 标签） |
| `ydsz_gateway_gray_hit_total` | Counter | 灰度路由命中计数（gray 标签） |
| `ydsz_gateway_ratelimit_triggered_total` | Counter | 限流触发计数（dimension/route 标签） |
| `ydsz_gateway_ratelimit_fallback_total` | Counter | 限流降级放行计数 |
| `ydsz_gateway_ratelimit_fallback_quota` | Gauge | 本地兜底令牌桶自适应配额 |
| `ydsz_gateway_jwt_validation_duration_seconds` | Timer | JWT 校验耗时（cached 标签） |
| `ydsz_gateway_jwt_cache_hit_rate` | Gauge | JWT 缓存命中计数 |
| `ydsz_gateway_jwt_cache_miss_total` | Gauge | JWT 缓存未命中计数 |
| `ydsz_gateway_circuit_breaker_state` | Gauge | 熔断器状态（0=closed, 1=open, 2=half-open） |
| `reactor_netty_connection_provider_*` | Gauge | Reactor Netty 连接池指标（官方 Micrometer） |

## 启动

### 本地启动（前提：基础设施已启动）

```bash
# 1. 确保 Nacos 已启动
curl http://127.0.0.1:8848/nacos/actuator/health

# 2. 编译公共模块（首次）
cd ydsz-cloud
mvn -pl ydsz-common -am install -DskipTests

# 3. 启动 gateway
mvn -pl ydsz-gateway spring-boot:run
```

### 一键启动（推荐）

```bash
# Linux / macOS
./deploy/ubuntu/scripts/start-all.sh

# Windows
.\deploy\windows\scripts\start-all.bat
```

### 验证

```bash
# 健康检查
curl http://localhost:9000/actuator/health

# Prometheus 指标
curl http://localhost:9000/actuator/prometheus

# 路由到 userinfo（需要先启动 userinfo）
curl http://localhost:9000/api/v1/user/actuator/health
```

## 测试

> 当前模块**暂无测试用例**（无 `src/test` 目录）。POM 已声明测试依赖：
> - `spring-boot-starter-test` — 单元测试框架
> - `testcontainers` + `junit-jupiter` — 集成测试 + 混沌测试
>
> 核心过滤器（AuthGlobalFilter / RateLimitFilter / GrayLoadBalancer / CircuitBreakerGlobalFilter）建议后续补齐测试覆盖。

## 路由配置参考

> Nacos DataId: `gateway-routes.json`（JSON 数组格式），Group = 当前 profile。

| 路由 ID | 服务 | 路径前缀 |
|---|---|---|
| `ydsz-userinfo` | 用户服务 | `/api/v1/auth/**`, `/api/v1/user/**`, `/api/v1/company/**`, `/api/v1/dept/**`, `/api/v1/menu/**`, `/api/v1/post/**`, `/api/v1/role/**`, `/api/v1/language/**`, `/api/v1/oauth2/**`, `/api/v1/userinfo/**`, `/api/internal/**`, `/feign/**` |
| `ydsz-workflow` | 工作流服务 | `/api/v1/workflow/**` |
| `ydsz-system` | 系统服务 | `/api/v1/config/**`, `/api/v1/dict/**`, `/api/v1/app/**`, `/api/v1/variable/**`, `/api/v1/system/**`, `/api/v1/search/**` |
| `ydsz-message` | 消息服务 | `/api/v1/message/**` |
| `ydsz-cronjob` | 定时任务服务 | `/api/v1/cronjob/**` |
| `ydsz-literule` | 规则引擎服务 | `/api/v1/literule/**` |
| `ydsz-agent` | Agent 服务 | `/api/v1/agent/**`（响应超时 120s） |
| `ydsz-nextwiki` | 知识库服务 | `/api/v1/nextwiki/**` |

## 常见问题

### Q1：Gateway 启动报 "Unable to find GatewayFilterFactory with name ..."

某个自定义 Filter 未声明。检查 `filter/` 目录下的 Filter 类是否带 `@Component` 或在 `GatewayRouteConfig` 中注册。

### Q2：跨域 CORS 报错

dev 环境默认允许 `http://localhost:5173`（单一来源），生产必须通过 Nacos 配置 `ydsz.gateway.cors.allowed-origin`。注意：启动时会拒绝"凭据 + 通配符"组合配置。

### Q3：灰度路由不生效

1. Nacos 实例 metadata 必须有 `version: gray`
2. 请求头带 `X-Gray-Tag: gray`
3. 确认 `ydsz.gray-loadbalancer.enabled=true` 且 `spring.cloud.loadbalancer.configurations=gray`

### Q4：Token 黑名单不生效

1. 检查 Redis 是否正常连接（黑名单依赖 Redis）
2. 检查 `AuthGlobalFilter` 是否正确注入了 `ReactiveTokenBlacklistService`
3. Redis 不可用时按「放行」处理（fail-open），已登出 Token 在此期间可能仍然有效

### Q5：API 版本协商返回 404

1. 检查 `ydsz.gateway.api-version.enabled=true`
2. 检查请求路径是否包含版本前缀（如 `/api/v2/...`）
3. 检查 `X-API-Version` 头是否在 `supported-versions` 列表中

### Q6：内部头签名校验失败

1. 确认 `ydsz.gateway.internal-sign-secret` 已配置且长度 >= 32 字节
2. 确认下游服务使用相同密钥验证 `X-Internal-Sig` 头
3. 签名 payload 格式：`traceId|userId|username|roles|permissions`

---

> 任何路由变更必须同步更新前端 `vite.config.ts` 的 proxy 配置。
