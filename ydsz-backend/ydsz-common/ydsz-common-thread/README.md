# ydsz-common-thread

> L4 基础数据层共享线程池自动配置与监控 — 配置驱动、按业务隔离、Micrometer 指标、优雅关闭、健康检查一体化。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L4 基础数据层（`ydsz-common` 公共依赖） |
| **类型** | 公共依赖库（不独立部署，随业务模块打包） |
| **作用** | 为业务模块提供共享线程池的自动配置、运行时监控、健康检查与优雅关闭 |
| **依赖** | `ydsz-common-core`（基础常量/上下文）；`spring-context`、`spring-boot`、`micrometer-core`（可选）、`spring-boot-actuator`/`spring-boot-health`（可选） |
| **激活方式** | Spring Boot 自动装配（`AutoConfiguration.imports`），默认启用 |

## 核心能力

### 1. 线程池自动配置

`ThreadPoolAutoConfiguration` 基于 `ydsz.thread.pools` 配置动态创建并注册多个线程池 Bean，业务代码通过 `@Resource(name = "xxxExecutor")` 注入即可使用。

- **按业务隔离**：每个线程池独立的 `coreSize` / `maxSize` / `queueCapacity` / `rejectPolicy` / `threadNamePrefix` 等参数。
- **平台线程 + 虚拟线程双模式**：`type=PLATFORM` 创建 `ThreadPoolTaskExecutor`；`type=VIRTUAL` 创建 `Executors.newThreadPerTaskExecutor`（JDK 21+，每任务一虚拟线程）。
- **动态注册**：实现 `BeanFactoryAware` + `InitializingBean`，在 `afterPropertiesSet` 阶段通过 `ConfigurableListableBeanFactory.registerSingleton` 将执行器以 `key + "Executor"` 名称注册为单例。
- **优雅关闭**：实现 `DisposableBean`，`destroy()` 时统一 `shutdown()` 全部线程池；平台线程池额外开启 `waitForTasksToCompleteOnShutdown=true` 与 `awaitTerminationSeconds` 等待。
- **Micrometer 指标自动绑定**：`MeterRegistry` 可用时为每个平台线程池注册 Gauge 指标（见下方「可观测性」）。
- **健康检查**：当 classpath 存在 `org.springframework.boot.health.contributor.HealthIndicator` 时，自动注册 `ThreadHealthIndicator` Bean（`@ConditionalOnMissingBean`，可被业务覆盖）。

### 2. 线程池配置属性

`ThreadPoolProperties` 绑定 `ydsz.thread` 前缀，支持按 Map 配置多个命名线程池，每个线程池独立配置 `PoolConfig`。支持两种线程池类型（`PoolType.PLATFORM` / `PoolType.VIRTUAL`）与四种拒绝策略（`RejectPolicy.ABORT` / `CALLER_RUNS` / `DISCARD_OLDEST` / `DISCARD`）。

### 3. 健康检查

`ThreadHealthIndicator` 实现 `HealthIndicator` + `ApplicationContextAware`，运行时通过 `ApplicationContext.getBeansOfType(ThreadPoolTaskExecutor.class)` 自动发现所有平台线程池 Bean，上报 `active` / `queueSize` / `poolSize` / `completed` / `threadNamePrefix` 详情；任一线程池获取底层 `ThreadPoolExecutor` 失败时整体状态为 `DOWN`。虚拟线程池（`ExecutorService` 类型）不在该指标采集范围内。

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
      cpu:
        type: PLATFORM
        core-size: 4
        max-size: 4
        queue-capacity: 100
        thread-name-prefix: ydsz-cpu-
        reject-policy: ABORT
      virtual-io:
        type: VIRTUAL
        thread-name-prefix: ydsz-virtual-io-
```

### 3. 使用示例

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

## 配置项

### 顶层配置（`ydsz.thread`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.thread.enabled` | `true` | 是否启用统一线程池自动配置（`matchIfMissing=true`，未配置时默认启用） |
| `ydsz.thread.pools` | 空 Map | 线程池配置映射，key 为线程池名称（如 `io`、`cpu`、`batch`），Bean 名称为 `key + "Executor"` |

### 单个线程池配置（`ydsz.thread.pools.<name>`）

| 配置 | 默认值 | 适用类型 | 说明 |
|---|---|---|---|
| `type` | `PLATFORM` | 全部 | 线程池类型：`PLATFORM`（平台线程） / `VIRTUAL`（虚拟线程，JDK 21+） |
| `core-size` | `2` | PLATFORM | 核心线程数 |
| `max-size` | `8` | PLATFORM | 最大线程数 |
| `queue-capacity` | `100` | PLATFORM | 阻塞队列容量 |
| `thread-name-prefix` | `ydsz-thread-` | 全部 | 线程名前缀 |
| `reject-policy` | `CALLER_RUNS` | PLATFORM | 拒绝策略：`ABORT` / `CALLER_RUNS` / `DISCARD_OLDEST` / `DISCARD` |
| `await-termination-seconds` | `60` | PLATFORM | 优雅关闭等待秒数 |
| `allow-core-thread-time-out` | `false` | PLATFORM | 是否允许核心线程超时回收 |
| `keep-alive-seconds` | `60` | PLATFORM | 线程空闲存活秒数 |

