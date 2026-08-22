# ydsz-common-lock

> 分布式锁与防重提交公共模块（L4 基础数据层）

YDSZ 分布式锁框架 — Redis 重入锁 / 公平锁 / 读写锁 / 信号量 / 多 Key 联锁、WatchDog 自动续期、锁泄漏检测、`@Idempotent` 幂等、`@RepeatSubmit` 防重提交、`@DistributedScheduled` 分布式调度、`@YdszDistributedLock` 声明式锁、降级策略、锁释放通知、锁事件监听、锁管理端点。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L4 基础数据层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供分布式锁、幂等性、防重提交、分布式调度等能力 |
| **源文件数** | 47 |
| **依赖** | common-core、common-redis、common-cache、common-json、common-auth（optional） |

## 核心能力

### 1. 分布式锁核心

| 类 | 说明 |
|---|---|
| `DistributedLocker` | 锁操作核心接口（tryLock / unlock / isLocked / getRemainTime / pexpire） |
| `AbstractRedisDistributedLock` | Redis 锁抽象基类 |
| `RedisReentrantLock` | Redis 可重入锁（Hash + Lua 原子释放，支持重入计数） |
| `RedisFairLock` | 公平锁（FIFO 队列排队，先到先得） |
| `RedisMultiLock` | 多锁（多个 Key 同时加锁，全部成功或全部失败） |
| `FallbackDistributedLock` | 降级锁（Redis 不可用时退化为 JVM `ReentrantLock`） |
| `DistributedLockException` | 分布式锁异常（获取锁超时 / 释放锁失败 / 续期失败，错误码 `LOCK_ERROR`） |
| `LockExceptionCode` | 锁异常码枚举 |

### 2. 读写锁与信号量

| 类 | 说明 |
|---|---|
| `RedisReadWriteLock` | 读写锁（读共享 / 写独占，Lua 原子操作，指数退避等待） |
| `RedisSemaphore` | 分布式信号量（并发数控制，超时自动释放，指数退避） |

> 读写锁与信号量自 v1.0.0 起实现 `DistributedLocker` 接口，可纳入 `LockStrategy` 统一管理。两者均使用 `ydsz-common-cache` 替代 `ThreadLocal` 存储线程持有状态，通过 TTL 与最大容量自动清理，避免线程池复用场景下的内存泄漏。

### 3. 幂等性

| 类 / 注解 | 说明 |
|---|---|
| `@Idempotent` | 接口幂等注解（基于请求参数摘要 + Redis `SET NX EX`，TTL 窗口内同一 key 只处理一次） |
| `@IdempotentExempt` | 幂等豁免标记（标注的字段/参数不参与幂等键摘要） |
| `IdempotentAspect` | 幂等切面（SpEL key 解析 + 幂等记录） |
| `IdempotentStrategy` | 幂等策略接口（acquire / release / exists） |
| `RedisIdempotentStrategy` | Redis 实现（`SET NX EX` + token 校验释放） |
| `IdempotentException` | 幂等异常 |
| `IdempotentUnavailableException` | 幂等不可用异常（Redis 降级时按 `fail-open` 策略决定是否放行） |

### 4. 防重提交

基于前端 Token 令牌模式防止表单重复提交，与 `@Idempotent`（服务端去重）形成互补。

| 类 / 注解 | 说明 |
|---|---|
| `@RepeatSubmit` | 防重提交注解（标注在 Controller 方法上） |
| `RepeatSubmitAspect` | 防重提交切面（从请求头提取 Token 校验消费） |
| `RepeatSubmitTokenService` | Token 服务（生成 / 校验并消费，与用户 ID 绑定，一次性使用） |
| `RepeatSubmitTokenController` | Token 接口（`GET /repeat-submit/token`，仅在 Web 环境装配） |

**工作流程：**

1. 前端调用 `GET /repeat-submit/token?ttlMillis=60000` 获取一次性 Token
2. 前端提交表单时在请求头携带 `X-Repeat-Token: {token}`
3. `RepeatSubmitAspect` 从请求头提取 Token，调用 `validateAndConsume` 校验
4. 校验通过则执行业务方法并删除 Token；失败抛出 `BusinessException`

**Redis Key 格式：** `ydsz:repeat:token:{userId}:{token}`，Token 与登录用户绑定，防止被盗用。

**与 `@Idempotent` 的区别：**

