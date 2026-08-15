# ydsz-common-event 模块运维指南

## 模块概述

`ydsz-common-event` 实现了 Transactional Outbox 模式，保障领域事件在微服务架构中的可靠投递。

核心能力：
- 事务内事件写入（业务写操作与 Outbox 消息写入同一数据库事务）
- 后台轮询器异步投递到 RocketMQ
- 指数退避重试 + 死信管理
- 多实例并发安全（原子 claim）
- Micrometer 指标 + Actuator 健康检查

---

## 配置参考

```yaml
ydsz:
  event:
    outbox:
      enabled: true                    # 是否启用 Outbox（默认 true）
      table-name: ydsz_outbox         # Outbox 表名
      poll-interval-seconds: 5        # 轮询间隔（秒）
      batch-size: 100                 # 每批最大条数
      max-retries: 5                  # 默认最大重试次数
      base-backoff-seconds: 10        # 基础退避秒数
      max-backoff-seconds: 3600       # 最大退避秒数（1 小时）
      sent-retention-days: 7          # 已投递消息保留天数
      auto-cleanup: true              # 是否启用自动清理
      cleanup-interval-hours: 6       # 清理间隔（小时）
      max-payload-size-bytes: 4194304 # 消息 payload 最大字节数（4MB）
      default-priority: 5             # 默认优先级（0-9，9 最高）
      default-schema-version: v1.0.0  # 默认 Schema 版本号
      stale-processing-threshold-minutes: 5  # PROCESSING 超时阈值（分钟）
      pending-alert-threshold: 10000  # PENDING 积压告警阈值
      dead-letter-alert-threshold: 10 # DEAD_LETTER 告警阈值
      enable-tenant-isolation: true   # 是否启用租户隔离
      enable-sync-publish: false      # 是否启用同步投递模式
      auto-dedup: false               # 是否自动生成幂等去重 ID
      worker-threads: 1               # 投递工作线程数
      fail-on-noop: true              # 检测到 Noop 网关时是否启动失败
      enable-domain-event-publish: true  # 是否发布 Spring 事件（供进程内订阅）
      status-count-cache-seconds: 5   # 队列深度统计缓存时间（秒）
```

---

## 数据库初始化

根据数据库类型选择对应的 DDL 脚本：

| 数据库 | DDL 文件 |
|--------|----------|
| PostgreSQL 16+ | `db/outbox_postgresql.sql` |
| MySQL 8.0+ | `db/outbox_mysql.sql` |
| Oracle 19c+ | `db/outbox_oracle.sql` |

---

## Micrometer 指标

模块暴露以下 Prometheus 指标：

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `ydsz.outbox.publish.success` | Counter | 投递成功次数 |
| `ydsz.outbox.publish.failure` | Counter | 投递失败次数 |
| `ydsz.outbox.dead_letter` | Counter | 进入死信的次数 |
| `ydsz.outbox.publish.single.duration` | Timer | 单条投递耗时（含 P50/P90/P99） |
| `ydsz.outbox.publish.batch.duration` | Timer | 批量投递耗时（含 P50/P90/P99） |
| `ydsz.outbox.queue.size` | Gauge | 队列深度（按 status 标签区分） |

---

## Actuator 健康检查

访问 `GET /actuator/health/outbox` 获取 Outbox 健康状态：

```json
{
  "status": "UP",
  "details": {
    "pending": 12,
    "processing": 3,
    "deadLetter": 0,
    "pendingThreshold": 10000,
    "deadLetterThreshold": 10,
    "timestamp": "2026-08-15T10:30:00Z"
  }
}
```

健康状态判定规则：
- `UP`：PENDING 和 DEAD_LETTER 均在阈值内
- `DEGRADED`：PENDING 超过阈值，或 PROCESSING 超过阈值的一半
- `DOWN`：DEAD_LETTER 超过阈值

---

## Grafana 面板配置

以下 PromQL 查询可用于构建 Outbox 监控面板：

```promql
# 队列深度（按状态）
ydsz_outbox_queue_size

# 投递速率（成功 / 失败）
rate(ydsz_outbox_publish_success_total[5m])
rate(ydsz_outbox_publish_failure_total[5m])

# 死信产生速率
rate(ydsz_outbox_dead_letter_total[5m])

# 投递延迟 P99
histogram_quantile(0.99, rate(ydsz_outbox_publish_single_duration_seconds_bucket[5m]))
```

---

## 使用示例

### 1. 发布领域事件（通过 OutboxService）

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OutboxService outboxService;

    @Transactional
    public void createOrder(OrderCreateDTO dto) {
        Order order = orderMapper.insert(dto);

        // 同一事务写入 Outbox
        outboxService.appendToOutbox(
            "Order", order.getId(), "OrderCreated",
            YdszJson.toJson(order)
        );
    }
}
```

### 2. 订阅跨模块事件（进程内）

```java
@Component
public class OrderEventListener {
    @Async
    @EventListener(condition = "#message.eventType == 'ORDER_CREATED'")
    public void onOrderCreated(OutboxMessage message) {
        // 处理订单创建事件
    }
}
```

### 3. 死信管理

```java
@Autowired
private OutboxAdminService adminService;

// 查询死信列表
Page<OutboxMessage> deadLetters = adminService.listDeadLetters(0, 20, null);

// 手动重试
adminService.retryDeadLetter("msg-001");

// 批量重试某类事件
adminService.retryAllDeadLetters("OrderCreated");
```

---

## 故障排查

### 消息积压（PENDING 持续增长）

1. 检查 RocketMQ 是否可用：`ydsz.outbox.publish.failure` 是否持续增加
2. 检查投递线程是否阻塞：`ydsz.outbox.publish.single.duration` P99 是否异常升高
3. 增加 `worker-threads` 提升投递并发

### 死信（DEAD_LETTER）产生

1. 查看死信错误信息：`SELECT id, event_type, error_message FROM ydsz_outbox WHERE status = 'DEAD_LETTER'`
2. 分析错误类型：网络超时（可恢复）vs 序列化失败（不可恢复）
3. 可恢复错误修复后通过 `OutboxAdminService.retryDeadLetter` 手动重试

### 健康检查 DOWN

1. 检查 `deadLetter` 是否超过 `dead-letter-alert-threshold`
2. 检查数据库连接是否正常
3. 检查 Outbox 表是否存在且索引完整
