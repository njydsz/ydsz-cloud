# ydsz-common-lock

YDSZ 分布式锁框架 — Redis 重入锁 / 公平锁 / 读写锁 / 信号量、看门狗自动续期、@Idempotent 幂等、@YdszDistributedLock 声明式锁、降级策略。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L4 基础数据层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 26 |

## 核心能力

### 锁实现

| 类 | 说明 |
|---|---|
| `RedisReentrantLock` | Redis 可重入锁（SET NX EX + Lua 原子释放） |
| `RedisFairLock` | 公平锁（FIFO 队列排队） |
| `RedisReadWriteLock` | 读写锁（读共享 / 写独占） |
| `RedisSemaphore` | 分布式信号量（并发数控制） |
| `RedisMultiLock` | 多锁（多个 Key 同时加锁，全部成功或全部失败） |
| `FallbackDistributedLock` | 降级锁（Redis 不可用时退化为 JVM 锁） |
| `AbstractRedisDistributedLock` | Redis 锁抽象基类 |
| `DistributedLocker` | 锁操作接口 |

### 看门狗机制

| 类 | 说明 |
|---|---|
| `LockWatchDog` | 看门狗调度器（定时续期锁，防止业务未完成锁过期） |
| `DefaultLockStrategy` | 默认锁策略（加锁 → 执行 → 释放 + 看门狗管理） |
| `LockStrategy` | 锁策略接口 |

### 声明式注解

| 注解 | 说明 |
|---|---|
| `@YdszDistributedLock` | 分布式锁注解（指定 Key / 超时 / 自动续期） |
| `@Idempotent` | 幂等注解（防重复提交，Token 机制） |
| `@IdempotentExempt` | 幂等豁免标记 |
| `LockType` | 锁类型枚举（REENTRANT / FAIR / READ / WRITE） |

### AOP 切面

| 类 | 说明 |
|---|---|
| `YdszDistributedLockAspect` | 分布式锁切面（SpEL Key 解析 + 锁获取/释放） |
| `IdempotentAspect` | 幂等切面（Token 校验 + 幂等记录） |

### 可观测性

| 类 | 说明 |
|---|---|
| `LockMetrics` / `LockMetricsExporter` | 指标导出接口 |
| `LockMicrometerCollector` | Micrometer 指标采集（锁等待时间 / 持有时间 / 成功率） |
| `LockMetricsConfiguration` | 指标自动配置 |
| `LockHealthIndicator` | 健康检查 |

## 使用示例

```java
// 声明式分布式锁
@YdszDistributedLock(key = "'order:' + #orderId", waitTime = 3, leaseTime = 30)
public Order getOrder(Long orderId) { ... }

// 声明式幂等
@Idempotent(key = "'pay:' + #request.orderId", expire = 10)
public PayResponse pay(PayRequest request) { ... }
```

## 配置项

```yaml
ydsz:
  lock:
    watchdog:
      enabled: true               # 看门狗开关
      check-interval: 10s         # 续期检查间隔
      extension-factor: 0.3       # 续期因子（剩余 30% 时续期）
    idempotent:
      default-expire: 10          # 默认幂等过期（秒）
      token-header: X-Idempotent-Token
    fallback:
      enabled: true               # 降级锁开关
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `DistributedLockAutoConfiguration` | Redis 可用时激活 |
| `LockMetricsConfiguration` | Micrometer 可用时激活 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-lock</artifactId>
</dependency>
```
