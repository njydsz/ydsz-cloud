# ydsz-common-queue

PMIS 统一消息队列框架 — 7 种 MQ 引擎（Redis Stream / Redis Pub-Sub / Redis List / RocketMQ / Kafka / RabbitMQ / ActiveMQ）、死信队列、消息轨迹追踪、消费者限流、消息去重、批量操作、熔断降级、消息压缩、顺序消息。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 60+ |
| **版本** | 1.0.0 |

## 核心能力

### MQ 引擎适配

| 类 | 引擎 | 说明 |
|---|---|---|
| `RedisStreamMQ` | Redis Stream | Redis 5.0+ Stream（消费者组 + ACK） |
| `RedisPubSubMQ` | Redis Pub-Sub | 发布订阅（广播模式） |
| `RedisListMQ` | Redis List | List + BLPOP（简单队列） |
| `RocketMQ` | Apache RocketMQ | RocketMQ Producer / Consumer |
| `KafkaMQ` | Apache Kafka | Kafka Producer / Consumer |
| `RabbitMQ` | RabbitMQ | RabbitMQ Producer / Consumer |
| `ActiveMQ` | ActiveMQ | ActiveMQ Producer / Consumer |

### 统一接口

| 接口 / 类 | 说明 |
|---|---|
| `IMessageQueue` | 消息队列接口 |
| `IMessageQueueProvider` | 队列提供者接口 |
| `IMessagePublisher` / `IMessageSubscriber` | 发布者 / 订阅者接口 |
| `IMessageHandler` | 消息处理器接口 |
| `AbstractMessageQueue` | 抽象消息队列基类 |
| `MessageQueueFactory` | 队列工厂（按类型创建，资源追踪 + 优雅关闭） |
| `QueueType` | 队列类型枚举 |
| `QueueMessage` | 消息实体（自动压缩 + 反序列化安全校验） |

### 发布者实现

| 类 | 说明 |
|---|---|
| `RedisStreamPublisher` / `RedisPubSubPublisher` / `RedisListPublisher` | Redis 三种发布者（支持 pipeline 批量发送） |
| `RocketMQPublisher` | RocketMQ 发布者（批量发送 + 顺序消息路由 + 延迟消息） |
| `KafkaMessagePublisher` | Kafka 发布者（批量发送 + 顺序消息分区路由） |
| `RabbitMQPublisher` / `ActiveMQPublisher` | 各 MQ 发布者 |
| `CircuitBreakerPublisher` | 熔断器装饰器（三态熔断 + 自动恢复探测） |

### 订阅者实现

| 类 | 说明 |
|---|---|
| `RedisStreamSubscriber` / `RedisPubSubSubscriber` / `RedisListSubscriber` | Redis 三种订阅者（指数退避重试 + 过期消息清理） |
| `RocketMQSubscriber` / `KafkaMessageSubscriber` / `RabbitMQSubscriber` / `ActiveMQSubscriber` | 各 MQ 订阅者 |
| `DedupAwareSubscriber` | 幂等去重装饰器（自动跳过重复消息） |

### 死信队列

| 类 | 说明 |
|---|---|
| `DeadLetterQueueService` | 死信队列接口 |
| `DeadLetterQueueServiceImpl` | 死信队列实现 |
| `NoOpDeadLetterQueueService` | 空操作实现（降级） |
| `DeadLetterRetryScheduler` | 死信重试调度器 |
| `DeadLetterQueueController` | 死信队列管理 REST API |

### 消息去重

| 类 | 说明 |
|---|---|
| `MessageDeduplicator` | 内存去重器（原子 checkAndMark） |
| `RedisMessageDeduplicator` | Redis 分布式去重实现 |
| `DedupCleanupScheduler` | 去重清理调度器 |
| `DedupAwareSubscriber` | 幂等去重订阅者装饰器 |

### 消息轨迹

| 类 | 说明 |
|---|---|
| `MessageTracer` / `MessageTrace` | 消息轨迹追踪 |
| `MessageTraceRecorder` | 轨迹记录器接口 |
| `RedisMessageTraceRecorder` / `DefaultMessageTraceRecorder` | Redis / 默认实现 |
| `MessageTraceInterceptor` / `MessageTraceAspect` | 轨迹拦截器 / 切面 |

### 熔断降级

| 类 | 说明 |
|---|---|
| `QueueCircuitBreaker` | 三态熔断器（CLOSED → OPEN → HALF_OPEN） |
| `CircuitBreakerPublisher` | 熔断器发布者装饰器 |

