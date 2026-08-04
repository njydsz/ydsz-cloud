# remi-common-event

> 事务性 Outbox 模式事件模块（L5 业务服务层）— 保障领域事件可靠投递

实现 Transactional Outbox 模式，业务操作与事件写入在同一数据库事务中完成，后台轮询器异步将 PENDING 消息投递到消息队列（RocketMQ / 自定义网关），提供指数退避重试、死信处理、多实例原子 claim、租户隔离、链路追踪、幂等去重等企业级能力，是所有业务模块领域事件投递的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供事务性 Outbox 模式、可靠事件投递、指数退避重试、死信处理、多实例原子 claim、健康检查能力 |
| **依赖** | common-core、common-exception、common-json、common-domain、spring-jdbc、spring-tx；可选依赖 spring-boot-actuator、spring-boot-health、micrometer-core、rocketmq-spring-boot-starter |
| **版本** | 1.0.0 |

## 核心能力

### 1. 配置与自动装配

| 类 | 说明 |
|---|---|
| `EventAutoConfiguration` | Spring Boot 自动配置，`remi.event.outbox.enabled=true`（默认）且 `JdbcTemplate` 存在时装配；含 `@PostConstruct` 校验 Noop 网关、`@PreDestroy` 停止处理器 |
| `EventProperties` | 配置属性（`remi.event.outbox.*`） |
| `RocketMqGatewayConfiguration` | RocketMQ 网关自动配置，classpath 存在 `RocketMQTemplate` 且容器有该 Bean 时注册 `RocketMqEventPublishGateway` |

### 2. 事件投递网关

| 类 | 说明 |
|---|---|
| `EventPublishGateway` | 事件投递网关 SPI，定义 `publish(OutboxMessage)` 与 `publishBatch(List<OutboxMessage>)` |
| `RocketMqEventPublishGateway` | RocketMQ 实现：Topic=`remi-outbox-events`，Tag=`eventType`，Body=`payload`，headers 作为用户属性；支持批量发送，失败自动降级为逐条投递 |
| `NoopEventPublishGateway` | 降级实现，仅记录日志不真正投递；`fail-on-noop=true` 时检测到会阻止应用启动 |

### 3. Outbox 模型与状态机

| 类 | 说明 |
|---|---|
| `OutboxMessage` | Outbox 消息实体（id / aggregateId / aggregateType / eventType / payload / headers / status / retryCount / nextRetryAt / tenantId / deduplicationId / schemaVersion / contentType / priority / traceId / createdAt） |
| `OutboxStatus` | 状态枚举：`PENDING` / `PROCESSING` / `SENT` / `DEAD_LETTER` |
| `StandardEventTypes` | 标准事件类型常量（FLOW_*、USER_*、CONFIG_*、JOB_*、FILE_*、RULE_*、AGENT_*、PROJECT_*、MESSAGE_* 等），命名规范 `MODULE_ENTITY_ACTION` |
| `DatabaseDialect` | 数据库方言枚举（POSTGRESQL / MYSQL / ORACLE / UNKNOWN），自动检测 + 适配 LIMIT 语法 |

状态流转：

```
PENDING → PROCESSING（claim 成功）
    ├── PROCESSING → SENT（投递成功）
    ├── PROCESSING → PENDING（投递失败，retryCount++，指数退避）
    ├── PROCESSING → DEAD_LETTER（超过 maxRetries）
    └── PROCESSING → PENDING（实例宕机，stale 超时回收）
```

### 4. 写入与查询

| 类 | 说明 |
|---|---|
| `OutboxService` | 写入服务，业务在事务中调用 `appendToOutbox`；自动注入 traceId（MDC/RequestContext）与 tenantId（RequestContext）；payload 大小校验；支持 DomainEvent 直接写入 |
| `OutboxEventStore` | `EventStore` SPI 适配器，将 `append`/`appendAll` 委托给 `OutboxService`；查询方法抛 `UnsupportedOperationException`（Outbox 专注 forward-only 投递，不提供回放） |
| `OutboxRepository` | JDBC 仓储，使用 `JdbcTemplate` + `SimpleJdbcInsert`（缓存元数据）；表名安全校验（防 SQL 注入，正则 `^[a-zA-Z_][a-zA-Z0-9_]*$`）；`RowMapper` 静态化（使用 `ResultSetMetaData` 检查列） |

### 5. 后台处理器

| 类 | 说明 |
|---|---|
| `OutboxProcessor` | 后台轮询处理器，`initMethod=start` 启动；含调度线程池（轮询 + claim）与投递线程池（多线程投递，可配置 `worker-threads`） |

核心增强：

- 批量 claim：单条 SQL 原子批量抢占消息（`UPDATE ... WHERE status='PENDING'`），避免 N+1
- 多线程投递：轮询与投递分离，MQ 慢时不阻塞轮询
- 超时回收：定期回收卡在 PROCESSING 状态的消息（`stale-processing-threshold-minutes`）
- 自动清理：定期清理已投递的 SENT 历史（`sent-retention-days` / `cleanup-interval-hours`）
- 指数退避：`baseBackoffSeconds * 2^min(retryCount, 30)`，最大不超过 `maxBackoffSeconds`（位移溢出保护 MAX_SHIFT=30）
- 批量投递：调用 `publishBatch`，失败自动降级为逐条投递
- 分离 Timer：单条投递与批量投递独立计时（P50/P90/P99）

