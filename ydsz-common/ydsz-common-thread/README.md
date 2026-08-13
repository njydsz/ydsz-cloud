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

`ThreadPoolAutoConfiguration` 结合 `ThreadPoolRegistrar`（`BeanDefinitionRegistryPostProcessor`）基于 `ydsz.thread.pools` 配置动态注册多个线程池 Bean，业务代码通过 `@Resource(name = "xxxExecutor")` 注入即可使用。

- **按业务隔离**：每个线程池独立的 `coreSize` / `maxSize` / `queueCapacity` / `rejectPolicy` / `threadNamePrefix` 等参数
- **平台线程 + 虚拟线程双模式**：`type=PLATFORM` 创建 `ThreadPoolTaskExecutor`；`type=VIRTUAL` 创建 `Executors.newThreadPerTaskExecutor`（JDK 21+，每任务一虚拟线程）
- **Bean 名称规则**：Bean 名称为 `beanNamePrefix + key + "Executor"`（前缀默认为空字符串）
- **优雅关闭**：平台线程池 `shutdown` 时自动等待任务完成
- **Micrometer 指标自动绑定**：`MeterRegistry` 可用时为每个平台线程池注册 11 项指标（见下方「可观测性」）
- **健康检查**：自动注册 `ThreadHealthIndicator` Bean，覆盖平台线程池与虚拟线程池

### 2. 线程池配置属性

`ThreadPoolProperties` 绑定 `ydsz.thread` 前缀，支持按 Map 配置多个命名线程池，每个线程池独立配置 `PoolConfig`。支持两种线程池类型（`PoolType.PLATFORM` / `PoolType.VIRTUAL`）与四种拒绝策略（`RejectPolicy.ABORT` / `CALLER_RUNS` / `DISCARD_OLDEST` / `DISCARD`）。

### 3. 健康检查

`ThreadHealthIndicator` 实现 `HealthIndicator` + `ApplicationContextAware`，运行时通过 `ApplicationContext` 自动发现所有线程池 Bean，检查其存活状态。平台线程池上报 `active` / `queueSize` / `poolSize` / `completed` / `threadNamePrefix` 详情；虚拟线程池上报类型标识与存活状态。任一线程池获取底层 `ThreadPoolExecutor` 失败时整体状态为 `DOWN`。

### 4. 动态调参（热更新）

`ThreadPoolHotUpdateListener` 支持运行时通过 Nacos / Spring Cloud Config 动态调整线程池参数，包括 `coreSize` / `maxSize` / `rejectPolicy` / `threadNamePrefix`，无需重启应用。

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
    bean-name-prefix: ydzs-           # 可选：所有线程池 Bean 名称前缀
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
        thread-name-prefix: ydsz-virtual-io-
```

## 配置项

### 顶层配置（`ydsz.thread`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.thread.enabled` | `true` | 是否启用统一线程池自动配置（`matchIfMissing=true`，未配置时默认启用） |
| `ydsz.thread.bean-name-prefix` | `""` | Bean 名称前缀，设置后 Bean 名称变为 `prefix + key + "Executor"`（如 `ydsz-ioExecutor`） |
| `ydsz.thread.pools` | 空 Map | 线程池配置映射，key 为线程池名称（如 `io`、`cpu`、`batch`） |

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
| `task-decorator-bean-names` | `[]` | PLATFORM | TaskDecorator Bean 名称列表，用于跨线程传播上下文 |

> 虚拟线程池（`type=VIRTUAL`）仅读取 `thread-name-prefix`，其余参数（core-size/max-size/queue-capacity/reject-policy 等）不生效，因为虚拟线程为每任务一线程模型，无队列与池大小限制。

### 配置校验

`ThreadPoolProperties` 启用 `@Validated` 校验，启动阶段即报错（不延迟到运行时）：

- `core-size` >= 1
- `max-size` >= 1
- `max-size` >= `core-size`
- `queue-capacity` >= 0
- `keep-alive-seconds` <= 3600

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
        thread-name-prefix: ydsz-virtual-io-
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
# 平台线程池基础指标（前缀 ydsz.executor）
ydsz.executor.active{pool.name="io"}       3.0
ydsz.executor.queue.size{pool.name="io"}   12.0
ydsz.executor.completed{pool.name="io"}   15420.0
ydsz.executor.pool.size{pool.name="io"}    8.0
ydsz.executor.rejected{pool.name="io"}     5.0

# 平台线程池耗时指标（前缀 ydsz.executor，Timer 类型）
ydsz.executor.execution{pool.name="io", quantile="0.99"}  1250.0
ydsz.executor.queue.wait{pool.name="io", quantile="0.99"}  50.0
ydsz.executor.slow.tasks{pool.name="io"}  3.0

