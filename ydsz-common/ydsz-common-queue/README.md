# ydsz-common-queue

> 统一消息队列框架（L5 业务服务层）

提供 7 种 MQ 引擎适配（Redis Stream / Redis Pub-Sub / Redis List / RocketMQ / Kafka / RabbitMQ / ActiveMQ）、死信队列、消息轨迹追踪、消费者限流、消息去重、批量操作、熔断降级、消息压缩、顺序消息能力，是 YDSZ 项目消息中间件接入的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供多 MQ 引擎统一抽象、死信重试、消息去重、轨迹追踪、熔断降级等能力 |
| **依赖** | common-core、common-util、common-exception、common-json；可选依赖 common-redis、jedis、kafka-clients、rocketmq-client、amqp-client、activemq-client、spring-boot-actuator |
| **版本** | 1.0.0 |

## 核心能力

### 1. 统一抽象层

| 类 | 说明 |
|---|---|
| `IMessageQueue` | 消息队列接口，定义 `createPublisher` / `createSubscriber` / `close` 标准操作，继承 `AutoCloseable` |
| `IMessageQueueProvider` | 队列提供者接口，按 `QueueType` 创建队列实例，继承 `AutoCloseable` |
| `IMessagePublisher` | 发布者接口，支持 `publish` / `publishDelayed` / `publishSequential` / `publishBatch` |
| `IMessageSubscriber` | 订阅者接口，支持同步 `subscribe` / `subscribeMessage` 与异步 `subscribeAsync` / `subscribeOnce` |
| `IMessageHandler` | 消息处理器函数式接口 |
| `AbstractMessageQueue` | 抽象消息队列基类，封装通用属性与生命周期 |
| `MessageQueueFactory` | 队列工厂实现，按类型创建队列、追踪已创建实例、统一 `close()` 优雅关闭 |
| `QueueType` | 队列类型枚举：`LIST` / `STREAM` / `PUBSUB` / `ACTIVE` / `RABBIT` / `ROCKET` / `KAFKA` |
| `QueueMessage` | 统一消息模型，含 body / headers / traceId / retryCount / priority / expireMillis / sequenceNumber / messageGroupKey |
| `@EnableQueue` | 启用注解，`@Import(QueueConfiguration.class)` |

### 2. MQ 引擎适配

| 类 | 引擎 | 说明 |
|---|---|---|
| `RedisStreamMQ` | Redis Stream | Redis 5.0+ Stream（消费者组 + ACK + PEL） |
| `RedisPubSubMQ` | Redis Pub-Sub | 发布订阅（广播模式，无持久化） |
| `RedisListMQ` | Redis List | List + BLPOP 阻塞队列（FIFO） |
| `RocketMQ` | Apache RocketMQ | RocketMQ Producer / Consumer（事务消息 + 18 级延迟） |
| `KafkaMQ` | Apache Kafka | Kafka Producer / Consumer（分区并行 + 高吞吐） |
| `RabbitMQ` | RabbitMQ | RabbitMQ Producer / Consumer（AMQP 路由） |
| `ActiveMQ` | ActiveMQ | ActiveMQ Producer / Consumer |

### 3. 发布者实现

| 类 | 说明 |
|---|---|
| `RedisStreamPublisher` / `RedisPubSubPublisher` / `RedisListPublisher` | Redis 三种发布者，支持 pipeline 批量发送 |
| `RocketMQPublisher` | RocketMQ 发布者，支持批量发送 + 顺序消息路由 + 延迟消息 |
| `KafkaMessagePublisher` | Kafka 发布者，支持批量发送 + 顺序消息分区路由 |
| `RabbitMQPublisher` / `ActiveMQPublisher` | RabbitMQ / ActiveMQ 发布者 |
| `CircuitBreakerPublisher` | 熔断器装饰器，三态熔断 + 自动恢复探测 |

### 4. 订阅者实现

| 类 | 说明 |
|---|---|
| `RedisStreamSubscriber` / `RedisPubSubSubscriber` / `RedisListSubscriber` | Redis 三种订阅者，支持指数退避重试 + 过期消息清理 |
| `RocketMQSubscriber` / `KafkaMessageSubscriber` / `RabbitMQSubscriber` / `ActiveMQSubscriber` | 各 MQ 订阅者 |
| `DedupAwareSubscriber` | 幂等去重装饰器，自动跳过重复消息 |

