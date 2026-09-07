# ydsz-common-queue

> 多引擎消息队列抽象模块（L5 业务服务层）— Redis(Kafka/RocketMQ/RabbitMQ) 多引擎 + 死信队列 + 延迟消息 + 消息去重 + 消费者组均衡

提供统一的消息队列抽象层，屏蔽底层消息引擎差异。通过 SPI 机制支持 Redis（Stream/List/PubSub）、Kafka、RocketMQ、RabbitMQ、ActiveMQ 等多种引擎，统一的生产者/消费者 API。内置死信队列（DLQ）与自动重试调度、延迟消息发送、消息去重（内容指纹）、消费者限速、消费者组再均衡监听、消息压缩、健康检查、可观测性指标等企业级能力。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供多引擎消息队列统一抽象，屏蔽底层差异，集成死信/延迟/去重/限速/压缩等能力 |
| **依赖** | common-core、common-util、common-redis（可选）、common-thread、common-json；可选依赖 jedis、kafka-clients、rocketmq-spring-boot-starter、spring-rabbit、spring-boot-health、micrometer-core、spring-boot-actuator |
| **版本** | 26.09.01-SNAPSHOT |

## 核心能力

### 1. 队列引擎抽象层

| 类 | 说明 |
|---|---|
| `IMessageQueue` | 队列核心接口，定义 `publish` / `subscribe` / `acknowledge` 等统一操作 |
| `AbstractMessageQueue` | 抽象基类，封装通用逻辑（消息序列化、压缩、死信处理、去重等），具体引擎继承此类 |
| `IMessageQueueProvider` | 队列提供者 SPI，根据 `QueueType` 创建对应引擎实例 |
| `MessageQueueFactory` | 默认 Provider 实现，根据 `QueueType` 创建 Stream/List/PubSub/Kafka/RocketMQ/RabbitMQ 实例 |
| `QueueManager` | 队列管理器，注册 / 查询 / 移除队列实例及对应监控指标 |
| `QueueType` | 队列引擎类型枚举：`STREAM`（Redis Stream，默认）、`KAFKA`、`ROCKET`（RocketMQ）、`PUBSUB`、`LIST`（已废弃）、`RABBIT`（RabbitMQ）、`ACTIVE`（ActiveMQ） |

各引擎实现：

| 引擎 | 生产类 | 消费类 | 说明 |
|---|---|---|---|
| Redis Stream | `RedisStreamPublisher` | `RedisStreamSubscriber` | Redis 5.0+ Stream，支持消费者组和 ACK |
| Redis List | `RedisListPublisher` | `RedisListSubscriber` | 基于 List + BLPOP，简单队列 |
| Redis Pub/Sub | `RedisPubSubPublisher` | `RedisPubSubSubscriber` | 发布订阅模型（无持久化） |
| Kafka | `KafkaMessagePublisher` | `KafkaMessageSubscriber` | 依赖 kafka-clients |
| RocketMQ | `RocketMQPublisher` | `RocketMQSubscriber` | 依赖 RocketMQ Spring |
| RabbitMQ | `RabbitMQPublisher` | `RabbitMQSubscriber` | 依赖 spring-rabbit |
| ActiveMQ | `ActiveMQPublisher` | `ActiveMQSubscriber` | 依赖 ActiveMQ Spring |

### 2. 消息模型

| 类 | 说明 |
|---|---|
| `QueueMessage` | 统一消息模型（含 messageId、topic、payload、headers、timestamp、retryCount、delayMillis 等字段） |
| `MessageProperties` | 消息属性扩展（headers map） |
| `QueueMessageBuilder` | 消息构建器（fluent API） |
| `QueueMessageHandler` | 消费端消息处理器接口 |
| `IMessagePublisher` | 统一生产者接口 |
| `IMessageSubscriber` | 统一消费者接口 |

### 3. 死信队列（DLQ）