| 维度 | `@Idempotent` | `@RepeatSubmit` |
|---|---|---|
| 防重维度 | 服务端基于请求参数摘要去重 | 前端 Token 令牌一次性消费 |
| 适用场景 | 接口幂等性（重复请求结果一致） | 表单提交防护（防止用户快速双击） |
| 前端配合 | 无需 | 需先获取 Token |

### 5. 分布式调度

| 类 / 注解 | 说明 |
|---|---|
| `@DistributedScheduled` | 分布式定时任务注解（包装 Spring `@Scheduled`） |
| `DistributedScheduledAspect` | 分布式调度切面（获取锁才执行，否则跳过） |

**与 Spring `@Scheduled` 的区别：** Spring `@Scheduled` 在多实例部署时每个节点都会执行；`@DistributedScheduled` 通过分布式锁确保同一任务同一时刻只有一个节点执行，获取不到锁的节点直接跳过本次执行（非阻塞，不抛异常）。

**降级策略：** 当 `LockStrategy` Bean 不存在（单节点 / 测试环境未装配 ydsz-common-lock）时，直接执行任务不加锁，保证功能可用。

**锁 Key 前缀：** `ydsz:schedule:`，与业务锁隔离。

### 6. WatchDog 续期

| 类 | 说明 |
|---|---|
| `LockWatchDog` | 看门狗（定时续期锁，防止业务未完成锁过期） |
| `LockRenewalService` | 分布式锁续期 SPI 服务（统一收口续期 Lua 脚本，消除"双锁冗余"） |

**WatchDog 机制：**

- 续期间隔 = `leaseTime / 3`（默认续期因子 1/3）
- 续期使用 Lua 脚本保证原子性（REENTRANT 用 `HEXISTS`，FAIR 用 `HGET 'owner'`）
- 续期失败自动重试（最多 3 次）
- 最大续期次数限制（默认 100 次，约 30 分钟），超限后停止续期，锁自动过期
- 支持批量续期（Pipeline 优化，减少 Redis 网络往返）
- 使用 `ReentrantLock` 替代 `synchronized`，避免 JDK 21 虚拟线程固定（pinning）
- `@PreDestroy` 优雅停机，清理所有续期任务

**LockRenewalService** 统一提供两类续期脚本：
- `RENEW_SCRIPT_HASH`：适用于可重入锁（clientId 作为 Hash field）
- `RENEW_SCRIPT_OWNER`：适用于公平锁（clientId 作为 owner 字段的值）

业务方可实现 `LockRenewalStrategy` 接口自定义续期逻辑，通过 `setStrategy()` 注入。

> 说明：`LockLeakDetector`（锁泄漏检测器）未实现，异常锁持有依赖 `LockWatchDog` 超时自动释放兜底。

### 7. 锁释放通知

| 类 | 说明 |
|---|---|
| `LockReleaseNotifier` | 锁释放通知器（对标 Redisson 发布订阅唤醒机制） |

**工作机制：**

- 启动时按 `ydsz:lock:release:*` 模式订阅一次，收到消息后唤醒对应锁键的本地等待者
- `awaitRelease` 注册等待者并以 `CompletableFuture` 阻塞等待，被唤醒或超时后返回
- `notifyRelease` 发布 Redis 消息并同时唤醒本地等待者（双保险，兼容订阅延迟）
- 等待上限（默认 50ms），即使错过通知也能在限定时间内重新探测锁状态
- 使用 `CompletableFuture` 实现异步等待，避免线程阻塞

### 8. 锁键校验

| 类 | 说明 |
|---|---|
| `LockKeyValidator` | 锁键校验工具类（防止恶意构造超长 / 特殊字符 key） |

**校验规则：**

- 锁键不能为空（null 或空字符串）
- 锁键长度不超过 512 字符（Redis 建议上限）
- 禁止包含控制字符：`\n`、`\r`、`\t`、`\0`

**方法：**

- `validate(String)` — 严格校验，违规抛 `IllegalArgumentException`
- `sanitize(String)` — 清理模式，超长截断，控制字符替换为下划线

### 9. 锁策略

| 类 / 接口 | 说明 |
|---|---|
| `LockStrategy` | 锁策略工厂接口（按 `LockType` 创建锁实例，含读写锁 / 信号量 / 看门狗） |
| `DefaultLockStrategy` | 默认实现（加锁 → 执行 → 释放 + 看门狗管理） |

### 10. 注解驱动

| 注解 / 枚举 | 说明 |
|---|---|
| `@YdszDistributedLock` | 分布式锁注解（key / lockType / waitTime / leaseTime / autoRenew / retryCount） |
| `LockType` | 锁类型枚举（REENTRANT / FAIR / READ_WRITE / SEMAPHORE） |