### 消息压缩

| 类 | 说明 |
|---|---|
| `MessageCompressor` | GZIP 压缩工具（阈值触发 + Base64 编码 + 自动解压） |

### 消费者管理

| 类 | 说明 |
|---|---|
| `QueueManager` | 队列管理器（统一注册 / 启动 / 停止） |
| `ConsumerRateLimiter` | 消费者限流器（令牌桶） |
| `RetryPolicy` | 重试策略（指数退避 + 固定间隔 + RetryState 状态跟踪） |
| `ConsumerThreadGuard` | 消费者线程保护（自动恢复崩溃线程） |

### 指标与健康检查

| 类 | 说明 |
|---|---|
| `MessageMetrics` | 消息指标采集（发送数 / 消费数 / 延迟 / 错误率） |
| `QueueMetricsBinder` | Micrometer 指标桥接器（Prometheus 暴露） |
| `QueueHealthIndicator` | 健康检查（Redis PING / TCP 端口探测） |

## 配置项

```yaml
ydsz:
  queue:
    enabled: true                          # 是否启用消息队列模块
    type: STREAM                           # LIST / PUBSUB / STREAM / KAFKA / ROCKET / RABBIT / ACTIVE
    host: 127.0.0.1
    port: 6379
    timeout: 3000

    # Stream 队列配置
    stream-group: group-1
    stream-consumer: consumer-1
    stream-retry-max: 3
    stream-block-millis: 2000
    stream-batch-size: 10
    stream-dead-letter-suffix: ":dlq"

    # List 队列配置
    list-block-timeout-seconds: 5

    # 消费者限流（0=不限流）
    consumer-rate-limit-per-second: 0

    # 死信队列
    dead-letter-retry-enabled: true
    dead-letter-max-retries: 3
    dead-letter-retry-interval: 60000

    # 消息去重
    dedup-enabled: false                   # 分布式场景推荐 true + RedisMessageDeduplicator
    dedup-window-millis: 300000            # 5 分钟去重窗口

    # 熔断器
    circuit-breaker:
      enabled: true
      failure-threshold: 10                # 连续失败 10 次触发熔断
      open-state-timeout-millis: 60000     # 熔断 60 秒后进入半开探测

    # 异步消费者线程池
    consumer-executor:
      core-size: 2
      max-size: 16
      queue-capacity: 256
      thread-name-prefix: "ydsz-queue-consumer-"
      await-termination-seconds: 30

    # 消息轨迹
    trace:
      enabled: false
      backend: memory                      # memory / redis
      ttl-minutes: 30
      max-capacity: 1000
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `QueueConfiguration` | `ydsz.queue.enabled=true`（默认激活） |

### 自动注册的 Bean

| Bean | 条件 |
|---|---|
| `QueueManager` | 总是注册 |
| `queueConsumerExecutor` | 总是注册（线程池） |
| `IMessageQueueProvider` | 总是注册 |
| `DeadLetterQueueService` | RedisService 可用时注册 Redis 实现，否则 NoOp |
| `DeadLetterRetryScheduler` | 死信重试启用时注册 |
| `MessageDeduplicator` | `dedup-enabled=true` 时注册 |
| `DedupCleanupScheduler` | `MessageDeduplicator` 存在时注册 |
| `MessageTraceRecorder` | `trace.enabled=true` 时注册 |
| `MessageTraceAspect` | `trace.enabled=true` 时注册 |
| `QueueHealthIndicator` | spring-boot-health 在 classpath 时注册 |
| `QueueMetricsBinder` | MeterRegistry 可用时注册 |
| `DeadLetterQueueController` | DeadLetterQueueService + spring-web 可用时注册 |

## REST API

### 死信队列管理

| 端点 | 方法 | 说明 |
|---|---|---|
| `/api/v1/queue/dead-letter/{topic}` | GET | 查询指定主题的死信消息 |
| `/api/v1/queue/dead-letter/{topic}/count` | GET | 获取死信消息数量 |
| `/api/v1/queue/dead-letter/{topic}/retry/{messageId}` | POST | 重试指定死信消息 |
| `/api/v1/queue/dead-letter/retry-all` | POST | 重试所有死信消息 |
| `/api/v1/queue/dead-letter/{topic}/retry-count/{messageId}` | GET | 获取消息重试次数 |

## 运维手册

### 1. 队列选型建议

| 场景 | 推荐引擎 | 原因 |
|---|---|---|
| 高可靠 + ACK + 消费组 | Redis Stream | 原生消费者组、ACK、死信队列 |
| 简单队列 + 低延迟 | Redis List | BLPOP 阻塞读取，简单高效 |
| 广播通知 | Redis PubSub | 一发多收，无持久化 |
| 高吞吐 + 分区 | Kafka | 分区并行、高吞吐量 |
| 事务消息 + 延迟 | RocketMQ | 事务消息、18 级延迟 |
| AMQP 协议兼容 | RabbitMQ | 路由灵活、AMQP 标准 |

### 2. 最佳实践

#### 2.1 消息发送

```java
// 基本发送
publisher.publish("Hello World");

