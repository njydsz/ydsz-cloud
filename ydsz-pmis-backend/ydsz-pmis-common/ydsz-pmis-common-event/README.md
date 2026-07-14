# ydsz-pmis-common-event

PMIS 公共事件模块 — 事务性 Outbox 模式，保障领域事件可靠投递。

## 核心能力

- **Transactional Outbox 模式**：业务操作与事件写入在同一数据库事务中完成
- **后台轮询投递**：定时扫描 PENDING 消息，通过 `EventPublishGateway` 投递到消息队列
- **指数退避重试**：投递失败自动重试，最大重试次数可配置
- **死信处理**：超过最大重试次数的消息标记为 DEAD_LETTER
- **健康检查**：Micrometer 指标 + HealthIndicator 监控消息积压
- **自动清理**：定期清理已投递的历史消息

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
        // 投递到 RocketMQ / Redis Stream / Kafka
        producer.send(new Message(message.getEventType(), message.getPayload().getBytes()));
        return true;
    }
}
```

### 4. 配置

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
```
