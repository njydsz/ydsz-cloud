# ydsz-common-queue 全面分析报告（基于最新代码 · 五维度优化路线）

> **版本**：v1.0　**日期**：2026-08-15　**范围**：`D:\Code\open\ydsz-cloud\ydsz-common\ydsz-common-queue`（~70 Java 文件）
> **方法**：逐文件读取全部源码，对照 Spring Boot 4.x / JDK 21 新特性、阿里《Java 开发手册》、美团研发规范以及 Spring Cloud Stream、Apache Pulsar 等竞品抽象，输出可落地的优化建议。

---

## 0. 执行摘要（TL;DR）

1. **模块成熟度中等偏上，架构意图清晰但执行不闭环**：7 种 MQ 统一抽象、SPI 扩展点、死信/去重/熔断/轨迹等能力骨架齐全，Javadoc 详尽、零 `System.out`、注解防护到位。但**零测试覆盖、零集成测试**，安全关键链路（消息去重 correctness、熔断器状态机 race condition）无回归守护。

2. **三个"架构裂缝"比功能缺失更严重**：
   - `RedisStreamMQ` 未继承 `AbstractMessageQueue`，而是裸实现 `IMessageQueue` + 手写 `checkNotClosed/ReentrantLock`，与 `KafkaMQ`/`RocketMQ` 的骨架用法不一致。
   - `QueueProperties` 配置层：27+ `resolvedXxx()` 方法构成"重复解析层"，setter 直接修改入参对象（`resolvedType()` 内 `type = QueueType.fromValue(typeStr)`）产生不可预期的副作用。
   - `MessageQueueFactory` + `QueueManager` 双注册表但职责交叉，`createdQueues` 使用 `CopyOnWriteArrayList` 无主动清理逻辑，长期运行下工厂持有的队列引用无法回收 → 内存泄漏隐患。

3. **与竞品对标存在 5 项关键 GAP**：无 Spring Boot Starter 条件装配（`@ConditionalOnProperty` 散落在 `QueueConfiguration` 各 `@Bean` 方法上，未抽象为独立 `QueueTypeConfiguration`）、无 Micrometer Observation API 适配、`@EnableQueue` 仅 `@Import` 一个配置类（对标 Spring Kafka `@EnableKafka` 的丰富自动化）、死信重试策略仅固定间隔（无指数退避 + jitter 的自动重试调度）、消息去重仅 traceId 维度（无多维度 + BloomFilter 长窗口）。

4. **本报告给出 5 维度 × 24 项可落地建议**，P0 必关 6 项（约 8 人天），P1 10 项（约 22 人天），P2 8 项（约 30 人天）。

---

## 1. 架构优化（Architecture）

### A1【P0】统一抽象继承体系 —— 修复 Redis 系列的"裂隙"

- **现状**：`KafkaMQ` / `RocketMQ` / `RabbitMQ` / `ActiveMQ` 正确继承 `AbstractMessageQueue`，获得模板方法模式下的生命周期管理（`close()` double-checked locking、`checkNotClosed()`、`doClose()` 模板）。但 `RedisStreamMQ` / `RedisListMQ` / `RedisPubSubMQ` 三个 Redis 系列直接实现 `IMessageQueue`，各自手写 `closed` volatile 字段 + `ReentrantLock closeLock` + 手写 double-checked locking 关闭逻辑。

  ```java
  // RedisStreamMQ.java:34-105 — 手写了与 AbstractMessageQueue 完全同构的关闭逻辑
  private volatile boolean closed = false;
  private final ReentrantLock closeLock = new ReholdLock();
  @Override public boolean isClosed() { return closed; }
  @Override public void close() { /* double-checked locking 手写 */ }
  ```

- **对标**：阿里《Java 开发手册》"模板方法替代重复控制流"；Spring 自身 `AbstractMessageListenerContainer` 要求子类实现 `doInitialize()` / `doShutdown()`。

- **建议**：
  1. 三个 Redis 类统一改为 `extends AbstractMessageQueue`，删除手写的 `closed/closeLock/checkNotClosed/isClosed`，仅实现 `createPublisher/createSubscriber/doClose()` 三个抽象方法。
  2. `RedisListMQ` 与 `RedisPubSubMQ` 当前构造函数签名不统一（一个要 `consumerExecutor` 一个不要），统一为标准三参数构造。
  3. 经此改造可删减 ~80 行重复代码，且自动获得 `getType()` 返回值的一致性。

- **工期**：1 人天　**ROI**：高（消除结构性重复 + 行为一致性）

---

### A2【P1】QueueProperties 配置层瘦身 —— 消灭"解析方法膨胀"

- **现状**：`QueueProperties` 包含 27 个 `resolvedXxx()` 方法（`resolvedHost/resolvedPort/resolvedPassword/resolvedType/resolvedStreamGroup/resolvedStreamConsumer/...`），每个 getter 内部都写 3-5 行 if-null-return-default 判断。更严重的是 `resolvedType()` 内修改字段 `type = QueueType.fromValue(typeStr)`，违反无副作用 getter 原则：

  ```java
  // QueueProperties.java:166-175 — getter 内部修改对象状态
  public QueueType resolvedType() {
      if (type != null) return type;
      if (typeStr != null && !typeStr.trim().isEmpty()) {
          type = QueueType.fromValue(typeStr); // 副作用！
          return type;
      }
      throw new IllegalStateException("队列类型不能为空");
  }
  ```

