# ydsz-common-event

> Outbox 事务消息模式（L5 业务服务层）— JDBC 落库保障可靠性 + RocketMQ 投递（可降级 Noop）+ 指数退避重试

提供基于 Outbox 模式的领域事件可靠投递能力：业务方法在同一数据库事务中将领域事件写入 outbox 表，`OutboxProcessor` 后台轮询 through outbox 表并向 RocketMQ 投递；投递成功标记为 SENT 并清理；投递失败按指数退避算法重新调度。当 RocketMQ 不可用时，降级为 `NoopEventPublishGateway`（生产环境应配置 `failOnNoop=true` 阻止启动），保障消息不丢。配套健康检查与运维管理服务。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供基于 Outbox 模式的领域事件可靠投递能力，保障数据库事务与消息投递的最终一致性 |
| **依赖** | common-core、common-exception、common-util、common-json、common-thread、common-domain；spring-jdbc、spring-tx；可选依赖 rocketmq-spring-boot-starter、spring-boot-health、micrometer-core、spring-data-commons（Page/Pageable） |
| **版本** | 26.09.01-SNAPSHOT |

## 核心能力

### 1. Outbox 写入

| 类 | 说明 |
|---|---|
| `OutboxService` | Outbox 业务写入服务，提供 `save(DomainEvent)` 方法，在当前事务中插入 outbox 记录，保障事件落库与业务操作的原子性 |
| `OutboxRepository` | Outbox 数据访问层，操作 `ydsz_com_outbox` 表，支持分页查询、状态计数（带缓存 TTL）、CAS 状态更新 |
| `OutboxMessage` | Outbox 消息实体（含 id、aggregateId、eventType、payload、status、retryCount、nextRetryAt 等字段） |
| `OutboxStatus` | 状态枚举：`PENDING`（待投递）、`PROCESSING`（投递中）、`SENT`（已投递）、`FAILED`（失败） |

### 2. Outbox 后台处理器

| 类 | 说明 |
|---|---|
| `OutboxProcessor` | 后台轮询处理器，按 `pollIntervalSeconds` 间隔扫描 PENDING + 到期的 FAILED 记录，批量拉取后调用 `EventPublishGateway` 投递，使用 CAS（乐观锁）防止并发重复投递；支持多 worker 线程并行投递 |

投递流程：

1. 按 batchSize 拉取 PENDING 状态消息（优先）+ 已到重试时间的 FAILED 消息
2. 每条消息 CAS 更新为 PROCESSING 状态（防止并发 worker 重复锁定）
3. 调用 `EventPublishGateway.publish()` 投递到 MQ
4. 成功 → 标记 SENT + 触发清理；失败 → 计算退避时间，更新 retryCount + nextRetryAt

### 3. 投递网关

| 类 | 说明 |
|---|---|
| `EventPublishGateway` | 投递网关接口，定义 `publish(OutboxMessage)` 方法 |
| `RocketMqEventPublishGateway` | RocketMQ 实现，使用 `RocketMQTemplate` 同步发送消息到 `ydsz-outbox-events` topic |
| `NoopEventPublishGateway` | 空操作实现，仅记录日志不实际投递。RocketMQ 不可用时降级兜底 |

网关优先级：

1. 容器中已有的 `EventPublishGateway` Bean（业务模块自定义）
2. `RocketMqGatewayConfiguration`（嵌套配置类）在 `RocketMQTemplate` 存在时自动注册
3. 降级为 `NoopEventPublishGateway`

### 4. 领域事件 SPI

| 类 | 说明 |
|---|---|
| `DomainEvent` | 领域事件接口，业务模块实现，提供 `aggregateId()` / `eventType()` / `occurredAt()` |
| `DomainEventPublisher` | 领域事件发布器，将领域事件通过 Spring `ApplicationEventPublisher` 发布到 Outbox 链路 |
| `DomainEventTypes` | 领域事件类型常量注册中心，业务模块声明自定义事件类型 |

### 5. 运维管理

| 类 | 说明 |
|---|---|
| `OutboxAdminService` | Outbox 运维管理服务，提供按 aggregateId/eventType 查询、手动重试、标记 SENT/FAILED 等运维操作 |

### 6. 自动配置

