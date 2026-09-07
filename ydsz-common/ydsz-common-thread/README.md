# ydsz-common-thread

> 共享线程池自动配置（L4 基础数据层）— 按业务隔离 / 监控 / 热更新 / Actuator

提供按业务隔离的线程池注册管理（`ThreadPoolRegistry`）、Micrometer 指标采集（活跃线程 / 队列 / 拒绝 / 计时）、虚拟线程支持、Nacos 配置热更新、自定义 Actuator 端点、HealthIndicator 等能力，是所有业务模块线程池治理的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L4 基础数据层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供线程池注册、监控、热更新、健康检查等企业级能力 |
| **依赖** | ydsz-common-core、spring-context、spring-boot、spring-boot-actuator、spring-boot-actuator-autoconfigure、spring-boot-health、micrometer-core、lombok |
| **版本** | 1.2.0 |

## 核心能力

### 1. 线程池注册与隔离

| 类 | 说明 |
|---|---|
| `ThreadPoolRegistry` | 线程池注册中心（Map<name, ThreadPoolExecutor>，统一管理所有业务线程池） |
| `ThreadPoolExecutorFactory` | 线程池工厂（根据 `ydsz.thread-pool.<name>.*` 配置创建 ThreadPoolExecutor） |
| `ThreadPoolProperties` | 线程池配置属性（`ydsz.thread-pool.global.*` 全局默认 + `ydsz.thread-pool.<name>.*` 业务单独配置） |
| `ThreadPoolRegistrar** | 线程池注册器（自动扫描配置，创建并注册所有线程池） |

**默认线程池**：系统自带 `common`（核心线程 8，最大 32，队列 256）和 `io`（核心线程 16，最大 64，队列 512）两个默认线程池。

### 2. 线程池指标（Micrometer）

| 类 | 说明 |
|---|---|
| `ThreadPoolMetrics` | 线程池指标定义（Gauge / Counter / Timer） |
| `ThreadPoolRegistryMetrics` | 注册表指标（注册线程池数量 / 关闭线程池列表） |
| `ThreadPoolTimerMetrics` | 线程池耗时指标（任务执行延迟 P50/P90/P99） |
| `VirtualThreadMetrics` | 虚拟线程指标（mounted / pinned / paused 计数） |
| `MeteredRejectedHandler` | 指标化的拒绝处理（将 rejectedExecution count 推送到 Micrometer） |
| `MeteredVirtualExecutorService` | 指标化的虚拟线程池 |
| `TimedTaskDecorator` | 任务装饰器（自动测量任务执行耗时并上报 Timer） |

**核心指标**：

| 指标 | 类型 | 说明 |
|---|---|---|
| `thread-pool.active` | Gauge | 活跃线程数 |
| `thread-pool.pool-size` | Gauge | 当前线程数 |
| `thread-pool.queue.size` | Gauge | 队列大小 |
| `thread-pool.completed` | Counter | 已完成任务数 |
| `thread-pool.rejected` | Counter | 拒绝任务数 |
| `thread-pool.task.duration` | Timer | 任务执行耗时（P50/P90/P99） |

### 3. 虚拟线程支持

| 类 | 说明 |
|---|---|
| `VirtualThreadMetrics` | 虚拟线程指标采集 |

**启用虚拟线程**：`ydsz.thread-pool.<name>.virtual=true` 自动选择虚拟线程池实现（基于 JDK 21+ `VirtualThreadExecutor`）。

### 4. 配置热更新（Nacos）

| 类 | 说明 |
|---|---|
| `ThreadPoolHotUpdateAutoConfiguration` | 热更新自动配置 |
| `ThreadPoolHotUpdateListener` | 监听 Nacos 配置变更，动态调整线程池参数（corePoolSize / maxPoolSize / queueCapacity / keepAliveTime） |

### 5. Actuator 与 HealthIndicator

| 类 | 说明 |
|---|---|
| `ThreadPoolMetricsEndpoint` | 自定义 Actuator 端点（`/actuator/thread-pools`，暴露所有线程池实时状态） |
| `ThreadHealthIndicator` | Spring Boot HealthIndicator（所有线程池状态汇总：UP / WARN / DOWN） |

### 6. 内部工具类

| 类 | 说明 |
|---|---|
| `InternalExecutorFactory` | 内部执行器工厂（用于框架内部线程池） |
| `DelegatingTaskExecutor` | 任务执行器装饰器 |
| `ExecutorUtils` | 线程池工具（创建 / 关闭 / 安全停止） |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-thread</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  thread-pool:
    global:
      default-core-size: 8
      default-max-size: 32
      default-queue-capacity: 256
      default-keep-alive-seconds: 60
      default-reject-policy: CALLER_RUNS       # ABORT / DISCARD / DISCARD_OLDEST / CALLER_RUNS
      default-shutdown-await-seconds: 30
      metrics-enabled: true
      health-check-enabled: true
      hot-update-enabled: true                 # Nacos 热更新
    pools:
      notification:
        core-size: 4
        max-size: 16
        queue-capacity: 512
      report:
        virtual: true                          # 启用虚拟线程
        max-size: 200
```

