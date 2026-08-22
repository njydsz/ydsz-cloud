# ydsz-common-thread

> L4 基础数据层共享线程池自动配置与监控 — 配置驱动、按业务隔离、Micrometer 指标、优雅关闭、健康检查一体化。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L4 基础数据层（`ydsz-common` 公共依赖） |
| **类型** | 公共依赖库（不独立部署，随业务模块打包） |
| **作用** | 为业务模块提供共享线程池的自动配置、运行时监控、健康检查与优雅关闭，以及编程式线程池工厂与可观测执行器 |
| **依赖** | 直接依赖 ydsz-common-core、spring-context、spring-boot、jackson-annotations（optional）；可选依赖 micrometer-core、spring-boot-actuator、spring-boot-health、lombok（provided） |
| **提供能力** | ① 配置驱动线程池（`ydsz.thread.pools.<name>`） ② 编程式工厂（`ExecutorUtils`） ③ 可观测执行器（`MeteredThreadPoolExecutor`） |
| **激活方式** | Spring Boot 自动装配（`AutoConfiguration.imports`），默认启用 |
| **版本** | 1.0.0 |

## 快速开始（TL;DR）

**1. 添加依赖**

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-thread</artifactId>
</dependency>
```

**2. 配置线程池**

```yaml
ydsz:
  thread:
    pools:
      io:
        type: PLATFORM
        core-size: 8
        max-size: 32
        queue-capacity: 200
        thread-name-prefix: ydsz-io-
        reject-policy: CALLER_RUNS
```

**3. 注入使用**

```java
@Resource(name = "ioExecutor")
private ThreadPoolTaskExecutor ioExecutor;
```

**完成。** 线程池会自动注册 Micrometer 指标、健康检查与优雅关闭钩子。

---

## 核心能力

### 1. 线程池自动配置

`ThreadPoolAutoConfiguration` 结合 `ThreadPoolRegistrar`（`BeanDefinitionRegistryPostProcessor`）基于 `ydsz.thread.pools` 配置动态注册多个线程池 Bean，业务代码通过 `@Resource(name = "xxxExecutor")` 注入即可使用。

- **按业务隔离**：每个线程池独立的 `coreSize` / `maxSize` / `queueCapacity` / `rejectPolicy` / `threadNamePrefix` 等参数
- **平台线程 + 虚拟线程双模式**：`type=PLATFORM` 创建 `ThreadPoolTaskExecutor`；`type=VIRTUAL` 创建 `Executors.newThreadPerTaskExecutor`（JDK 21+，每任务一虚拟线程）
- **Bean 名称规则**：Bean 名称为 `beanNamePrefix + key + "Executor"`（前缀默认为空字符串，推荐使用 `ydsz-` 前缀避免命名冲突）
- **优雅关闭**：平台线程池 `shutdown` 时自动等待任务完成
- **Micrometer 指标自动绑定**：`MeterRegistry` 可用时为每个平台线程池注册核心 5 项指标，可选开启详细 3 项指标（见「可观测性」）
- **健康检查**：`ThreadHealthIndicator` 类已提供，但当前**未注册为自动装配 Bean**（`AutoConfiguration.imports` 仅装配 `ThreadPoolAutoConfiguration`），如需使用请业务侧显式声明

**内部核心类**：

| 类 | 说明 |
|---|---|
| `ThreadPoolRegistrar` | `BeanDefinitionRegistryPostProcessor`，扫描 `ydsz.thread.pools` 配置动态注册线程池 Bean 定义 |
| `ThreadPoolExecutorFactory` | 线程池工厂，实现 `ApplicationContextAware`，根据 `PoolType` 创建 `ThreadPoolTaskExecutor` 或虚拟线程执行器；解析 `task-decorator-bean-names` 配置注入 `TaskDecorator` |
| `TimedTaskDecorator` | 任务装饰器，记录任务提交时间戳到 `TimedTask` 上下文，供 `ThreadPoolTimerMetrics` 计算耗时（静态 `AtomicReference` 已移除，改为不可变对象传递） |
| `ThreadPoolHotUpdateAutoConfiguration` | 热更新自动配置（`ydsz.thread.hot-update.enabled=true` 时激活），注册 `ThreadPoolHotUpdateListener` 支持运行时动态调参 |

### 2. 线程池配置属性

`ThreadPoolProperties` 绑定 `ydsz.thread` 前缀，支持按 Map 配置多个命名线程池，每个线程池独立配置 `PoolConfig`。支持两种线程池类型（`PoolType.PLATFORM` / `PoolType.VIRTUAL`）与四种拒绝策略（`RejectPolicy.ABORT` / `CALLER_RUNS` / `DISCARD_OLDEST` / `DISCARD`）。

### 3. 健康检查

> **注意**：`ThreadHealthIndicator` 当前不会被自动装配（未在自动配置中注册 Bean），健康检查能力为预留。若需使用，请业务侧自行注册 Bean。

平台线程池上报 `active` / `queueSize` / `poolSize` / `completed` / `threadNamePrefix` 详情；虚拟线程池上报类型标识与存活状态。任一线程池获取底层 `ThreadPoolExecutor` 失败时整体状态为 `DOWN`。

### 4. 动态调参（热更新）

通过配置 `ydsz.thread.hot-update.enabled=true` 启用 `ThreadPoolHotUpdateListener`，支持运行时通过 Nacos / Spring Cloud Config 动态调整线程池参数，包括 `coreSize` / `maxSize` / `rejectPolicy` / `threadNamePrefix`，无需重启应用。

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-thread</artifactId>
</dependency>
```

