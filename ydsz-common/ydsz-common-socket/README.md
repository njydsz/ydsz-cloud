# ydsz-common-socket

> WebSocket 实时推送公共模块（L5 业务服务层）

提供 WebSocket 集群广播（Redis Pub/Sub）、离线消息补偿、在线用户管理、JWT 握手鉴权、消息限流、实时推送模板、熔断降级、心跳保活、消息重试与死信队列、审计日志、分布式 traceId 跨节点传播、Micrometer 指标采集与 Actuator 健康检查等开箱即用能力，是所有业务模块实时推送的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供 WebSocket STOMP 端点、集群广播、离线消息、限流、熔断、重试、心跳保活、健康检查等能力 |
| **依赖** | common-core、common-exception、common-json、common-auth（可选）；可选依赖 spring-boot-actuator、micrometer-core、spring-data-redis、common-redis |
| **版本** | 1.0.0 |

## 核心能力

### 1. WebSocket 自动配置与端点

| 类 | 说明 |
|---|---|
| `WebSocketAutoConfiguration` | 主自动配置类，`@EnableScheduling` 开启心跳与重试刷新定时任务，按依赖顺序注册全部 Bean |
| `WebSocketClusterAutoConfiguration` | 集群自动配置类，注册 `WebSocketClusterPublisher` / `WebSocketClusterSubscriber` / `RedisMessageListenerContainer` |
| `WebSocketConfigurer` | 实现 `WebSocketMessageBrokerConfigurer`，注册 STOMP 端点、SimpleBroker（`/topic` / `/queue`）、应用前缀 `/app`、消息大小限制、发送超时、入站通道拦截器 |
| `WebSocketProperties` | 配置属性（`ydsz.websocket.*`），含端点、心跳、消息大小、集群、离线、限流、熔断、重试、连接限制 9 个子配置 |
| `WebSocketConstants` | 常量定义（Redis key 前缀、推送类型等） |

### 2. 集群广播（Redis Pub/Sub）

| 类 | 说明 |
|---|---|
| `WebSocketClusterPublisher` | 集群消息发布者，将推送指令发布到 Redis Channel；注入 traceId；熔断保护下失败返回 false 触发降级 |
| `WebSocketClusterSubscriber` | 集群消息订阅者，订阅 Redis Channel；恢复 MDC traceId；通过 `SimpMessagingTemplate` 推送到本地 session |
| `WebSocketClusterMessage` | 集群消息实体（pushType / userId / topic / type / payloadJson / traceId / tags / priority）；提供 `forUser` / `forBroadcast` / `forTopic` 静态工厂 |

### 3. 在线用户管理

| 类 | 说明 |
|---|---|
| `OnlineUserService` | 基于 Redis Hash（`ydsz:ws:online:{userId}`，field=sessionId，value=时间戳）的在线状态管理；多端 session 共存；TTL 默认 1h 由心跳续期；Redis 不可用时降级为 no-op |
| `WebSocketSessionEventListener` | Session 事件监听器（上下线、离线补偿、心跳注册、自定义 `WebSocketConnectionListener` 回调） |
| `MultiDevicePolicy` | 多端登录策略枚举（ALLOW_ALL / MUTEX / NEW_REPLACE_OLD） |
| `WebSocketConnectionListener` | 连接生命周期监听器 SPI（详见 SPI 扩展点章节） |

> **多端策略说明**：`WebSocketSessionEventListener.enforceMultiDevicePolicy` 已实现 `ALLOW_ALL` / `MUTEX` / `NEW_REPLACE_OLD` 三种策略，通过 `ydsz.websocket.multi-device.*` 配置（policy 默认 `ALLOW_ALL`、max-sessions-per-user 默认 5）控制，无需业务方自实现。

### 4. 离线消息

| 类 | 说明 |
|---|---|
| `OfflineMessageStore` | 离线消息存储接口（cacheOffline / drainOffline / countOffline） |
| `RedisOfflineMessageStore` | 默认实现，Redis List + TTL + 熔断保护；超出 maxCache 时自动丢弃最旧消息；Redis 不可用时降级为 no-op |

### 5. 实时推送模板

