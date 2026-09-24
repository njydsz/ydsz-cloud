# Common 模块使用分析

## queue 推广待接入清单

### 已接入模块（通过 ydzs-common-queue）

| 模块 | Pom 引用 | 说明 |
|------|----------|------|
| ydsz-message-server | `ydsz-common-queue` | 消息推送核心，已封装 MessageQueueOperations |
| ydsz-cronjob-server | `ydsz-common-queue` | 定时任务 JobResult 队列投递 |
| ydzs-agent-server | `ydsz-common-queue` | Agent 任务队列处理 |
| ydsz-workflow-server | `ydsz-common-queue` | 工作流 FlowQueue 消息发布订阅 |

### 待接入模块（直接使用 MQ 原生客户端或经 Spring Template 接入）

| 模块 | 当前接入方式 | 问题描述 | 接入优先级 |
|------|-------------|----------|-----------|
| ydzs-common-event | 直接使用 `KafkaTemplate` / `RocketMQTemplate` | 事件网关直接依赖 Spring Kafka / RocketMQ Spring 模板，未通过 `common-queue` 的 `IMessagePublisher` / `IMessageSubscriber` 抽象；无法享受死信队列、幂等去重、PEL 恢复等能力 | **高** — 作为事件基础设施，应先统一接入 |

### 分析说明

1. **common-event 模块**：`KafkaEventPublishGateway` 与 `RocketMqEventPublishGateway` 直接 import `org.apache.kafka` / `org.apache.rocketmq` 原生类型，绕过 `common-queue` 封装。建议重构为通过 `MessagePublisherHelper` / `MessageSubscriberHelper` 委托，以获得：
   - 统一死信队列（DeadLetterQueueService）
   - 幂等去重（MessageDeduplicator）
   - PEL 恢复（PendingEntryListCleaner）
   - 指标监控（QueueMetrics / QueueMetricsBinder）

2. **message 模块消费者**（BatchMessageConsumer / MessageConsumer / MessageDlqConsumer）：Mafka consumer 使用 Mafka-Spring 原生 `@MafkaConsumer` 注解，已接入 `CommonQueueMessageOperations`，队列抽象覆盖完整，无需额外改造。

3. **其他业务模块**（gateway / literule / system / userinfo / nextwiki）：未发现直接使用 MQ 原生客户端；如有事件发布需求，建议统一通过 `common-event` 接入（待 common-event 自身完成 common-queue 迁移后自动获得能力）。

---

## locales 基座集成说明

- `ydsz-common-base` 已通过 pom 传递依赖 `ydsz-common-common-locales`
- `LocalesAutoConfiguration` 已在 base 的 `AutoConfiguration.imports` 中显式注册
- 所有依赖 base 的 web/app 模块自动获得 i18n MessageSource 与 MessageSourceHolder 能力

## config 基座集成说明

- `ydsz-common-base` 已通过 pom 传递依赖 `ydsz-common-common-config`
- `ConfigAutoConfiguration` 已在 base 的 `AutoConfiguration.imports` 中显式注册
- 所有依赖 base 的 web/app 模块自动获得配置变更桥接与加密健康检查能力
- 配置加解密本身由 `jasypt-spring-boot-starter` 全局处理（按需引入），config 模块提供增强层