模块已通过 `AutoConfiguration.imports` 自动装配，引入依赖后无需额外 `@EnableXxx` 注解。默认 `ydsz.thread.enabled=true`，如需关闭可设为 `false`。

### 2. 配置启用

```yaml
ydsz:
  thread:
    enabled: true
    bean-name-prefix: ydzs-           # 推荐：所有线程池 Bean 名称前缀（避免与业务 Bean 命名冲突）
    hot-update:
      enabled: true                   # 可选：启用热更新监听器
    pools:
      io:
        type: PLATFORM
        core-size: 8
        max-size: 32
        queue-capacity: 200
        thread-name-prefix: ydsz-io-
        reject-policy: CALLER_RUNS
        await-termination-seconds: 60
        allow-core-thread-time-out: false
        keep-alive-seconds: 60
        slow-task-threshold-ms: 3000   # 可选：慢任务阈值（默认 5000）
        enable-detailed-metrics: true # 可选：启用详细指标（默认 false）
        task-decorator-bean-names:    # 可选：跨线程传播上下文
          - mdcTaskDecorator
          - requestContextTaskDecorator
      cpu:
        type: PLATFORM
        core-size: 4
        max-size: 4
        queue-capacity: 100
        thread-name-prefix: ydsz-cpu-
        reject-policy: ABORT
      virtual-io:
        type: VIRTUAL
        thread-name-prefix: ydzs-virtual-io-
```

## 配置项

### 顶层配置（`ydsz.thread`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.thread.enabled` | `true` | 是否启用统一线程池自动配置（`matchIfMissing=true`，未配置时默认启用） |
| `ydsz.thread.bean-name-prefix` | `""` | Bean 名称前缀，设置后 Bean 名称变为 `prefix + key + "Executor"`（如 `ydsz-ioExecutor`） |
| `ydsz.thread.hot-update.enabled` | `false` | 是否启用热更新监听器 |

### 单个线程池配置（`ydsz.thread.pools.<name>`）