### 11. 编程式锁操作模板

| 类 | 说明 |
|---|---|
| `LockTemplate` | 编程式锁操作模板（提供 try-with-resources 风格 API，自动管理锁的获取与释放） |

**使用示例：**

```java
lockTemplate.execute("order:lock:" + orderId, 30, TimeUnit.SECONDS, () -> {
    // 业务逻辑
    return null;
});
```

### 12. 锁生命周期事件与降级回调

| 类 | 说明 |
|---|---|
| `LockEventListener` | 分布式锁事件监听器 SPI（onLockAcquired / onLockReleased / onLockAcquireTimeout / onLockRenewalFailed） |
| `LockDegradationCallback` | 锁降级回调接口（Redis 不可用时触发，可发送告警/切换只读模式/触发业务熔断） |
| `CurrentUserIdResolver` | 当前用户 ID 解析器 SPI |

### 13. 锁等待策略

| 类 | 说明 |
|---|---|
| `LockWaitTimePolicy` | 锁等待时间策略（基于历史统计数据动态调整等待时间） |
| `LockWaitStats` | 锁等待统计数据（总等待次数/耗时/超时次数，LongAdder 原子操作） |
| `BackoffPolicy` | 指数退避策略工具类（全抖动 Full Jitter 算法，避免惊群效应） |
| `LockExpressionUtils` | SpEL 表达式解析工具 |

### 14. 指标与健康检查

| 类 | 说明 |
|---|---|
| `LockMetrics` | 锁指标收集器（活跃锁数 / 获取成功失败数 / 超时数 / 续期数 / 竞争数） |
| `LockMetricsConfiguration` | Micrometer 指标自动配置 |
| `LockMicrometerCollector` | Micrometer 指标采集（锁等待时间 / 持有时间 / 成功率） |
| `LockHealthIndicator` | 健康检查（Redis PING + 看门狗配置 + 指标汇总，端点 `/actuator/health/lock`） |

### 15. 锁管理端点

| 类 | 说明 |
|---|---|
| `LockAdminController` | 锁管理端点（查询/释放分布式锁，Web 环境且 `StringRedisTemplate` 可用时装配） |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-lock</artifactId>
</dependency>
```

Web 场景下若需启用 `RepeatSubmitTokenController` 提供 Token 接口，消费方需自行引入 `spring-boot-starter-web` 与 `ydsz-common-auth`（Token 与登录用户绑定）。

### 2. 配置启用

```yaml
ydsz:
  lock:
    enabled: true                       # 模块总开关
    fallback-enabled: false             # Redis 不可用时快速失败（默认不降级）
    watchdog-enabled: true              # 看门狗自动续期开关
    max-renew-times: 100                # 看门狗最大续期次数
    scheduler-pool-size: 2              # 续期调度线程池大小
    namespace: ${spring.application.name}  # 锁键命名空间前缀（多应用隔离）
    default-lock-timeout-seconds: 30    # 默认锁超时
    acquire-pool:
      core-size: 4
      max-size: 32
      queue-capacity: 256
    idempotent:
      default-ttl-seconds: 5
      key-prefix: "ydsz:idem:"
      fail-open: true                   # Redis 不可用时幂等降级策略
    multi-lock:
      max-renew-count: 30
      renew-interval-seconds: 10