### 3. 直接使用

```java
import com.njydsz.common.base.thread.registry.ThreadPoolRegistry;

// 注入线程池
@Autowired
private ThreadPoolRegistry threadPoolRegistry;

// 获取业务线程池
ThreadPoolExecutor notificationPool = threadPoolRegistry.getExecutor("notification");
notificationPool.execute(() -> sendEmail(...));

// 定时任务装饰（自动上报指标）
TimedTaskDecorator.decorate(pool, () -> heavyTask());
```

## 配置项

### 全局配置（`ydsz.thread-pool.global.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.thread-pool.global.default-core-size` | 8 | 全局默认核心线程数 |
| `ydsz.thread-pool.global.default-max-size` | 32 | 全局默认最大线程数 |
| `ydsz.thread-pool.global.default-queue-capacity` | 256 | 全局默认队列容量 |
| `ydsz.thread-pool.global.default-keep-alive-seconds` | 60 | 全局默认空闲线程存活时间 |
| `ydsz.thread-pool.global.default-reject-policy` | CALLER_RUNS | 全局默认拒绝策略 |
| `ydsz.thread-pool.global.default-shutdown-await-seconds` | 30 | 优雅关闭等待时间 |
| `ydsz.thread-pool.global.metrics-enabled` | true | 是否采集 Micrometer 指标 |
| `ydsz.thread-pool.global.health-check-enabled` | true | 健康检查开关 |
| `ydsz.thread-pool.global.hot-update-enabled` | false | Nacos 热更新开关 |

### 业务线程池配置（`ydsz.thread-pool.pools.<name>.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.thread-pool.pools.<name>.core-size` | - | 核心线程数 |
| `ydsz.thread-pool.pools.<name>.max-size` | - | 最大线程数 |
| `ydsz.thread-pool.pools.<name>.queue-capacity` | - | 队列容量 |
| `ydsz.thread-pool.pools.<name>.keep-alive-seconds` | - | 空闲存活时间 |
| `ydsz.thread-pool.pools.<name>.virtual` | false | 是否启用虚拟线程 |

## SPI 扩展点

无 SPI 接口；通过配置扩展 + `TimedTaskDecorator` 装饰器实现自定义指标采集。

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/thread-pools` | 线程池健康检查 | `ydsz.thread-pool.global.health-check-enabled=true` |
| `/actuator/thread-pools` | 线程池状态详情 | Actuator Web 暴露 |

`ThreadHealthIndicator` 状态判定：
- 所有线程池无拒绝 / 队列使用率 < 80% → UP
- 任意线程池队列使用率 > 80% → WARN
- 任意线程池关闭或不可用 → DOWN

## 自动配置类

| 类 | 触发条件 |
|---|---|
| `ThreadPoolAutoConfiguration` | `ydsz-common-thread` 在 classpath |
| `ThreadPoolHotUpdateAutoConfiguration` | Nacos + hot-update-enabled |

## 注意事项

1. **线程池隔离**：不同业务请使用不同线程池名称，避免慢任务拖垮全局线程池。
2. **虚拟线程限制**：虚拟线程启用需 JDK 21+；固定线程池场景（池大小受限）请根据业务评估。
3. **拒绝策略选择**：关键任务使用 CALLER_RUNS（调用方执行）防丢失；非关键任务使用 DISCARD。
4. **热更新粒度**：仅 corePoolSize / maxPoolSize / queueCapacity / keepAliveTime 支持热更新；拒绝策略需重启生效。

## 变更记录

- **1.2.0**（2026-09-04）：新增 `VirtualThreadMetrics` 虚拟线程指标（mounted / pinned）；新增 `TimedTaskDecorator` 任务装饰器。
- **1.1.0**（2026-09-01）：Nacos 热更新支持；`ThreadPoolRegistry` 注册表重构。
- **1.0.0**（2026-08-02）：初始版本（线程池注册 / 监控 / Actuator）。
