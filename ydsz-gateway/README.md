# ydsz-gateway

> API 网关（Spring Cloud Gateway）

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9000**（按构建顺序 1/10） |
| **服务名** | `ydsz-gateway` |
| **构建顺序** | 1/10（Maven 构建第一个部署单元） |
| **Nacos 注册** | ✅ 是（注册中心 + 配置中心） |
| **数据库** | ❌ 不直接访问 |
| **作用** | 统一入口、路由分发、限流、CORS、认证、灰度、API 版本协商 |

## 核心职责

1. **路由分发**：Nacos 动态路由为唯一入口（`gateway-routes.json` JSON 数组格式）
2. **鉴权拦截**：解析 JWT（Caffeine 防击穿缓存 + 自适应 TTL，验签切出事件循环）、转发 `X-User-Id` / `X-Tenant-Id` / `X-Trace-Id` 内部头
3. **限流**：Redis + Lua 令牌桶二维限流（IP / 用户）
4. **熔断**：Resilience4j 按路由隔离熔断（防下游雪崩）
5. **CORS**：按环境白名单放行（生产必须显式域名）
6. **IP 访问控制**：`ydsz.gateway.ip-control.*` 统一黑白名单（Redis 动态黑名单）
7. **灰度路由**：基于 `X-Gray-Tag` 头 + Nacos `metadata.version` 元数据 + `weight` 权重加权随机 + `grayRatio` 比例分流
8. **WebSocket**：握手认证 + Origin 校验 + 连接数限制（`/ws` 前缀），实际转发路由需自行配置
9. **API 版本协商**：`X-API-Version` 头（Path > Header > Query 优先级）

## 数据库表设计

本模块为**纯路由网关**，**不直接访问任何业务数据库**，仅作为流量入口与横切关注点（限流/熔断/灰度）的执行点。

- ✅ 注册中心：Nacos（仅做服务发现 + 配置中心）
- ✅ 缓存：Redis（限流计数 / IP 白名单缓存）
- ❌ 业务 DB：**不持有任何 `ydsz_*` 表**
- ❌ 业务实体：模块内**不定义 `*DO.java`**，所有业务数据均通过路由转发到下游服务（userinfo / system / nextwiki / message / workflow / cronjob / literule / agent）

> **设计原则**：
> - 网关注入业务表会带来分布式事务与数据一致性风险，违反"网关无状态"约束；
> - 所有审计 / 操作日志下沉到 `ydsz-system` 的 `ydsz_operation_log`（由下游业务服务经 Feign 写入）；
> - 限流统计写入 Redis（`ydsz:gateway:ratelimit:*`），不落库；
> - 灰度标签仅作为请求头/Metadata 透传，不持久化。