| 配置 | 默认值 | 适用类型 | 说明 |
|---|---|---|---|
| `type` | `PLATFORM` | 全部 | 线程池类型：`PLATFORM`（平台线程） / `VIRTUAL`（虚拟线程，JDK 21+） |
| `core-size` | `2` | PLATFORM | 核心线程数 |
| `max-size` | `8` | PLATFORM | 最大线程数（必须 >= coreSize） |
| `queue-capacity` | `100` | PLATFORM | 阻塞队列容量（0 表示 SynchronousQueue） |
| `thread-name-prefix` | `ydsz-thread-` | 全部 | 线程名前缀 |
| `reject-policy` | `CALLER_RUNS` | PLATFORM | 拒绝策略：`ABORT` / `CALLER_RUNS` / `DISCARD_OLDEST` / `DISCARD` |
| `await-termination-seconds` | `60` | 全部 | 优雅关闭等待秒数 |
| `allow-core-thread-time-out` | `false` | PLATFORM | 是否允许核心线程超时回收 |
| `keep-alive-seconds` | `60` | PLATFORM | 线程空闲存活秒数（建议不超过 3600） |
| `metric-prefix` | `ydsz.executor` | PLATFORM | Micrometer 指标前缀 |
| `slow-task-threshold-ms` | `5000` | PLATFORM | 慢任务阈值（毫秒），≥ 100 |
| `enable-detailed-metrics` | `false` | PLATFORM | 是否启用详细指标（pool.max / queue.remaining / queue.usage） |
| `task-decorator-bean-names` | `[]` | PLATFORM | TaskDecorator Bean 名称列表，用于跨线程传播上下文 |

> 虚拟线程池（`type=VIRTUAL`）仅读取 `thread-name-prefix`，其余参数（core-size/max-size/queue-capacity/reject-policy 等）不生效，因为虚拟线程为每任务一线程模型，无队列与池大小限制。

### 配置校验

`ThreadPoolProperties` 启用 `@Validated` 校验，启动阶段即报错（不延迟到运行时）：

- `core-size` >= 1
- `max-size` >= 1
- `max-size` >= `core-size`
- `queue-capacity` >= 0
- `keep-alive-seconds` <= 3600
- `slow-task-threshold-ms` >= 100

## 使用示例

### 1. 自定义线程池（配置驱动）

在 `application.yml` 中声明线程池，业务代码通过 `@Resource(name = "xxxExecutor")` 注入：

```yaml
ydsz:
  thread:
    pools:
      io:
        type: PLATFORM
        core-size: 8
        max-size: 32
        queue-capacity: 200
        thread-name-prefix: ydsz-io-
        reject-policy: CALLER_RUNS
```

```java
import jakarta.annotation.Resource;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class FileImportService {

    @Resource(name = "ioExecutor")
    private ThreadPoolTaskExecutor ioExecutor;

    public void importAsync(String path) {
        ioExecutor.submit(() -> doImport(path));
    }

    private void doImport(String path) {
        // 业务逻辑
    }
}
```

### 2. @Async 异步方法

将线程池用于 `@Async`，通过 `@Async("ioExecutor")` 显式指定执行器：

```java
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    @Async("ioExecutor")
    public void sendAsync(String userId, String content) {
        // 异步发送逻辑
    }
}
```

> 需在启动类或配置类上添加 `@EnableAsync` 开启异步支持（由业务模块负责，本模块不自动开启）。

### 3. 编程式提交任务

通过依赖注入的 `ThreadPoolTaskExecutor` 直接提交 `Runnable` / `Callable`：

```java
import java.util.concurrent.Future;

import jakarta.annotation.Resource;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    @Resource(name = "cpuExecutor")
    private ThreadPoolTaskExecutor cpuExecutor;

    public Future<String> generateAsync(String reportId) {
        return cpuExecutor.submit(() -> doGenerate(reportId));
    }

    private String doGenerate(String reportId) {
        return "report-" + reportId;
    }
}
```

### 4. 虚拟线程池（JDK 21+）

配置 `type=VIRTUAL` 创建虚拟线程池，适合 IO 密集型且高并发的场景：

```yaml
ydsz:
  thread:
    pools:
      virtual-io:
        type: VIRTUAL
        thread-name-prefix: ydzs-virtual-io-
```

```java
import java.util.concurrent.ExecutorService;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;

@Service
public class VirtualTaskService {

    @Resource(name = "virtualIoExecutor")
    private ExecutorService virtualIoExecutor;

    public void runIoBound(String taskId) {
        virtualIoExecutor.submit(() -> doBlockingIo(taskId));
    }

    private void doBlockingIo(String taskId) {
        // 阻塞型 IO：HTTP / DB / 文件
    }
}
```

> 虚拟线程池仅读取 `thread-name-prefix`，其余参数（core-size/max-size/queue-capacity/reject-policy）不生效。要求 JDK 21+，低版本会抛错（本模块不自动回退）。