| 类 | 说明 |
|---|---|
| `EventAutoConfiguration` | 自动配置类，提供 `@PostConstruct` 网关校验（`failOnNoop=true` + Noop 时阻止启动）和 `@PreDestroy` 优雅停机 |
| `EventAutoConfiguration.RocketMqGatewayConfiguration` | 嵌套配置类，`RocketMQTemplate` 存在时注册 `RocketMqEventPublishGateway`，使用嵌套 `@Configuration` 确保条件注解正确生效 |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-event</artifactId>
</dependency>
```

### 2. 数据库建表

```sql
CREATE TABLE ydsz_com_outbox (
    id              VARCHAR(64)   PRIMARY KEY,
    aggregate_id    VARCHAR(128)  NOT NULL,
    aggregate_type  VARCHAR(128),
    event_type      VARCHAR(256)  NOT NULL,
    payload         TEXT          NOT NULL,
    status          VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    retry_count     INT           NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMP,
    idempotency_key VARCHAR(128),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at         TIMESTAMP,
    error_message   VARCHAR(2000),
    INDEX idx_status_next_retry (status, next_retry_at),
    INDEX idx_aggregate (aggregate_id),
    INDEX idx_event_type (event_type)
);
```

### 3. 配置启用

```yaml
ydsz:
  event:
    outbox:
      enabled: true
      fail-on-noop: true    # 生产环境必须 true，防止无 MQ 时消息丢失
```

### 4. 发布领域事件

```java
// 在业务 Service 方法中（同一事务内）
@Service
@Transactional
public class OrderService {

    @Autowired
    private OutboxService outboxService;

    public void createOrder(Order order) {
        // 1. 业务写库
        orderRepository.save(order);

        // 2. 发布领域事件（同一事务内写入 outbox 表）
        outboxService.save(new OrderCreatedEvent(order));
    }
}

// 事件定义
public record OrderCreatedEvent(Order order) implements DomainEvent {
    @Override public String aggregateId() { return order.getId().toString(); }
    @Override public String eventType() { return "order.created"; }
    @Override public Instant occurredAt() { return Instant.now(); }
}
```

## 配置项

### EventProperties（`ydsz.event.outbox.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.event.outbox.enabled` | `true` | 是否启用 Outbox 模式 |
| `ydsz.event.outbox.table-name` | `ydsz_com_outbox` | Outbox 表名 |
| `ydsz.event.outbox.poll-interval-seconds` | `5` | 后台轮询间隔（秒） |
| `ydsz.event.outbox.batch-size` | `100` | 每批最大条数 |
| `ydsz.event.outbox.max-retries` | `5` | 默认最大重试次数 |
| `ydsz.event.outbox.base-backoff-seconds` | `10` | 基础退避秒数（指数退避=base*2^retryCount） |
| `ydsz.event.outbox.max-backoff-seconds` | `3600` | 最大退避秒数（退避上限） |
| `ydsz.event.outbox.sent-retention-days` | `7` | 已投递消息保留天数（0=不清理） |
| `ydsz.event.outbox.auto-cleanup` | `true` | 是否自动清理已投递消息 |
| `ydsz.event.outbox.cleanup-interval-hours` | `6` | 清理间隔（小时） |
| `ydsz.event.outbox.max-payload-size-bytes` | `4194304` (4MB) | 消息 payload 最大字节数 |
| `ydsz.event.outbox.stale-processing-threshold-minutes` | `5` | PROCESSING 状态超时阈值（分钟），超时后回收为 PENDING |
| `ydsz.event.outbox.worker-threads` | `1` | 投递工作线程数 |
| `ydsz.event.outbox.await-termination-seconds` | `10` | 优雅关闭等待超时（秒） |
| `ydsz.event.outbox.fail-on-noop` | `true` | 无 EventPublishGateway 实现时是否阻止启动 |
| `ydsz.event.outbox.status-count-cache-seconds` | `5` | 队列深度统计缓存时间（秒） |

## 使用示例

### 1. 基本领域事件发布

```java
outboxService.save(
    DomainEventBuilder.builder()
        .aggregateId(order.getId().toString())
        .aggregateType("Order")
        .eventType("order.paid")
        .payload(YdszJson.toJson(order))
        .build()
);
```

### 2. 业务模块集成 DomainEventPublisher

```java
// 在领域层使用
domainEventPublisher.publish(new CustomerSignedEvent(customer));
// 事件监听器在同一事务中将事件写入 outbox 表
```

### 3. 运维管理 - 手动重试

```java
@Autowired
private OutboxAdminService adminService;

// 按 aggregateId 查询
List<OutboxMessage> messages = adminService.findByAggregateId("order-123");