### 6. 可观测性

`OutboxProcessor` 在 `MeterRegistry` 可用时自动注册以下指标：

| 指标 | 类型 | 说明 |
|---|---|---|
| `remi.outbox.publish.success` | Counter | 成功投递消息数 |
| `remi.outbox.publish.failure` | Counter | 投递失败消息数 |
| `remi.outbox.dead_letter` | Counter | 死信消息数 |
| `remi.outbox.publish.single.duration` | Timer | 单条投递耗时（P50/P90/P99） |
| `remi.outbox.publish.batch.duration` | Timer | 批量投递耗时（P50/P90/P99） |
| `remi.outbox.queue.size` | Gauge | 队列深度（按 status 标签：PENDING / PROCESSING / DEAD_LETTER） |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.remisoft</groupId>
    <artifactId>remi-common-event</artifactId>
</dependency>
```

如需 RocketMQ 自动投递，额外引入可选依赖：

```xml
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-spring-boot-starter</artifactId>
</dependency>
```

### 2. 创建 Outbox 表

```sql
-- 参见 deploy/sql/modules/pm_event_outbox.sql
-- 表名默认 remi_outbox，可通过 remi.event.outbox.table-name 覆盖
```

### 3. 配置启用

```yaml
remi:
  event:
    outbox:
      enabled: true
      table-name: remi_outbox
      poll-interval-seconds: 5
      batch-size: 100
      max-retries: 5
      fail-on-noop: true   # 生产环境必须 true，避免消息静默丢失
```

### 4. 在业务事务中写入事件

```java
import com.remisoft.common.event.service.OutboxService;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OutboxService outboxService;

    @Transactional
    public void createOrder(OrderCreateDTO dto) {
        Order order = orderMapper.insert(dto);
        // 同一事务写入 Outbox，事务提交后由后台轮询器投递
        outboxService.appendToOutbox("Order", order.getId(), "OrderCreated", toJson(order));
    }
}
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.event.outbox.enabled` | true | 是否启用 Outbox 模式 |
| `remi.event.outbox.table-name` | `remi_outbox` | Outbox 表名（仅允许字母/数字/下划线） |
| `remi.event.outbox.poll-interval-seconds` | 5 | 轮询间隔（秒） |
| `remi.event.outbox.batch-size` | 100 | 每批最大条数 |
| `remi.event.outbox.max-retries` | 5 | 最大重试次数（超过则进入死信） |
| `remi.event.outbox.base-backoff-seconds` | 10 | 基础退避秒数（指数退避基数） |
| `remi.event.outbox.max-backoff-seconds` | 3600 | 最大退避秒数（退避上限） |
| `remi.event.outbox.sent-retention-days` | 7 | 已投递消息保留天数（0=不清理） |
| `remi.event.outbox.auto-cleanup` | true | 是否启用自动清理 |
| `remi.event.outbox.cleanup-interval-hours` | 6 | 清理间隔（小时） |
| `remi.event.outbox.max-payload-size-bytes` | 4194304 (4MB) | payload 最大字节数 |
| `remi.event.outbox.default-priority` | 5 | 默认优先级（0-9，9 最高） |
| `remi.event.outbox.default-schema-version` | `v1.0.0` | 默认 Schema 版本号 |
| `remi.event.outbox.stale-processing-threshold-minutes` | 5 | PROCESSING 超时阈值（分钟），超时回收为 PENDING |
| `remi.event.outbox.pending-alert-threshold` | 10000 | PENDING 积压告警阈值 |
| `remi.event.outbox.dead-letter-alert-threshold` | 10 | DEAD_LETTER 告警阈值 |
| `remi.event.outbox.enable-tenant-isolation` | true | 是否启用租户隔离（自动注入 tenantId） |
| `remi.event.outbox.enable-sync-publish` | false | 是否启用同步投递（事务提交后立即投递） |
| `remi.event.outbox.auto-dedup` | false | 是否自动生成幂等去重 ID（基于 payload SHA-256） |
| `remi.event.outbox.worker-threads` | 1 | 投递工作线程数 |
| `remi.event.outbox.fail-on-noop` | true | 检测到 NoopEventPublishGateway 时是否阻止启动 |

## 使用示例

### 1. DomainEvent 集成（推荐）

```java
import com.remisoft.common.domain.event.DomainEvent;
import com.remisoft.common.event.service.OutboxService;

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

### 2. 带优先级、Schema 版本和自定义去重 ID

```java
outboxService.appendToOutbox(
    "Order", order.getId(), "OrderCreated", toJson(order),
    Map.of("source", "api"),                 // headers
    9,                                        // priority (0-9, 9 最高)
    "v2.0.0",                                 // schemaVersion
    "application/vnd.remi.order.v2+json",     // contentType
    "custom-dedup-id"                         // deduplicationId
);
```