### 5. 监控指标查询

通过 Micrometer `/actuator/metrics` 端点查询线程池指标：

```
# 平台线程池核心指标（前缀 ydsz.executor）—— 始终注册
ydsz.executor.active{pool.name="io"}       3.0
ydsz.executor.queue.size{pool.name="io"}   12.0
ydsz.executor.completed{pool.name="io"}   15420.0
ydsz.executor.pool.size{pool.name="io"}    8.0
ydsz.executor.rejected{pool.name="io"}     5.0

# 平台线程池详细指标（配置 enable-detailed-metrics: true 时注册）
ydsz.executor.pool.max{pool.name="io"}         32.0
ydsz.executor.queue.remaining{pool.name="io"}  188.0
ydsz.executor.queue.usage{pool.name="io"}      0.06

# 平台线程池耗时指标（前缀 ydsz.executor，Timer 类型）
ydsz.executor.execution{pool.name="io", quantile="0.99"}  1250.0
ydsz.executor.queue.wait{pool.name="io", quantile="0.99"}  50.0
ydsz.executor.slow.tasks{pool.name="io"}  3.0

# 虚拟线程池指标（前缀 ydsz.virtual.executor）
ydsz.virtual.executor.submitted{pool.name="virtual-io"}  1234.0
ydsz.virtual.executor.completed{pool.name="virtual-io"}  1230.0
```

或通过 `/actuator/health` 查看线程池健康检查详情（见「健康检查」章节）。

## SPI 扩展点

本模块提供以下可覆盖能力：

| Bean | 注册方式 | 覆盖方式 |
|---|---|---|
| `ThreadHealthIndicator` | 当前**未自动注册**（预留） | 业务侧自行 `@Bean` 声明即可使用 |
| `ThreadPoolMetrics` | 由 `ThreadPoolRegistrar` 自动注册 | 使用 `ydsz.thread.enabled=false` 关闭自动配置，完全手动管理 |

### 2. 编程式线程池工厂

`com.njydsz.common.thread.util.ExecutorUtils` 提供静态工厂方法，适用于非 Spring 场景、短生命周期临时线程池或需要按需创建线程池的场景：

```java
// 创建 CPU 密集型线程池
ExecutorService cpuPool = ExecutorUtils.newCpuBoundThreadPool("audit-cpu-");

// 创建虚拟线程执行器（适用于 IO 密集）
ExecutorService virtualPool = ExecutorUtils.newVirtualThreadExecutor("notify-virtual-");

// 优雅关闭
ExecutorUtils.shutdownGracefully(cpuPool, 30, TimeUnit.SECONDS);
```

详见 `ExecutorUtils` javadoc 中的「快速选择指南」表格。

### 3. 线程池指标组件

本模块提供以下指标采集组件，适用于编程式创建但仍需指标采集的场景：

| 类 | 说明 |
|---|---|
| `ThreadPoolMetrics` | Micrometer `MeterBinder`，为 `ThreadPoolTaskExecutor` 注册核心 5 项指标（active / pool.size / queue.size / completed / rejected）+ 可选 3 项详细指标 |
| `ThreadPoolTimerMetrics` | 任务执行耗时 `Timer`（execution / queue.wait）和慢任务 `Counter` |
| `MeteredRejectedHandler` | 拒绝策略包装器，自动计数拒绝任务数 |
| `MeteredVirtualExecutorService` | 虚拟线程执行器包装器，自动同步 `VirtualThreadMetrics` 计数器（submitted / completed） |
| `VirtualThreadMetrics` | 虚拟线程池指标绑定器（submitted / completed 计数器） |

### 扩展建议

如需自定义线程池行为（如自定义 `ThreadFactory`、`RejectedExecutionHandler`），建议：

1. **优先使用配置驱动**：`ydsz.thread.pools.<name>` 创建托管线程池
2. **编程式补充**：使用本模块的 `ExecutorUtils` 创建线程池，再通过 `ThreadPoolMetrics` + `MeteredRejectedHandler` 绑定指标
3. **通过配置属性定制**：`ydsz.thread.pools.<name>.*` 已支持 `reject-policy`、`allow-core-thread-time-out`、`keep-alive-seconds`、`await-termination-seconds`、`task-decorator-bean-names` 等参数
4. **健康检查**：`ThreadHealthIndicator` 未自动装配，需要时由业务侧显式声明 Bean