// 手动重试单条
adminService.retry(messageId);
```

### 4. 集成 RocketMQ

```xml
<!-- 业务模块 pom.xml 额外引入 -->
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-spring-boot-starter</artifactId>
</dependency>
```

```yaml
# 业务模块 application.yml
rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: ydsz-outbox-producer
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `EventPublishGateway` | 自定义投递网关（如 Kafka / RabbitMQ / gRPC 投递），替换默认 RocketMQ 实现 | 业务模块实现并注册为 Bean |
| `DomainEvent` | 领域事件接口，业务模块定义各自领域事件类型 | 业务模块实现 |
| `DomainEventPublisher` | 领域事件发布器 SPI，自定义发布策略（如事务同步、延迟发布等） | 业务模块实现 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/outbox` | Outbox 健康检查（队列深度、积压率、投递成功率、平均投递延迟等） | `spring-boot-health` 在 classpath + `OutboxRepository` Bean 存在 |

`OutboxHealthIndicator` 暴露信息：

| 字段 | 说明 |
|---|---|
| `pendingCount` | PENDING 状态积压量 |
| `processingCount` | 投递中的消息量 |
| `failedCount` | FAILED 状态消息量 |
| `staleProcessingCount` | PROCESSING 超时（> threshold）的消息量 |
| `publishSuccessRate` | 最近窗口投递成功率 |
| `avgPublishLatencyMs` | 平均投递延迟（毫秒） |
| `status` | 健康状态（UP / DEGRADED / DOWN） |

降级判定：

- PENDING 积压量 > 10,000 → DEGRADED
- 投递成功率 < 95% → DEGRADED
- PROCESSING 超时率 > 10% → DEGRADED
- 数据库连接异常 / 投递网关根本不可用 → DOWN

## 自动配置类

| 类 | 说明 |
|---|---|
| `EventAutoConfiguration` | 核心自动配置，条件：`JdbcTemplate` Bean 存在 + `ydsz.event.outbox.enabled=true` |
| `EventAutoConfiguration.RocketMqGatewayConfiguration` | 嵌套配置类，条件：`RocketMQTemplate` 在 classpath 且 Bean 存在时注册 |

装配条件：

| Bean | 条件 |
|---|---|
| `OutboxRepository` | 无自定义 + `JdbcTemplate` 存在（由外部条件保证） |
| `OutboxService` | 无自定义 + `OutboxRepository` 存在 |
| `RocketMqEventPublishGateway` | 无自定义 `EventPublishGateway` + `RocketMQTemplate` 在 classpath 且 Bean 存在 |
| `NoopEventPublishGateway` | 无自定义 `EventPublishGateway` + `RocketMQTemplate` 不可用 |
| `OutboxProcessor` | 始终创建（构造时确定网关引用），initMethod=start |
| `OutboxHealthIndicator` | spring-boot-health 在 classpath |
| `OutboxAdminService` | 无自定义 |

## 注意事项

1. **fail-on-noop 必须开启**：生产环境必须保持 `failOnNoop=true`，防止 RocketMQ 不可用时 Outbox 处理器静默降级为 Noop，导致消息堆积且无告警。
2. **指数退避有上限**：`baseBackoff * 2^retryCount` 超过 `maxBackoffSeconds` 时取上限值，避免重试间隔无限增长导致消息长时间无法重试。
3. **索引必须创建**：`idx_status_next_retry` 复合索引是轮询查询的性能核心，未创建会导致全表扫描。
4. **事务一致性保证**：`outboxService.save()` 必须在业务方法的同一事务中调用（`@Transactional`），确保业务数据与 outbox 消息同时提交或同时回滚。
5. **PROCESSING 超时回收**：投递过程中应用崩溃会导致消息卡在 PROCESSING 状态，`stale-threshold-minutes` 参数控制超时回收阈值，超时后回收为 PENDING 重新投递。
6. **清理任务有延迟**：`auto-cleanup=true` 默认每 6 小时执行一次，可能有最多数小时的 SENT 记录残留，非实时清理不影响投递语义。
7. **多个 worker 需防并发**：`worker-threads > 1` 时通过 CAS 更新 PROCESSING 状态防止并发重复投递，但不可跨 JVM，分布式场景需额外分布式锁。
8. **payload 大小限制**：`maxPayloadSizeBytes=4MB`，超过限制的事件在写入阶段抛出异常；大消息建议只传引用 ID，消费端通过 ID 查询详情。
9. **网关覆盖优先级**：业务模块自定义 `EventPublishGateway` Bean 优先于所有内置实现；自定义 Bean 注册时应使用 `@ConditionalOnMissingBean` 避免覆盖。
10. **优雅停机有超时**：`awaitTerminationSeconds=10`，停机时 OutboxProcessor 最多等待 10 秒让正在投递的消息完成，超时后强制中断，投递中的消息将保持 PROCESSING 状态等待下次启动回收。

## 变更记录

- **26.09.01**（2026-09-01）：对标 common-jdbc 标准格式重构 README，补全全部章节。
- **26.09.01**（2026-08-20）：移除 JSON Schema 校验框架和同步投递模式的自动配置，精简职责；移除已废弃的 `EventStore` 接口支持，统一使用 `DomainEventPublisher`；将 `RocketMqGatewayConfiguration` 由独立顶层配置类改为嵌套配置类，修复 `@Import` 导致条件注解失效问题。
- **26.09.01**（2026-08-02）：初始版本，提供 OutboxService + OutboxProcessor + RocketMqEventPublishGateway + NoopEventPublishGateway。
