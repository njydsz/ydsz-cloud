# ydsz-common-socket

YDSZ WebSocket 实时推送框架 — 集群广播、离线消息存储、在线用户管理、认证拦截、消息限流、实时推送模板、熔断降级、心跳保活、消息重试、ACK 确认、消息压缩、慢连接检测、审计日志、分布式追踪、Micrometer 指标。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 30+ |

## 核心能力

### WebSocket 核心

| 类 | 说明 |
|---|---|
| `WebSocketAutoConfiguration` | WebSocket 自动配置（注册全部 Bean，`@EnableScheduling`） |
| `WebSocketClusterAutoConfiguration` | 集群自动配置 |
| `WebSocketProperties` | 配置属性（端点/心跳/消息大小/集群/离线/限流/熔断/重试/ACK/多端/连接限制/压缩/慢连接） |
| `WebSocketConstants` | 常量定义 |

### 集群支持

| 类 | 说明 |
|---|---|
| `WebSocketClusterPublisher` | 集群消息发布器（Redis Pub-Sub + 熔断保护 + traceId 注入） |
| `WebSocketClusterSubscriber` | 集群消息订阅器（MDC traceId 恢复 + 消息解压） |
| `WebSocketClusterMessage` | 集群消息实体（含 traceId/tags/priority 字段） |

### 在线用户管理

| 类 | 说明 |
|---|---|
| `OnlineUserService` | 在线用户服务（用户 ↔ Session 映射） |
| `WebSocketSessionEventListener` | Session 事件监听器（上下线 + 离线补偿 + 心跳注册 + 连接监听器通知） |
| `DeviceSessionInfo` | 设备 Session 信息（多端管理） |
| `MultiDevicePolicy` | 多端登录策略枚举（ALLOW_ALL / MUTEX / NEW_REPLACE_OLD） |
| `WebSocketConnectionListener` | 连接生命周期监听器接口（P3-5 扩展点） |

### 离线消息

| 类 | 说明 |
|---|---|
| `OfflineMessageStore` | 离线消息存储接口 |
| `RedisOfflineMessageStore` | Redis 离线消息存储（List + 过期 + 熔断保护） |

### 实时推送

| 类 | 说明 |
|---|---|
| `RealtimePushTemplate` | 实时推送模板接口（单播/广播/群组/带优先级/带 TTL/重试刷新） |
| `DefaultRealtimePushTemplate` | 默认推送实现（过滤器链 + 序列化 + 压缩 + traceId + 集群广播 + 降级 + 重试 + ACK + 审计 + 慢连接检测） |

### 安全与限流

| 类 | 说明 |
|---|---|
| `WebSocketAuthInterceptor` | 认证拦截器（JWT + 连接数限制 + 审计） |
| `WebSocketRateLimiter` | 消息限流器（防消息洪泛 + 熔断保护） |
| `ConnectionLimiter` | 连接数限制器（全局 + per-user） |

### 消息可靠性

| 类 | 说明 |
|---|---|
| `MessageRetryQueue` | 消息重试队列接口（P0-4） |
| `RedisMessageRetryQueue` | Redis Sorted Set 重试队列实现 |
| `RetryableMessage` | 可重试消息实体 |
| `DeadLetterQueue` | 死信队列接口 |
| `RedisDeadLetterQueue` | Redis List 死信队列实现 |
| `MessageAckService` | ACK 确认服务（P1-2） |

### 可观测性

| 类 | 说明 |
|---|---|
| `WebSocketMetrics` | WebSocket 指标（连接数 / 消息数 / 延迟） |
| `WebSocketHealthIndicator` | 健康检查（活跃连接/集群状态/Redis 连通性/配置摘要） |
| `WebSocketAuditService` | 审计日志服务（P2-5，专用 Logger `WS_AUDIT`） |
| `SlowConnectionDetector` | 慢连接检测器（P2-2） |
| `WebSocketTraceContext` | 链路追踪辅助工具（P1-1，MDC traceId 跨节点传播） |

### 熔断降级

| 类 | 说明 |
|---|---|
| `WebSocketCircuitBreaker` | 轻量级熔断器（P0-2，AtomicReference + CAS 线程安全） |

### 心跳保活

| 类 | 说明 |
|---|---|
| `WebSocketHeartbeatHandler` | 心跳保活处理器（P0-3，`@Scheduled` 定时清理僵尸 Session） |

### 消息处理

| 类 | 说明 |
|---|---|
| `MessageCompressor` | 消息压缩器（P2-3，GZIP + Base64） |
| `MessageSerializer` | 消息序列化器接口（P3-5 扩展点） |
| `JsonMessageSerializer` | JSON 消息序列化器默认实现 |
| `MessageFilter` | 消息过滤器接口（P3-5 扩展点） |
| `MessagePriority` | 消息优先级枚举（P1-4） |
| `StompMessageInterceptor` | STOMP 消息拦截器（P3-1，CONNECT 注入 traceId + SEND 限流审计） |

## 配置项

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
    message-size-limit: 65536
    send-timeout-ms: 5000
    session-ttl-seconds: 3600
    cluster:
      enabled: true
      channel: ydsz:ws:cluster:push
    offline:
      enabled: true
      max-cache: 100
      ttl: 30d
      db-persist-threshold: 50
    rate-limit:
      enabled: false
      max-per-user-per-minute: 60
      max-per-ip-per-minute: 300
    circuit-breaker:
      failure-rate-threshold: 0.5
      sliding-window-size: 20
      half-open-after: 30s
    retry:
      enabled: true
      max-retries: 3
      retry-delay: 5s
      dead-letter-enabled: true
    ack:
      enabled: false
      timeout: 30s
    multi-device:
      policy: ALLOW_ALL
      max-sessions-per-user: 5
    connection-limit:
      max-global-connections: 10000
      max-per-user-connections: 5
    compression:
      enabled: false
      min-size: 1024
    slow-connection:
      enabled: true
      threshold-ms: 5000
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `WebSocketAutoConfiguration` | `SimpMessagingTemplate` 在 classpath 且 `ydsz.websocket.enabled=true` |
| `WebSocketClusterAutoConfiguration` | `StringRedisTemplate` 在 classpath 且 `ydsz.websocket.cluster.enabled=true` |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-socket</artifactId>
</dependency>
```

## P2-4 消息体大小限制使用说明

`ydsz.websocket.message-size-limit` 配置项定义了最大消息大小（字节）。
业务应用在配置 `WebSocketMessageBrokerConfigurer` 时，应调用
`registration.setMessageSizeLimit(properties.getMessageSizeLimit())` 使其生效。

## 扩展点

### 自定义消息序列化器

```java
@Bean
public MessageSerializer messageSerializer() {
    return new ProtobufMessageSerializer(); // 替换默认 JSON 序列化
}
```

### 自定义连接生命周期监听器

```java
@Component
public class MyConnectionListener implements WebSocketConnectionListener {
    @Override
    public void onConnected(String userId, String sessionId) {
        // 更新用户最后在线时间
    }
    @Override
    public void onDisconnected(String userId, String sessionId) {
        // 清理资源
    }
}
```

### 自定义消息过滤器

```java
@Component
public class SensitiveWordFilter implements MessageFilter {
    @Override
    public boolean shouldSend(String userId, String pushType, String payload) {
        // 敏感词检查
        return !containsSensitiveWords(payload);
    }
    @Override
    public String getName() { return "SensitiveWordFilter"; }
}
```
