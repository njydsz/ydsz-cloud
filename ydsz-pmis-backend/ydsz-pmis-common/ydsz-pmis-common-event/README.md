# ydsz-pmis-common-event

PMIS 公共事件模块 — 事务性 Outbox 模式，保障领域事件可靠投递。

## 核心能力

### 基础能力
- **Transactional Outbox 模式**：业务操作与事件写入在同一数据库事务中完成
- **后台轮询投递**：定时扫描 PENDING 消息，通过 `EventPublishGateway` 投递到消息队列
- **指数退避重试**：投递失败自动重试，最大重试次数可配置
- **死信处理**：超过最大重试次数的消息标记为 DEAD_LETTER
- **健康检查**：Micrometer 指标 + HealthIndicator 监控消息积压
- **自动清理**：定期清理已投递的历史消息

### 增强能力（v2）
- **多实例原子 claim**：通过 DB UPDATE 原子抢占消息（PROCESSING 状态），避免重复投递
- **超时回收**：定期回收卡在 PROCESSING 状态的消息（实例宕机恢复）
- **租户隔离**：自动注入 tenantId，支持按租户查询和清理
- **幂等去重**：基于 SHA-256 内容哈希生成 deduplicationId，防止重复写入
- **数据库方言适配**：自动检测 PostgreSQL/MySQL/Oracle，适配 LIMIT 语法
- **事件版本化**：支持 schemaVersion 和 contentType，兼容 Schema 演进
- **优先级投递**：支持 0-9 优先级，高优先级消息优先投递
- **同步投递模式**：事务提交后立即投递（可选），适用于实时性要求高的场景
- **批量投递**：利用 MQ 批量发送能力提升吞吐量，失败自动降级为逐条投递
- **降级网关**：FallbackEventPublishGateway 装饰器，MQ 不可用时记录日志并重试
- **Timer 指标**：P50/P90/P99 投递耗时监控
- **链路追踪**：自动注入 traceId（从 RequestContext/MDC 获取）
- **表名安全校验**：防 SQL 注入，表名仅允许字母/数字/下划线
- **Payload 大小限制**：防止数据库行过大 / MQ 投递失败
- **可配置告警阈值**：PENDING 积压和 DEAD_LETTER 告警阈值可配置

## 快速使用

### 1. 创建 Outbox 表

```sql
-- 参见 deploy/sql/modules/pm_event_outbox.sql
```

### 2. 写入事件（在业务事务中）

```java
@Service
public class OrderService {
    private final OutboxService outboxService;

    @Transactional
    public void createOrder(OrderCreateDTO dto) {
        Order order = orderMapper.insert(dto);
        outboxService.appendToOutbox("Order", order.getId(), "OrderCreated", toJson(order));
    }
}
```

### 3. 实现投递网关

```java
@Component
public class RocketMqPublishGateway implements EventPublishGateway {
    @Override
    public boolean publish(OutboxMessage message) throws Exception {
        producer.send(new Message(message.getEventType(), message.getPayload().getBytes()));
        return true;
    }

    @Override
    public List<Boolean> publishBatch(List<OutboxMessage> messages) throws Exception {
        // 利用 MQ 批量发送能力
        ...
    }
}
```

### 4. 高级用法

```java
// 带优先级、Schema 版本和自定义去重 ID
outboxService.appendToOutbox(
    "Order", order.getId(), "OrderCreated", toJson(order),
    Map.of("source", "api"),    // headers
    9,                           // priority (0-9, 9 最高)
    "v2.0.0",                    // schemaVersion
    "application/vnd.ydsz.order.v2+json",  // contentType
    "custom-dedup-id"            // deduplicationId
);

// 使用降级网关装饰器
@Bean
public EventPublishGateway eventPublishGateway(RealGateway realGateway) {
    return new FallbackEventPublishGateway(realGateway);
}
```

### 5. 配置

```yaml
pmis:
  event:
    outbox:
      enabled: true
      table-name: pmis_outbox
      poll-interval-seconds: 5
      batch-size: 100
      max-retries: 5
      base-backoff-seconds: 10
      max-backoff-seconds: 3600
      sent-retention-days: 7
      auto-cleanup: true
      cleanup-interval-hours: 6
      max-payload-size-bytes: 4194304  # 4MB
      default-priority: 5
      default-schema-version: v1.0.0
      stale-processing-threshold-minutes: 5
      pending-alert-threshold: 10000
      dead-letter-alert-threshold: 10
      enable-tenant-isolation: true
      enable-sync-publish: false
```

## Micrometer 指标

| 指标 | 类型 | 说明 |
|---|---|---|
| `pmis.outbox.publish.success` | Counter | 成功投递消息数 |
| `pmis.outbox.publish.failure` | Counter | 投递失败消息数 |
| `pmis.outbox.dead_letter` | Counter | 死信消息数 |
| `pmis.outbox.publish.duration` | Timer | 投递耗时（P50/P90/P99） |

## 消息状态流转

```
PENDING → PROCESSING（claim 成功）
    ├── PROCESSING → SENT（投递成功）
    ├── PROCESSING → PENDING（投递失败，重试中）
    ├── PROCESSING → DEAD_LETTER（超过最大重试次数）
    └── PROCESSING → PENDING（实例宕机，reclaim 回收）
```

## 自动配置

| Bean | 条件 | 说明 |
|---|---|---|
| `OutboxRepository` | `JdbcTemplate` 存在 | JDBC 仓储（自动检测数据库方言） |
| `OutboxService` | `OutboxRepository` 存在 | 事件写入服务 |
| `EventPublishGateway` | 无其他实现时 | NoopEventPublishGateway（降级） |
| `OutboxProcessor` | `OutboxRepository` + `EventPublishGateway` 存在 | 后台轮询处理器 |
| `OutboxHealthIndicator` | `HealthIndicator` 在类路径 | 健康检查 |
