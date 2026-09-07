# ydsz-common-lock

> 分布式锁与幂等（L4 基础数据层）— 可重入 / 公平 / 联锁 + WatchDog 续约 + 幂等注解

提供基于 Redis 的分布式可重入锁 / 公平锁 / 联锁 / 信号量 / 读写锁，WatchDog 自动续约，锁降级回调 SPI；`@Idempotent` 幂等注解（fail-open 可配置），`@RepeatSubmit` 重复提交 Token，`@DistributedScheduled` 分布式定时任务。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L4 基础数据层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供分布式锁、幂等、重复提交、分布式定时任务能力 |
| **依赖** | ydsz-common-core、ydsz-common-util、ydsz-common-exception、ydsz-common-cache、ydsz-common-redis、ydsz-common-json；spring-boot-starter-aspectj；可选 spring-web、springdoc-openapi、jakarta.servlet-api、micrometer-core |
| **版本** | 2.1.0 |

## 核心能力

### 1. 分布式锁核心抽象

| 类 | 说明 |
|---|---|
| `DistributedLocker` | 分布式锁核心接口（`lock` / `tryLock` / `unlock` / 可重入计数） |
| `AbstractRedisDistributedLock` | Redis 分布式锁抽象基类（Lua 保证原子性、可重入计数、WatchDog 续约） |
| `RedisReentrantLock` | 可重入锁（默认实现，支持重入计数） |
| `RedisFairLock` | 公平锁（按请求时序获取，避免饥饿） |
| `RedisMultiLock` | 联锁（多资源原子加锁，防死锁） |
| `RedisReadWriteLock` | 读写锁（读共享 / 写独占，锁降级支持） |
| `RedisSemaphore` | 信号量（控制并发访问数量） |
| `FallbackDistributedLock` | 降级锁（Redis 不可用时，自动降级为本地 `ReentrantLock`） |

**WatchDog 续约机制**：锁持有期间后台线程自动续期（1/3 锁 TTL 时续一次），业务执行完毕后停止。

### 2. 注解驱动的锁

| 注解 | 说明 |
|---|---|
| `@YdszDistributedLock` | 分布式锁注解（key 支持 SpEL，支持 leaseTime / waitTime / TimeUnit 配置） |
| `@LockType` | 锁类型标记（REENTRANT / FAIR / MULTI） |
| `@DistributedScheduled` | 分布式定时任务注解（单节点执行，防多实例重复触发） |
| `@Idempotent` | 幂等注解（Redis Lua 原子判断 + 写入） |
| `@IdempotentExempt` | 幂等豁免标记（标注在方法上跳过幂等检查） |
| `@RepeatSubmit` | 重复提交注解（Token 机制，前端先申请 Token 再提交） |

### 3. 幂等与防重

| 类 | 说明 |
|---|---|
| `IdempotentAspect` | `@Idempotent` AOP 切面 |
| `RepeatSubmitAspect` | `@RepeatSubmit` AOP 切面 |
| `IdempotentStrategy` **SPI** | 幂等策略接口 |
| `RedisIdempotentStrategy` | Redis 幂等策略（Lua 原子 SETNX + TTL） |
| `RepeatSubmitTokenService` | 重复提交 Token 服务（Redis 存储） |
| `IdempotentUnavailableException` | 幂等不可用异常 |

**fail-open 配置**：`ydsz.lock.idempotent.fail-open=true`（默认）时，Redis 不可用放行请求；`false` 时拒绝。

### 4. 锁监控与降级

| 类 | 说明 |
|---|---|
| `LockWaitStats` | 锁等待统计（waitTime 分布 / 失败次数） |
| `LockWaitTimePolicy` | 锁等待时间策略接口（自定义拒绝策略） |
| `LockDegradationCallback` **SPI** | 锁降级回调（Redis 不可用通知业务方） |
| `LockEventListener` | 锁事件监听器（acquire / release / timeout 事件） |
| `LockMetricsConfiguration` / `LockMetrics` | Micrometer 锁指标采集 |
| `LockMicrometerCollector` | 锁指标收集器（gauge / counter） |

### 5. 运维端点