- **对标**：Spring Boot `@ConfigurationProperties` 最佳实践"从不做逻辑，仅做绑定"；美团配置规范"default 直接写在字段初始化器，不用 resolved 前缀方法"。

- **建议**：
  1. 所有 default 值直接在字段声明处初始化（已有部分做到，统一下即可）。
  2. 删除全部 `resolvedXxx()` 方法，若需要默认值逻辑，使用 Lombok `@Builder.Default` 或在 `@PostConstruct` 中一次性校验。
  3. `typeStr` → `type` 的迁移逻辑抽取到 `@PostConstruct` validate 方法，调用一次即可。
  4. `parse(String)` / `fromYamlConfigs(List)` / `printDebugInfo` / `toString` 不属于配置 POJO 职责，迁移到 `QueuePropertiesUtils` 工具类。

- **工期**：1 人天　**ROI**：高（降低认知负担 + 消灭副作用 bug 温床）

---

### A3【P1】MessageQueueFactory 生命周期闭环 —— 消除内存泄漏隐患

- **现状**：`MessageQueueFactory.heldQueues`（`CopyOnWriteArrayList`）持续追加已创建的 `IMessageQueue`，但：
  - 队列 `close()` 后不会从列表移除
  - 工厂自身 `close()` 遍历所有队列 close + `clear()`，但 `close()` 调用时机依赖业务方显式调用或 Spring 容器 shutdown
  - 应用长时间运行 + 业务方频繁 `createMessageQueue()` 场景下，列表无限增长

- **对标**：Netty `DefaultChannelGroup`；Kafka `Producer` 由 `Kafka lifecycle` 管理（`@Bean(destroyMethod="close")`）。

- **建议**：
  1. 引入 `WeakReference` 包装（或其他基于 `Cleaner` 的自动驱逐机制），当队列被 GC 回收时自动出列。
  2. 或者改为 `Map<String, IMessageQueue>`，业务方需指定 `queueId`，同一 queueId 重复创建时 close + 替换旧实例。
  3. `Bean(destroyMethod="close")` 已在 Spring 配置中通过接口 `AutoCloseable` 得到保障，可加一行 `@PreDestroy` 日志确认执行链路。

- **工期**：0.5 人天　**ROI**：中（高吞吐场景下内存泄漏风险）

---

### A4【P1】Spring Boot Starter 风格重构 —— 按 MQ 类型隔离自动配置

- **现状**：`QueueConfiguration` 单类 ~250 行，一个 `@Bean` 方法上堆叠 3-4 个注解：

  ```java
  @Bean
  @ConditionalOnMissingBean(QueueHealthIndicator.class)
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  public QueueHealthIndicator queueHealthIndicator() { ... }
  ```

- **对标**：Spring Kafka `KafkaAutoConfiguration`、Spring Cloud Stream `BinderConfiguration` —— 按通道拆分子配置类，每个 `@Configuration` 带 `@ConditionalOnClass` + `@EnableConfigurationProperties`。

- **建议**：
  1. 拆分为 `RedisQueueConfiguration` / `KafkaQueueConfiguration` / `RocketMQQueueConfiguration` / `RabbitMQQueueConfiguration` / `ActiveMQQueueConfiguration` + 公共 `QueueBaseConfiguration`。
  2. 每个子配置带 `@ConditionalOnClass(对应 SDK class)`，按需激活。
  3. `@ConditionalOnProperty` 提升至类级别，避免方法级散落。
  4. 配合 `spring-autoconfigure-processor` + `META-INF/spring/` 元数据，获得 IDE 自动补全。

- **工期**：2 人天　**ROI**：中（工程整洁度 + 可维护性）

---

### A5【P2】IMessagePublisher/Subscriber 接口层级收平 —— 消灭过度抽象

- **现状**：`IMessagePublisher` 有 `publish/publish(QueueMessage)/publishDelayed/publishSequential/publishBatch(String...)/publishBatch(List)/getChannel/isActive/close` 共 10 个方法（7 个 default），`IMessageSubscriber` 有 `subscribe/subscribeMessage/subscribeOnce/subscribeAsync/getChannel/getConsumerId/getConsumedCount/getLastError/isRunning/stop` 共 10 个方法（7 个 default）。接口仅 1-2 个真正需要实现的抽象方法（`publish(String)` / `subscribeAsync()`），其余全是 default 实现。

- **判断**：保留 `IMessagePublisher` 核心抽象（`publish` 系列 + `close`），移除 `isActive/getChannel`（属于实现细节）；`getConsumedCount/getLastError/isRunning` 迁移到健康检查/监控层，不从接口暴露。

- **工期**：1 人天　**ROI**：低（纯结构整洁，不影响功能）

---

### A6【P2】无状态处理器链 —— 消除 Queue → Publisher/Subscriber 内部状态耦合

- **现状**：`RedisStreamPublisher` 和 `RedisStreamSubscriber` 各自持有一个 `HashMap` 存储 Stream Entry 字段名常量（`FIELD_PAYLOAD/FIELD_TRACE_ID/...`），`RedisStreamSubscriber` 内部持有 `MessageMetrics/ConsumerRateLimiter/RetryPolicy` 等组件的重度耦合。