```

### 3. 自动装配

`DistributedLockAutoConfiguration` 在 `StringRedisTemplate` 可用且 `ydsz.lock.enabled=true`（默认 true）时激活，自动注册以下 Bean：

| Bean | 激活条件 |
|---|---|
| `LockMetrics` | 模块启用 |
| `LockWatchDog` | 模块启用 |
| `DefaultLockStrategy` | 模块启用 |
| `YdszDistributedLockAspect` | 模块启用 |
| `RedisIdempotentStrategy` | 模块启用 |
| `IdempotentAspect` | 模块启用 |
| `RepeatSubmitTokenService` | 模块启用 |
| `RepeatSubmitAspect` | 模块启用 |
| `DistributedScheduledAspect` | 模块启用（LockStrategy 不存在时降级） |
| `LockRenewalService` | 模块启用（统一续期脚本） |
| `LockReleaseNotifier` | 模块启用（锁释放通知） |
| `LockHealthIndicator` | `spring-boot-health` 可用 |
| `LockAdminController` | Web 环境 + `StringRedisTemplate` 可用 |
| `lockAcquireExecutor` | 模块启用（锁获取线程池） |
| `lockWatchDogScheduler` | 模块启用（续期调度线程池） |

所有 AOP 切面均通过 `@ConditionalOnMissingBean` 注册，允许业务方覆盖默认实现。

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.lock.enabled` | `true` | 模块总开关 |
| `ydsz.lock.fallback-enabled` | `false` | Redis 不可用时降级为本地 `ReentrantLock`（默认快速失败） |
| `ydsz.lock.watchdog-enabled` | `true` | 看门狗自动续期开关 |
| `ydsz.lock.max-renew-times` | `100` | 看门狗最大续期次数（约 30 分钟） |
| `ydsz.lock.scheduler-pool-size` | `2` | 续期调度线程池大小 |
| `ydsz.lock.namespace` | - | 锁键命名空间前缀（多应用共享 Redis 时隔离） |
| `ydsz.lock.default-lock-timeout-seconds` | `30` | 默认锁超时时间（秒） |
| `ydsz.lock.acquire-pool.core-size` | `4` | 锁获取线程池核心线程数 |
| `ydsz.lock.acquire-pool.max-size` | `32` | 锁获取线程池最大线程数 |
| `ydsz.lock.acquire-pool.queue-capacity` | `256` | 锁获取线程池队列容量 |
| `ydsz.lock.idempotent.default-ttl-seconds` | `5` | 幂等锁默认过期时间（秒） |
| `ydsz.lock.idempotent.key-prefix` | `ydsz:idem:` | 幂等键 Redis 前缀 |
| `ydsz.lock.idempotent.fail-open` | `true` | Redis 不可用时幂等降级策略（true=放行，false=拒绝） |
| `ydsz.lock.multi-lock.max-renew-count` | `30` | 多 Key 联锁最大续期次数 |
| `ydsz.lock.multi-lock.renew-interval-seconds` | `10` | 多 Key 联锁续期间隔（秒） |

## 使用示例

### 1. 注解式分布式锁

```java
import com.njydsz.common.lock.annotation.YdszDistributedLock;

@YdszDistributedLock(key = "'order:' + #orderId", waitTime = 3, leaseTime = 30)
public Order getOrder(Long orderId) {
    // 业务逻辑
}
```

### 2. 编程式分布式锁

```java
import java.util.concurrent.TimeUnit;
import com.njydsz.common.lock.annotation.LockType;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.common.lock.strategy.LockStrategy;

@Service
public class OrderService {

    private final LockStrategy lockStrategy;

    public OrderService(LockStrategy lockStrategy) {
        this.lockStrategy = lockStrategy;
    }

    public void processOrder(String orderId) {
        DistributedLocker locker = lockStrategy.getLock(LockType.REENTRANT);
        String lockValue = locker.tryLock("order:" + orderId, 30, TimeUnit.SECONDS);
        if (lockValue == null) {
            throw new IllegalStateException("获取锁失败");
        }
        try {
            // 业务逻辑
        } finally {
            locker.unlock("order:" + orderId, lockValue);
        }
    }
}
```

### 3. 编程式锁模板

```java
import java.util.concurrent.TimeUnit;
import com.njydsz.common.lock.core.LockTemplate;

@Service
public class OrderService {

    private final LockTemplate lockTemplate;

    public OrderService(LockTemplate lockTemplate) {
        this.lockTemplate = lockTemplate;
    }

    public void processOrder(String orderId) {
        lockTemplate.execute("order:lock:" + orderId, 30, TimeUnit.SECONDS, () -> {
            // 业务逻辑
            return null;
        });
    }
}
```

### 4. 幂等性控制

```java
import com.njydsz.common.lock.annotation.Idempotent;

@Idempotent(key = "'pay:' + #request.orderId", ttlSeconds = 10)
@PostMapping("/pay")
public Result<PayResponse> pay(@RequestBody PayRequest request) {
    // 业务逻辑
}
```

### 5. 防重提交

```java
import com.njydsz.common.lock.annotation.RepeatSubmit;

@RepeatSubmit(interval = 3000, message = "请勿重复提交")
@PostMapping("/orders")
public Result<Order> createOrder(@RequestBody OrderDTO dto) {
    // 业务逻辑
}
```

前端配合：

```text
1. GET  /repeat-submit/token?ttlMillis=60000  -> 拿到 token
2. POST /orders  Header: X-Repeat-Token: {token}
```

### 6. 分布式调度