## 健康检查

`ThreadHealthIndicator` 实现 Spring Boot `HealthIndicator` + `ApplicationContextAware`，可注册到 `/actuator/health` 端点（需业务模块引入 `spring-boot-starter-actuator` 并暴露 health 端点，且业务侧显式声明 Bean）。

**激活条件：**

- `org.springframework.boot.health.contributor.HealthIndicator` 类在 classpath（Spring Boot 4.x 独立 health 模块）
- 容器中不存在名为 `threadHealthIndicator` 的 Bean（`@ConditionalOnMissingBean` 守卫）

**重要：** 为避免误纳业务模块自定义的线程池导致健康检查误报，本模块只检查满足以下条件的 Bean：
- Bean 名称以 `"Executor"` 结尾（本模块注册约定）

业务模块自行定义的其他 `ThreadPoolTaskExecutor` Bean（如 `"orderProcessExecutor"`）不会被纳入检查。

**报告项：**

| 字段 | 适用类型 | 说明 |
|---|---|---|
| `<beanName>.active` | PLATFORM | 活跃线程数 |
| `<beanName>.queueSize` | PLATFORM | 队列堆积任务数 |
| `<beanName>.poolSize` | PLATFORM | 当前线程池大小 |
| `<beanName>.completed` | PLATFORM | 已完成任务总数 |
| `<beanName>.threadNamePrefix` | PLATFORM | 线程名前缀 |
| `<beanName>.type` | 全部 | 线程池类型（PLATFORM / VIRTUAL） |
| `<beanName>.alive` | VIRTUAL | 虚拟线程池存活状态 |

**响应示例（节选）：**

```json
{
  "status": "UP",
  "components": {
    "thread": {
      "status": "UP",
      "details": {
        "ioExecutor.active": 3,
        "ioExecutor.queueSize": 12,
        "ioExecutor.poolSize": 8,
        "ioExecutor.completed": 15420,
        "ioExecutor.threadNamePrefix": "ydsz-io-",
        "ioExecutor.type": "PLATFORM",
        "virtualIoExecutor.type": "VIRTUAL",
        "virtualIoExecutor.alive": true,
        "totalPools": 2,
        "platformPools": 1,
        "virtualPools": 1
      }
    }
  }
}
```

**状态判定：**

- 当 `ApplicationContext` 未初始化时返回 UP 并标注 `ApplicationContext not initialized`
- 容器中无 ydsz 管理的线程池 Bean 时返回 UP 并标注 `pools: none (ydsz-managed)`
- 任一线程池获取底层 `ThreadPoolExecutor` 异常时整体状态为 `DOWN`，详情中包含 `<beanName>.error` 字段

## 可观测性（Micrometer 指标）

### 平台线程池核心指标（前缀 `ydsz.executor`，始终注册）

| 指标 | 类型 | 说明 |
|---|---|---|
| `ydsz.executor.active` | Gauge | 活跃线程数（`getActiveCount()`） |
| `ydsz.executor.pool.size` | Gauge | 当前线程池大小（`getPoolSize()`） |
| `ydsz.executor.queue.size` | Gauge | 队列堆积任务数（`getQueue().size()`） |
| `ydsz.executor.completed` | Gauge | 累计完成任务数 |
| `ydsz.executor.rejected` | Counter | 累计拒绝任务数（自动通过 `MeteredRejectedHandler` 包装拒绝策略触发计数） |

### 平台线程池详细指标（配置 `enable-detailed-metrics: true` 时注册）

| 指标 | 类型 | 说明 |
|---|---|---|
| `ydsz.executor.pool.max` | Gauge | 线程池最大容量（`getMaximumPoolSize()`） |
| `ydsz.executor.queue.remaining` | Gauge | 工作队列剩余容量 |
| `ydsz.executor.queue.usage` | Gauge | 工作队列使用率（0.0 - 1.0） |