| 类 | 说明 |
|---|---|
| `DeadLetterQueueService` | 死信队列服务接口，定义 `sendToDLQ` / `retryFromDLQ` / `listDLQ` 操作 |
| `DeadLetterQueueServiceImpl` | 基于 Redis 的实现：将消费失败/重试耗尽的消息发送到 DLQ，支持手动重试 |
| `NoOpDeadLetterQueueService` | 空操作实现，Redis 不可用时降级 |
| `DeadLetterRetryScheduler` | 自动重试调度器，定时扫描 DLQ 中带重试标记的消息，使用带抖动的延迟策略避免惊群 |

死信流转：消息消费失败 → 重试（最多 N 次）→ 超过重试次数后写入 DLQ → 运维人员通过 API 手动重试或自动调度器按退避策略重试。

### 4. 延迟消息

| 类 | 说明 |
|---|---|
| `DelayedMessageSender` | 延迟消息发送器接口 |
| `TimerBasedDelayedMessageSender` | 基于时间轮的延迟发送实现，支持指定延迟时间后投递 |
| `DelaySpec` | 延迟规格定义（含 delayMillis、maxDelay、unit 字段） |

### 5. 消息去重

| 类 | 说明 |
|---|---|
| `MessageDeduplicator` | 内存消息去重器，基于内容指纹 + 滑动时间窗拦截重复消息 |
| `RedisMessageDeduplicator` | 基于 Redis 的分布式去重实现（依赖 `ydsz-common-redis` 的 Redis 组件） |
| `DedupCleanupScheduler` | 去重记录定时清理调度器，定期清理窗口外的指纹记录 |
| `DedupAwareSubscriber` | 去重感知消费者装饰器，在消息处理前检查指纹 |

### 6. 消费者治理

| 类 | 说明 |
|---|---|
| `ConsumerGroupRebalanceListener` | 消费者组再均衡监听器接口，监听分区/队列的重新分配事件 |
| `RebalanceMonitor` | 再均衡监控器，跟踪消费者组成员变化与分区分配历史 |
| `ConsumerGroupEvent` | 再均衡事件类型枚举：`ASSIGNED` / `REVOKED` / `RESET` |
| `ConsumerRateLimiter` | 消费者限速器，按滑动窗口控制每秒消费速率，保护下游服务 |
| `ConsumerThreadGuard` | 消费者线程守卫，监控消费线程健康状态，异常时自动重建 |

### 7. 消息序列化与压缩

| 类 | 说明 |
|---|---|
| `MessageSerializer` | 消息序列化接口 |
| `JsonMessageSerializer` | JSON 默认实现（基于 YdszJson） |
| `ProtobufMessageSerializer` | Protobuf 实现，高密度场景可选 |
| `MessageCompressor` | 消息压缩接口，支持 gzip / snappy 等算法 |
| `SerializerFactory` | 序列化器工厂，根据配置选择序列化策略 |

### 8. 消息追踪

| 类 | 说明 |
|---|---|
| `MessageTrace` | 消息追踪上下文，记录消息全链路节点（生产→投递→消费→ACK） |
| `MessageTracer` | 追踪器接口，定义上下文传播方法 |

### 9. 可观测性

| 类 | 说明 |
|---|---|
| `QueueMetrics` | 队列度量信息（发送/消费 TPS、延迟、重试次数、积压等） |
| `QueueMetricsBinder` | Micrometer 指标桥接器，将 QueueManager 中所有队列指标暴露到 Prometheus |
| `QueueHealthIndicator` | 健康检查指示器 |
| `QueueEndpoint` | Actuator 自定义端点（ID: `queues`），暴露所有队列运行状态 |

### 10. 自动配置

| 类 | 说明 |
|---|---|
| `QueueConfiguration` | 自动配置类，注册 QueueManager / MessageQueueFactory / DeadLetterQueueService / 去重器 / 健康检查 / 端点 / 指标桥接器 |
| `EnableQueue` | 开关注解，在启动类上标注即可启用 |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-queue</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
# application.yml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379