| 类 | 说明 |
|---|---|
| `RealtimePushTemplate` | 统一推送接口：单播（带/不带优先级）、广播、主题推送、带 TTL 推送、离线补偿推送、刷新重试队列 |
| `DefaultRealtimePushTemplate` | 默认实现，推送流程：过滤器链 → 序列化 → 注入 traceId → 集群广播 → 失败降级本地推送 → 本地失败入重试队列 → 审计 + 指标 |

### 6. 安全与限流

| 类 | 说明 |
|---|---|
| `WebSocketAuthInterceptor` | JWT 握手鉴权拦截器（依赖 `TokenService`），含连接数限制 + 审计日志 |
| `WebSocketRateLimiter` | 消息速率限制器（每用户/每 IP 每分钟），基于 Redis Lua 脚本实现滑动窗口限流 + 熔断保护；Redis 不可用时降级为 no-op |
| `ConnectionLimiter` | 连接数限制器（全局 max + 每用户 max），基于 `OnlineUserService` 与 active 计数器 |

### 7. 消息可靠性

| 类 | 说明 |
|---|---|
| `MessageRetryQueue` | 消息重试队列接口（enqueue / dequeueExpired / markSuccess / markFailed / getPendingCount） |
| `RedisMessageRetryQueue` | Redis Sorted Set 实现的重试队列；支持指数退避策略；Redis 不可用时降级为 no-op |
| `DeadLetterQueue` | 死信队列接口（enqueue / list / count） |
| `RedisDeadLetterQueue` | Redis List 死信队列实现 |
| `RetryableMessage` | 可重试消息实体（messageId / userId / type / payload / retryCount / nextRetryAt） |

### 8. 熔断降级

| 类 | 说明 |
|---|---|
| `WebSocketCircuitBreaker` | 轻量级熔断器，基于 `AtomicReference` + CAS 线程安全；状态机 CLOSED → OPEN → HALF_OPEN；滑动窗口失败率统计；保护集群广播、限流器、离线消息存储 |

### 9. 心跳保活

| 类 | 说明 |
|---|---|
| `WebSocketHeartbeatHandler` | `@Scheduled` 定时清理僵尸 Session（超过 `staleSessionTimeout` 未收到心跳则下线）；Redis Sorted Set 存储 `userId:sessionId` 格式，集群级一致 |

### 10. 消息处理

| 类 | 说明 |
|---|---|
| `MessageSerializer` | 消息序列化器接口（SPI 扩展点） |
| `JsonMessageSerializer` | 默认 JSON 序列化器实现 |
| `MessageFilter` | 消息过滤器接口（SPI 扩展点），任一 Filter 返回 false 则跳过推送 |
| `MessagePriority` | 消息优先级枚举（URGENT=1 / HIGH=2 / NORMAL=3 / LOW=4） |
| `StompMessageInterceptor` | STOMP 入站通道拦截器，CONNECT 注入 traceId + SEND 限流 + 审计 |

> **消息压缩建议**：推荐使用 WebSocket 协议层 permessage-deflate（RFC 7692）压缩，无需应用层 GZIP+Base64 编码。可在 `WebSocketConfigurer` 中通过 `setAllowedNativeHeaders` 或在反向代理层启用。

### 11. 可观测性

| 类 | 说明 |
|---|---|
| `WebSocketMetrics` | Micrometer 指标采集（推送次数/耗时，按 type 与 result 分组）；MeterRegistry 不存在时降级为 no-op |
| `WebSocketHealthIndicator` | Actuator 健康检查（详见健康检查章节） |
| `WebSocketAuditService` | 审计日志服务，专用 Logger `WS_AUDIT`，同步输出结构化审计日志 |
| `WebSocketTraceContext` | 链路追踪辅助工具，MDC traceId 跨节点传播 |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-socket</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  websocket:
    enabled: true
    endpoint: /ws
    allowed-origin-patterns: ["*"]
    sock-js-enabled: true
    heartbeat:
      server-interval: 10000
      client-interval: 10000
      stale-session-timeout: 60000
    cluster:
      enabled: true
      channel: ydsz:ws:cluster:push
    offline:
      enabled: true
      max-cache: 100
      ttl: 30d