> 虚拟线程池（`type=VIRTUAL`）仅读取 `thread-name-prefix`，其余参数（core-size/max-size/queue-capacity/reject-policy 等）不生效，因为虚拟线程为每任务一线程模型，无队列与池大小限制。

## 可观测性

### Micrometer 指标

当 classpath 存在 `MeterRegistry` 时，`ThreadPoolAutoConfiguration.bindMetrics()` 为每个**平台线程池**自动注册以下 Gauge 指标（tag `name` 为线程池配置 key）：

| 指标 | 说明 |
|---|---|
| `executor.active` | 活跃线程数（`getActiveCount()`） |
| `executor.queue.size` | 队列堆积任务数（`getQueue().size()`） |
| `executor.completed` | 已完成任务总数（`getCompletedTaskCount()`） |
| `executor.pool.size` | 当前线程池大小（`getPoolSize()`） |

> 虚拟线程池底层无 `ThreadPoolExecutor`，不绑定上述指标。`bindMetrics` 对单个池绑定失败时仅 `WARN` 日志，不阻断启动。

## 健康检查

`ThreadHealthIndicator` 自动注册到 `/actuator/health`（需业务模块引入 `spring-boot-starter-actuator` 并暴露 health 端点）。

响应示例（节选）：

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
        "ioExecutor.threadNamePrefix": "ydsz-io-"
      }
    }
  }
}
```

- 当 `ApplicationContext` 未初始化时返回 UP 并标注 `ApplicationContext not initialized`。
- 当容器中无 `ThreadPoolTaskExecutor` Bean 时返回 UP 并标注 `pools: none`。
- 任一池获取底层 `ThreadPoolExecutor` 异常时整体状态为 `DOWN`，详情中包含 `<beanName>.error` 字段。

## 与 common-util 的边界

`ydsz-common-util` 模块也提供线程池相关能力，本模块与它们**职责互补、不互相替代**：

| 维度 | `ydsz-common-util` | `ydsz-common-thread`（本模块） |
|---|---|---|
| **`ExecutorUtils`** | 静态工厂方法（`newFixedThreadPool` / `newCachedThreadPool` / `newVirtualThreadExecutor` / `newCpuBoundThreadPool` 等），业务自行管理生命周期 | 不提供静态工厂，仅基于配置文件创建托管 Bean |
| **`ThreadPoolMonitorAutoConfiguration`** | 提供 `ThreadPoolMonitor` Bean，业务需**手动调用** `monitor.register(name, executor)` 注册线程池才能被采集；指标前缀 `ydsz.threadpool.*` | 配置驱动的池**自动绑定** Micrometer 指标，无需手动注册；指标前缀 `executor.*` |
| **生命周期** | 工厂方法返回的 `ExecutorService` 由调用方自行 shutdown | 实现 `DisposableBean`，随 Spring 容器统一优雅关闭 |
| **健康检查** | 不提供 | 提供 `ThreadHealthIndicator`，自动发现所有 `ThreadPoolTaskExecutor` Bean |
| **虚拟线程** | `ExecutorUtils.newVirtualThreadExecutor()` 静态创建，不支持时回退到缓存池 | 配置 `type=VIRTUAL` 创建，不回退（要求 JDK 21+） |

**选用建议**：
- 需要 Spring 容器托管、配置化、统一监控与关闭 → 用本模块（`ydsz-common-thread`）。
- 需要在非 Spring 场景或临时创建短生命周期线程池 → 用 `ExecutorUtils`。
- 已通过本模块托管的池**无需**再注册到 `ThreadPoolMonitor`，避免重复采集。

## 注意事项

- **不要直接 `new ThreadPoolExecutor(...)`**：业务模块应通过配置 `ydsz.thread.pools.<name>` 声明线程池，并以 `@Resource(name = "xxxExecutor")` 注入，确保被统一监控与关闭。
- **虚拟线程要求 JDK 21+**：`type=VIRTUAL` 在低版本 JVM 上会抛错（本模块不自动回退，与 `ExecutorUtils` 行为不同）。
- **Bean 名命名规则**：注册的 Bean 名称为 `<poolKey>Executor`（如 key 为 `io` → Bean 名 `ioExecutor`），注入时必须带上 `Executor` 后缀。
- **指标依赖 Micrometer**：未引入 `micrometer-core` 时 `bindMetrics` 静默跳过，不会报错。
- **健康检查依赖 spring-boot-health**：Spring Boot 4.x 将 health 类迁至独立模块，未引入时 `ThreadHealthIndicator` Bean 不会注册。
- **未配置线程池时跳过初始化**：`ydsz.thread.pools` 为空时仅打印 `ydsz-thread: 未配置线程池，跳过初始化` 日志，不报错。
- **拒绝策略默认 `CALLER_RUNS`**：避免任务丢失，但会阻塞调用线程；对延迟敏感场景应改用 `ABORT` 并在业务侧捕获 `RejectedExecutionException`。

## 变更记录

- **v1.0.0**（2026-08-02）：初始版本。提供 `ThreadPoolAutoConfiguration` 配置驱动的多线程池自动装配、`ThreadPoolProperties` 平台/虚拟线程双模式配置、`ThreadHealthIndicator` 健康检查、Micrometer 指标自动绑定、`DisposableBean` 优雅关闭。