ydsz:
  queue:
    enabled: true
    type: STREAM          # STREAM / KAFKA / ROCKET / RABBIT / ACTIVE
    stream-group: ydsz-group
    stream-consumer: consumer-1
    stream-batch-size: 10
    stream-retry-max: 3
```

### 3. 使用示例 - 生产消息

```java
@Autowired
private IMessageQueueProvider queueProvider;

IMessageQueue queue = queueProvider.getQueue("order-events");

// 发送普通消息
queue.publish(QueueMessageBuilder.builder()
    .payload(orderEvent)
    .header("eventType", "order.created")
    .build());

// 发送延迟消息
queue.publish(QueueMessageBuilder.builder()
    .payload(delayedEvent)
    .delay(DelaySpec.ofSeconds(30))
    .build());
```

### 4. 使用示例 - 消费消息

```java
@Component
public class OrderEventConsumer implements QueueMessageHandler {

    @PostConstruct
    public void init() {
        IMessageQueue queue = queueProvider.getQueue("order-events");
        queue.subscribe("order-events", this);
    }

    @Override
    public void handle(QueueMessage message) {
        OrderEvent event = message.getPayload(OrderEvent.class);
        // 业务处理...
        // 处理失败抛异常 → 框架自动重试 → 重试耗尽 → 写入 DLQ
    }
}
```

### 5. 使用示例 - 死信队列手动重试

```java
@Autowired
private DeadLetterQueueService dlqService;

// 查询死信列表
List<QueueMessage> dlqMessages = dlqService.listDLQ("order-events");

// 手动重试单条
dlqService.retryFromDLQ("order-events", dlqMessages.get(0).getMessageId());
```

## 配置项

### QueueProperties（`ydsz.queue.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.queue.enabled` | `true` | 是否启用消息队列模块 |
| `ydsz.queue.type` | - | 队列引擎类型（STREAM / KAFKA / ROCKET / RABBIT / ACTIVE） |
| `ydsz.queue.host` | `127.0.0.1` | 服务器地址 |
| `ydsz.queue.port` | `6379` | 服务器端口 |
| `ydsz.queue.password` | - | 连接密码 |
| `ydsz.queue.timeout` | `3000` | 连接超时（毫秒） |
| `ydsz.queue.stream-group` | `group-1` | Redis Stream 消费者组名 |
| `ydsz.queue.stream-consumer` | `consumer-1` | Redis Stream 消费者名 |
| `ydsz.queue.stream-retry-max` | `3` | Stream 消费失败最大重试次数 |
| `ydsz.queue.stream-block-millis` | `2000` | Stream 阻塞读取时间（毫秒） |
| `ydsz.queue.stream-batch-size` | `10` | Stream 批量拉取大小 |
| `ydsz.queue.stream-dead-letter-suffix` | `:dlq` | Stream 死信队列后缀 |
| `ydsz.queue.consumer-rate-limit-per-second` | `0` | 消费者限流速率（每秒消息数，0=不限流） |
| `ydsz.queue.consumer-executor-core-size` | `2` | 异步消费者线程池核心线程数 |
| `ydsz.queue.consumer-executor-max-size` | `16` | 异步消费者线程池最大线程数 |
| `ydsz.queue.consumer-executor-queue-capacity` | `256` | 异步消费者线程池任务队列容量 |

## 使用示例

### 1. 完整消息收发示例

```java
// 配置
ydsz:
  queue:
    type: STREAM
    stream-group: order-service
    stream-consumer: order-consumer-1
    stream-batch-size: 20

// 生产者
@Service
public class OrderEventPublisher {
    @Autowired private IMessageQueueProvider provider;
    public void publishOrderCreated(Order order) {
        IMessageQueue queue = provider.getQueue("order-events");
        queue.publish(QueueMessageBuilder.builder()
            .payload(new OrderCreatedEvent(order))
            .header("source", "order-service")
            .build());
    }
}

