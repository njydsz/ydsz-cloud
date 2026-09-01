# ydsz-common-event

YDSZ 通用事件模块——事务性 Outbox 模式，保障领域事件在微服务架构中的可靠投递。

## 模块概述

基于 Transactional Outbox 模式实现，核心能力：

- 事务内事件写入（业务写操作与 Outbox 消息写入同一数据库事务）
- 后台轮询器异步投递到 RocketMQ
- 指数退避重试 + 死信管理
- 多实例并发安全（原子 claim）
- Micrometer 指标 + Actuator 健康检查

## 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-event</artifactId>
</dependency>
```

### 基本使用

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OutboxService outboxService;

    @Transactional
    public void createOrder(OrderCreateDTO dto) {
        Order order = orderMapper.insert(dto);

        // 同一事务写入 Outbox
        outboxService.appendToOutbox(OutboxMessage.builder()
                .aggregateType("Order")
                .aggregateId(order.getId())
                .eventType("OrderCreated")
                .payload(YdszJson.toJson(order)));
    }
}

// 订阅跨模块事件
@Component
public class OrderEventListener {
    @Async
    @EventListener
    public void onOrderCreated(OutboxMessage message) {
        // 处理订单创建事件
    }
}
```

### 数据库初始化

根据数据库类型选择 DDL 脚本：

| 数据库 | DDL 文件 |
|--------|----------|
| PostgreSQL 16+ | `src/main/resources/db/outbox_postgresql.sql` |
| MySQL 8.0+ | `src/main/resources/db/outbox_mysql.sql` |

## 配置参考

```yaml
ydsz:
  event:
    outbox:
      enabled: true                    # 是否启用（默认 true）
      table-name: ydsz_com_outbox         # Outbox 表名
      poll-interval-seconds: 5        # 轮询间隔（秒）
      batch-size: 100                 # 每批最大条数
      max-retries: 5                  # 默认最大重试次数
      base-backoff-seconds: 10        # 基础退避秒数
      max-backoff-seconds: 3600       # 最大退避秒数（1 小时）
      sent-retention-days: 7          # 已投递消息保留天数
      auto-cleanup: true              # 是否启用自动清理
      cleanup-interval-hours: 6       # 清理间隔（小时）
      max-payload-size-bytes: 4194304 # 消息 payload 最大字节数（4MB）
      stale-processing-threshold-minutes: 5  # PROCESSING 超时阈值（分钟）
      worker-threads: 1               # 投递工作线程数
      await-termination-seconds: 10   # 优雅关闭等待超时（秒）
      fail-on-noop: true              # 检测到 Noop 网关时是否启动失败
      status-count-cache-seconds: 5   # 队列深度统计缓存时间（秒）
```

详见 [运维手册](src/main/resources/db/OUTBOX_README.md)（含 Grafana 面板配置、故障排查、Actuator 健康检查）。

---

## 编码规范

本文档定义模块的编码规范，确保代码一致性和可维护性。

### 1. Javadoc 规范

#### 类级 Javadoc

所有公开类必须包含类级 Javadoc：

```java
/**
 * 类的简要描述
 *
 * <p>详细说明（可选）
 *
 * @author ydsz-team
 * @since 26.09.01
 */
```

#### 方法级 Javadoc

所有 public/protected 方法必须包含：
- 方法描述（一句话）
- `@param` 每个参数说明
- `@return` 返回值说明（非 void 方法）
- `@throws` 所有受检异常说明
- `@since` 版本号（新增方法）

#### 字段注释

- `private static final` 常量：必须注释说明用途
- 实例字段：使用 `/** */` 注释

---

### 2. 日志规范

#### 日志级别

| 级别 | 使用场景 |
|------|----------|
| TRACE | 开发调试（生产不输出） |
| DEBUG | 关键分支、重要状态变更 |
| INFO | 启动/停止、重要业务流程 |
| WARN | 降级、可恢复异常 |
| ERROR | 不可恢复异常、系统错误 |

#### 占位符格式

使用 SLF4J `{}` 占位符，**禁止字符串拼接**：

```java
// 正确
log.info("Outbox message appended: id={}, type={}, aggregate={}/{}",
        message.getId(), message.getEventType(),
        message.getAggregateType(), message.getAggregateId());

// 错误
log.info("Outbox message appended: id=" + message.getId());
```

#### 日志语言

- 日志消息：英文或中英混合（确保 Sentry 等系统可搜索）
- 异常信息：使用 `e.getMessage()`，不强制翻译
- 上下文 ID：总是包含业务 ID（如 messageId, eventType）

---

### 3. Import 规范

遵循 Java import 三段式分隔：

```java
// 1. Java 标准库
import java.time.Instant;
import java.util.List;

// 2. 第三方库
import org.slf4j.Logger;
import org.springframework.context.ApplicationEventPublisher;

// 3. 项目内部
import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.config.EventProperties;
```

- 三段之间空一行分隔
- 不使用 `import *`
- 按字母顺序排列

---

### 4. 线程池命名

符合云顶编码规范 15.4.4，使用 `ydsz-{module}-{biz}-` 前缀：

```java
// 调度线程（单线程）
Thread t = new Thread(r, "ydsz-outbox-scheduler");

// 投递线程
Thread t = new Thread(r, "ydsz-outbox-worker");
```

---

### 5. 异常处理规范

#### 异常分类