# 虚拟线程池指标（前缀 ydsz.virtual.executor）
ydsz.virtual.executor.submitted{pool.name="virtual-io"}  1234.0
ydsz.virtual.executor.completed{pool.name="virtual-io"}  1230.0
ydsz.virtual.executor.rejected{pool.name="virtual-io"}  0.0
```

或通过 `/actuator/health` 查看线程池健康检查详情（见「健康检查」章节）。

## SPI 扩展点

本模块提供以下可覆盖能力：

| Bean | 注册方式 | 覆盖方式 |
|---|---|---|
| `ThreadHealthIndicator` | `@ConditionalOnMissingBean(name = "threadHealthIndicator")` | 业务方提供名为 `threadHealthIndicator` 的 Bean 即可替换默认健康检查实现 |
| `ThreadPoolMetrics` | 由 `ThreadPoolRegistrar` 自动注册 | 使用 `ydsz.thread.enabled=false` 关闭自动配置，完全手动管理 |

### 扩展建议

如需自定义线程池行为（如自定义 `ThreadFactory`、`RejectedExecutionHandler`），建议：

1. **不在本模块托管范围内创建线程池**：使用 `ydsz-common-util` 的 `ExecutorUtils` 静态工厂方法创建短生命周期线程池，业务自行管理生命周期
2. **通过配置属性定制**：`ydsz.thread.pools.<name>.*` 已支持 `reject-policy`、`allow-core-thread-time-out`、`keep-alive-seconds`、`await-termination-seconds`、`task-decorator-bean-names` 等参数
3. **覆盖健康检查**：提供自定义 `ThreadHealthIndicator` Bean（名为 `threadHealthIndicator`）替换默认实现

## 健康检查

`ThreadHealthIndicator` 实现 Spring Boot `HealthIndicator` + `ApplicationContextAware`，自动注册到 `/actuator/health` 端点（需业务模块引入 `spring-boot-starter-actuator` 并暴露 health 端点）。

**激活条件：**

- `org.springframework.boot.health.contributor.HealthIndicator` 类在 classpath（Spring Boot 4.x 独立 health 模块）
- 容器中不存在名为 `threadHealthIndicator` 的 Bean（`@ConditionalOnMissingBean` 守卫）

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
- 当容器中无线程池 Bean 时返回 UP 并标注 `pools: none`
- 任一线程池获取底层 `ThreadPoolExecutor` 异常时整体状态为 `DOWN`，详情中包含 `<beanName>.error` 字段

## 可观测性（Micrometer 指标）

### 平台线程池指标（前缀 `ydsz.executor`）

当 classpath 存在 `MeterRegistry` 时，为每个平台线程池自动注册以下指标（tag `pool.name` 为线程池配置 key）：

| 指标 | 类型 | 说明 |
|---|---|---|
| `ydsz.executor.active` | Gauge | 活跃线程数（`getActiveCount()`） |
| `ydsz.executor.pool.size` | Gauge | 当前线程池大小（`getPoolSize()`） |
| `ydsz.executor.pool.max` | Gauge | 线程池最大容量（`getMaximumPoolSize()`） |
| `ydsz.executor.queue.size` | Gauge | 队列堆积任务数（`getQueue().size()`） |
| `ydsz.executor.queue.remaining` | Gauge | 工作队列剩余容量 |
| `ydsz.executor.queue.usage` | Gauge | 工作队列使用率（0.0 - 1.0） |
| `ydsz.executor.completed` | Gauge | 累计完成任务数 |
| `ydsz.executor.rejected` | Counter | 累计拒绝任务数（自动通过 `MeteredRejectedHandler` 包装拒绝策略触发计数） |
| `ydsz.executor.execution` | Timer | 任务执行耗时（含 P50/P95/P99 分位数） |
| `ydsz.executor.queue.wait` | Timer | 任务在队列中等待时长 |
| `ydsz.executor.slow.tasks` | Counter | 慢任务累计计数（执行耗时 > 5s） |

### 虚拟线程池指标（前缀 `ydsz.virtual.executor`）

虚拟线程执行器无原生计数 API，通过 `MeteredVirtualExecutorService` 包装实现：

| 指标 | 类型 | 说明 |
|---|---|---|
| `ydsz.virtual.executor.submitted` | Gauge | 累计提交任务数 |
| `ydsz.virtual.executor.completed` | Gauge | 累计完成任务数 |
| `ydsz.virtual.executor.rejected` | Gauge | 累计拒绝任务数 |

## 注意事项

- **不要直接 `new ThreadPoolExecutor(...)`**：业务模块应通过配置 `ydsz.thread.pools.<name>` 声明线程池，并以 `@Resource(name = "xxxExecutor")` 注入，确保被统一监控与关闭
- **虚拟线程要求 JDK 21+**：`type=VIRTUAL` 在低版本 JVM 上会抛错（本模块不自动回退，与 `ExecutorUtils` 行为不同）
- **Bean 名命名规则**：注册的 Bean 名称为 `<beanNamePrefix><poolKey>Executor`（如 prefix 为空、key 为 `io` → Bean 名 `ioExecutor`；prefix 为 `ydsz-` → `ydsz-ioExecutor`），注入时必须带上 `Executor` 后缀
- **指标依赖 Micrometer**：未引入 `micrometer-core` 时指标绑定静默跳过，不会报错
- **健康检查依赖 spring-boot-health**：Spring Boot 4.x 将 health 类迁至独立模块，未引入时 `ThreadHealthIndicator` Bean 不会注册
- **未配置线程池时跳过初始化**：`ydsz.thread.pools` 为空时仅打印 info 日志，不报错
- **拒绝策略默认 `CALLER_RUNS`**：避免任务丢失，但会阻塞调用线程；对延迟敏感场景应改用 `ABORT` 并在业务侧捕获 `RejectedExecutionException`
- **配置校验启动即失败**：`coreSize`/`maxSize`/`queueCapacity`/`keepAliveSeconds` 范围违规或 `maxSize < coreSize` 时启动抛 `ConstraintViolationException`
- **上下文传播**：通过 `task-decorator-bean-names` 配置 MDC / RequestContext 等装饰器，多个装饰器会自动串联执行

### 与 common-util 的边界

`ydsz-common-util` 模块也提供线程池相关能力，本模块与它**职责互补、不互相替代**：

| 维度 | `ydsz-common-util` | `ydsz-common-thread`（本模块） |
|---|---|---|
| **`ExecutorUtils`** | 静态工厂方法（`newFixedThreadPool` / `newCachedThreadPool` / `newVirtualThreadExecutor` / `newCpuBoundThreadPool` 等），业务自行管理生命周期 | 不提供静态工厂，仅基于配置文件创建托管 Bean |
| **`ThreadPoolMonitorAutoConfiguration`** | 提供 `ThreadPoolMonitor` Bean，业务需**手动调用** `monitor.register(name, executor)` 注册线程池才能被采集；指标前缀 `ydsz.threadpool.*` | 配置驱动的池**自动绑定** Micrometer 指标，无需手动注册；指标前缀 `ydsz.executor` |
| **生命周期** | 工厂方法返回的 `ExecutorService` 由调用方自行 shutdown | 随 Spring 容器统一优雅关闭 |
| **健康检查** | 不提供 | 提供 `ThreadHealthIndicator`，自动发现所有线程池 Bean |
| **虚拟线程** | `ExecutorUtils.newVirtualThreadExecutor()` 静态创建，不支持时回退到缓存池 | 配置 `type=VIRTUAL` 创建，不回退（要求 JDK 21+） |
| **运行时可观测性** | 需手动注册后可采集 | 自动暴露 11 项平台池指标 + 3 项虚拟池指标（含耗时 P50/P95/P99） |
| **动态调参** | 不支持 | 支持通过 `ThreadPoolHotUpdateListener` 运行时调整参数 |

**选用建议：**

- 需要 Spring 容器托管、配置化、统一监控与关闭 → 用本模块（`ydsz-common-thread`）
- 需要在非 Spring 场景或临时创建短生命周期线程池 → 用 `ExecutorUtils`
- 已通过本模块托管的池**无需**再注册到 `ThreadPoolMonitor`，避免重复采集

## 变更记录

- **v1.3.1**（2026-08-13）：
  - **修复 P0-1**：`ThreadPoolRegistrar` 由 `ThreadPoolAutoConfiguration` 通过 `@Bean` 显式注册，修复装配链路断裂导致线程池不生效问题
  - **修复 P0-2**：`ThreadPoolExecutorFactory` 实现 `ApplicationContextAware`，修复 `task-decorator-bean-names` 配置无效问题
  - **修复 P0-4**：虚拟线程池自动包装 `MeteredVirtualExecutorService`，`VirtualThreadMetrics` 计数器（submitted/completed/rejected）真正生效
  - **修复 P1-3**：`ThreadHealthIndicator.isAlive()` 改用 `ExecutorService.isShutdown()` 标准 API 替代 toString 伪判定
  - **修复 P1-4**：统一命名 `ydsz-`，修正代码 Javadoc 中 `ydzz`/`ydzs` 残留
  - **修复 P3-1**：移除 `ThreadPoolHotUpdateListener` 中冗余的 `ReentrantReadWriteLock`
  - **修复 P3-2**：移除 `VirtualThreadMetrics` 无意义的 `active=1.0` Gauge，计数器改用 `LongAdder` 优化高并发
  - **增强 P2-1**：`additional-spring-configuration-metadata.json` 补充枚举值提示与 `PoolType` / `RejectPolicy` 描述
  - **增强 P2-2**：新增任务执行耗时 `Timer` 指标（`execution` / `queue.wait`）和慢任务 `Counter`
  - **增强 P2-3**：`ThreadPoolProperties` 启用 `@Validated` 校验，`maxSize >= coreSize` 违规时启动失败
  - **增强 P2-4**：`ThreadPoolRegistrar` 提供 `getManagedBeanNames()` 方法，便于下游按配置 key 查找 Bean 名称
- **v1.3.0**（2026-08-10）：新增 Micrometer 指标绑定、拒绝策略自动包装、TaskDecorator 配置化上下文传播、热更新监听器提取
- **v1.2.0**（2026-08-05）：`ThreadHealthIndicator` 支持虚拟线程池感知；`ThreadPoolRegistrar` 提取为独立组件
- **v1.0.0**（2026-08-02）：初始版本。提供配置驱动的多线程池自动装配、平台/虚拟线程双模式配置、健康检查、Micrometer 指标、优雅关闭