> **架构约束**：`ydsz-gateway` 为 reactive 栈（WebFlux），**不依赖** `common-web`（servlet 栈），按需挑选 11 个细粒度子模块（core / util / base / exception / auth / safe / cache / sentry / notify / audit / thread）。

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
    │   ├── GatewayApplication.java              # 启动类 + @EnableConfigurationProperties
    │   ├── config/
    │   │   ├── GatewayConstants.java            # 内部头常量
    │   │   ├── GatewayFilterConfig.java         # 过滤器配置 + 错误响应（GatewayExceptionHandler）
    │   │   ├── GatewayErrorWriter.java          # 统一错误响应写出器（bizCode + ProblemDetail + traceId）
    │   │   ├── GatewayHealthIndicator.java      # 网关健康指标
    │   │   ├── GatewayHttpClientConfig.java     # HttpClient 连接池配置（真实连接池指标）
    │   │   ├── GatewayIpUtils.java              # IP 工具类（可信代理链）
    │   │   ├── GatewayMetrics.java              # Prometheus 指标
    │   │   ├── GatewayRouteConfig.java          # Nacos 动态路由装配
    │   │   ├── InternalHeaderSigner.java        # 内部头 HMAC 签名
    │   │   ├── NacosRouteDefinitionRepository.java  # Nacos 路由仓库（gateway-routes.json 模板）
    │   │   ├── PathGuard.java                   # 路径安全防护（双层检测）
    │   │   ├── RateLimitProperties.java         # 限流配置属性（per-ip/user）
    │   │   ├── IpAccessControlProperties.java   # IP 黑白名单配置（prefix ydsz.gateway.ip-control）
    │   │   ├── ApiVersionProperties.java        # API 版本协商配置（prefix ydsz.gateway.api-version）
    │   │   ├── CorsProperties.java              # CORS 配置
    │   │   ├── WebSocketConnectionLimiter.java  # WebSocket 连接数限制器（Redis 原子计数）
    │   │   └── CachedJwtValidator.java          # JWT 校验缓存（防击穿/穿透 + 自适应 TTL）
    │   ├── filter/
    │   │   ├── AccessLogGlobalFilter.java       # 访问日志（JSON 转义 + 采样）
    │   │   ├── ApiKeyAuthFilter.java            # API Key 认证（SHA-256 摘要比对）
    │   │   ├── AuthGlobalFilter.java            # JWT 解析 + 内部头注入（验签切出事件循环）
    │   │   ├── CircuitBreakerGlobalFilter.java  # 熔断（Resilience4j，按路由隔离）
    │   │   ├── GrayLoadBalancerRequestFilter.java  # 灰度路由请求过滤器
    │   │   ├── GrayResponseHeaderFilter.java    # 灰度路由响应头（X-Gray-Hit）
    │   │   ├── IpAccessControlFilter.java       # IP 黑白名单统一过滤（Redis 动态黑名单）
    │   │   ├── PayloadValidationFilter.java     # 请求体安全校验（大小 + Content-Type）
    │   │   ├── RateLimitFilter.java             # Redis 令牌桶二维限流（IP/用户）
    │   │   ├── W3CTraceContextFilter.java       # W3C 链路追踪
    │   │   ├── WebSocketAuthFilter.java         # WebSocket 握手认证
    │   │   ├── AuditLogFilter.java              # 审计日志（桥接 sys_audit_log）
    │   │   └── ApiVersionHeaderFilter.java      # API 版本协商（X-API-Version / Sunset 头）
    │   ├── loadbalancer/
    │   │   ├── GrayLoadBalancer.java            # 权重加权随机 + 比例分流 + Alias Method O(1)
    │   │   └── GrayLoadBalancerConfig.java      # 负载均衡器配置（主动健康检查）
    │   └── constant/
    │       └── InternalSignatureHeaderConstants.java
    └── resources/
        ├── bootstrap.yml                       # Nacos 连接 + 端口（9000）
        ├── routes-nacos.yaml                   # Nacos 动态路由模板（JSON 数组格式，8 条）
        ├── config/                             # 环境配置（Nacos DataId）
        │   ├── ydsz-gateway-common.yaml          # 跨环境共享配置（shared-configs 引入）
        │   ├── ydsz-gateway-dev.yaml
        │   ├── ydsz-gateway-sit.yaml
        │   ├── ydsz-gateway-uat.yaml
        │   └── ydsz-gateway-prod.yaml
        └── META-INF/
            ├── additional-spring-configuration-metadata.json  # IDE 配置补全
            └── spring/
                └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

## 配置项

### 核心配置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `server.port` | 9000 | 网关端口 |
| `ydsz.gateway.internal-sign-secret` | （空） | 内部头签名密钥（HMAC-SHA256） |
| `ydsz.gateway.websocket.allowed-origins` | （空） | WebSocket Origin 白名单 |

### HttpClient 连接池

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.gateway.httpclient.pool.max-connections` | 500 | 最大连接数 |
| `ydsz.gateway.httpclient.pool.pending-acquire-timeout-ms` | 45000 | 获取连接超时（ms） |
| `ydsz.gateway.httpclient.pool.max-idle-time-seconds` | 30 | 最大空闲时间（秒） |
| `ydsz.gateway.httpclient.pool.max-life-time-seconds` | 60 | 最大生命周期（秒） |
| `ydsz.gateway.httpclient.pool.eviction-interval-seconds` | 60 | 驱逐检查间隔（秒） |

### 限流配置（二维度：IP / 用户）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.gateway.ratelimit.enabled` | true | 限流总开关 |
| `ydsz.gateway.ratelimit.per-ip.enabled` | true | IP 级限流 |
| `ydsz.gateway.ratelimit.per-ip.default-qps` | 30 | IP 默认 QPS |
| `ydsz.gateway.ratelimit.per-ip.burst-capacity` | 60 | IP 突发容量 |
| `ydsz.gateway.ratelimit.per-user.enabled` | true | 用户级限流 |
| `ydsz.gateway.ratelimit.per-user.default-qps` | 50 | 用户默认 QPS |
| `ydsz.gateway.ratelimit.per-user.burst-capacity` | 100 | 用户突发容量 |
| `ydsz.gateway.ratelimit.response-headers.enabled` | true | 限流响应头 |

