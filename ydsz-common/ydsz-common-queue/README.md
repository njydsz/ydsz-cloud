# ydsz-common-queue

> 统一消息队列框架（L5 业务服务层）

提供 MQ 引擎适配（Redis Stream / Kafka / RocketMQ）、死信队列、消费者限流、消息去重、批量操作、顺序消息能力，是 YDSZ 项目消息中间件接入的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供多 MQ 引擎统一抽象、死信重试、消息去重等能力 |
| **依赖** | common-core、common-util、common-exception、common-json；可选依赖 common-redis、kafka-clients、rocketmq-client |
| **版本** | 1.0.0 |

## 核心能力

### 1. 统一抽象层

| 类 | 说明 |
|---|---|
| `IMessageQueue` | 消息队列接口，定义 `createPublisher` / `createSubscriber` / `close` 标准操作 |
| `IMessageQueueProvider` | 队列提供者接口，按 `QueueType` 创建队列实例 |
| `IMessagePublisher` | 发布者接口，支持 `publish(String)` / `publish(QueueMessage)` / `publishBatch(List)` / `close()` |
| `IMessageSubscriber` | 订阅者接口，支持同步 `subscribe()` 与异步 `subscribeAsync(handler)` / `stop()` / `isRunning()` |
| `IMessageHandler` | 消息处理器函数式接口 |
| `MessagePublisherHelper` | 发布者辅助工具，提供 `publishSequential` / `publishDelayed` 等组合操作 |
| `MessageSubscriberHelper` | 订阅者辅助工具，提供 `subscribeMessage` / `subscribeOnce` 等组合操作 |
| `AbstractMessageQueue` | 抽象消息队列基类，封装通用属性与生命周期 |
| `MessageQueueFactory` | 队列工厂实现，按类型创建队列、追踪已创建实例、统一 `close()` 优雅关闭 |
| `QueueType` | 队列类型枚举：`STREAM` / `KAFKA` / `ROCKET` 为推荐引擎，`LIST` / `PUBSUB` / `RABBIT` 为兼容引擎（javadoc 建议优先使用推荐引擎，无 @Deprecated 标注） |
| `QueueMessage` | 统一消息模型，含 body / headers / traceId / retryCount / messageGroupKey |
| `@EnableQueue` | 启用注解，`@Import(QueueConfiguration.class)` |

### 2. MQ 引擎适配（推荐）

| 类 | 引擎 | 说明 |
|---|---|---|
| `RedisStreamMQ` | Redis Stream（推荐） | Redis 5.0+ Stream（消费者组 + ACK + PEL + 死信队列） |
| `KafkaMQ` | Apache Kafka（推荐） | Kafka Producer / Consumer（分区并行 + 高吞吐） |
| `RocketMQ` | Apache RocketMQ | RocketMQ Producer / Consumer（事务消息 + 18 级延迟） |

### 3. MQ 引擎适配（完整支持）

| 类 | 引擎 | 说明 |
|---|---|---|
| `RedisListMQ` | Redis List | List + BLPOP 阻塞队列（FIFO） |
| `RedisPubSubMQ` | Redis Pub-Sub | 发布订阅（广播模式，无持久化） |
| `RabbitMQ` | RabbitMQ | RabbitMQ Producer / Consumer（AMQP 路由） |

### 4. 订阅者装饰器

| 类 | 说明 |
|---|---|
| `DedupAwareSubscriber` | 幂等去重装饰器，自动跳过重复消息 |

### 5. 死信队列

| 类 | 说明 |
|---|---|
| `DeadLetterQueueService` | 死信队列服务接口 |
| `DeadLetterQueueServiceImpl` | Redis 实现的死信队列 |
| `NoOpDeadLetterQueueService` | 空操作降级实现 |
| `DeadLetterRetryScheduler` | 死信重试定时调度器 |

### 6. 消息去重

| 类 | 说明 |
|---|---|
| `MessageDeduplicator` | 内存去重器，原子 `checkAndMark` 操作 |
| `DedupAwareSubscriber` | 幂等去重订阅者装饰器 |

### 7. 消费者管理

| 类 | 说明 |
|---|---|
| `ConsumerRateLimiter` | 消费者限流器（令牌桶，`consumerRateLimitPerSecond=0` 表示不限流） |
| `ConsumerThreadGuard` | 消费者线程保护，自动恢复崩溃线程 |

### 8. 指标与健康检查

| 类 | 说明 |
|---|---|
| `QueueMetrics` | 消息指标采集（发送数 / 消费数 / 延迟 / 错误率） |
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
    type: STREAM               # STREAM / KAFKA / ROCKET （推荐）
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
        this.queue = messageQueueProvider.createMessageQueue(QueueType.STREAM);
        this.publisher = queue.createPublisher("order-topic");
        this.subscriber = queue.createSubscriber("order-topic");
    }

    public void publish(String payload) {
        publisher.publish(payload);
    }

    public void startConsuming() {
        subscriber.subscribeAsync(message -> {
            processOrder(message);
        });
    }

    @PreDestroy
    public void cleanup() {
        if (queue != null) {
            queue.close();
        }
    }
}
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.queue.enabled` | true | 是否启用消息队列模块 |
| `ydsz.queue.type` | - | 队列类型（STREAM / KAFKA / ROCKET） |
| `ydsz.queue.host` | 127.0.0.1 | MQ 服务器地址 |
| `ydsz.queue.port` | 6379 | MQ 服务器端口 |
| `ydsz.queue.username` | - | 用户名 |
| `ydsz.queue.password` | - | 密码 |
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
| `ydsz.queue.consumer-executor.core-size` | 2 | 消费者线程池核心线程数 |
| `ydsz.queue.consumer-executor.max-size` | 16 | 消费者线程池最大线程数 |
| `ydsz.queue.consumer-executor.queue-capacity` | 256 | 任务队列容量 |
| `ydsz.queue.consumer-executor.thread-name-prefix` | `ydsz-queue-consumer-` | 线程名前缀 |
| `ydsz.queue.consumer-executor.await-termination-seconds` | 30 | 优雅停机等待秒数 |

## 使用示例

### 1. 发送消息

```java
import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.service.MessagePublisherHelper;