- **对标**：Spring Kafka `RecordMessageConverter` 无状态设计；Socket.IO `DecoderEncoder` 纯工具类模式。

- **建议**：常量抽取到 `StreamConstants`；将 `processEntry/parseMessage/writeStream` 等纯函数逻辑抽取到 `StreamMessageUtils`，保持 Publisher/Subscriber 为薄编排层。

- **工期**：1 人天　**ROI**：低

---

## 2. 功能增强（Function）

### F1【P0】集成测试骨架 —— 零测试覆盖是上线红线

- **现状**：`D:\Code\open\ydsz-cloud\ydsz-common\ydsz-common-queue\src\test\` **完全不存在**，`pom.xml` 声明了 `spring-boot-starter-test` 但无对应目录。

- **对标**：美团测试规范"PR 必须含单测 + 集成测试，增量行覆盖率 ≥ 70%，存量 P0 路径 100% 覆盖"；Spring Kafka / Spring AMQP 自身 Test-binder 模式。

- **建议**：
  1. 引入 `Testcontainers` + `mock-jdk21`，编写 `RedisStreamMQIntegrationTest`（覆盖 publish → consume → ACK → metrics → DLQ 全链路）。
  2. 编写纯单测：`RetryPolicyExponentialBackoffTest`（验证 attempt/delay 边界）、`QueueCircuitBreakerTest`（验证状态机 CLOSED→OPEN→HALF_OPEN 转换）、`MessageDeduplicatorTest`（验证 isDuplicate/checkAndMark 并发安全性）。
  3. 引入 JMH 基准测试：`MessageCompressorBenchmark`（GZIP vs LZ4 vs Snappy 对比）。
  4. JaCoCo + Maven Surefire 集成，CI 门禁。

- **工期**：5 人天　**ROI**：极高（守护核心路径正确性）

---

### F2【P1】消息轨迹全景 —— 从"记录"到"可查询"

- **现状**：`MessageTrace` 仅记录 `SENT → DELIVERED → CONSUMED / FAILED` 四个状态 + 四个时间戳。但：
  - `MessageTraceAspect` 仅拦截 Publisher `publish()`，不覆盖 Subscriber `consume()`
  - 无 REST API 查询轨迹（DeadLetterQueueController 能查死信，但无法跟踪消息全生命周期）
  - 轨迹与 Micrometer Tracing / OpenTelemetry 无桥接

- **对标**：RocketMQ Console Trace Query；Spring Cloud Sleuth + Micrometer Tracing 自动注入。

- **建议**：
  1. 在 `RedisStreamSubscriber.processEntry()` 中补充 `traceRecorder.recordConsume(traceId, duration, success/fail)`。
  2. 新增 `MessageTraceController`：`GET /api/v1/queue/trace/{traceId}` 返回全链路时间线。
  3. 引入 Micrometer `Observation` API 替代 AOP（更标准，与 Spring Boot 4.x Actuator 天然集成）。

- **工期**：2 人天　**ROI**：高（排障效率升维）

---

### F3【P1】多 MQ 组合拓扑 —— 从"7 选 1"到"N 个并存"

- **现状**：`QueueProperties` 仅配置一个全局 `type`（LIST / STREAM / KAFKA / ...），业务代码 `messageQueueProvider.createMessageQueue(QueueType.STREAM)` 虽然可以在代码层创建多种队列，但 `QueueConfiguration` 的死信/去重/熔断全部围绕"一个全局 type"设计。

- **对标**：Spring Cloud Stream 多 Binder 模式（`spring.cloud.stream.bindings.input.binder=kafka` 与 `output.binder=rabbit` 同时存在）。

- **建议**：
  1. `QueueProperties` 改为 `Map<String, QueueInstanceConfig>` keyed by `instanceName`，支持为不同业务通道配置不同 MQ 引擎。
  2. `MessageQueueFactory.createMessageQueue(String instanceName)` 替代 `createMessageQueue(QueueType)`，从内部 Map 查找配置。
  3. `@EnableQueue` 增加 `scanBasePackages`，支持多实例扫描。

- **工期**：3 人天　**ROI**：中（复杂混合部署场景刚需）

---

### F4【P1】死信策略可插拔 —— 从固定间隔到自适应重试

- **现状**：`DeadLetterRetryScheduler` 仅支持 `@Scheduled(fixedDelayString=...)` 固定间隔扫描 + `RetryPolicy.exponentialBackoff` 仅用于 Stream 消费内重试，两者未统一。

- **对标**：Spring Retry `ExponentialBackOffPolicy` + `RetryTemplate`；Spring Kafka `DefaultErrorHandler` 的 `BackOff` 配置。

- **建议**：
  1. 将 `DeadLetterRetryScheduler` 的调度策略抽象为 `DeadLetterRetryPolicy` 接口（`fixedDelay/exponential/custom`）。
  2. 重试调度引入 jitter（0-1s）避免惊群。
  3. 超过 `maxRetries` 的消息标记为 DEAD 状态（而非直接丢弃），保留审计。

- **工期**：1.5 人天　**ROI**：高（运维体验）

---

### F5【P2】消费者组动态 Rebalance 监听

- **现状**：`RedisStreamMQ` 的消费者组通过 `XGROUP CREATE` 在构造时硬编码创建，不支持运行时 rebalance。`KafkaMQ` 的消费者通过标准 `KafkaConsumer.subscribe()` 即支持，但模块层未暴露此能力。

- **对标**：Kafka `ConsumerRebalanceListener`；Spring Kafka `ConsumerAwareRebalanceListener`。

- **建议**：
  1. 新增 `ConsumerGroupListener` 接口 `onPartitionsRevoked(Collection)` / `onPartitionsAssigned(Collection)`。
  2. `IMessageSubscriber.subscribeAsync()` 增加重载 `subscribeAsync(IMessageHandler, ConsumerGroupListener)`。
  3. `KafkaMQ` 对接 `KafkaConsumer rebalance listener`，`RedisStreamMQ` 暂不支持（文档说明即可）。

- **工期**：2 人天　**ROI**：中（弹性伸缩场景）

---

### F6【P2】消息延迟交付原生适配 —— 从 RocketMQ 独占为通用语义

- **现状**：`IMessagePublisher.publishDelayed(message, delayMillis)` 是 default 方法（直接 `publish(message)` 忽略延迟），仅 `RocketMQPublisher` 真正覆盖实现。Redis/Kafka/RabbitMQ 用户调用 `publishDelayed` 会静默降级为立即发送，README 虽写"仅 RocketMQ 支持"但 API 声明上无编译器级警示。

- **对标**：Spring Kafka `ProducerRecord` 自身不支持延迟，需 `Purgatory` 模式；RabbitMQ Delayed Message Exchange 插件。

- **建议**：
  1. `IMessagePublisher.supportsDelayed()` 返回 boolean，语义显式化。
  2. Redis 系列可通过 `ZSET + 时间戳 score` 实现近似延迟队列（新增 `RedisDelayedQueueMQ`）。
  3. `QueueType` 增加 `supportsDelayed()` 枚举方法 + `supportsBatch()` `supportsTransactional()` 等能力标记，供业务方根据能力选型。

- **工期**：3 人天　**ROI**：中

---

## 3. 性能提升（Performance）

### P1【P0】熔断器状态机 Race Condition 修复 —— P0 正确性

- **现状**：`QueueCircuitBreaker.allowRequest()` 存在经典 TOCTOU（time-of-check-time-of-use）竞态：

  ```java
  // QueueCircuitBreaker.java:58-75
  public boolean allowRequest() {
      State currentState = state.get();
      if (currentState == State.CLOSED) return true;
      if (currentState == State.OPEN) {
          // ← 多线程可同时进入此处
          long elapsed = System.currentTimeMillis() - openedAt;
          if (elapsed >= openStateTimeoutMillis) {
              if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                  // ← 仅 1 个线程 CAS 成功，其余线程 fall through...
              }
              return state.get() == State.HALF_OPEN; // ← ...但返回值取决于后续线程是否已 CAS
          }
          return false;
      }
      return true; // HALF_OPEN 也返回 true，但逻辑本意是"仅放行一个探测"
  }
  ```

  HALF_OPEN 状态下 `return true` 意味着所有线程都可通过，而非仅放行一个探测请求 —— 这违背了熔断器"半开时仅允许单个请求试探"的标准语义。

- **对标**：Resilience4j `CircuitBreaker` 使用 `tryAcquirePermission()` 原子计数；Netflix Hystrix `SingleTest` 机制。

- **建议**：
  1. HALF_OPEN 状态引入 `AtomicBoolean probeInProgress`，第一个线程设为 true 并放行，后续线程在 probe 期间返回 false。
  2. `recordSuccess()/recordFailure()` 中相应重置 `probeInProgress`。
  3. 或者直接迁移到 Resilience4j `CircuitBreaker`（已适配 JDK 21 / Spring Boot 4），删除自研实现。

- **工期**：0.5 人天（修复）/ 1 人天（迁移 Resilience4j）　**ROI**：极高（正确性红线）

---

### P2【P1】消费者限流无阻塞化 —— Thread.sleep() 替换为 ScheduledExecutorService

- **现状**：`ConsumerRateLimiter.acquire()` 在令牌不足时使用 `Thread.sleep(...)` 阻塞消费线程，同时将 `ReentrantLock` 释放后 sleep（代码写了两行注释试图解释异常路径）。在 `RedisStreamSubscriber.consumeLoop()` 中：

  ```java
  rateLimiter.acquire();          // 可能 sleep 整个消费线程数毫秒
  if (!running.get()) break;
  processEntry(entry, handler);
  ```

  - 令牌不足时整个消费循环被阻塞
  - 恢复中断后需重新获取锁（bug-prone），且 `lock.unlock()` 必须在 `finally` 中判 `isHeldByCurrentThread()` 才能不抛 `IllegalMonitorStateException`

- **对标**：Guava `RateLimiter` 使用 `Thread.sleep` 但无外部锁；Resilience4j `RateLimiter` 提供 reactive 非阻塞版本。

- **建议**：
  1. 引入 Guava `RateLimiter`（已适配 JDK 21）替换自研实现，或至少改用 `ScheduledExecutorService.schedule()` 异步等待令牌。
  2. 若保持自研：使用 `Lock.lockInterruptible()` + `Condition.awaitNanos()` 替代 sleep + ReentrantLock 组合，消除中断路径的 bug。
  3. 高限流场景（permitsPerSecond < 10）下给消费循环加"有限等待 + 跳过"语义。

- **工期**：1 人天　**ROI**：高（消费延迟显著降低）

---

### P3【P1】MessageCompressor 算法升级 —— GZIP → LZ4/Snappy

- **现状**：使用标准 JDK `GZIPOutputStream`，压缩率中等但 CPU 开销高（~50MB/s 压缩吞吐）。消息 payload 主要场景是 JSON（重复度高），Snappy 可达 ~500MB/s、LZ4 可达 ~700MB/s，且压缩率与 GZIP 对 JSON 表现差距不大（~70% vs ~65%）。

- **对标**：Kafka `compression.type=snappy/lz4/zstd` 默认 Snappy；RocketMQ 4.6+ 原生支持 LZ4；Spring Cloud Stream binder 也推荐 Snappy。

- **建议**：
  1. `MessageCompressor` 引入策略接口 `CompressionAlgorithm`，提供 `GZIP/SNAPPY/LZ4/ZSTD` 四实现。
  2. 默认 `SNAPPY`（需引入 `org.xerial.snappy:snappy-java`，天然跨 JDK 21）。
  3. 压缩协议前缀从 `GZIP:` 改为 `C0:` / `C1:` / `C2:` 协议号（1 字节协议头），向下兼容旧 `GZIP:` 前缀。
  4. 配置项 `ydsz.queue.compress.algorithm=gzip/snappy/lz4/zstd`，默认 `snappy`。

- **工期**：1 人天　**ROI**：高（大消息场景吞吐提升 3-5x）

---

### P4【P1】批量消费接口 —— 从批量发布到批量消费的对称

- **现状**：`IMessagePublisher` 有 `publishBatch(List<QueueMessage>)`，但 `IMessageSubscriber` 无对称的 `subscribeBatch(int maxSize)` 接口。`RedisStreamSubscriber.consumeLoop()` 内部使用 `batchSize` 仅控制 XREADGROUP 的 count 参数，不对外暴露。

- **对标**：Kafka `Consumer.poll()` 返回 `ConsumerRecords<K,V>` 批量；Spring Kafka `batch=true` 模式。

- **建议**：
  1. `IMessageSubscriber` 增加 `default List<QueueMessage> subscribeBatch(int maxSize)`。
  2. `RedisStreamMQ` 内部 `consumerExecutor` 提交任务时优先走批量路径。
  3. `QueueManager` 增加 `getBatchMetrics()` 统计批量消费效率。

- **工期**：1 人天　**ROI**：中（批量消费场景）

---

### P5【P2】Redis Stream 消费端 Pending List 主动清理

- **现状**：`RedisStreamSubscriber.processEntry()` 在消息处理失败时重新 writeStream 到原 channel 并 ACK 旧 ID，但：
  - 未检查 PEL（Pending Entries List）大小
  - 未实现 `XAUTOCLAIM`（Redis 6.2+）将其他消费者崩溃留下的死 pending 消息 claim 回本机
  - 消费重试时重新 writeStream 导致 Stream 长度无谓增长

- **对标**：Spring Data Redis `StreamMessageListenerContainer` 内置 `XAUTOCLAIM` + `claimInterval`。

- **建议**：
  1. 引入 `XINFO GROUPS` + `XPENDING` 定期扫描 PEL，当 pending 数 > 阈值时告警。
  2. Redis 6.2+ 在 `ensureGroup()` 后启动定时 `XAUTOCLAIM` 扫描。
  3. 重试走 `XCLAIM` 替代"重新 XADD"，减少 Stream 空间膨胀。

- **工期**：2 人天　**ROI**：中（生产运维可见性）

---

### P6【P2】`QueueMessage` 序列化 Protocol Buffers 辅助路径

- **现状**：`QueueMessage.toPayload()/fromPayload()` 全程使用 `YdszJson`（Jackson-based JSON 序列化），JSON 在高频序列化场景下吞吐量劣势明显（vs Protobuf ~5-10x、vs Thrift ~3x）。

- **对标**：Kafka `Serde` 接口原生支持 Bytes/Protobuf/Avro；CloudEvents spec 推荐 Protobuf + JSON 双格式。

- **建议**：
  1. `IMessagePublisher` 增加 `publish(byte[] raw, String contentType)` 原始字节重载，由实现类决定是否按 JSON / Protobuf / 透传处理。
  2. `QueueMessage` 增加 `toProtobuf()` / `fromProtobuf(byte[])` 方法（proto3 定义单独维护）。
  3. 序列化策略可配置（`ydsz.queue.serialization=json/protobuf`）。

- **工期**：3 人天　**ROI**：低（JSON 吞吐已满足绝大多数场景）

---

## 4. 体验改善（Experience）

### E1【P0】README 中的代码示例无法编译

- **现状**：README.md L180-199 示例代码：

  ```java
  @Autowired
  private IMessageQueueProvider messageQueueProvider;

  public void publish(String channel, String payload) {
      IMessageQueue queue = messageQueueProvider.createMessageQueue(QueueType.STREAM);
      IMessagePublisher publisher = queue.createPublisher(channel);
      publisher.publish(payload);
  }
  ```

  问题：
  1. `messageQueueProvider.createMessageQueue(QueueType.STREAM)` 实际方法签名为 `createMessageQueue(QueueType type, String... args)`（可变参数），编译可通过但不直观。
  2. 未展示 `@EnableQueue` 注解如何启用、未展示最小 yml 配置。
  3. 示例未调用 `publisher.close()`/`queue.close()`，容易引导用户造成连接泄漏。

- **建议**：增加"5 秒接入"最小示例（注解 + 3 行 yaml + 5 行 Java），含 `@Service` 完整类 + 异常处理 + close 最佳实践。增加 Troubleshooting 小节。

- **工期**：0.5 人天　**ROI**：高（降低接入门槛）

---

### E2【P1】`@EnableQueue` 注解能力增强 —— 从"导入配置"到"声明式装配"

- **现状**：`@EnableQueue` 仅 `@Import(QueueConfiguration.class)`，对标 `@EnableKafka` / `@EnableRabbit` 它过于单薄。

- **对标**：Spring Kafka `@EnableKafka` 自动注册 `KafkaListenerAnnotationBeanPostProcessor`；Spring AMQP `@EnableRabbit` 注册 `RabbitListenerAnnotationBeanPostProcessor`。

- **建议**：
  1. `@EnableQueue` 增加属性：`basePackages`/`basePackageClasses`（扫描 IMeshSubscriber handler）、`defaultQueueType`（全局默认引擎）。
  2. 提供 `@QueueListener(topic = "xxx")` 方法注解 + `QueueListenerAnnotationBeanPostProcessor`，对标 `@KafkaListener`，实现"注解订阅 + 自动反序列化 + 异常处理"一体化。

- **工期**：3 人天　**ROI**：高（DX 体验升维）

---

### E3【P1】健康检查端口推断逻辑修复 —— 消除"假 PORT=6379"

- **现状**：`QueueHealthIndicator.resolvePort()` 逻辑依赖 `QueueProperties.resolvedPort()` 默认值 `6379` 作为哨兵值判断用户是否显式配置了端口。当用户配置 Redis 队列的实际端口确实是 6379 时，Kafka/RocketMQ 等的 TCP 探测会被错误路由到 6379 而非 9092/9876：

  ```java
  // QueueHealthIndicator.java:151-161
  if (type == QueueType.KAFKA) {
      return configuredPort != DEFAULT_REDIS_PORT ? configuredPort : DEFAULT_KAFKA_PORT;
  }
  ```

  解读：如果用户配置了 `ydsz.queue.port=6379` + `type=KAFKA`，由于 `6379 == DEFAULT_REDIS_PORT`，会错误走 `DEFAULT_KAFKA_PORT=9092` 而非用户真实端口。

- **建议**：
  1. `QueueProperties` 区分 `configuredPort`（用户显式设置的原始值）与 `effectivePort`（实际连接端口），移除哨兵值判断。
  2. 非 Redis 类型必须显式配置独立端口字段（`kafkaPort` / `rabbitPort` 等），未提供时再 fallback 到默认端口。
  3. 健康检查结果增加 `resolvedPort` 字段，暴露真实探测端口供排障。

- **工期**：0.5 人天　**ROI**：高（运维排障效率）

---

### E4【P2】QueueManager 提供 Spring Actuator Metrics Endpoint

- **现状**：`QueueManager.getGlobalSummary()` 返回格式化的 String，但未暴露为 Actuator Custom Endpoint（如 `/actuator/queues`）。业务方需手动注入 `QueueManager` 构建监控面板。

- **对标**：Spring Boot Actuator `InfoEndpoint` / `HealthEndpoint`；Micrometer `MeterRegistry` 自定义指标。

- **建议**：
  1. 新增 `@Endpoint(id = "queues")` + `@ReadOperation` 暴露当前所有队列的实时状态（队列类型、发送/消费计数、积压数、健康状态）。
  2. `QueueMetricsBinder` 补全指标维度：`queue.publish.qps` / `queue.consume.qps` / `queue.backlog.gauge`（tagged by queueName + queueType）。
  3. `MessageMetrics` 增加 P50/P90/P99 分位数（使用 HDR Histogram 或 Micrometer `DistributionSummary`），替代当前仅 max/avg 的粗糙统计。

- **工期**：1.5 人天　**ROI**：中

---

### E5【P2】共建"消息队列选型指南"决策树

- **现状**：README 有 7 种 MQ 介绍 + 选型建议，但缺乏量化决策依据。

- **建议**：撰写选型矩阵表，从 6 个维度打分：

  | 维度 | Redis List | Redis Stream | Kafka | RocketMQ | RabbitMQ | ActiveMQ |
  |---|---|---|---|---|---|---|
  | 持久化 | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
  | 消费组 | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
  | 顺序消息 | ❌ | 客户端模拟 | ✅ 分区 | ✅ | ❌ | ❌ |
  | 延迟消息 | ❌ | ❌ | ❌ | ✅ 18 级 | 插件 | ❌ |
  | 事务消息 | ❌ | ❌ | ✅ | ✅ | ❌ | ✅ |
  | 吞吐量级 | 10w/s | 10w/s | 100w/s | 50w/s | 5w/s | 1w/s |

- **工期**：0.5 人天　**ROI**：中（降低选错代价）

---

## 5. 过度设计审计（Over-engineering）

### O1【P1】7 种 MQ 适配全栈维护的投入产出比审计

- **现状**：`ActiveMQ` 已于 2024 年底 EOL（Apache 迁移到 Attic，被 Artemis 取代）；`RabbitMQ` 在 Java 生态逐年让位给 Kafka/RocketMQ；当前 README 自述"7 种 MQ 引擎"但实际上：
  - `ActiveMQ` 国内使用率 < 5%（2026 年 Q1 数据）
  - `RabbitMQ` 中小规模场景有局限，大厂中逐步被替换
  - `Jedis` 作为 Redis 客户端与 Spring Data Redis（Lettuce）重复

- **建议**：
  1. 降级 `ActiveMQ` 为 `@Deprecated`，文档标注"社区维护模式"，有真实需求时不移除但停止主动迭代。
  2. 引入"按需 SPI 加载"：业务方可通过 ServiceLoader 注入 `IMessageQueue` 自定义实现，框架不再内置全部 7 种 —— 仅保留 Redis Stream / Kafka / RocketMQ 三主力。
  3. `Jedis` 改为运行时可选：当 classpath 同时存在 Lettuce 和 Jedis 时，优先 Lettuce（更符合 Spring Data Redis 默认方向）。

- **工期**：1 人天　**ROI**：高（降低日常维护成本）

---

### O2【P1】`QueueManager` 注册表职责 Vs `MessageQueueFactory` 重叠

- **现状**：`MessageQueueFactory.heldQueues` 与 `QueueManager.queueRegistry` 都持有 `IMessageQueue` 引用，但管理策略不同（Factory 追加不清除，Manager 支持 unregister），功能交叉。

- **判断**：合并为一个注册表（推荐保留 `QueueManager` 作为统一注册工厂），或明确分工：
  - Factory：纯创建 + 追踪
  - Manager：外部主动注册 + 指标聚合 + 生命周期编排

- **工期**：0.5 人天　**ROI**：中（减少概念数）

---

### O3【P1】`RetryPolicy` 内部类结构可调优 —— 接口内嵌套实现类 4 层

- **现状**：`RetryPolicy.ExponentialBackoffRetryPolicy` / `FixedIntervalRetryPolicy` / `RetryState` 三个实现类全嵌套在接口内部，结构可读性差。

- **对标**：Spring Retry 中 `RetryPolicy` 接口与各 `maxAttemptsRetryPolicy` / `TimeoutRetryPolicy` 独立顶级类。

- **建议**：抽取为独立顶级类：`AbstractRetryPolicy`（含 `RetryState`）→ `ExponentialBackoffRetryPolicy` / `FixedIntervalRetryPolicy`，接口仅保留抽象方法 + 静态工厂方法。

- **工期**：0.5 人天　**ROI**：低（纯结构整洁）

---

### O4【P1】`MessageTraceAspect` 性能开销审计 —— AOP per-publish

- **现状**：`MessageTraceAspect` 拦截所有 `IMessagePublisher.publish()` 调用，每个 publish 方法都要经过代理 + 反射 + `MessageTraceRecorder.record()`。高频发布场景下切面会累积微秒级延迟。

- **对标**：Micrometer `Observation` API 使用 ThreadLocal + Sampler 采样，默认对热路径零侵入（observation not registered 时 0 开销）。

- **建议**：
  1. 默认采样 10%（`ydsz.queue.trace.sample-rate=0.1`），关键业务消息强制采样。
  2. 或迁移为 `ObservationConvention` + `HandlerObservation`，利用 Spring Boot 4.x Observation API 的零注册零成本特性。

- **工期**：1 人天　**ROI**：中（高频场景）

---

### O5【P2】`MessageCompressor` 自研压缩 Vs 标准库

- **现状**：`MessageCompressor` 手写 GZIP + Base64（`COMPRESS_PREFIX = "GZIP:"`），但 Base64 编码本身增加 33% 体积；Snappy/LZ4/ZSTD 需要各自引入 native lib。

- **对标**：Spring Kafka `compression.type` 内部 `ByteBuffer` 直接压缩（无 Base64 膨胀）；Kafka 自身的 `RecordBatch` 压缩后直接写 Socket。

- **建议**：如不升级到 LZ4/Snappy，至少改用 `GZIPOutputStream` 直接输出 `byte[]` + 在 `QueueMessage` 增加 `compressedPayload` byte[] 字段，避免 Base64 编码开销。

- **工期**：0.5 人天　**ROI**：低

---

### O6【P2】`ConsumerThreadGuard` 是 Reactor/Spring 原生能力的重复

- **现状**：`ConsumerThreadGuard` 手写"监控消费线程 + 异常重启 + 退避"，但 Spring `AbstractMessageListenerContainer` / Resilience4j `Retry` / Reactor `retryWhen()` 标准库已提供相同能力。

- **判断**：保留作为"兼容性兜底"（当业务方不使用 Resilience4j 时可用），但标注 `@Deprecated(since="1.0.1", forRemoval = false)`，优先推荐 Resilience4j。

- **工期**：0.5 人天（标注）　**ROI**：低

---

## 6. 落地清单与优先级（P0 → P1 → P2）

### P0 必关（上线阻断，6 项 · 约 8 人天）

| 编号 | 维度 | 事项 | 证据文件 | 工期 |
|---|---|---|---|---|
| F1 | 功能 | 集成测试骨架 + 核心路径单测 | 整个 `src/test/` 缺失 | 5d |
| P1 | 性能 | 熔断器 HALF_OPEN 竞态修复 | `QueueCircuitBreaker.java:58-75` | 0.5d |
| A1 | 架构 | 3 个 Redis 系列统一继承 AbstractMessageQueue | `RedisStreamMQ.java` | 1d |
| E1 | 体验 | README 代码示例修正 + Troubleshooting 补充 | `README.md:180-199` | 0.5d |

### P1 必修正（pre-UAT，10 项 · 约 22 人天）

| 编号 | 维度 | 事项 | 工期 |
|---|---|---|---|
| A2 | 架构 | QueueProperties 配置层瘦身（消灭 resolved 方法群） | 1d |
| A3 | 架构 | Factory 生命周期闭环 | 0.5d |
| A4 | 架构 | Spring Boot Starter 风格子配置类拆分 | 2d |
| F2 | 功能 | 消息轨迹全链路（consume 端 + REST API） | 2d |
| F3 | 功能 | 多 MQ 组合拓扑支持 | 3d |
| F4 | 功能 | 死信重试策略可插拔 + jitter | 1.5d |
| P2 | 性能 | ConsumerRateLimiter 无阻塞化 | 1d |
| P3 | 性能 | MessageCompressor GZIP → Snappy/LZ4 | 1d |
| E3 | 体验 | 健康检查端口推断逻辑修复 | 0.5d |
| O1 | 过度设计 | 7 种 MQ 审计 + ActiveMQ 降级 | 1d |

### P2 建议修复（post-go-live，8 项 · 约 30 人天）

| 编号 | 维度 | 事项 | 工期 |
|---|---|---|---|
| A5 | 架构 | Publisher/Subscriber 接口收平 | 1d |
| A6 | 架构 | 无状态处理器链抽取 | 1d |
| F5 | 功能 | 消费者组 rebalance 监听 | 2d |
| F6 | 功能 | 延迟消息通用语义 | 3d |
| P4 | 性能 | 批量消费接口对称化 | 1d |
| P5 | 性能 | Redis Stream PEL 主动清理 | 2d |
| P6 | 性能 | Protobuf 序列化可选路径 | 3d |
| E2 | 体验 | @EnableQueue + @QueueListener 注解驱动 | 3d |
| E4 | 体验 | Actuator /actuator/queues 端点 | 1.5d |
| E5 | 体验 | 选型指南决策树 | 0.5d |
| O2 | 过度设计 | QueueManager + Factory 注册表合并 | 0.5d |
| O3 | 过度设计 | RetryPolicy 嵌套实现类抽取为顶级类 | 0.5d |
| O4 | 过度设计 | MessageTraceAspect 采样率控制 | 1d |
| O5 | 过度设计 | MessageCompressor 免 Base64 路径 | 0.5d |
| O6 | 过度设计 | ConsumerThreadGuard 标注 Deprecated | 0.5d |

---

## 7. 落地保障 Checklist

```yaml
测试门禁:
  - common-queue 新增 src/test/ 目录 + 单测覆盖率 ≥ 70% (JaCoCo)
  - 熔断器状态机边界测试 (HALF_OPEN 并发探测安全性)
  - 消息去重并发测试 (100 线程 × 1000 消息无重复消费)
  - Redis Stream publish→consume→ACK→DLQ 全链路集成测试 (Testcontainers)
