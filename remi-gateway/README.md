# remi-gateway

> API 网关（Spring Cloud Gateway）

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9000**（按构建顺序 1/10） |
| **服务名** | `remi-gateway` |
| **构建顺序** | 1/10（Maven 构建第一个部署单元） |
| **Nacos 注册** | ✅ 是（注册中心 + 配置中心） |
| **数据库** | ❌ 不直接访问 |
| **作用** | 统一入口、路由分发、限流/熔断、CORS、认证、Sentinel、灰度 |

## 核心职责

1. **路由分发**：根据 `path` 转发到对应微服务（基于 Nacos 服务发现）
2. **鉴权拦截**：解析 JWT、转发 `X-User-Id` / `X-Tenant-Id` / `X-Trace-Id` 内部头
3. **限流/熔断**：Sentinel Dashboard 对接（端口 8719）
4. **CORS**：按环境白名单放行（生产必须显式域名）
5. **IP 白名单**：`remi.security.ip-whitelist` 可配置
6. **灰度路由**：基于 `X-Gray-Tag` 头 + Nacos `metadata.version` 元数据
7. **WebSocket**：转发到 `message` 服务的通知推送通道

## 数据库表设计

本模块为**纯路由网关**，**不直接访问任何业务数据库**，仅作为流量入口与横切关注点（限流/熔断/灰度）的执行点。

- ✅ 注册中心：Nacos（仅做服务发现 + 配置中心）
- ✅ 缓存：Redis（限流计数 / IP 白名单缓存）
- ❌ 业务 DB：**不持有任何 `remi_*` 表**
- ❌ 业务实体：模块内**不定义 `*DO.java`**，所有业务数据均通过路由转发到下游服务（userinfo / system / project / message / workflow / cronjob / agent）

> **设计原则**：
> - 网关注入业务表会带来分布式事务与数据一致性风险，违反"网关无状态"约束；
> - 所有审计 / 操作日志下沉到 `remi-system` 的 `remi_operation_log`（由下游业务服务经 Feign 写入）；
> - 限流统计写入 Redis（`remi:gateway:ratelimit:*`），不落库；
> - 灰度标签仅作为请求头/Metadata 透传，不持久化。

