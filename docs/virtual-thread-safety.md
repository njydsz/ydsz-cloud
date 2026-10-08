# 虚拟线程 Pinning 安全防护指南

> **适用范围**：ydsz-cloud 所有使用 Java 21 虚拟线程（VirtualThread）的模块
> **最后更新**：2025-01-24
> **关联模块**：`ydsz-common-thread`（`ExecutorUtils`）

---

## 1. 项目虚拟线程使用现状

### 1.1 核心入口

项目通过 `ydsz-common-thread` 模块的 `ExecutorUtils` 工具类提供虚拟线程能力：

| 方法 | 说明 | 使用场景 |
|------|------|---------|
| `newVirtualThreadExecutor()` | 每任务一虚拟线程（无界） | IO 密集型：HTTP 调用、DB 查询 |
| `newPlatformThreadExecutor()` | 平台线程缓存池（回退方案） | CPU 密集型或 virtual thread 不可用时 |
| `newCachedThreadPool()` | 弹性平台线程池 | 兼容旧代码/非 virtual 场景 |

### 1.2 配置开关

Nacos 配置（`ydsz-common.yml`）中 `ydsz.thread.virtual-by-default` 控制是否默认使用虚拟线程（当前默认 `false`）。业务模块可通过编程方式调用 `ExecutorUtils.newVirtualThreadExecutor()` 按需启用。

### 1.3 载体线程（Carrier Thread）

Java 21 虚拟线程由 ForkJoinPool 中的平台线程（carrier）调度。虚拟线程在 `monitorenter`（进入 `synchronized` 块）期间如果执行阻塞操作，会被"钉"（pinned）在载体线程上，无法让出。

---

## 2. Pinning 风险点

### 2.1 高危场景

| 风险操作 | 严重程度 | 说明 |
|----------|---------|------|
| `synchronized` 块内调用 `BlockingQueue.take()` | **HIGH** | 阻塞导致 carrier pinning，吞吐量骤降 |
| `synchronized` 块内调用 `Thread.sleep()` | **HIGH** | 同上 |
| `synchronized` 块内执行 IO（Socket/文件读写） | **HIGH** | 长时间 pin 住 carrier |
| `synchronized` 包裹 `ReentrantLock.lock()` | LOW | ReentrantLock 本身不会 pin，但 synchronized 会 |

### 2.2 不会 Pinning 的场景

- 在虚拟线程中使用 `ReentrantLock` 代替 `synchronized` 进行互斥（推荐）
- `LockSupport.park()` 阻塞（JVM 感知并自动 unmount）
- `BlockingQueue` 不使用 `synchronized` 包裹的阻塞操作
- `CompletableFuture` / `CountDownLatch.await()` 等标准阻塞原语

---

## 3. 排查方法

### 3.1 JVM 启动参数

| 参数 | 输出详细度 | 性能开销 | 推荐环境 |
|------|-----------|---------|---------|
| `-Djdk.tracePinnedThreads=full` | 完整堆栈 + 线程名 + 阻塞时长 | 中等 | 性能测试/调优阶段 |
| `-Djdk.tracePinnedThreads=short` | 仅简短信息 | 较低 | 开发/测试环境 |
| 不设置 | 不输出 | 无 | 生产环境 |

### 3.2 输出示例

```
Pinned thread: <virtual thread "virtual-3"> carrier=#56
  at com.example.Service.process(Service.java:42)
  - parking on <0x000000061a3f8a10> (a java.util.concurrent.locks.ReentrantLock$NonfairSync)
```

### 3.3 日志过滤

配合日志框架收集 pinning 信息：

```bash
# 若 pinning 信息输出到 stderr，在 CI 脚本中 grep
grep -E "Pinned thread|Thread pinning" app.log
```

---

## 4. 代码示例

### 4.1 不安全代码

```java
// ✗ 错误：synchronized 内阻塞导致 carrier pinning
private final BlockingQueue<Task> queue = new LinkedBlockingQueue<>();

public Task takeNext() {
    synchronized (this) {
        try {
            return queue.take(); // BLOCKED inside synchronized = PINNING
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}

// ✗ 错误：synchronized 内 Thread.sleep
public void retryWithBackoff(int attempt) {
    synchronized (this) {
        try {
            Thread.sleep(1000L << attempt); // PINNING
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

### 4.2 安全替代方案

```java
// 正确：使用 ReentrantLock 替代 synchronized
private final ReentrantLock lock = new ReentrantLock();
private final BlockingQueue<Task> queue = new LinkedBlockingQueue<>();

public Task takeNext() {
    lock.lock();
    try {
        return queue.take(); // 安全：ReentrantLock 不会导致 pinning
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
    } finally {
        lock.unlock();
    }
}

// 正确：分离同步和阻塞操作
public Task takeNextSafely() {
    Task task;
    synchronized (this) {
        task = queue.poll(); // 非阻塞操作，快速释放锁
    }
    if (task == null) {
        try {
            task = queue.take(); // 在 synchronized 外阻塞
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    return task;
}

// 正确：使用 tryLock 限时等待避免死锁
public boolean tryProcess(long timeout, TimeUnit unit) {
    try {
        if (lock.tryLock(timeout, unit)) {
            try {
                // 临界区逻辑
                return true;
            } finally {
                lock.unlock();
            }
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
    return false;
}
```

### 4.3 性能对比结论

| 方式 | 10K 并发任务吞吐量 | Carrier 利用率 |
|------|-------------------|---------------|
| `synchronized` + 阻塞操作 | 极低（pinned carrier 无法复用） | ~5-10% |
| `ReentrantLock` + 阻塞操作 | 正常（JVM 自动 mount/unmount） | ~95%+ |
| 无同步 + 阻塞操作 | 最优 | ~98%+ |

---

## 5. 最佳实践总结

1. **禁止** 在 `synchronized` 块内执行任何可能阻塞虚拟线程的操作（`take()`、`sleep()`、`await()`、IO 等）
2. **优先使用** `ReentrantLock` 替代 `synchronized` 作为互斥手段
3. **同步块应保持极短**，仅保护内存级原子操作
4. **阻塞操作放在同步块外** 执行
5. **测试/调优阶段** 启用 `-Djdk.tracePinnedThreads=short` 监控
6. **生产环境不启用** `tracePinnedThreads`（有性能开销）

---

## 6. 参考资料

- [JEP 491: Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491)（Java 24+ 已修复，但本项目基于 Java 21）
- [Oracle 官方文档：Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
- 项目编码规范：`docs/云顶编码规范.md` — 线程池章节