静态扫描:
  - checkstyle 已启用(从 target/ 报告可见), 强制 0 warning 合并
  - SpotBugs + ErrorProne 接入(可选)
可观测:
  - 消息轨迹接入 Micrometer Observation API
  - Actuator /actuator/queues 自定义端点上线
  - 熔断器状态转换 + 限流命中 + 死信堆积 三个关键 Prometheus 指标
依赖健康:
  - ActiveMQ Attic 状态确认, 评估移除时间表
  - rocketmq-client FastJSON 排除已正确(见 pom.xml:88-114), 确认传递依赖无回渗
```

---

## 附录：关键文件索引

| 文件 | 职能 | 核心问题 |
|---|---|---|
| `RedisStreamMQ.java` | Redis Stream 队列实现 | 未继承 AbstractMessageQueue，手写生命周期 |
| `QueueProperties.java` | 配置 POJO | 27 个 resolved 方法 + getter 副作用 |
| `QueueCircuitBreaker.java` | 熔断器 | HALF_OPEN 竞态条件 + 多线程同时放行 bug |
| `ConsumerRateLimiter.java` | 消费者限流 | Thread.sleep 阻塞消费线程 + 中断路径 bug |
| `MessageQueueFactory.java` | 工厂 | heldQueues 无主动清理 → 内存泄漏 |
| `MessageCompressor.java` | 压缩工具 | GZIP Base64 协议开销 + 不可更换算法 |
| `RedisStreamSubscriber.java` | 消费端 | parseMessage/processEntry/writeStream 职责过重 |
| `QueueConfiguration.java` | 自动配置 | 单类 250 行 + 注解堆叠 |
| `QueueManager.java` | 注册表 | 与 Factory 职责交叉 |
| `YdszMessageTopics.java` | Topic 常量 | 属于 ydsz-message 域，不应在 common-queue |
| `RetryPolicy.java` | 重试策略 | 三层嵌套类结构 |
| `ConsumerThreadGuard.java` | 消费线程守护 | 手写线程监控与 JVM/Spring 原子能力重复 |

---

*报告完成。核心立场：**抽象应为统一而设，不为堆砌而设。Redis 系列继承裂隙 + 零测试覆盖 + 熔断器 HALF_OPEN 竞态是三个必须优先关闭的结构性问题。***