### 5. 死信队列

| 类 | 说明 |
|---|---|
| `DeadLetterQueueService` | 死信队列服务接口，定义 `queryDeadLetters` / `getDeadLetterCount` / `retry` / `retryAll` / `getRetryCount` |
| `DeadLetterQueueServiceImpl` | Redis Hash 实现的死信队列 |
| `NoOpDeadLetterQueueService` | 空操作降级实现（RedisService 不可用时使用） |
| `DeadLetterRetryScheduler` | 死信重试定时调度器（基于 `@EnableScheduling`） |
| `DeadLetterQueueController` | 死信队列管理 REST API（`/api/v1/queue/dead-letter/**`） |

### 6. 消息去重

| 类 | 说明 |
|---|---|
| `MessageDeduplicator` | 内存去重器，原子 `checkAndMark` 操作 |
| `RedisMessageDeduplicator` | Redis 分布式去重实现（分布式场景推荐） |
| `DedupCleanupScheduler` | 去重记录定时清理调度器 |
| `DedupAwareSubscriber` | 幂等去重订阅者装饰器 |

### 7. 消息轨迹

| 类 | 说明 |
|---|---|
| `MessageTrace` | 消息轨迹实体 |
| `MessageTracer` | 消息轨迹追踪工具 |
| `MessageTraceRecorder` | 轨迹记录器接口 |
| `DefaultMessageTraceRecorder` | 内存 LRU 缓存实现（默认） |
| `RedisMessageTraceRecorder` | Redis Hash 持久化实现 |
| `MessageTraceInterceptor` / `MessageTraceAspect` | 轨迹拦截器 / AOP 切面（拦截 `IMessagePublisher.publish`） |

### 8. 熔断降级

| 类 | 说明 |
|---|---|
| `QueueCircuitBreaker` | 三态熔断器（CLOSED → OPEN → HALF_OPEN → CLOSED），无外部依赖 |
| `CircuitBreakerPublisher` | 熔断器发布者装饰器 |

状态机：

| 状态 | 行为 |
|---|---|
| `CLOSED` | 正常工作，记录连续失败次数；达到 `failureThreshold` 后切换到 OPEN |
| `OPEN` | 熔断中，所有请求被快速拒绝；超过 `openStateTimeoutMillis` 后切换到 HALF_OPEN |
| `HALF_OPEN` | 半开探测，允许单个请求试探恢复；探测成功 → CLOSED，探测失败 → OPEN |

### 9. 消息压缩与重试

| 类 | 说明 |
|---|---|
| `MessageCompressor` | GZIP 压缩工具，阈值触发（4KB）+ Base64 编码 + 自动解压 |
| `RetryPolicy` | 重试策略，支持指数退避 + 固定间隔 + `RetryState` 状态跟踪 |

### 10. 消费者管理

| 类 | 说明 |
|---|---|
| `QueueManager` | 队列管理器，统一注册 / 查询 / 移除队列实例与监控指标 |
| `ConsumerRateLimiter` | 消费者限流器（令牌桶，`consumerRateLimitPerSecond=0` 表示不限流） |
| `ConsumerThreadGuard` | 消费者线程保护，自动恢复崩溃线程 |

### 11. 指标与健康检查

| 类 | 说明 |
|---|---|
| `MessageMetrics` | 消息指标采集（发送数 / 消费数 / 延迟 / 错误率） |
| `QueueMetricsBinder` | Micrometer 指标桥接器，将 `QueueManager` 中所有队列指标暴露为 Prometheus |
| `QueueHealthIndicator` | 健康检查：Redis 类型执行 PING，非 Redis 类型 TCP 端口探测 |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-queue</artifactId>
</dependency>
```

按需引入 MQ 引擎依赖：

```xml
<!-- Redis（推荐复用 ydsz-common-redis 连接池） -->
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-redis</artifactId>
</dependency>

<!-- Kafka -->
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-clients</artifactId>
</dependency>

<!-- RocketMQ -->
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-client</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  queue:
    enabled: true
    type: STREAM               # LIST / PUBSUB / STREAM / KAFKA / ROCKET / RABBIT / ACTIVE
    host: 127.0.0.1
    port: 6379
    stream-group: ydsz-group
    stream-consumer: ydsz-consumer