```

### 3. 业务服务调用推送模板

```java
import com.njydsz.common.socket.push.RealtimePushTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final RealtimePushTemplate pushTemplate;

    public NotificationService(RealtimePushTemplate pushTemplate) {
        this.pushTemplate = pushTemplate;
    }

    public void notifyUser(String userId, String content) {
        // 用户在线 → 集群广播推送；离线 → 缓存等待补偿
        pushTemplate.pushToUserWithOffline(userId, "NOTIFICATION", content);
    }
}
```

## 配置项

### `ydsz.websocket.*`（WebSocketProperties）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.websocket.enabled` | true | 是否启用 WebSocket 模块 |
| `ydsz.websocket.endpoint` | `/ws` | WebSocket 端点路径 |
| `ydsz.websocket.allowed-origin-patterns` | `["*"]` | 允许的 Origin 模式列表 |
| `ydsz.websocket.sock-js-enabled` | true | 是否启用 SockJS 回退 |
| `ydsz.websocket.message-size-limit` | `65536`（64KB） | 最大消息大小（字节） |
| `ydsz.websocket.send-timeout-ms` | `5000`（5s） | 消息发送超时（毫秒） |
| `ydsz.websocket.session-ttl-seconds` | `3600`（1h） | Session TTL（秒），心跳未续期时自动清理 |
| `ydsz.websocket.heartbeat.server-interval` | `10000`（10s） | 服务端心跳间隔（毫秒） |
| `ydsz.websocket.heartbeat.client-interval` | `10000`（10s） | 客户端心跳间隔（毫秒） |
| `ydsz.websocket.heartbeat.stale-session-timeout` | `60000`（60s） | 僵尸 Session 超时阈值（毫秒） |

### `ydsz.websocket.cluster.*`

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.websocket.cluster.enabled` | true | 是否启用集群广播（Redis Pub/Sub） |
| `ydsz.websocket.cluster.channel` | `ydsz:ws:cluster:push` | Redis Channel 名称 |

### `ydsz.websocket.offline.*`

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.websocket.offline.enabled` | true | 是否启用离线消息补偿 |
| `ydsz.websocket.offline.max-cache` | `100` | Redis 缓存最大条数 |
| `ydsz.websocket.offline.ttl` | `30d` | 缓存 TTL |

### `ydsz.websocket.rate-limit.*`

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.websocket.rate-limit.enabled` | false | 是否启用速率限制 |
| `ydsz.websocket.rate-limit.max-per-user-per-minute` | `60` | 每用户每分钟最大消息数 |
| `ydsz.websocket.rate-limit.max-per-ip-per-minute` | `300` | 每 IP 每分钟最大消息数 |

### `ydsz.websocket.circuit-breaker.*`

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.websocket.circuit-breaker.failure-rate-threshold` | `0.5` | 失败率阈值（0-1），超过则熔断 |
| `ydsz.websocket.circuit-breaker.sliding-window-size` | `20` | 滑动窗口大小 |
| `ydsz.websocket.circuit-breaker.half-open-after` | `30s` | 熔断后进入半开状态的等待时间 |

### `ydsz.websocket.retry.*`

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.websocket.retry.enabled` | true | 是否启用消息重试 |
| `ydsz.websocket.retry.max-retries` | `3` | 最大重试次数 |
| `ydsz.websocket.retry.retry-delay` | `5s` | 重试延迟 |
| `ydsz.websocket.retry.dead-letter-enabled` | true | 是否启用死信队列 |
| `ydsz.websocket.retry.backoff-strategy` | `exponential` | 退避策略：fixed / exponential / exponential_with_jitter |
| `ydsz.websocket.retry.max-retry-delay-ms` | `60000`（60s） | 最大重试延迟（毫秒），退避后不超过此值 |

### `ydsz.websocket.connection-limit.*`

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.websocket.connection-limit.max-global-connections` | `10000` | 全局最大连接数 |
| `ydsz.websocket.connection-limit.max-per-user-connections` | `5` | 每用户最大连接数 |

| `ydsz.websocket.multi-device.policy` | `ALLOW_ALL` | 多端登录策略（ALLOW_ALL / MUTEX / NEW_REPLACE_OLD） |
| `ydsz.websocket.multi-device.max-sessions-per-user` | `5` | 每用户最大会话数 |
| `ydsz.websocket.auth.gateway-secret` | （空） | 网关透传认证密钥 |
| `ydsz.websocket.auth.trusted-ips` | （空） | 可信 IP 列表 |

