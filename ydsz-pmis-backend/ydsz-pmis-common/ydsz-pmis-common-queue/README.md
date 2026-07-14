# ydsz-pmis-common-queue

PMIS 统一消息队列框架 — 5 种 MQ 引擎（Redis Stream / Redis Pub-Sub / Redis List / RocketMQ / Kafka / RabbitMQ / ActiveMQ）、死信队列、消息轨迹追踪、消费者限流、消息去重、批量操作。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 58 |

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
| `MessageQueueFactory` | 队列工厂（按类型创建） |
| `QueueType` | 队列类型枚举 |
| `QueueMessage` | 消息实体 |

### 发布者实现

| 类 | 说明 |
|---|---|
| `RedisStreamPublisher` / `RedisPubSubPublisher` / `RedisListPublisher` | Redis 三种发布者 |
| `RocketMQPublisher` / `KafkaMessagePublisher` / `RabbitMQPublisher` / `ActiveMQPublisher` | 各 MQ 发布者 |

### 订阅者实现

| 类 | 说明 |
|---|---|
| `RedisStreamSubscriber` / `RedisPubSubSubscriber` / `RedisListSubscriber` | Redis 三种订阅者 |
| `RocketMQSubscriber` / `KafkaMessageSubscriber` / `RabbitMQSubscriber` / `ActiveMQSubscriber` | 各 MQ 订阅者 |

### 死信队列

| 类 | 说明 |
|---|---|
| `DeadLetterQueueService` | 死信队列接口 |
| `DeadLetterQueueServiceImpl` | 死信队列实现 |
| `NoOpDeadLetterQueueService` | 空操作实现（降级） |
| `DeadLetterRetryScheduler` | 死信重试调度器 |

### 消息去重

| 类 | 说明 |
|---|---|
| `MessageDeduplicator` | 消息去重接口 |
| `RedisMessageDeduplicator` | Redis 去重实现 |
| `DedupCleanupScheduler` | 去重清理调度器 |

### 消息轨迹

| 类 | 说明 |
|---|---|
| `MessageTracer` / `MessageTrace` | 消息轨迹追踪 |
| `MessageTraceRecorder` | 轨迹记录器接口 |
| `RedisMessageTraceRecorder` / `DefaultMessageTraceRecorder` | Redis / 默认实现 |
| `MessageTraceInterceptor` / `MessageTraceAspect` | 轨迹拦截器 / 切面 |

### 消费者管理

| 类 | 说明 |
|---|---|
| `QueueManager` | 队列管理器（统一注册 / 启动 / 停止） |
| `ConsumerRateLimiter` | 消费者限流器 |
| `RetryPolicy` | 重试策略 |
| `ConsumerThreadGuard` | 消费者线程保护 |

### 指标与健康检查

| 类 | 说明 |
|---|---|
| `MessageMetrics` | 消息指标采集（发送数 / 消费数 / 延迟 / 错误率） |
| `QueueHealthIndicator` | 健康检查 |

## 配置项

```yaml
pmis:
  queue:
    type: redis-stream              # redis-stream / redis-pubsub / redis-list / rocketmq / kafka / rabbitmq / activemq
    redis-stream:
      consumer-group: pmis-cg
      batch-size: 10
      block-timeout: 3s
    rocketmq:
      name-server: ${ROCKETMQ_NAMESERVER}
      producer-group: pmis-producer
    kafka:
      bootstrap-servers: ${KAFKA_SERVERS}
    rabbit:
      addresses: ${RABBIT_ADDRESSES}
    dead-letter:
      enabled: true
      max-retries: 3
      retry-interval: 30s
    dedup:
      enabled: true
      window: 300s
    trace:
      enabled: true
    consumer:
      rate-limit: 100               # 消费者 QPS 限制
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `QueueConfiguration` | 总是激活 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz.pmis</groupId>
    <artifactId>ydsz-pmis-common-queue</artifactId>
</dependency>
```