```

### 3. 注入并使用

```java
import com.njydsz.common.queue.queue.IMessageQueueProvider;
import com.njydsz.common.queue.enums.QueueType;
import com.njydsz.common.queue.service.IMessagePublisher;
import com.njydsz.common.queue.service.IMessageSubscriber;
import com.njydsz.common.queue.annotation.EnableQueue;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * 订单消息服务示例
 *
 * <p>展示如何正确初始化队列、发布消息、订阅消息，并确保资源在服务销毁时释放。
 */
@Service
public class OrderMessageService {

    private final IMessageQueueProvider messageQueueProvider;
    private IMessageQueue queue;
    private IMessagePublisher publisher;
    private IMessageSubscriber subscriber;

    public OrderMessageService(IMessageQueueProvider messageQueueProvider) {
        this.messageQueueProvider = messageQueueProvider;
    }

    @PostConstruct
    public void init() {
        // 在服务启动时创建队列实例，避免每次调用都创建
        this.queue = messageQueueProvider.createMessageQueue(QueueType.STREAM);
        this.publisher = queue.createPublisher("order-topic");
        this.subscriber = queue.createSubscriber("order-topic");
    }

    public void publish(String payload) {
        publisher.publish(payload);
    }

    /**
     * 订阅消费（异步模式，推荐）
     */
    public void startConsuming() {
        subscriber.subscribeAsync(message -> {
            // 业务处理逻辑
            processOrder(message);
        });
    }

    @PreDestroy
    public void cleanup() {
        // 服务关闭时释放队列资源，防止连接泄漏
        if (queue != null) {
            queue.close();
        }
    }
}
```

> **注意事项：**
> - 使用 `@EnableQueue` 注解启用队列自动配置（放在任意 `@Configuration` 类上即可）
> - 队列实例应在服务初始化时创建（如 `@PostConstruct`），避免频繁创建/销毁带来的连接开销
> - 务必在 `@PreDestroy` 中调用 `queue.close()` 释放资源，否则可能导致连接泄漏

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.queue.enabled` | true | 是否启用消息队列模块 |
| `ydsz.queue.type` | - | 队列类型（LIST / STREAM / PUBSUB / KAFKA / ROCKET / RABBIT / ACTIVE） |
| `ydsz.queue.host` | 127.0.0.1 | MQ 服务器地址 |
| `ydsz.queue.port` | 6379 | MQ 服务器端口 |
| `ydsz.queue.username` | - | 用户名 |
| `ydsz.queue.password` | - | 密码 |
| `ydsz.queue.timeout` | 3000 | 连接超时时间（毫秒） |
| `ydsz.queue.list-block-timeout-seconds` | 5 | List 队列阻塞超时（秒） |
| `ydsz.queue.stream-group` | group-1 | Stream 消费者组 |
| `ydsz.queue.stream-consumer` | consumer-1 | Stream 消费者名称 |
| `ydsz.queue.stream-retry-max` | 3 | Stream 重试最大次数 |
| `ydsz.queue.stream-block-millis` | 2000 | Stream 阻塞时间（毫秒） |
| `ydsz.queue.stream-batch-size` | 10 | Stream 批量拉取大小 |
| `ydsz.queue.stream-dead-letter-suffix` | `:dlq` | Stream 死信队列后缀 |
| `ydsz.queue.consumer-rate-limit-per-second` | 0 | 消费者限流速率（0=不限流） |
| `ydsz.queue.dead-letter-retry-enabled` | true | 死信队列自动重试开关 |
| `ydsz.queue.dead-letter-max-retries` | 3 | 死信最大重试次数 |
| `ydsz.queue.dead-letter-retry-interval` | 60000 | 死信重试间隔（毫秒） |
| `ydsz.queue.dedup-enabled` | false | 消息去重开关（分布式推荐 true + RedisMessageDeduplicator） |
| `ydsz.queue.dedup-window-millis` | 300000 | 去重窗口（毫秒，5 分钟） |
| `ydsz.queue.circuit-breaker.enabled` | true | 熔断器开关 |
| `ydsz.queue.circuit-breaker.failure-threshold` | 10 | 连续失败阈值 |
| `ydsz.queue.circuit-breaker.open-state-timeout-millis` | 60000 | 熔断恢复等待时间（毫秒） |
| `ydsz.queue.consumer-executor.core-size` | 2 | 消费者线程池核心线程数 |
| `ydsz.queue.consumer-executor.max-size` | 16 | 消费者线程池最大线程数 |
| `ydsz.queue.consumer-executor.queue-capacity` | 256 | 任务队列容量 |
| `ydsz.queue.consumer-executor.thread-name-prefix` | `ydsz-queue-consumer-` | 线程名前缀 |
| `ydsz.queue.consumer-executor.await-termination-seconds` | 30 | 优雅停机等待秒数 |
| `ydsz.queue.trace.enabled` | false | 消息轨迹开关 |
| `ydsz.queue.trace.backend` | memory | 轨迹存储后端（memory / redis） |
| `ydsz.queue.trace.ttl-minutes` | 30 | 轨迹过期时间（分钟） |
| `ydsz.queue.trace.max-capacity` | 1000 | 内存后端最大缓存条目 |

