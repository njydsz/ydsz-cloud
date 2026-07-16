# ydsz-common-event

PMIS 公共事件模块 — 事务性 Outbox 模式，保障领域事件可靠投递。

## 核心能力

### 基础能力
- **Transactional Outbox 模式**：业务操作与事件写入在同一数据库事务中完成
- **后台轮询投递**：定时扫描 PENDING 消息，通过 `EventPublishGateway` 投递到消息队列
- **指数退避重试**：投递失败自动重试，最大重试次数可配置（位移溢出保护）
- **死信处理**：超过最大重试次数的消息标记为 DEAD_LETTER
- **健康检查**：Micrometer 指标 + HealthIndicator 监控消息积压
- **自动清理**：定期清理已投递的历史消息

### 增强能力
- **多实例原子 claim**：批量单条 SQL 原子抢占消息，避免 N+1 查询
- **多线程投递**：轮询和投递分离，可配置工作线程数
- **超时回收**：定期回收卡在 PROCESSING 状态的消息（实例宕机恢复）
- **租户隔离**：自动注入 tenantId（从 RequestContext 获取）
- **幂等去重**：基于 SHA-256 内容哈希生成 deduplicationId（opt-in，默认关闭）
- **数据库方言适配**：自动检测 PostgreSQL/MySQL/Oracle，适配 LIMIT 语法
- **事件版本化**：支持 schemaVersion 和 contentType，兼容 Schema 演进
- **优先级投递**：支持 0-9 优先级，正确处理 priority=0（Integer nullable）
- **DomainEvent 集成**：直接接受 `DomainEvent` 写入 Outbox，实现 `EventStore` SPI
- **RocketMQ 网关**：内置 `RocketMqEventPublishGateway`，classpath 有 RocketMQTemplate 时自动装配
- **Noop 防护**：`fail-on-noop=true` 时检测到 NoopEventPublishGateway 阻止启动
- **同步投递模式**：事务提交后立即投递（可选）
- **批量投递**：利用 MQ 批量发送能力提升吞吐量，失败自动降级为逐条投递
- **分离 Timer 指标**：单条投递和批量投递独立计时（P50/P90/P99）
- **队列深度 Gauge**：按状态暴露队列深度到 Prometheus
- **链路追踪**：自动注入 traceId（从 RequestContext/MDC 获取）
- **表名安全校验**：防 SQL 注入，表名仅允许字母/数字/下划线
- **Payload 大小限制**：防止数据库行过大 / MQ 投递失败
- **SimpleJdbcInsert 缓存**：避免每次 save 查数据库元数据
- **RowMapper 静态化**：使用 ResultSetMetaData 检查列，避免 try-catch SQLException 开销

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

### 3. 领域事件集成（推荐）

```java
@Service
public class OrderService {
    private final OutboxService outboxService;

    @Transactional
    public void createOrder(OrderCreateDTO dto) {
        Order order = orderMapper.insert(dto);
        // DomainEvent 自动转换为 Outbox 消息
        outboxService.appendToOutbox(new OrderCreatedEvent(order.getId(), order));
    }
}
```

### 4. RocketMQ 自动装配

当 classpath 存在 `rocketmq-spring-boot-starter` 时，`RocketMqEventPublishGateway` 自动注册：
- Topic: `pmis-outbox-events`
- Tag: `eventType`（消费端可按事件类型订阅）
- Body: `payload`

无需手动实现 `EventPublishGateway`。

### 5. 高级用法

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
```

### 6. 配置

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
      auto-dedup: false               # 自动生成去重 ID（默认关闭）
      worker-threads: 1               # 投递工作线程数
      fail-on-noop: true              # Noop 网关时阻止启动
```

## Micrometer 指标

| 指标 | 类型 | 说明 |
|---|---|---|
| `pmis.outbox.publish.success` | Counter | 成功投递消息数 |
| `pmis.outbox.publish.failure` | Counter | 投递失败消息数 |
| `pmis.outbox.dead_letter` | Counter | 死信消息数 |
| `pmis.outbox.publish.single.duration` | Timer | 单条投递耗时（P50/P90/P99） |
| `pmis.outbox.publish.batch.duration` | Timer | 批量投递耗时（P50/P90/P99） |
| `pmis.outbox.queue.size` | Gauge | 队列深度（按状态标签） |

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
| `OutboxEventStore` | 无其他 `EventStore` 实现时 | DomainEvent → Outbox 适配器 |
| `RocketMqEventPublishGateway` | `RocketMQTemplate` 在 classpath | RocketMQ 投递网关 |
| `NoopEventPublishGateway` | 无其他 `EventPublishGateway` | 降级网关（fail-on-noop=true 时阻止启动） |
| `OutboxProcessor` | `OutboxRepository` + `EventPublishGateway` 存在 | 后台轮询处理器 |
| `OutboxHealthIndicator` | `HealthIndicator` 在类路径 | 健康检查 |