// 基本发送
publisher.publish("Hello World");

// 发送 QueueMessage（自动序列化）
QueueMessage message = QueueMessage.of("order data");
message.addHeader("type", "order");
publisher.publish(message);

// 顺序消息（相同 groupKey 路由到同一分区）
QueueMessage seqMsg = QueueMessage.of("order step 1");
seqMsg.setMessageGroupKey("order-123");
MessagePublisherHelper.publishSequential(publisher, seqMsg);

// 批量发送
publisher.publishBatch(List.of(msg1, msg2, msg3));

// 延迟消息（通过 Helper 调用）
MessagePublisherHelper.publishDelayed(publisher, message, 60000);
```

### 2. 消费消息

```java
import com.njydsz.common.queue.service.MessageSubscriberHelper;

// 同步消费
String payload = subscriber.subscribe();

// 异步消费（推荐）
subscriber.subscribeAsync(message -> {
    processOrder(message);
});

// 一次性消费（通过 Helper）
String traceId = MessageSubscriberHelper.subscribeOnce(subscriber, message -> {
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

## 接口精简说明

从 v1.0.0 起，`IMessagePublisher` 和 `IMessageSubscriber` 接口进行了精简：

**IMessagePublisher（10 → 4 方法）**

保留：
- `publish(String)` — 发布字符串消息
- `publish(QueueMessage)` — 发布结构化消息
- `publishBatch(List<QueueMessage>)` — 批量发布
- `close()` — 关闭发布者

迁移到 `MessagePublisherHelper`：
- `publishSequential(publisher, message)` — 发布顺序消息
- `publishDelayed(publisher, message, delay)` — 发布延迟消息

**IMessageSubscriber（10 → 4 方法）**

保留：
- `subscribe()` — 同步订阅
- `subscribeAsync(handler)` — 异步订阅
- `stop()` — 停止订阅
- `isRunning()` — 运行状态

迁移到 `MessageSubscriberHelper`：
- `subscribeMessage(subscriber)` — 同步消费并反序列化
- `subscribeOnce(subscriber, handler)` — 单次消费

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `IMessageQueue` | 消息队列抽象，业务可自定义新 MQ 引擎实现 | 框架内置实现 |
| `IMessageQueueProvider` | 队列提供者，可替换默认 `MessageQueueFactory` | 框架内置 |
| `IMessagePublisher` | 发布者接口，可自定义装饰器 | 框架内置 |
| `IMessageSubscriber` | 订阅者接口，可自定义装饰器 | 框架内置 |
| `IMessageHandler` | 消息处理器函数式接口 | 业务模块实现 |
| `DeadLetterQueueService` | 死信队列服务 SPI | 框架内置 Redis 实现 + NoOp 降级 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/queue` | 消息队列健康检查 | `spring-boot-actuator` 在类路径，`ydsz.queue.enabled=true` |

## 废弃功能清单

以下功能已标注 `@Deprecated`，将在后续版本移除：

| 废弃项 | 替代方案 |
|---|---|
| （当前无废弃引擎） | — |

## 已移除功能

以下功能在过度设计评估后已移除，如需类似能力推荐使用标准库替代：

| 已移除项 | 替代方案 |
|---|---|
| `CircuitBreakerPublisher` / `QueueCircuitBreaker` | Resilience4j CircuitBreaker |
| `MessageTraceAspect` | OpenTelemetry / Spring Cloud Sleuth |
| `MultiMQTopology` | Nacos / Spring Cloud Config |
| `JsonSchemaValidator`（配置校验） | Spring Boot `@Validated` + JSR-303 |

> 注意：`MessageTracer` / `MessageCompressor` / `QueueManager` / `QueueMetricsBinder` 4 个组件**仍然存在**（保留实现），未在本次移除清单中。

## 注意事项

1. **Redis 连接复用**：推荐引入 `ydsz-common-redis` 复用连接池
2. **死信队列降级**：Redis 不可用时返回 `NoOpDeadLetterQueueService`，死信功能静默失效
3. **顺序消息**：仅 Kafka / RocketMQ 原生支持分区顺序；Redis Stream 通过 `messageGroupKey` 在客户端模拟
4. **延迟消息**：仅 RocketMQ 原生支持 18 级延迟；其他引擎调用 `publishDelayed` 等同于立即发送
5. **Payload 限制**：`QueueMessage.fromPayload` 限制最大 16MB
6. **优雅停机**：消费者线程池由 Spring 管理，`await-termination-seconds=30` 等待积压消息处理

## 故障排查

```yaml
logging:
  level:
    com.njydsz.common.queue: DEBUG
```

## 变更记录

- **v1.1.0**（2026-08-16）：接口精简（IMessagePublisher/Subscriber 10→4 方法）；移除过度设计组件（熔断器、消息轨迹、自动压缩等）；对齐云顶编码规范 v2.0
- **v1.0.0**（2026-08-02）：初始版本