| 类 | 说明 |
|---|---|
| `LockAdminController` | 锁管理端点（`/admin/locks`，展示当前活跃锁 / 等待队列） |
| `RepeatSubmitTokenController` | 重复提交 Token 验签端点 |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-lock</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  lock:
    enabled: true
    idempotent:
      fail-open: true              # Redis 不可用时是否放行（默认 true）
      ttl-seconds: 30              # 幂等键 TTL
    repeat-submit:
      token-ttl-seconds: 60        # Token 有效期
    distributed:
      watch-dog-enabled: true      # 是否启用 WatchDog 续约
      watch-dog-interval-ms: 3000  # 续约间隔（锁 TTL 的 1/3）
      metrics-enabled: true         # 是否采集锁指标
    scheduler:
      enabled: true                # 是否启用分布式定时任务
      lock-ttl-seconds: 60
```

### 3. 注解使用

```java
@Service
public class OrderService {

    // 分布式锁（SpEL key，SpEL root = method args）
    @YdszDistributedLock(key = "'order:lock:' + #orderId", leaseTime = 30)
    public void processOrder(Long orderId) {
        // 业务逻辑（锁自动释放）
    }

    // 幂等注解（30s 内同一 requestId 仅处理一次）
    @Idempotent(key = "#req.requestId", expire = 30)
    public Result processRequest(IdempotentRequest req) { ... }

    // 防重复提交（前端先 GET /api/repeat-token，再 POST 携带 token）
    @RepeatSubmit(expire = 60)
    public Result submitOrder(OrderRequest request) { ... }

    // 分布式定时任务（每小时执行，仅一个节点执行）
    @Scheduled(cron = "0 0 * * * *")
    @DistributedScheduled(lockKey = "order:cleanup:job", leaseTime = 300)
    public void cleanupExpiredOrders() { ... }
}
```

### 4. 编程式使用

```java
@Autowired
private DistributedLocker distributedLocker;

distributedLocker.execute("order:lock:" + orderId, 30, TimeUnit.SECONDS, () -> {
    // 业务逻辑
    return result;
});
```

## SPI 扩展点

| SPI 接口 | 用途 | 注册方式 |
|---|---|---|
| `LockStrategy` **SPI** | 分布式锁策略工厂（默认 Redis） | `@ConditionalOnMissingBean` |
| `IdempotentStrategy` **SPI** | 幂等键策略 | `@ConditionalOnMissingBean` |
| `DistributedLocker` **SPI** | 分布式锁核心契约 | 由 `LockStrategy` 间接扩展 |
| `LockWaitTimePolicy` | 锁等待时间策略 | `@Component` |
| `LockDegradationCallback` **SPI** | 锁降级回调接口 | `@Component` |
| `LockEventListener` **SPI** | 锁事件监听器（acquire/release/timeout） | `@Component` |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/lock` | 分布式锁健康检查 | `ydsz.lock.enabled=true` + `spring-boot-health` 在 classpath |

`LockHealthIndicator` 暴露信息：
- `redis_connection` — Redis 连接状态
- `active_locks` — 当前活跃锁数量
- `wait_queue_size` — 锁等待队列大小

## 自动配置类

| 类 | 触发条件 |
|---|---|
| `DistributedLockAutoConfiguration` | `ydsz-common-redis` 在 classpath |
| `LockMetricsConfiguration` | Micrometer 存在 |

## 注意事项

1. **WatchDog 不跨节点**：续约仅在当前业务节点持有锁时有效；节点宕机后锁依赖 TTL 自动释放（leaseTime 务必覆盖业务执行时间）。
2. **幂等 fail-open 语义**：默认 `true`（Redis 不可用放行），安全敏感场景请设置为 `false`（拒绝服务）。
3. **锁降级**：Redis 不可用时自动降级为本地 `ReentrantLock`，通过 `LockDegradationCallback` 通知业务方。
4. **避免锁中远程调用**：持有锁期间尽量避免 RPC / DB 远程调用，防止锁过期但业务仍在执行。
5. **公平锁性能**：公平锁比可重入锁慢约 20%（ZSET 排序），非必要使用可重入锁。

## 变更记录

- **2.1.0**（2026-09-04）：新增 RedisReadWriteLock 读写锁；新增 @IdempotentExempt 幂等豁免标记；WatchDog 续约间隔改为可配置。
- **2.0.0**（2026-09-01）：分布式锁 + 幂等体系重构（可重入 / 公平 / 联锁 / 信号量）；WatchDog 续约机制；分布式定时任务（@DistributedScheduled）。
- **1.0.0**（2026-08-02）：初始版本。