```java
import org.springframework.scheduling.annotation.Scheduled;
import com.njydsz.common.lock.annotation.DistributedScheduled;

@Scheduled(fixedDelay = 60_000L)
@DistributedScheduled(lockKey = "message:expiry-clean", leaseTime = 300)
public void cleanExpiredMessages() {
    // 多节点部署时，同一时刻只有一个节点执行
}
```

### 7. 读写锁

```java
import java.util.concurrent.TimeUnit;
import com.njydsz.common.lock.RedisReadWriteLock;
import com.njydsz.common.lock.strategy.LockStrategy;

// 注入 LockStrategy lockStrategy
RedisReadWriteLock rwLock = lockStrategy.getReadWriteLock("config:" + key);
String lockValue = rwLock.writeLock().tryLock("config:" + key, 30, TimeUnit.SECONDS);
try {
    // 写入配置（写独占）
} finally {
    rwLock.writeLock().unlock("config:" + key, lockValue);
}
// 读锁共享：rwLock.readLock().tryLock(...)
```

### 8. 信号量

```java
import java.util.concurrent.TimeUnit;
import com.njydsz.common.lock.RedisSemaphore;
import com.njydsz.common.lock.strategy.LockStrategy;

// 注入 LockStrategy lockStrategy
RedisSemaphore semaphore = lockStrategy.getSemaphore("resource:pool", 10);
String permitId = semaphore.tryAcquire(5, TimeUnit.SECONDS);
try {
    // 最多 10 个并发访问
} finally {
    semaphore.release(permitId);
}
```

## SPI 扩展点

| 接口 | 作用 | 默认实现 |
|---|---|---|
| `LockStrategy` | 锁策略工厂（按类型创建锁实例） | `DefaultLockStrategy` |
| `IdempotentStrategy` | 幂等键策略（acquire / release / exists） | `RedisIdempotentStrategy` |
| `DistributedLocker` | 分布式锁核心契约 | `RedisReentrantLock` / `RedisFairLock` 等 |
| `LockEventListener` | 锁生命周期事件监听（获取/释放/超时/续期失败） | — |
| `LockDegradationCallback` | 锁降级回调（Redis 不可用时触发） | — |
| `CurrentUserIdResolver` | 当前用户 ID 解析器 | — |

所有默认实现均通过 `@ConditionalOnMissingBean` 注册，业务方在自定义 `@Configuration` 中声明同名 Bean 即可覆盖。

## 健康检查

访问 `/actuator/health/lock` 端点获取分布式锁健康状态，返回详情包含：

- `lockType` / `redisStatus` / `responseTimeMs`：锁后端类型、Redis 连接状态（UP / DOWN）、PING 响应耗时
- `watchDogMaxRenewTimes`：看门狗最大续期次数配置
- `activeLocks`：当前活跃锁数
- `acquireSuccessCount` / `acquireFailCount` / `lockTimeoutCount` / `competitionCount`：锁获取成功 / 失败 / 超时 / 竞争次数
- `watchdogRenewCount`：看门狗续期总次数

Redis 不可用时健康检查返回 DOWN，触发降级策略（若 `fallback-enabled=true`，默认 false 快速失败）。

## 注意事项

1. **防重提交需 Web 环境**：`RepeatSubmitTokenController` 仅在 Servlet Web 环境且 `RepeatSubmitTokenService` Bean 存在时装配；纯后台批处理服务不会暴露 Token 接口。
2. **防重提交需登录上下文**：`RepeatSubmitTokenService` 通过 `AuthContext` 获取当前登录用户 ID，消费方需引入 `ydsz-common-auth` 并完成登录鉴权。
3. **分布式调度降级**：`@DistributedScheduled` 在 `LockStrategy` 不可用时不加锁直接执行，单节点 / 测试环境无需额外配置。
4. **看门狗与 leaseTime**：`@YdszDistributedLock(autoRenew = false)` 时不会启动续期任务，锁将在 `leaseTime` 到期后自动释放，适用于快速完成的操作。
5. **锁键命名空间**：设置 `ydsz.lock.namespace` 后，锁键自动添加前缀 `${namespace}:lock:${userKey}`，多应用共享 Redis 时务必配置以避免锁键冲突。
6. **锁键校验**：用户通过 SpEL 传入的 lockKey 应通过 `LockKeyValidator.validate` 校验，防止超长或包含控制字符的键影响 Redis 性能与日志可读性。
7. **最大续期限制**：WatchDog 默认续期 100 次后停止，锁自动过期。若业务执行时间可能超长，需调大 `max-renew-times` 或拆分任务。
8. **版本兼容**：配置前缀统一为 `ydsz.lock`，历史版本曾使用 `ydsz.distributed-lock` 前缀已废弃，不再支持。
9. **幂等降级策略**：`idempotent.fail-open` 默认 `true`（Redis 不可用时放行），资金类等强幂等场景建议设为 `false`。
10. **锁释放通知**：`LockReleaseNotifier` 通过 Redis 发布订阅 + 本地唤醒双机制，替代高竞争场景下指数退避轮询，降低无效 Redis QPS。