## 使用示例

### 1. 发送消息

```java
import com.njydsz.common.queue.domain.QueueMessage;

// 基本发送
publisher.publish("Hello World");

// 发送 QueueMessage（自动序列化 + 大消息压缩）
QueueMessage message = QueueMessage.of("order data");
message.addHeader("type", "order");
message.setExpireMillis(60000);
publisher.publish(message);

// 顺序消息（相同 groupKey 路由到同一分区）
QueueMessage seqMsg = QueueMessage.of("order step 1");
seqMsg.setSequential("order-123", 1L);
publisher.publishSequential(seqMsg);

// 批量发送（利用原生批量 API）
publisher.publishBatch(List.of(msg1, msg2, msg3));

// 延迟消息（仅 RocketMQ 支持）
publisher.publishDelayed(message, 60000);
```

### 2. 消费消息

```java
// 同步消费
String payload = subscriber.subscribe();

// 异步消费（推荐）
subscriber.subscribeAsync(message -> {
    processOrder(message);
});

// 一次性消费
String traceId = subscriber.subscribeOnce(message -> {
    log.info("处理消息: {}", message.getBody());
});
```

### 3. 幂等去重装饰器

```java
import com.njydsz.common.queue.dedup.MessageDeduplicator;
import com.njydsz.common.queue.dedup.DedupAwareSubscriber;

MessageDeduplicator dedup = new MessageDeduplicator(300000);
IMessageSubscriber dedupSubscriber = new DedupAwareSubscriber(rawSubscriber, dedup);
dedupSubscriber.subscribeAsync(handler);
```

### 4. 熔断保护装饰器

```java
import com.njydsz.common.queue.resilience.QueueCircuitBreaker;
import com.njydsz.common.queue.resilience.CircuitBreakerPublisher;

QueueCircuitBreaker cb = new QueueCircuitBreaker("order-topic", 10, 60000);
IMessagePublisher cbPublisher = new CircuitBreakerPublisher(rawPublisher, cb);
cbPublisher.publish(message);
```

### 5. 死信队列管理

```http
GET  /api/v1/queue/dead-letter/{topic}?limit=100           # 查询死信消息
GET  /api/v1/queue/dead-letter/{topic}/count               # 死信消息数量
POST /api/v1/queue/dead-letter/{topic}/retry/{messageId}   # 重试单条死信
POST /api/v1/queue/dead-letter/retry-all                   # 重试所有死信
GET  /api/v1/queue/dead-letter/{topic}/retry-count/{messageId}  # 查询重试次数
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `IMessageQueue` | 消息队列抽象，业务可自定义新 MQ 引擎实现 | 框架内置 7 种实现 |
| `IMessageQueueProvider` | 队列提供者，可替换默认 `MessageQueueFactory` 实现自定义资源管理 | 框架内置 `MessageQueueFactory` |
| `IMessagePublisher` | 发布者接口，可自定义装饰器（如熔断、限流、追踪） | 框架内置 7 种发布者 + `CircuitBreakerPublisher` |
| `IMessageSubscriber` | 订阅者接口，可自定义装饰器（如去重、限流） | 框架内置 7 种订阅者 + `DedupAwareSubscriber` |
| `IMessageHandler` | 消息处理器函数式接口 | 业务模块实现 |
| `MessageTraceRecorder` | 消息轨迹记录器 SPI | 框架内置内存 + Redis 两种实现 |
| `DeadLetterQueueService` | 死信队列服务 SPI | 框架内置 Redis 实现 + NoOp 降级 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/queue` | 消息队列健康检查 | `spring-boot-actuator` 在类路径，`ydsz.queue.enabled=true` |

检查策略：