### 3. 自定义 EventPublishGateway

```java
import com.remisoft.common.event.gateway.EventPublishGateway;
import com.remisoft.common.event.model.OutboxMessage;

@Component
public class KafkaEventPublishGateway implements EventPublishGateway {

    @Override
    public boolean publish(OutboxMessage message) throws Exception {
        // 投递到 Kafka
        kafkaTemplate.send("remi-outbox-events", message.getEventType(), message.getPayload());
        return true;
    }

    @Override
    public List<Boolean> publishBatch(List<OutboxMessage> messages) throws Exception {
        // 利用 Kafka 批量发送能力
        // ...
    }
}
```

### 4. RocketMQ 自动装配

当 classpath 存在 `rocketmq-spring-boot-starter` 且容器中有 `RocketMQTemplate` Bean 时，`RocketMqEventPublishGateway` 自动注册，无需手动实现：

- Topic: `remi-outbox-events`
- Tag: `eventType`（消费端可按事件类型订阅）
- Body: `payload`
- 用户属性: `headers` / `tenantId` / `traceId` / `deduplicationId`

### 5. 同步投递模式

```yaml
remi:
  event:
    outbox:
      enable-sync-publish: true   # 事务提交后立即投递
```

启用后，`OutboxService` 在事务提交后通过 `TransactionSynchronization.afterCommit` 立即调用 `EventPublishGateway.publish`，减少后台轮询延迟；失败不影响业务事务，仍由后台轮询器兜底重试。

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `EventPublishGateway` | 事件投递网关，将 Outbox 消息投递到消息队列 | 框架内置 `RocketMqEventPublishGateway`、`NoopEventPublishGateway`；业务可自定义 Kafka / Redis Stream 等实现 |
| `EventStore` | 领域事件存储 SPI（来自 common-domain） | 框架内置 `OutboxEventStore`（无其他实现时自动注册）；业务可自定义支持事件回放的实现 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/outbox` | Outbox 健康检查（消息积压监控） | `spring-boot-health` 在 classpath 且 `OutboxRepository` Bean 存在 |

健康检查暴露信息：

| 字段 | 说明 |
|---|---|
| `pending` | PENDING 状态消息数 |
| `processing` | PROCESSING 状态消息数 |
| `deadLetter` | DEAD_LETTER 状态消息数 |
| `pendingThreshold` | PENDING 告警阈值（来自配置） |
| `deadLetterThreshold` | DEAD_LETTER 告警阈值（来自配置） |
| `timestamp` | 检查时间戳 |

状态判定规则：

- `DEAD_LETTER` 数 > `dead-letter-alert-threshold` → DOWN
- `PENDING` 数 > `pending-alert-threshold` → DEGRADED
- `PROCESSING` 数 > `pending-alert-threshold / 2` → DEGRADED（可能有实例宕机）
- 其他 → UP

查询优化：仅统计非 SENT 状态消息（SENT 由清理任务定期删除，不参与健康检查），避免大表 COUNT。

## 注意事项

1. **fail-on-noop 生产必开**：生产环境必须设置 `remi.event.outbox.fail-on-noop=true`，否则检测到 `NoopEventPublishGateway` 时不报错，消息将静默丢失。
2. **Outbox 表必须先建**：模块不会自动建表，需手动执行 `deploy/sql/modules/pm_event_outbox.sql`；表名仅允许字母/数字/下划线，防 SQL 注入。
3. **事务边界**：`OutboxService.appendToOutbox` 必须在业务 `@Transactional` 中调用，确保业务操作与事件写入原子提交；脱离事务调用会导致事件先于业务提交被轮询器投递。
4. **多实例原子 claim**：通过 `UPDATE ... WHERE status='PENDING'` 原子抢占，无需分布式锁；同一消息只会被一个实例处理。
5. **超时回收阈值**：`stale-processing-threshold-minutes` 应大于正常投递耗时，避免误回收正在投递的消息；实例宕机后该阈值决定消息恢复延迟。
6. **幂等去重默认关闭**：`auto-dedup=false` 时业务需自行传入 `deduplicationId`；启用后基于 payload SHA-256 自动生成，相同内容只投递一次。
7. **payload 大小限制**：超过 `max-payload-size-bytes`（默认 4MB）的消息写入时抛异常，防数据库行过大与 MQ 投递失败。
8. **OutboxEventStore 不支持回放**：Outbox 专注 forward-only 投递，`findByAggregate` / `findByType` / `getLatestVersion` 抛 `UnsupportedOperationException`；如需事件回放请实现独立的 `EventStore`。
9. **同步投递不阻塞业务事务**：`enable-sync-publish=true` 时投递在 `afterCommit` 钩子中执行，业务事务已提交，投递失败不影响业务，仍由后台轮询器兜底。

## 变更记录

- **v1.0.0**（2026-08-02）：对标 common-jdbc 标准格式重构 README，补全全部 9 个章节