## 命名规范

### 幂等键命名约定

统一格式：`{namespace}:{domain}:{resource}:{action}`

| 段 | 说明 | 示例 |
|---|---|---|
| `namespace` | 固定 `ydsz`，公司级前缀 | `ydsz` |
| `domain` | 业务域 / 模块标识 | `workflow`、`cronjob`、`literule` |
| `resource` | 领域资源（实体 / 聚合根） | `order`、`attachment`、`decision` |
| `action` | 动词过去式或操作名 | `create`、`delete`、`publish` |

**推荐示例：**

```java
@Idempotent(key = "ydsz:workflow:attachment:delete")
@Idempotent(key = "ydsz:literule:trace:replay")
@Idempotent(key = "ydsz:cronjob:glue:save")
```

### 命名原则

1. **只标注写操作**：查询 / 分页 / 导出等只读接口不应使用 `@Idempotent`，避免无意义地膨胀幂等键集合
2. **使用领域资源名**：优先使用领域模型名而非 Controller 类名（`attachment` 而非 `FlowAttachmentController`），保持键长稳定不受重构影响
3. **禁止使用 `:lock` 后缀**：`@Idempotent` 本身已表达锁语义，`:lock` 后缀冗余
4. **禁止使用缩写类名**：`FlowDefinitionController` 与 `FlowDefinitionDesignController` 不一致，应统一用资源名
5. **SpEL 动态键**：同一资源同一操作但需区分租户/用户时，使用 SpEL 动态拼接：`key = "ydsz:order:create:" + #tenantId`
6. **字符集**：小写字母、数字、冒号、连字符、下划线；禁止空格、控制字符、中文

### 反例

```java
// ❌ 冗余 :lock 后缀 + 缩写类名
@Idempotent(key = "ydsz:workflow:FlowDefinitionController:updateDefinition:lock")

// ❌ 只读接口误用幂等
@Idempotent(key = "ydsz:workflow:FlowCcController:pageCc:lock")

// ❌ 前缀不统一
@Idempotent(key = "ruleAdmin:publishPack")

// ✅ 正确写法
@Idempotent(key = "ydsz:literule:pack:publish")
```

### CI 规则

`docs/checkstyle.xml` 已集成 `IdempotentKeyNamingCheck` 正则校验：所有 `@Idempotent(key = "...")` 参数值必须匹配正则 `^[\w:\-\.#$\{\}]+$` 且不以 `:lock` 结尾（SpEL 表达式除外）。执行 `mvn checkstyle:check` 即可校验。

## 变更记录

- **v1.3.0**（2026-08-18）：新增锁生命周期事件监听（`LockEventListener`）、锁降级回调（`LockDegradationCallback`）、锁释放通知器（`LockReleaseNotifier`）、编程式锁模板（`LockTemplate`）、锁等待策略（`LockWaitTimePolicy` / `LockWaitStats` / `BackoffPolicy`）、用户 ID 解析器 SPI（`CurrentUserIdResolver`）、锁管理端点（`LockAdminController`）；新增幂等不可用异常（`IdempotentUnavailableException`）、异常码枚举（`LockExceptionCode`）。
- **v1.2.0**（2026-08-17）：新增分布式锁续期 SPI 服务（`LockRenewalService`），统一收口续期 Lua 脚本；新增幂等降级策略配置项 `idempotent.fail-open`。
- **v1.1.0**（2026-08-15）：新增「命名规范」章节，统一幂等键命名约定（`{namespace}:{domain}:{resource}:{action}`）；在 `docs/checkstyle.xml` 中新增 `@Idempotent` 键名正则校验规则。
- **v1.0.0**（2026-08-02）：补全 `@RepeatSubmit` 防重提交、`@DistributedScheduled` 分布式调度、`LockKeyValidator` 键校验章节；完善配置项表与自动装配说明，新增编程式锁、读写锁、信号量使用示例。