// 消费者
@Component
public class InventoryUpdateConsumer implements QueueMessageHandler {
    @Autowired private IMessageQueueProvider provider;

    @PostConstruct
    public void start() {
        IMessageQueue queue = provider.getQueue("order-events");
        queue.subscribe("order-events", this);
    }

    @Override
    public void handle(QueueMessage message) {
        OrderCreatedEvent event = message.getPayload(OrderCreatedEvent.class);
        inventoryService.updateStock(event.getProductId(), event.getQuantity());
    }
}
```

### 2. 延迟消息

```java
// 30 秒后投递（订单超时取消场景）
queue.publish(QueueMessageBuilder.builder()
    .payload(new OrderTimeoutCheckEvent(orderId))
    .delay(DelaySpec.ofSeconds(30))
    .build());
```

### 3. Kafka 引擎

```xml
<!-- 引入 Kafka 依赖 -->
<dependency>
    <groupId>org.apache.kafka</groupId>
    <artifactId>kafka-clients</artifactId>
</dependency>
```

```yaml
ydsz:
  queue:
    type: KAFKA
    host: kafka-broker
    port: 9092
```

### 4. Actuator 端点查询

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,queues
```

访问 `/actuator/queues` 获取队列运行状态（TPS、积压、重试率等）。

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `IMessageQueueProvider` | 自定义队列引擎适配（如 Pulsar / NATS），替换默认 `MessageQueueFactory` | 业务模块实现 |
| `IMessageQueue` | 自定义队列实现，扩展新的队列类型 | 业务模块实现 |
| `MessageSerializer` | 自定义序列化协议（如 Avro / MessagePack），替换默认 JSON | 业务模块实现 |
| `MessageCompressor` | 自定义压缩算法（如 LZ4 / Zstd），替换默认 Gzip | 业务模块实现 |
| `ConsumerGroupRebalanceListener` | 自定义再均衡策略，监听分区/队列分配变化 | 业务模块实现 |
| `DeadLetterQueueService` | 自定义死信实现（如持久化到数据库），替换默认 Redis 实现 | 业务模块实现 |
| `QueueMessageHandler` | 消费者端消息处理器，订阅并处理消息 | 业务模块实现 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/queue` | 消息队列健康检查（连接状态、消费者线程、队列积压、重试率、DLQ 深度等） | `spring-boot-health` 在 classpath + `QueueHealthIndicator` Bean 存在 |

`QueueHealthIndicator` 暴露信息：

| 字段 | 说明 |
|---|---|
| `engine` | 当前队列引擎类型（STREAM / KAFKA / ROCKET 等） |
| `connected` | 连接状态（true / false） |
| `consumerThreadAlive` | 消费线程运行状态 |
| `queueDepth` | 各主题当前积压量 |
| `dlqDepth` | 死信队列深度 |
| `retryRate` | 消息重试率 |
| `compressEnabled` | 消息压缩是否启用 |
| `dedupEnabled` | 消息去重是否启用 |
| `status` | 综合健康状态（UP / DEGRADED / DOWN） |

降级判定：

- 连接断开 → DOWN
- 消费线程全部死亡 → DOWN
- DLQ 深度 > 1000 → DEGRADED
- 重试率 > 20% → DEGRADED
- 队列积压 > 10000 → DEGRADED

## 自动配置类

| 类 | 说明 |
|---|---|
| `QueueConfiguration` | 核心自动配置，条件：`ydsz.queue.enabled=true`；注册 QueueManager / 线程池 / Provider / DLQ / 健康检查 / 端点 / 指标桥接器 |
| `EnableQueue` | 开关注解，在启动类上标注启用模块注册（可省略，模块默认启用） |

装配条件：

| Bean | 条件 |
|---|---|
| `QueueManager` | 无自定义 |
| `MessageQueueFactory` | 无自定义 `IMessageQueueProvider` |
| `DeadLetterQueueServiceImpl` | RedisTemplate 可用 + 无自定义 |
| `NoOpDeadLetterQueueService` | RedisTemplate 不可用 + 无自定义 |
| `DeadLetterRetryScheduler` | DLQ 可用 + `deadLetterRetryEnabled=true`（默认启用） |
| `MessageDeduplicator` | `dedupEnabled=true` + 无自定义 |
| `QueueHealthIndicator` | spring-boot-health 在 classpath + 无自定义 |
| `QueueEndpoint` | spring-boot-actuator 在 classpath |
| `QueueMetricsBinder` | Micrometer 在 classpath + QueueManager 存在 |
| `queueConsumerExecutor` | 无同名 Bean |

## 注意事项

1. **引擎按需引入**：模块声明各引擎依赖为 `optional`，仅在使用对应引擎时才引入相关 jar。如使用 Kafka 需引入 `kafka-clients`；RocketMQ 需引入 `rocketmq-spring-boot-starter`。
2. **Redis 连接复用**：`MessageQueueFactory` 优先使用 `ydsz-common-redis` 的 `RedisTemplate` 复用连接；未引入时回退到 `QueueProperties` 自建 JedisPool。
3. **消费幂等性**：消息引擎存在「至少一次」投递语义，消费端业务逻辑必须保证幂等。建议启用 `dedupEnabled` 利用指纹去重 + 业务层幂等双重保障。
4. **死信队列依赖 Redis**：`DeadLetterQueueServiceImpl` 基于 Redis 实现，Redis 不可用时降级为 `NoOpDeadLetterQueueService`（消息直接丢弃）。生产环境务必引入 `ydsz-common-redis`。
5. **消费者线程池治理**：`queueConsumerExecutor` 为兜底线程池，生产环境推荐通过 `ydsz-common-thread` 配置：线程池统一管理（监控、热更新、上下文传播）。
6. **Stream 消费者组需预创建**：Redis Stream 使用前需确保消费者组已创建（可通过 `XGROUP CREATE` 或首次消费时自动创建）。
7. **延迟消息精度**：`TimerBasedDelayedMessageSender` 基于时间轮实现，实际投递时间可能有 ±1 秒抖动，不适用于毫秒级精确定时。
8. **消息压缩阈值**：仅当 payload 消息体大小超过阈值时触发压缩（由 `MessageCompressor` 配置），避免小消息压缩后反而增大的开销。
9. **限速器单位**：`consumerRateLimitPerSecond=0` 表示不限流。设置过低会导致消费延迟增加，需根据下游承压能力调整。
10. **去重窗口一致性**：`dedupWindowMillis` 决定消息指纹保留时长（默认 60s），窗口内重复消息被拦截；窗口外同一业务消息可重新投递，需根据业务幂等窗口合理配置。

## 变更记录

- **26.09.01**（2026-09-01）：对标 common-jdbc 标准格式重构 README，补全全部章节。
- **26.09.01**（2026-08-25）：新增 `ConsumerRateLimiter`（消费者限速）、`ConsumerThreadGuard`（消费线程守卫）、`RebalanceMonitor`（再均衡监控）；新增 DelayedMessageSender / TimerBasedDelayedMessageSender 延迟消息组件；新增 ActiveMQ 引擎适配（ActiveMQPublisher / ActiveMQSubscriber）。
- **26.09.01**（2026-08-15）：架构重构为子模块分包（config / domain / enums / service / queue / mq / health / metrics / actuator / trace / dedup / delayed / compress / serializer / recovery / rate / group / retry / scheduler / constant / annotation / manager），拆分为 20+ 子包。
- **26.09.01**（2026-08-02）：初始版本，提供 StreamMQ / ListMQ / PubSubMQ 三种 Redis 引擎 + 死信队列 + 基础生产和消费 API。