### 平台线程池耗时指标（始终注册，依赖 `MeterRegistry`）

| 指标 | 类型 | 说明 |
|---|---|---|
| `ydsz.executor.execution` | Timer | 任务执行耗时（含 P50/P95/P99 分位数） |
| `ydsz.executor.queue.wait` | Timer | 任务在队列中等待时长 |
| `ydsz.executor.slow.tasks` | Counter | 慢任务累计计数（执行耗时超过 `slow-task-threshold-ms`，默认 5s） |

### 虚拟线程池指标（前缀 `ydsz.virtual.executor`，始终注册）

| 指标 | 类型 | 说明 |
|---|---|---|
| `ydsz.virtual.executor.submitted` | Gauge | 累计提交任务数 |
| `ydsz.virtual.executor.completed` | Gauge | 累计完成任务数 |

> `ydsz.virtual.executor.rejected` 已被移除（JDK 21 的虚拟线程执行器从不拒绝任务，该计数器始终为 0，无实际意义）。

## 注意事项

- **不要直接 `new ThreadPoolExecutor(...)`**：业务模块应通过配置 `ydsz.thread.pools.<name>` 声明线程池，并以 `@Resource(name = "xxxExecutor")` 注入，确保被统一监控与关闭
- **虚拟线程要求 JDK 21+**：`type=VIRTUAL` 在低版本 JVM 上会抛错（本模块不自动回退，与 `ExecutorUtils` 行为不同）
- **Bean 名命名规则**：注册的 Bean 名称为 `<beanNamePrefix><poolKey>Executor`（如 prefix 为空、key 为 `io` → Bean 名 `ioExecutor`；prefix 为 `ydsz-` → `ydsz-ioExecutor`），注入时必须带上 `Executor` 后缀
- **指标依赖 Micrometer**：未引入 `micrometer-core` 时指标绑定静默跳过，不会报错
- **健康检查依赖 spring-boot-health**：Spring Boot 4.x 将 health 类迁至独立模块，未引入时 `ThreadHealthIndicator` Bean 不会注册
- **未配置线程池时跳过初始化**：`ydsz.thread.pools` 为空时仅打印 info 日志，不报错
- **拒绝策略默认 `CALLER_RUNS`**：避免任务丢失，但会阻塞调用线程；对延迟敏感场景应改用 `ABORT` 并在业务侧捕获 `RejectedExecutionException`
- **配置校验启动即失败**：`coreSize`/`maxSize`/`queueCapacity`/`keepAliveSeconds`/`slowTaskThresholdMs` 范围违规或 `maxSize < coreSize` 时启动抛 `ConstraintViolationException`
- **上下文传播**：通过 `task-decorator-bean-names` 配置 MDC / RequestContext 等装饰器，多个装饰器会自动串联执行
- **慢任务阈值**：默认 5000ms 适用于大多数 IO 密集场景；AI Agent 等长耗时场景建议设置为 30000
- **详细指标**：(pool.max / queue.remaining / queue.usage) 默认关闭，通过 `enable-detailed-metrics: true` 按需启用，可降低 Micrometer 指标内存开销约 40-60%


## 变更记录