## 使用示例

### 1. 单播 / 广播 / 主题推送

```java
import com.njydsz.common.socket.push.RealtimePushTemplate;

@Service
public class PushService {

    private final RealtimePushTemplate pushTemplate;

    public PushService(RealtimePushTemplate pushTemplate) {
        this.pushTemplate = pushTemplate;
    }

    public void pushToUser(String userId, Object payload) {
        // 在线 → 集群广播；离线 → 缓存补偿
        pushTemplate.pushToUserWithOffline(userId, "NOTIFICATION", payload);
    }

    public void broadcast(Object payload) {
        pushTemplate.broadcast("BROADCAST", payload);
    }

    public void pushTopic(String topic, Object payload) {
        pushTemplate.pushToTopic(topic, payload);
    }
}
```

### 2. 带优先级与 TTL 的推送

```java
// 紧急告警：高优先级，TTL 60 秒
pushTemplate.pushToUser(userId, "ALERT", alertPayload, MessagePriority.URGENT.name());
pushTemplate.pushToUserWithTtl(userId, "ALERT", alertPayload, 60L);
```

### 3. 自定义消息过滤器

```java
import com.njydsz.common.socket.filter.MessageFilter;
import org.springframework.stereotype.Component;

@Component
public class SensitiveWordFilter implements MessageFilter {

    @Override
    public boolean shouldSend(String userId, String pushType, String payload) {
        // 敏感词检查，返回 false 拦截推送
        return !containsSensitiveWords(payload);
    }

    @Override
    public String getName() {
        return "SensitiveWordFilter";
    }
}
```

### 4. 自定义连接生命周期监听器

```java
import com.njydsz.common.socket.lifecycle.WebSocketConnectionListener;
import org.springframework.stereotype.Component;

@Component
public class MyConnectionListener implements WebSocketConnectionListener {

    @Override
    public void onConnected(String userId, String sessionId) {
        // 更新用户最后在线时间
    }

    @Override
    public void onDisconnected(String userId, String sessionId) {
        // 清理业务资源
    }
}
```

### 5. 自定义消息序列化器

```java
import com.njydsz.common.socket.serialize.MessageSerializer;
import org.springframework.stereotype.Component;

@Component
public class ProtobufMessageSerializer implements MessageSerializer {

    @Override
    public String serialize(Object payload) {
        // Protobuf 序列化逻辑
        return protobufHex;
    }

    @Override
    public String getName() {
        return "Protobuf";
    }
}
```

