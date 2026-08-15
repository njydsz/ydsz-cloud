# MQ 选型决策树

> 本文档提供 ydsz-common-queue 支持的 7 种 MQ 引擎选型指南和决策流程。

## 决策流程图

```
┌─────────────────────────────────────────────────────────────────────┐
│                         MQ 选型决策树                                │
└─────────────────────────────────────────────────────────────────────┘

                        ┌─────────────────────┐
                        │  是否需要消息持久化？  │
                        └──────────┬──────────┘
                           ┌───────┴───────┐
                           │               │
                          是              否
                           │               │
                 ┌─────────▼─────────┐   ┌─────────────────────────┐
                 │ 是否需要消息确认/ACK？│   │    Redis PubSub        │
                 └─────────┬─────────┘   │  （广播通知、实时事件）    │
                     ┌──────┴──────┐     └─────────────────────────┘
                     │             │
                    是            否
                     │             │
           ┌─────────▼─────────┐ ┌───────────────────────────────┐
           │ 是否需要消费组能力？ │ │       Redis List             │
           └─────────┬─────────┘ │  （简单队列、日志收集）        │
               ┌──────┴──────┐   └───────────────────────────────┘
               │             │
              是            否
               │             │
     ┌─────────▼──────────┐  ┌─────────────────────────────────────┐
     │ 吞吐量 > 10万/秒？   │  │         Redis Stream              │
     └─────────┬──────────┘  │  （消息确认、消费组、死信队列）       │
         ┌──────┴──────┐     └─────────────────────────────────────┘
         │             │
        是            否
         │             │
    ┌────▼────┐  ┌─────▼──────────────┐
    │  Kafka  │  │ 需要事务/顺序消息？  │
    │(高吞吐) │  └─────┬──────────────┘
    └─────────┘    ┌──────┴──────┐
                   │             │
                  是            否
                   │             │
             ┌─────▼─────┐  ┌────▼─────────────────┐
             │ RocketMQ  │  │     RabbitMQ        │
             │(事务+顺序) │  │  (复杂路由、AMQP)    │
             └───────────┘  └─────────────────────┘
```

## 快速选型建议

| 场景 | 推荐 MQ | 关键理由 |
|------|---------|---------|
| 简单异步解耦 | Redis List | 无额外依赖，轻量级 |
| 实时通知/广播 | Redis PubSub | 多订阅者，即发即忘 |
| 可靠消息传递 | Redis Stream | 原生支持 ACK、消费组、死信 |
| 高吞吐日志 | Kafka | 分区并行，吞吐量 > 10万/秒 |
| 事务消息 | RocketMQ | 原生事务消息 + 18 级延迟 |
| 复杂路由 | RabbitMQ | AMQP 路由、死信队列 |
| 遗留系统集成 | ActiveMQ | JMS 兼容（已废弃，不推荐新用） |

## 详细对比矩阵

| 特性 | Redis List | Redis PubSub | Redis Stream | Kafka | RocketMQ | RabbitMQ | ActiveMQ |
|------|-----------|-------------|-------------|-------|----------|----------|----------|
| **持久化** | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **消息确认** | ❌ | ❌ | ✅ (ACK) | ✅ (offset) | ✅ (ACK) | ✅ (ACK) | ✅ (ACK) |
| **消费组** | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **广播** | ❌ | ✅ | ❌ | ❌ | ❌ | ✅ (fanout) | ❌ |
| **顺序消息** | ❌ | ❌ | ⚠️ (单分区) | ✅ (分区内) | ✅ (全局) | ❌ | ❌ |
| **事务消息** | ❌ | ❌ | ❌ | ⚠️ (幂等) | ✅ (原生) | ❌ | ❌ |
| **延迟消息** | ❌ | ❌ | ❌ | ❌ | ✅ (18 级) | ⚠️ (插件) | ❌ |
| **吞吐量/秒** | ~5万 | ~10万 | ~5万 | ~100万 | ~10万 | ~5万 | ~3万 |
| **依赖复杂度** | 低 | 低 | 低 | 高 | 中 | 中 | 中 |
| **运维成本** | 低 | 低 | 低 | 高 | 中 | 中 | 中 |

## 选型 Q&A

### Q1：业务刚起步，用什么 MQ？
**推荐：Redis Stream**
- 已有 Redis 依赖，零额外成本
- 支持消费组、ACK、死信队列
- 后续可平滑迁移到 Kafka/RocketMQ

### Q2：订单支付场景，要求不丢消息且支持事务？
**推荐：RocketMQ**
- 原生事务消息（两阶段提交）
- 消息轨迹追踪
- 18 级延迟消息（超时关单）

### Q3：日志收集，每秒几十万条？
**推荐：Kafka**
- 分区并行消费，水平扩展
- 批量压缩，存储成本低
- 与 ELK/数据湖生态集成

### Q4：需要多订阅者广播（如配置变更通知）？
**推荐：Redis PubSub**
- 天然支持多订阅者
- 实现简单，无持久化开销
- 注意：订阅者不在线时消息丢失

### Q5：物联网设备数据上报，需要顺序处理？
**推荐：RocketMQ**
- 全局顺序消息（MessageQueueSelector）
- 分区顺序保证同一设备消息有序
- 支持消息过滤

### Q6：已有 RabbitMQ 基础设施，需要换吗？
**不一定需要换**
- RabbitMQ 功能完善，适合复杂路由场景
- ydsz-common-queue 已提供 RabbitMQ 适配
- 仅在吞吐量成为瓶颈时考虑 Kafka

### Q7：ActiveMQ 还值得用吗？
**不推荐新项目使用**
- ActiveMQ Classic 已进入维护模式
- 推荐迁移到 ActiveMQ Artemis 或 RocketMQ
- ydsz-common-queue 保留兼容但标注 @Deprecated

## 配置示例

### Redis Stream（推荐入门）
```yaml
ydsz:
  queue:
    enabled: true
    type: STREAM
    stream-group: my-group
    stream-consumer: my-consumer
    stream-retry-max: 3
```

### Kafka（高吞吐）
```yaml
ydsz:
  queue:
    enabled: true
    type: KAFKA
    host: kafka-broker
    port: 9092
    stream-group: my-consumer-group
```

### RocketMQ（事务消息）
```yaml
ydsz:
  queue:
    enabled: true
    type: ROCKET
    host: rocketmq-namesrv
    port: 9876
    stream-group: my-producer-group
```

## 迁移指南

| 从 | 到 | 难度 | 注意事项 |
|----|-----|------|---------|
| Redis List | Redis Stream | 低 | 需要重新设计消费者组 |
| Redis PubSub | Redis Stream | 中 | 广播消费组模式替代 |
| ActiveMQ | RocketMQ | 中 | JMS → RocketMQ API 适配 |
| RabbitMQ | Kafka | 高 | 路由模型差异大 |
| 直接 Kafka | ydsz + Kafka | 低 | 替换原生客户端为 ydsz 适配层 |

## 相关资源

- [ydsz-common-queue README](../README.md) - 模块总览
- [死信队列配置](../src/main/java/com/njydsz/common/queue/service/DeadLetterQueueService.java)
- [熔断器配置](../src/main/java/com/njydsz/common/queue/resilience/QueueCircuitBreaker.java)
- [消息轨迹追踪](../src/main/java/com/njydsz/common/queue/trace/)