| 类型 | 处理方式 |
|------|----------|
| 可恢复异常（网络超时、连接拒绝） | WARN 日志 + 依赖重试 |
| 不可恢复异常（序列化失败、配置错误） | ERROR 日志 + 快速失败 |

#### 异常抛出

```java
// IllegalArgumentException（参数错误）
throw new IllegalArgumentException(
        "Outbox payload size " + size + " exceeds maximum " + maxSize);

// IllegalStateException（配置错误）
throw new IllegalStateException(
        "NoopEventPublishGateway is in use and fail-on-noop=true");
```

#### 异常捕获

捕获异常时记录上下文：

```java
} catch (Exception e) {
    log.warn("Publish failed (recoverable), message will be retried: id={}, err={}",
            message.getId(), e.getMessage());
}
```

---

### 6. 方法设计规范

- 单个方法不超过 50 行（不含注释），超过时拆分为私有方法
- 超过 5 个参数时，使用 Builder 模式或 DTO
- 查询方法返回空集合而非 null（`List.of()` / `Map.of()`）
- 布尔返回值命名：`can`, `has`, `should`, `exists`

---

### 7. 测试规范

#### 命名

- 测试类：`{被测类名}Test`
- 测试方法：`{方法名}_{场景}_{预期行为}`

```java
void appendToOutbox_withOversizedPayload_shouldThrowException()
void processBatch_whenMaxRetriesReached_shouldBeDeadLetter()
```

#### 结构

遵循 Arrange-Act-Assert 模式，使用空行分隔三个阶段。

#### 覆盖要求

- 核心业务逻辑：100% 分支覆盖
- 异常路径：每个 `throw` 有对应测试
- 边界条件：null、空集合、极值

---

### 8. 安全规范

- SQL 注入：必须使用 `NamedParameterJdbcTemplate` 参数化查询，**禁止**拼接 SQL 字符串
- 日志脱敏：日志中**禁止**输出密码、Token、密钥

---

## 架构说明

### 核心组件

| 组件 | 说明 |
|------|------|
| `OutboxService` | 事件写入入口，事务内写 Outbox 表 |
| `OutboxProcessor` | 后台轮询器，扫描 PENDING 消息并投递 |
| `EventPublishGateway` | 投递网关 SPI（RocketMQ / Noop 实现） |
| `OutboxRepository` | 数据访问层，JDBC 操作 |
| `OutboxAdminService` | 死信运维管理 |

### 状态流转

```
PENDING → PROCESSING → SENT
   ↑          │
   └──────────┘ (投递失败，指数退避重试)
   │
   └──→ DEAD_LETTER (超过最大重试次数)
```

### 时序图

```
业务代码                OutboxService            数据库              OutboxProcessor         MQ
   │                       │                      │                      │                    │
   │── @Transactional ───▶│                      │                      │                    │
   │   appendToOutbox()    │                      │                      │                    │
   │                       │── INSERT Outbox ────▶│                      │                    │
   │                       │                      │                      │                    │
   │   事务提交            │                      │                      │                    │
   │                       │── afterCommit() ──────────────────────────▶│                    │
   │                       │                      │                      │── SELECT PENDING ─▶│
   │                       │                      │                      │── UPDATE PROCESSING▶│
   │                       │                      │                      │── publish() ───────▶│
   │                       │                      │                      │── UPDATE SENT ─────▶│
```

---

## 监控

### Micrometer 指标

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `ydsz.outbox.publish.success` | Counter | 投递成功次数 |
| `ydsz.outbox.publish.failure` | Counter | 投递失败次数 |
| `ydsz.outbox.dead_letter` | Counter | 进入死信的次数 |
| `ydsz.outbox.publish.single.duration` | Timer | 单条投递耗时 |
| `ydsz.outbox.publish.batch.duration` | Timer | 批量投递耗时 |
| `ydsz.outbox.queue.size` | Gauge | 队列深度（按 status 标签区分） |

### Actuator 健康检查

访问 `GET /actuator/health/outbox` 获取健康状态（详见 [运维手册](src/main/resources/db/OUTBOX_README.md)）。

---

## 版本变更

### 26.09.01 (2026-08-16)

**精简重构：去除过度设计，聚焦核心 Outbox 模式**

- **移除 JSON Schema 校验框架**：删除 `JsonSchemaRegistry`、`JsonSchemaValidator` 等 5 个 SPI 类（原框架无实际实现，属于幽灵 SPI）
- **移除同步投递模式**：删除 `doSyncPublish`、`registerSyncPublishCallback`、`isRecoverableException` 等方法及相关配置（与 Outbox 异步本质冲突）
- **EventProperties 配置瘦身**：从 25+ 个字段精简至 16 个核心配置，移除未验证/占位配置项
- **DatabaseDialect 枚举移除**：所有数据库方言生成相同 SQL（`LIMIT ?`），删除抽象层
- **OutboxMessage 字段精简**：移除 `headers`、`schemaVersion`、`contentType`、`priority` 字段及 `OutboxMessageDraft` 类
- **DomainEvent 精简**：移除 `Serializable` 接口和 `Clock` 参数
- **DDL 简化**：同步移除对应列定义和索引条件
- **新增**：`await-termination-seconds` 配置支持优雅关闭超时自定义

---

## 附录：常用缩写

| 缩写 | 含义 |
|------|------|
| MQ | Message Queue（消息队列） |
| CAS | Compare-And-Set |
| SPI | Service Provider Interface |
| DDL | Data Definition Language |
| Outbox | 事务性发件箱 |

---

## 许可证

MIT License