- **v1.5.0**（2026-08-16）：
  - **架构调整**：线程池管理能力统一归属 `ydsz-common-thread`，从 `ydsz-common-util` 迁入 `ExecutorUtils` 编程式工厂与 `MeteredThreadPoolExecutor` 可观测执行器
  - `ydsz-common-util` 自 v4.1.0 起不再提供线程池创建与监控能力
  - 新增 `com.njydsz.common.thread.util` 包（编程式工具）与 `com.njydsz.common.thread.executor` 包（执行器实现）
  - `ydsz-common-audit`、`ydsz-common-notify` 模块同步更新 import 并新增 `ydsz-common-thread` 依赖
  - **修复 P0-2**：`TimedTaskDecorator` 移除全局静态 `ConcurrentMap`，改为不可变包装对象传递时间戳，消除 threadId 复用导致的串扰风险与内存泄漏
  - **修复 P0-3**：`ThreadHealthIndicator` 收紧扫描范围，仅检查 ydzs-common-thread 管理的 Bean（名称以 `"Executor"` 结尾），避免误纳业务自定义线程池导致误报
  - **修复 P1-1**：移除 `MeteredVirtualExecutorService` 中的 rejected 相关逻辑和 `VirtualThreadMetrics.rejected` 指标（JDK 21 虚拟线程从不拒绝任务，为不可达代码）
  - **修复 P1-2**：`ThreadPoolHotUpdateListener` 从依赖 `ThreadPoolAutoConfiguration` 改为直接注入 `ApplicationContext`；新增 `ThreadPoolHotUpdateAutoConfiguration`，通过 `ydsz.thread.hot-update.enabled=true` 自动注册，无需业务模块手动创建 Bean
  - **功能增强**：新增 `slow-task-threshold-ms` 配置，支持池级别自定义慢任务阈值（默认 5000，范围 100-∞）
  - **功能增强**：新增 `enable-detailed-metrics` 配置，指标分核心 5 项（始终注册）和可选 3 项（按需启用），降低默认内存开销约 40-60%
  - **增强**：`ARM` 架构检测与 ARM64 兼容性改进
- **v1.3.1**（2026-08-13）：
  - **修复 P0-1**：`ThreadPoolRegistrar` 由 `ThreadPoolAutoConfiguration` 通过 `@Bean` 显式注册，修复装配链路断裂导致线程池不生效问题
  - **修复 P0-2**：`ThreadPoolExecutorFactory` 实现 `ApplicationContextAware`，修复 `task-decorator-bean-names` 配置无效问题
  - **修复 P0-4**：虚拟线程池自动包装 `MeteredVirtualExecutorService`，`VirtualThreadMetrics` 计数器（submitted/completed）真正生效
  - **修复 P1-3**：`ThreadHealthIndicator.isAlive()` 改用 `ExecutorService.isShutdown()` 标准 API 替代 toString 伪判定
  - **修复 P1-4**：统一命名 `ydsz-`，修正代码 Javadoc 中 `ydzz`/`ydzs` 残留
  - **修复 P3-1**：移除 `ThreadPoolHotUpdateListener` 中冗余的 `ReentrantReadWriteLock`
  - **修复 P3-2**：移除 `VirtualThreadMetrics` 无意义的 `active=1.0` Gauge，计数器改用 `LongAdder` 优化高并发
  - **增强 P2-1**：`additional-spring-configuration-metadata.json` 补充枚举值提示与 `PoolType` / `RejectPolicy` 描述
  - **增强 P2-2**：新增任务执行耗时 `Timer` 指标（`execution` / `queue.wait`）和慢任务 `Counter`
  - **增强 P2-3**：`ThreadPoolProperties` 启用 `@Validated` 校验，`maxSize >= coreSize` 违规时启动失败
  - **增强 P2-4**：`ThreadPoolRegistrar` 提供 `getManagedBeanNames()` 方法，便于下游按配置 key 查找 Bean 名称
- **v1.5.2**（2026-08-17）：
  - 更新依赖说明：移除不存在的 `transmittable-thread-local` 依赖，添加 `jackson-annotations`（optional）
  - 补全内部核心类：`ThreadPoolRegistrar`、`ThreadPoolExecutorFactory`、`TimedTaskDecorator`、`ThreadPoolHotUpdateAutoConfiguration` 文档
- **v1.5.1**（2026-08-17）：修正 README — 移除不存在的 `MeteredThreadPoolExecutor` 类，替换为实际指标组件（`ThreadPoolMetrics` / `ThreadPoolTimerMetrics` / `MeteredRejectedHandler` / `MeteredVirtualExecutorService` / `VirtualThreadMetrics`）
- **v1.3.0**（2026-08-10）：新增 Micrometer 指标绑定、拒绝策略自动包装、TaskDecorator 配置化上下文传播、热更新监听器提取
- **v1.2.0**（2026-08-05）：`ThreadHealthIndicator` 支持虚拟线程池感知；`ThreadPoolRegistrar` 提取为独立组件
- **v1.0.0**（2026-08-02）：初始版本。提供配置驱动的多线程池自动装配、平台/虚拟线程双模式配置、健康检查、Micrometer 指标、优雅关闭