> **架构约束**：`remi-gateway` 为 reactive 栈（WebFlux），**不依赖** `common-web`（servlet 栈），只挑选 `common-core` / `common-exception` / `common-auth` 三个细粒度子模块。

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
agent (9008) ────┤
project (9009) ──┘
```

## 目录结构

```
remi-gateway/
├── pom.xml
└── src/main/
    ├── java/com/remisoft/gateway/
    │   ├── GatewayApplication.java              # 启动类 + @EnableConfigurationProperties
    │   ├── config/
    │   │   ├── GatewayConstants.java            # 内部头常量
    │   │   ├── GatewayErrorConfig.java          # 错误响应配置
    │   │   ├── GatewayHealthIndicator.java     # P3-1 网关健康指标
    │   │   ├── GatewayHttpClientConfig.java     # HttpClient 连接池配置
    │   │   ├── GatewayIpUtils.java              # IP 工具类（可信代理链）
    │   │   ├── GatewayMetrics.java             # P2-3 Prometheus 指标
    │   │   ├── GatewaySentinelConfig.java       # Sentinel 限流/熔断响应
    │   │   ├── GatewaySentinelRulesConfig.java  # Sentinel 规则配置
    │   │   ├── IpWhitelistProperties.java       # IP 白名单配置属性
    │   │   ├── InternalHeaderSigner.java       # 内部头 HMAC 签名
    │   │   ├── NacosRouteConfig.java           # Nacos 动态路由配置
    │   │   ├── NacosRouteDefinitionRepository.java  # Nacos 路由仓库
    │   │   ├── PathGuard.java                  # 路径安全防护
    │   │   ├── RateLimitProperties.java         # 限流配置属性
    │   │   ├── RouteConfig.java                # Java 路由配置
    │   │   ├── SecurityHeadersProperties.java   # 安全响应头配置属性
    │   │   └── SentinelApiLimitConfig.java      # API 级限流规则
    │   ├── filter/
    │   │   ├── AccessLogGlobalFilter.java       # 访问日志 + 结构化 JSON
    │   │   ├── ApiKeyAuthFilter.java           # P1-3 API Key 认证
    │   │   ├── AuthGlobalFilter.java           # JWT 解析 + 内部头注入
    │   │   ├── GrayLoadBalancerRequestFilter.java  # 灰度路由请求过滤器
    │   │   ├── IpBlacklistFilter.java          # IP 黑名单
    │   │   ├── IpWhitelistFilter.java          # IP 白名单
    │   │   ├── PayloadValidationFilter.java    # P1-8 请求体安全校验
    │   │   ├── RateLimitFilter.java            # Redis 令牌桶多维度限流
    │   │   ├── W3CTraceContextFilter.java      # W3C 链路追踪
    │   │   └── WebSocketAuthFilter.java        # WebSocket 认证
    │   └── loadbalancer/
    │       ├── GrayLoadBalancer.java           # P3-5 加权轮询灰度负载均衡器
    │       └── GrayLoadBalancerConfig.java     # 负载均衡器配置
    └── resources/
        ├── bootstrap.yml                       # Nacos 连接 + 端口（9000）
        ├── config/                             # 环境配置（Nacos DataId）
        │   ├── remi-gateway-dev.yaml
        │   ├── remi-gateway-sit.yaml
        │   ├── remi-gateway-uat.yaml
        │   └── remi-gateway-prod.yaml
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
| `remi.gateway.internal-sign-secret` | （空） | 内部头签名密钥（HMAC-SHA256） |
| `remi.gateway.websocket.allowed-origins` | （空） | WebSocket Origin 白名单 |
| `remi.gateway.health-probe.paths` | /actuator/** | K8s 健康探针放行路径 |

### HttpClient 连接池

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `remi.gateway.httpclient.pool.max-connections` | 500 | 最大连接数 |
| `remi.gateway.httpclient.pool.pending-acquire-timeout-ms` | 45000 | 获取连接超时（ms） |
| `remi.gateway.httpclient.pool.max-idle-time-seconds` | 30 | 最大空闲时间（秒） |
| `remi.gateway.httpclient.pool.max-life-time-seconds` | 60 | 最大生命周期（秒） |
| `remi.gateway.httpclient.pool.eviction-interval-seconds` | 60 | 驱逐检查间隔（秒） |

### 限流配置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `remi.gateway.ratelimit.enabled` | true | 限流总开关 |
| `remi.gateway.ratelimit.per-user.enabled` | true | 用户级限流 |
| `remi.gateway.ratelimit.per-user.default-qps` | 50 | 用户默认 QPS |
| `remi.gateway.ratelimit.per-user.burst-capacity` | 100 | 用户突发容量 |
| `remi.gateway.ratelimit.per-ip.enabled` | true | IP 级限流 |
| `remi.gateway.ratelimit.per-ip.default-qps` | 30 | IP 默认 QPS |
| `remi.gateway.ratelimit.per-ip.burst-capacity` | 60 | IP 突发容量 |
| `remi.gateway.ratelimit.per-tenant.enabled` | false | 租户级限流 |
| `remi.gateway.ratelimit.response-headers.enabled` | true | 限流响应头 |
| `remi.gateway.ratelimit.response-headers.retry-after` | 5 | Retry-After 值 |

### 安全响应头

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `remi.gateway.security-headers.enabled` | true | 安全头总开关 |
| `remi.gateway.security-headers.csp.enabled` | true | CSP 响应头 |
| `remi.gateway.security-headers.hsts.enabled` | true | HSTS 响应头 |
| `remi.gateway.security-headers.hsts.max-age` | 31536000 | HSTS max-age |
| `remi.gateway.security-headers.coop.enabled` | true | COOP 响应头 |
| `remi.gateway.security-headers.coep.enabled` | true | COEP 响应头 |
| `remi.gateway.security-headers.corp.enabled` | true | CORP 响应头 |

### IP 白名单

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `remi.security.ip-whitelist` | （空） | IP 白名单（CIDR/单 IP） |
| `remi.security.ip-whitelist-enabled` | false | 白名单开关 |
| `remi.security.ip-whitelist-skip-paths` | （空） | 白名单跳过路径 |

### 动态路由

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `remi.gateway.dynamic-routes.enabled` | false | Nacos 动态路由开关 |
| `remi.gateway.dynamic-routes.data-id` | gateway-routes.json | 路由配置 DataId |

### Sentinel

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `remi.gateway.sentinel.nacos-datasource-enabled` | false | Nacos 数据源 |
| `remi.gateway.sentinel.degrade-rule-data-id` | remi-gateway-sentinel-degrade.json | 熔断规则 DataId |
| `remi.gateway.sentinel.system-rule-data-id` | remi-gateway-sentinel-system.json | 系统规则 DataId |
| `remi.gateway.sentinel.api-limits.enabled` | true | API 级限流规则 |

### API Key 认证

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `remi.gateway.api-key.enabled` | false | API Key 认证开关 |
| `remi.gateway.api-key.keys` | （空） | 有效 API Key 列表 |
| `remi.gateway.api-key.protected-paths` | /api/project/**,/api/workflow/** | 受保护路径 |

### 请求体校验

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `remi.gateway.payload-validation.enabled` | true | 请求体校验开关 |
| `remi.gateway.payload-validation.max-body-size-mb` | 10 | 最大请求体大小（MB） |
| `remi.gateway.payload-validation.max-json-depth` | 50 | JSON 最大嵌套深度 |
| `remi.gateway.payload-validation.strict-content-type` | true | 强制校验 Content-Type |

## 启动

### 本地启动（前提：基础设施已启动）

```bash
# 1. 确保 Nacos 已启动
curl http://127.0.0.1:8848/nacos/actuator/health

# 2. 编译公共模块（首次）
cd remi-cloud
mvn -pl remi-common -am install -DskipTests

# 3. 启动 gateway
mvn -pl remi-gateway spring-boot:run
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
curl http://localhost:9000/remi-userinfo/actuator/health
```

## 测试

```bash
# 仅测试 gateway
mvn -pl remi-gateway -am test

# 集成测试（需基础设施）
mvn -pl remi-gateway -am verify
```

## 常见问题

### Q1：Gateway 启动报 "Unable to find GatewayFilterFactory with name ..."

某个自定义 Filter 未声明。检查 `filter/` 目录下的 Filter 类是否带 `@Component` 或在 `RouteConfig` 中注册。

### Q2：跨域 CORS 报错

dev 环境默认 `*`，但生产必须设置 `CORS_ALLOWED_ORIGINS=https://example.com,https://*.example.com`。

### Q3：灰度路由不生效

Nacos 实例 metadata 必须有 `version: gray`，且请求头带 `X-Gray-Tag: gray`。

---

> 任何路由变更必须同步更新前端 `vite.config.ts` 的 proxy 配置。