注册为 Spring Bean 后，`WebSocketAutoConfiguration` 通过 `@ConditionalOnMissingBean` 检测到已有实现，自动跳过 `JsonMessageSerializer` 装配。

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `RealtimePushTemplate` | 统一推送模板，业务方通过此接口推送消息 | 框架内置 `DefaultRealtimePushTemplate` |
| `MessageSerializer` | 消息序列化器，可替换为 Protobuf 等协议 | 框架内置 `JsonMessageSerializer`，业务可替换 |
| `MessageFilter` | 消息过滤器链，推送前执行统一过滤（权限/敏感词/大小限制） | 业务方实现，注册为 Bean 即自动接入过滤器链 |
| `WebSocketConnectionListener` | 连接生命周期监听器，监听连接建立/断开 | 业务方实现 |
| `OfflineMessageStore` | 离线消息存储，可扩展为 DB 持久化（如 Redis 溢出后落库） | 框架内置 `RedisOfflineMessageStore` |
| `MessageRetryQueue` | 消息重试队列，可扩展为基于 MQ 的实现 | 框架内置 `RedisMessageRetryQueue` |
| `DeadLetterQueue` | 死信队列，可扩展为基于 DB / MQ 的实现 | 框架内置 `RedisDeadLetterQueue` |
| `MultiDevicePolicy` | 多端登录策略枚举，业务方在 `WebSocketConnectionListener` 中据此实现踢人逻辑 | 框架提供枚举，业务方实现策略 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/websocket` | WebSocket 模块健康检查，由 `WebSocketHealthIndicator` 注册 | `spring-boot-actuator` 在 classpath 且 `ydsz.websocket.enabled=true` |

`WebSocketHealthIndicator` 暴露的详情字段：

| 字段 | 说明 |
|---|---|
| `local.activeConnections` | 本节点活跃 WebSocket 连接数 |
| `cluster.enabled` | 集群广播是否启用 |
| `cluster.redisReachable` | Redis 连通性（PING 探测，集群启用且 Redis 可用时返回 true） |
| `offline.enabled` | 离线消息存储是否启用 |
| `rateLimit.enabled` | 速率限制是否启用 |
| `heartbeat.serverIntervalMs` | 服务端心跳间隔（毫秒） |
| `heartbeat.clientIntervalMs` | 客户端心跳间隔（毫秒） |
| `heartbeat.staleSessionTimeoutMs` | 僵尸 Session 超时阈值（毫秒） |
| `messageSizeLimitBytes` | 最大消息大小（字节） |
| `sessionTtlSeconds` | Session TTL（秒） |
| `circuitBreaker.enabled` | 熔断器是否启用（始终 true，框架内部装配） |
| `onlineUserService` | 在线用户服务后端（`redis-backed` 或 `no-op (Redis unavailable)`） |

健康检查判定逻辑：
- 探测过程抛异常 → **DOWN**（详情中带 error 字段）
- 其他情况 → **UP**

## 注意事项

1. **Redis 不是必须依赖**：`OnlineUserService`、`OfflineMessageStore`、`MessageRetryQueue`、`WebSocketRateLimiter` 在 Redis 不可用时自动降级为 no-op。但多实例部署时**必须**引入 Redis，否则集群广播、跨节点在线状态、离线消息、重试队列都无法工作。
2. **集群广播降级**：`WebSocketClusterPublisher.publish` 失败时返回 false，`DefaultRealtimePushTemplate` 自动降级为本地直接推送，保证消息不丢。
3. **熔断器保护范围**：`WebSocketCircuitBreaker` 仅保护 Redis 相关操作（集群广播、限流器、离线消息存储），不保护 STOMP 推送本身。
4. **重试队列定时刷新**：`WebSocketAutoConfiguration.RetryFlushTask` 通过 `@Scheduled(fixedDelay = 10000)` 每 10 秒刷新重试队列，业务方无需手动调用 `flushRetryMessages`。
5. **STOMP 端点路径**：默认 `/ws`，前端需通过 SockJS + STOMP 客户端连接；`/app` 为应用前缀，`/topic` 与 `/queue` 为 SimpleBroker 前缀。
6. **消息大小限制**：`ydsz.websocket.message-size-limit` 通过 `WebSocketConfigurer.configureWebSocketTransport` 设置到 STOMP 传输层，超过限制的客户端消息会被拒绝。
7. **认证拦截器依赖**：`WebSocketAuthInterceptor` 仅在 `TokenService`（来自 common-auth）在 classpath 时才注册；未引入 common-auth 时跳过认证。
8. **多端登录策略**：`WebSocketSessionEventListener` 已实现 `ALLOW_ALL` / `MUTEX` / `NEW_REPLACE_OLD` 三种策略，通过 `ydsz.websocket.multi-device.*` 配置控制。
9. **`@EnableScheduling` 副作用**：本模块自动配置类已标注 `@EnableScheduling`，若业务模块也标注，Spring 会自动去重，无副作用。
10. **消息压缩推荐**：推荐使用 WebSocket 协议层 permessage-deflate（RFC 7692）压缩消息，无需应用层 GZIP+Base64 编码。可在反向代理层（如 Nginx）启用 `WebSocket` 压缩。

## 变更记录

- **v1.1.0**（2026-08-16）：精简过度设计 — 移除 MessageCompressor（推荐使用 permessage-deflate）、SlowConnectionDetector（职责错位）、MessageAckService（半成品）；移除 LocalSessionRegistry Serializable 标记；修复 RateLimiter 原子性（Lua 脚本）；精简心跳机制（统一 Redis 存储，消除双重 TTL）；精简审计服务（移除自研异步框架）；精简熔断器（移除事件消费者）；移除未使用配置项（Compression、SlowConnection、Ack、dbPersistThreshold）
- **v1.0.0**（2026-08-02）：对标 common-jdbc 标准格式重构 README，补全全部 9 个章节