- **Redis 类型**（LIST / PUBSUB / STREAM）：复用 `RedisService` 执行 `hasKey` 协议级 PING，返回响应时间
- **非 Redis 类型**（KAFKA / RABBIT / ROCKET / ACTIVE）：TCP 端口连通性探测（默认端口：Kafka=9092、RabbitMQ=5672、RocketMQ=9876、ActiveMQ=61616）
- 探测超时：2000ms

## 注意事项

1. **Redis 连接复用**：推荐引入 `ydsz-common-redis` 复用连接池；未引入时回退到 `QueueProperties` 配置自建 JedisPool（不推荐）。
2. **死信队列降级**：`RedisService` 不可用时返回 `NoOpDeadLetterQueueService`，死信功能静默失效，需关注启动日志 WARN。
3. **去重分布式**：单机用 `MessageDeduplicator`，分布式场景必须用 `RedisMessageDeduplicator`，避免重复消费。
4. **熔断器装饰**：`CircuitBreakerPublisher` 仅装饰发布端，消费端熔断需在 handler 内自行实现或配合 Resilience4j。
5. **顺序消息**：仅 Kafka / RocketMQ 原生支持分区顺序；Redis 系列通过 `messageGroupKey` 在客户端模拟，多消费者场景不严格保证。
6. **延迟消息**：仅 RocketMQ 原生支持 18 级延迟；其他引擎调用 `publishDelayed` 等同于立即发送。
7. **Payload 限制**：`QueueMessage.fromPayload` 限制最大 16MB，超过抛 `IllegalArgumentException`；超过 4KB 自动 GZIP 压缩。
8. **优雅停机**：消费者线程池由 Spring 管理，`await-termination-seconds=30` 等待积压消息处理；`ConsumerThreadGuard` 自动恢复崩溃线程。
9. **RocketMQ 依赖排除**：`rocketmq-client` 强制排除 FastJSON 系列与 Jackson，统一使用 `ydsz-common-json` 提供的 Jackson 实现。

## 故障排查（Troubleshooting）

### 常见问题

| 现象 | 可能原因 | 解决方案 |
|---|---|---|
| `QueueConfiguration` 未生效、Bean 未注入 | 未添加 `@EnableQueue` 注解 | 在任意 `@Configuration` 类上添加 `@EnableQueue` |
| `NoSuchBeanDefinitionException: IMessageQueueProvider` | `ydsz.queue.enabled=false` 或缺少引擎依赖 | 确认配置项开启且对应 MQ 引擎依赖已引入 |
| 启动报 `Kafka 连接失败` | Kafka broker 不可达或 bootstrapServers 配置错误 | 检查网络连通性与 `ydsz.queue.kafka.bootstrap-servers` 配置 |
| 消费端无消息输出 | 消费者组未创建或 offset 已到最新 | 确认生产者先发送消息，或重置 consumer group offset |
| 死信队列 REST API 返回 503 | `DeadLetterQueueServiceImpl` 因 Redis 不可用降级为 NoOp | 检查 `ydsz-common-redis` 连接是否正常 |
| 消息体乱码或反序列化失败 | 发送端/接收端序列化协议不一致 | 统一使用 `ydsz-common-json` 序列化，避免混用 JSON 库 |
| `IllegalStateException: 队列已关闭` | 操作了已 `close()` 的队列实例 | 检查是否在 `@PreDestroy` 后仍持有队列引用 |
| 消费者线程池拒绝任务 | 消费速率跟不上生产速率，队列满 | 增大 `consumer-executor.queue-capacity` 或 `max-size` |
| 健康检查 DOWN | Redis 连接失败或 MQ 端口不可达 | 参见上方健康检查章节，检查连接配置与网络 |

### 调试日志

开启 DEBUG 级别日志可观察消息全链路：

```yaml
logging:
  level:
    com.njydsz.common.queue: DEBUG
```

关键日志关键字：
- `[RedisStreamMQ]` — Redis Stream 队列生命周期
- `[CircuitBreaker-xxx]` — 熔断器状态切换
- `[DeadLetterRetryScheduler]` — 死信重试扫描
- `[ConsumerGuard]` — 消费者线程恢复

## 变更记录

- **v1.0.0**（2026-08-02）：对标 common-jdbc 标准格式重构 README，补全全部 9 个章节；完善配置项表、SPI 扩展点、健康检查、注意事项