// 发送 QueueMessage（自动序列化 + 大消息压缩）
QueueMessage message = QueueMessage.of("order data");
message.addHeader("type", "order");
message.setExpireMillis(60000);  // 60秒后过期
publisher.publish(message);

// 顺序消息（相同 groupKey 路由到同一分区）
QueueMessage seqMsg = QueueMessage.of("order step 1");
seqMsg.setSequential("order-123", 1L);
publisher.publishSequential(seqMsg);

// 批量发送（利用原生批量 API）
List<QueueMessage> batch = List.of(msg1, msg2, msg3);
publisher.publishBatch(batch);

// 延迟消息（仅 RocketMQ 支持）
publisher.publishDelayed(message, 60000);  // 延迟 60 秒
```

#### 2.2 消息消费

```java
// 同步消费
String payload = subscriber.subscribe();

// 异步消费（推荐）
subscriber.subscribeAsync(message -> {
    // 处理消息
    processOrder(message);
});

// 幂等消费（装饰器模式）
MessageDeduplicator dedup = new MessageDeduplicator(300000);
IMessageSubscriber dedupSubscriber = new DedupAwareSubscriber(rawSubscriber, dedup);
dedupSubscriber.subscribeAsync(handler);

// 熔断保护（装饰器模式）
QueueCircuitBreaker cb = new QueueCircuitBreaker("order-topic", 10, 60000);
IMessagePublisher cbPublisher = new CircuitBreakerPublisher(rawPublisher, cb);
cbPublisher.publish(message);
```

#### 2.3 死信处理

```java
// 死信消息会自动进入死信队列
// 可通过 REST API 或代码查询和重试
// GET /api/v1/queue/dead-letter/order-topic?limit=100
// POST /api/v1/queue/dead-letter/order-topic/retry/{messageId}
```

### 3. 监控指标

| 指标 | 类型 | 说明 |
|---|---|---|
| `ydsz_queue_publish_total` | Counter | 消息发送总数（标签：channel, result） |
| `ydsz_queue_consume_total` | Counter | 消息消费总数（标签：channel, result） |
| `ydsz_queue_publish_latency_seconds` | Histogram | 发送延迟分布 |
| `ydsz_queue_consume_latency_seconds` | Histogram | 消费延迟分布 |
| `ydsz_queue_backlog` | Gauge | 队列积压消息数 |
| `ydsz_queue_dead_letter_count` | Gauge | 死信队列消息数 |
| `ydsz_queue_retry_count` | Gauge | 重试消息数 |

Grafana 仪表盘：`deploy/monitoring/grafana/dashboards/pmis-queue-dashboard.json`

### 4. 故障排查

| 现象 | 排查方向 |
|---|---|
| 消息发送失败 | 检查熔断器状态、MQ 引擎连通性、网络 |
| 消息消费延迟高 | 检查消费者线程数、限流配置、处理逻辑耗时 |
| 死信队列堆积 | 检查消息处理逻辑异常、消费者是否正常运行 |
| 消息重复消费 | 启用消息去重、使用 DedupAwareSubscriber |
| 内存溢出 | 检查 payload 大小、启用消息压缩、调低 batch-size |

### 5. 优雅停机

消费者线程池由 Spring 管理，支持优雅停机：
- `await-termination-seconds=30`：等待 30 秒处理完积压消息
- `ConsumerThreadGuard`：自动恢复崩溃的消费线程
- `MessageQueueFactory.close()`：关闭所有创建的队列实例

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-queue</artifactId>
</dependency>
```

### 按需引入 MQ 引擎依赖

```xml
<!-- Redis（推荐复用 ydsz-common-redis） -->
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
