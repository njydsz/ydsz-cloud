# ydsz-common-event 编码规范

本文档定义模块的编码规范，确保代码一致性和可维护性。

---

## 1. Javadoc 规范

### 1.1 类级 Javadoc

所有公开类必须包含类级 Javadoc，包含以下要素：

```java
/**
 * 类的简要描述
 *
 * <p>详细说明（可选，用于复杂类）
 *
 * <p><b>设计说明：</b>
 * <ul>
 *   <li>要点 1</li>
 *   <li>要点 2</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
```

### 1.2 方法级 Javadoc

所有 public/protected 方法必须包含：
- 方法描述（一句话）
- `@param` 每个参数说明
- `@return` 返回值说明（非 void 方法）
- `@throws` 所有受检异常说明
- `@since` 版本号（新增方法）

```java
/**
 * 追加事件到 Outbox
 *
 * @param aggregateType 聚合根类型
 * @param aggregateId   聚合根 ID
 * @param eventType     事件类型
 * @param payload       事件负载（JSON）
 * @throws IllegalArgumentException payload 超过最大限制
 * @since 1.0.0
 */
```

### 1.3 字段注释

- `private static final` 常量：必须注释说明用途
- 实例字段：使用 `/** */` 注释

```java
/** 日志实例 */
private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

/** 位移量上限，防止 1L << retryCount 整数溢出 */
private static final int MAX_SHIFT = 30;
```

---

## 2. 日志规范

### 2.1 日志级别

| 级别 | 使用场景 |
|------|----------|
| TRACE | 开发调试（生产不输出） |
| DEBUG | 关键分支、重要状态变更 |
| INFO | 启动/停止、重要业务流程 |
| WARN | 降级、可恢复异常 |
| ERROR | 不可恢复异常、系统错误 |

### 2.2 占位符格式

使用 SLF4J `{}` 占位符，**禁止字符串拼接**：

```java
// 正确
log.info("Outbox message appended: id={}, type={}, aggregate={}/{}",
        message.getId(), message.getEventType(),
        message.getAggregateType(), message.getAggregateId());

// 错误
log.info("Outbox message appended: id=" + message.getId());
```

### 2.3 日志语言

- **日志消息**：英文或中英混合（确保 Sentry 等系统可搜索）
- **异常信息**：使用 `e.getMessage()`，不强制翻译
- **上下文 ID**：always 包含业务 ID（如 messageId, eventType）

### 2.4 启动/停止日志

模块启动时必须输出 INFO 日志，包含关键配置参数：

```java
log.info("OutboxProcessor started: pollInterval={}s, batchSize={}, workerThreads={}",
        pollInterval, properties.getBatchSize(), properties.getWorkerThreads());
```

---

## 3. Import 规范

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

## 4. 线程池命名

符合云顶编码规范 15.4.4，使用 `ydsz-{module}-{biz}-` 前缀：

```java
// 调度线程（单线程）
Thread t = new Thread(r, "ydsz-outbox-scheduler");

// 投递线程
Thread t = new Thread(r, "ydsz-outbox-worker");
```

---

## 5. 异常处理规范

### 5.1 异常分类

| 类型 | 处理方式 |
|------|----------|
| 可恢复异常（网络超时、连接拒绝） | WARN 日志 + 依赖重试 |
| 不可恢复异常（序列化失败、校验错误） | ERROR 日志 + 快速失败 |

### 5.2 异常抛出

```java
//  IllegalArgumentException（参数错误）
throw new IllegalArgumentException(
        "Outbox payload size " + size + " exceeds maximum " + maxSize);

//  IllegalStateException（配置错误）
throw new IllegalStateException(
        "NoopEventPublishGateway is in use and fail-on-noop=true");
```

### 5.3 异常捕获

捕获异常时记录上下文：

```java
} catch (Exception e) {
    log.warn("Sync publish failed (recoverable), message will be retried: id={}, err={}",
            message.getId(), e.getMessage());
}
```

---

## 6. 方法设计规范

### 6.1 方法长度

单个方法不超过 50 行（不含注释）。超过时拆分为私有方法。

### 6.2 参数数量

- 超过 5 个参数时，使用 Builder 模式或 DTO
- 必须保持向后兼容时，使用 `@Deprecated` 标记旧方法

### 6.3 返回值

- 查询方法返回空集合而非 null（`List.of()` / `Map.of()`）
- 布尔返回值命名：`can`, `has`, `should`, `exists`

---

## 7. 测试规范

### 7.1 命名

- 测试类：`{被测类名}Test`
- 测试方法：`{方法名}_{场景}_{预期行为}`

```java
void appendToOutbox_withOversizedPayload_shouldThrowException()
void processBatch_whenMaxRetriesReached_shouldBeDeadLetter()
```

### 7.2 结构

遵循 Arrange-Act-Assert 模式，使用空行分隔三个阶段：

```java
// Arrange
when(repository.findAll()).thenReturn(List.of());

// Act
service.processAll();

// Assert
verify(publishGateway, times(1)).publish(any());
```

### 7.3 覆盖要求

- 核心业务逻辑：100% 分支覆盖
- 异常路径：每个 `throw` 有对应测试
- 边界条件：null、空集合、极值

---

## 8. 安全规范

### 8.1 SQL 注入

- 必须使用 `NamedParameterJdbcTemplate` 参数化查询
- **禁止**拼接 SQL 字符串

### 8.2 日志脱敏

- 日志中**禁止**输出密码、Token、密钥
- payload 内容按需截断（防止超大 payload 冲爆日志）

---

## 附录：常用缩写

| 缩写 | 含义 |
|------|------|
| MQ | Message Queue（消息队列） |
| CAS | Compare-And-Set |
| SPI | Service Provider Interface |
| DDL | Data Definition Language |
| Javadoc | Java Documentation Comment |