### 安全响应头

> 安全响应头由 `common-safe` 模块统一管理（prefix `ydsz.safe.security-headers`），Gateway 不再单独配置。
> 详见 `SecurityHeaderProperties` / `SecurityHeaderConfigurer`。

### IP 访问控制（统一黑白名单，prefix `ydsz.gateway.ip-control`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.gateway.ip-control.whitelist-enabled` | false | 白名单开关 |
| `ydsz.gateway.ip-control.whitelist` | （空） | IP 白名单（CIDR/单 IP） |
| `ydsz.gateway.ip-control.whitelist-skip-paths` | （空） | 白名单跳过路径 |
| `ydsz.gateway.ip-control.blacklist-enabled` | false | 黑名单开关（支持 Redis 动态更新） |

### 动态路由（唯一路由入口）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.gateway.dynamic-routes.enabled` | true | Nacos 动态路由开关（默认启用） |
| `ydsz.gateway.dynamic-routes.data-id` | gateway-routes.json | 路由配置 DataId（JSON 数组格式，Group=当前 profile） |

### 熔断（Resilience4j，prefix `ydsz.gateway.circuit-breaker`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.gateway.filter.circuit-breaker` | true | 熔断过滤器开关 |
| `ydsz.gateway.circuit-breaker.failure-rate-threshold` | 50 | 失败率阈值（%） |
| `ydsz.gateway.circuit-breaker.wait-duration-in-open-state-ms` | 10000 | OPEN 状态持续时间（ms） |
| `ydsz.gateway.circuit-breaker.sliding-window-size` | 10 | 滑动窗口大小（次数） |
| `ydsz.gateway.circuit-breaker.minimum-number-of-calls` | 5 | 最少调用次数 |

### 访问日志采样

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.gateway.access-log.sample-rate` | 100 | 采样率（0-100，4xx/5xx 全量） |

### API 版本协商（prefix `ydsz.gateway.api-version`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.gateway.api-version.enabled` | true | 版本协商开关 |
| `ydsz.gateway.api-version.default-version` | v2 | 默认版本（Path > Header > Query 优先级） |

### API Key 认证

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.gateway.api-key-auth.enabled` | false | API Key 认证开关 |
| `ydsz.gateway.api-key-auth.keys` | （空） | 有效 API Key 列表 |
| `ydsz.gateway.api-key-auth.protected-paths` | （按需配置） | 受保护路径 |

### 请求体校验

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.gateway.payload-validation.enabled` | true | 请求体校验开关 |
| `ydsz.gateway.payload-validation.max-body-size-mb` | 10 | 最大请求体大小（MB） |
| `ydsz.gateway.payload-validation.strict-content-type` | true | 强制校验 Content-Type |

> 网关层仅做传输层防护（大小 + Content-Type），JSON 深度/内容校验由下游解析器负责。

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

# 路由到 userinfo（需要先启动 userinfo）
curl http://localhost:9000/ydsz-userinfo/actuator/health
```

## 测试

> 当前模块**暂无单元测试**（无 `src/test` 目录）。核心过滤器（AuthGlobalFilter / RateLimitFilter / GrayLoadBalancer）建议后续补齐测试覆盖。

## 常见问题

### Q1：Gateway 启动报 "Unable to find GatewayFilterFactory with name ..."

某个自定义 Filter 未声明。检查 `filter/` 目录下的 Filter 类是否带 `@Component` 或在 `RouteConfig` 中注册。

### Q2：跨域 CORS 报错

dev 环境默认允许 `http://localhost:5173`（单一来源），生产必须显式设置 `CORS_ALLOWED_ORIGINS`。注意：启动时会拒绝"凭据 + 通配符"组合配置。

### Q3：灰度路由不生效

Nacos 实例 metadata 必须有 `version: gray`，且请求头带 `X-Gray-Tag: gray`。

---

> 任何路由变更必须同步更新前端 `vite.config.ts` 的 proxy 配置。
