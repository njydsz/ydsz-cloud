package com.njydsz.common.util.concurrent;

import java.util.Comparator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import com.alibaba.ttl.TtlCallable;
import com.alibaba.ttl.TtlRunnable;
import com.alibaba.ttl.threadpool.TtlExecutors;
import lombok.extern.slf4j.Slf4j;

/**
 * 线程池工具类
 *
 * <p>提供全面的线程池创建和管理方法，支持常规线程池、VirtualThread、CPU 密集型有界队列、
 * 自定义命名、守护线程、拒绝策略、TTL 上下文透传等能力。
 *
 * <h2>快速选择指南</h2>
 * <table border="1">
 *   <caption>场景与 API 映射</caption>
 *   <tr><th>场景</th><th>推荐方法</th><th>说明</th></tr>
 *   <tr><td>固定大小线程池</td><td>{@link #newFixedThreadPool(int)}</td><td>核心线程数固定，有界队列</td></tr>
 *   <tr><td>CPU 密集型</td><td>{@link #newCpuBoundThreadPool()}</td><td>核心数 + 1，有界队列</td></tr>
 *   <tr><td>IO 密集型（Java 21+）</td><td>{@link #newVirtualThreadExecutor()}</td><td>每任务一线程，百万级并发</td></tr>
 *   <tr><td>IO 密集型（兼容）</td><td>{@link #newPlatformThreadExecutor()}</td><td>平台线程缓存池</td></tr>
 *   <tr><td>单个后台线程</td><td>{@link #newSingleThreadExecutor()}</td><td>顺序执行，有界队列</td></tr>
 *   <tr><td>定时任务</td><td>{@link #newScheduledThreadPool(int)}</td><td>ScheduledExecutorService</td></tr>
 *   <tr><td>TTL 上下文透传</td><td>{@link #newTtlFixedThreadPool(int)}</td><td>包装 TransmittableThreadLocal</td></tr>
 *   <tr><td>完全自定义</td><td>{@link #builder()}</td><td>Builder 模式，灵活配置</td></tr>
 *   <tr><td>优雅关闭</td><td>{@link #shutdownGracefully(ExecutorService, long, TimeUnit)}</td><td>先 shutdown，超时强制 shutdownNow</td></tr>
 *   <tr><td>带超时任务</td><td>{@link #submitWithTimeout(ExecutorService, Callable, long, TimeUnit)}</td><td>超时自动取消</td></tr>
 * </table>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
// CHECKSTYLE.OFF: RegexpSinglelineJava — ExecutorUtils 为线程池工具类（云顶规范 15.4 授权实现层），统一封装线程池创建
public final class ExecutorUtils {

    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();

    private static final int DEFAULT_MAX_POOL_SIZE = Math.max(CPU_CORES * 4, 64);
    private static final int DEFAULT_QUEUE_CAPACITY = 1024;

    private static final String THREAD_NAME_PREFIX = "ydsz-";
    private static final AtomicInteger POOL_NUMBER = new AtomicInteger(1);

    private ExecutorUtils() {
        throw new UnsupportedOperationException("ExecutorUtils is a utility class and cannot be instantiated");
    }

    // ==================== ThreadPoolBuilder ====================

    /**
     * 线程池构建器（Builder 模式），用于替代冗长的 {@code newCustomThreadPool} 调用。
     *
     * <p>提供 Fluent API 逐步配置线程池参数，未设置的参数使用默认值。
     *
     * <p>使用示例：
     * <pre>{@code
     * ThreadPoolExecutor pool = ExecutorUtils.builder()
     *     .corePoolSize(10)
     *     .maxPoolSize(20)
     *     .queueCapacity(512)
     *     .threadNamePrefix("my-biz")
     *     .daemon(true)
     *     .build();
     * }</pre>
     @return 处理结果
     */
    public static ThreadPoolBuilder builder() {
        return new ThreadPoolBuilder();
    }

    /**
     * 线程池构建器。
     */
    public static final class ThreadPoolBuilder {
        private int corePoolSize = CPU_CORES;
        private int maximumPoolSize = DEFAULT_MAX_POOL_SIZE;
        private long keepAliveTime = 60L;
        private TimeUnit unit = TimeUnit.SECONDS;
        private int queueCapacity = DEFAULT_QUEUE_CAPACITY;
        private BlockingQueueType queueType = BlockingQueueType.LINKED;
        private String threadNamePrefix;
        private boolean daemon;
        private RejectedExecutionHandler handler = new ThreadPoolExecutor.CallerRunsPolicy();

        private ThreadPoolBuilder() {
        }

        public ThreadPoolBuilder corePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
            return this;
        }

        public ThreadPoolBuilder maxPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
            return this;
        }

        public ThreadPoolBuilder keepAliveTime(long keepAliveTime, TimeUnit unit) {
            this.keepAliveTime = keepAliveTime;
            this.unit = unit;
            return this;
        }

        public ThreadPoolBuilder queueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
            return this;
        }

        public ThreadPoolBuilder queueType(BlockingQueueType queueType) {
            this.queueType = queueType;
            return this;
        }

        public ThreadPoolBuilder threadNamePrefix(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
            return this;
        }

        public ThreadPoolBuilder daemon(boolean daemon) {
            this.daemon = daemon;
            return this;
        }

        public ThreadPoolBuilder rejectedHandler(RejectedExecutionHandler handler) {
            this.handler = handler;
            return this;
        }

        public ThreadPoolExecutor build() {
            ThreadFactory tf = createThreadFactory(threadNamePrefix, daemon);
            BlockingQueue<Runnable> queue = createQueue(queueType, queueCapacity);
            return new ThreadPoolExecutor(
                    corePoolSize, maximumPoolSize,
                    keepAliveTime, unit,
                    queue, tf, handler
            );
        }

        private static BlockingQueue<Runnable> createQueue(BlockingQueueType type, int capacity) {
            return switch (type) {
                case LINKED -> new LinkedBlockingQueue<>(capacity);
                case ARRAY -> new ArrayBlockingQueue<>(capacity);
                case SYNCHRONOUS -> new SynchronousQueue<>();
                case PRIORITY -> new PriorityBlockingQueue<>(capacity);
            };
        }
    }

    /**
     * 阻塞队列类型枚举。
     */
    public enum BlockingQueueType {
        LINKED, ARRAY, SYNCHRONOUS, PRIORITY
    }

    // ==================== 常规线程池 ====================

    /**
     * 创建固定大小线程池
     * @param nThreads 线程数
     @return 线程池实例
     */
    public static ExecutorService newFixedThreadPool(int nThreads) {
        return newFixedThreadPool(nThreads, null);
    }

    /**
     * 创建固定大小线程池
     * @param nThreads 线程数
     * @param threadNamePrefix 线程名前缀
     @return 线程池实例
     */
    public static ExecutorService newFixedThreadPool(int nThreads, String threadNamePrefix) {
        return new ThreadPoolExecutor(
                nThreads,
                nThreads,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(DEFAULT_QUEUE_CAPACITY),
                createThreadFactory(threadNamePrefix),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 创建缓存线程池
     @return 线程池实例
     */
    public static ExecutorService newCachedThreadPool() {
        return newCachedThreadPool(null);
    }

    /**
     * 创建缓存线程池
     * @param threadNamePrefix 线程名前缀
     @return 线程池实例
     */
    public static ExecutorService newCachedThreadPool(String threadNamePrefix) {
        return new ThreadPoolExecutor(
                0,
                DEFAULT_MAX_POOL_SIZE,
                60L,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                createThreadFactory(threadNamePrefix),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 创建单线程池
     @return 线程池实例
     */
    public static ExecutorService newSingleThreadExecutor() {
        return newSingleThreadExecutor(null);
    }

    /**
     * 创建单线程池
     * @param threadNamePrefix 线程名前缀
     @return 线程池实例
     */
    public static ExecutorService newSingleThreadExecutor(String threadNamePrefix) {
        return new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(DEFAULT_QUEUE_CAPACITY),
                createThreadFactory(threadNamePrefix),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    // ==================== CPU 密集型线程池 ====================

    /**
     * 创建 CPU 密集型线程池（核心数 + 1，有界队列）
     @return 线程池实例
     */
    public static ExecutorService newCpuBoundThreadPool() {
        return newCpuBoundThreadPool(null);
    }

    /**
     * 创建 CPU 密集型线程池（核心数 + 1，有界队列）
     * @param threadNamePrefix 线程名前缀
     @return 线程池实例
     */
    public static ExecutorService newCpuBoundThreadPool(String threadNamePrefix) {
        return newCpuBoundThreadPool(threadNamePrefix, DEFAULT_QUEUE_CAPACITY);
    }

    /**
     * 创建 CPU 密集型线程池
     *
     * @param threadNamePrefix 线程名前缀
     * @param queueCapacity    队列容量
     @return 线程池实例
     */
    public static ExecutorService newCpuBoundThreadPool(String threadNamePrefix, int queueCapacity) {
        int corePoolSize = CPU_CORES + 1;
        return new ThreadPoolExecutor(
                corePoolSize,
                corePoolSize,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                createThreadFactory(threadNamePrefix),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    // ==================== VirtualThread 支持 ====================

    /**
     * 创建 VirtualThread 线程池（Java 21+，IO 密集型场景）。
     *
     * <p>适用于大量 IO 操作场景，如 HTTP 请求、数据库查询等。
     * VirtualThread 由 JVM 调度，可创建数百万个而不会耗尽系统线程资源。
     *
     * <p><b>与 {@link #newPlatformThreadExecutor()} 的区别：</b>
     * VirtualThread 不支持线程局部变量池化（{@code ThreadLocal}）的高频修改，
     * 且不支持 {@code synchronized} 块中的无限阻塞（可能导致 carrier thread pinning）。
     * 如无明确需求，建议使用 {@link #newPlatformThreadExecutor()}。
     *
     * @return VirtualThread 每任务一线程执行器
     * @throws UnsupportedOperationException 当前 JVM 不支持 VirtualThread 时抛出
     */
    public static ExecutorService newVirtualThreadExecutor() {
        return newVirtualThreadExecutor(null);
    }

    /**
     * 创建 VirtualThread 线程池（Java 21+，IO 密集型场景）。
     *
     * <p><b>显式 API（无静默回退）：</b>若当前 JVM 不支持 VirtualThread 将直接抛出异常，
     * 避免运行时才发现性能特征与预期不符。
     *
     * @param threadNamePrefix 线程名前缀（不含序号）
     * @return VirtualThread 每任务一线程执行器
     * @throws UnsupportedOperationException 当前 JVM 不支持 VirtualThread 时抛出
     */
    public static ExecutorService newVirtualThreadExecutor(String threadNamePrefix) {
        if (!isVirtualThreadSupported()) {
            throw new UnsupportedOperationException(
                    "VirtualThread 需要 Java 21+，当前 JVM 版本不支持。请降级使用 newPlatformThreadExecutor()");
        }
        String name = threadNamePrefix != null ? threadNamePrefix : "virtual-";
        ThreadFactory factory = Thread.ofVirtual()
                .name(name, 0)
                .uncaughtExceptionHandler(ExecutorUtils::handleUncaughtException)
                .factory();
        // 每任务一虚拟线程：newThreadPerTaskExecutor 使用传入的虚拟线程工厂为每个任务创建独立虚拟线程，
        // 无共享队列、无"无界队列"风险，与《云顶编码规范》禁止的 newFixedThreadPool/newCachedThreadPool
        // 语义不同，属规范豁免项。
        return Executors.newThreadPerTaskExecutor(factory);
    }

    /**
     * 创建显式平台线程线程池（非 VirtualThread）。
     *
     * <p>明确表达"使用 OS 线程"的意图，与 {@link #newVirtualThreadExecutor()} 互补。
     * 底层实现为缓存线程池（核心 0、最大 {@link #DEFAULT_MAX_POOL_SIZE}、60s 空闲回收），
     * 适合 CPU 密集型或混合负载场景。
     *
     * @return 平台线程执行器
     */
    public static ExecutorService newPlatformThreadExecutor() {
        return newPlatformThreadExecutor(null);
    }

    /**
     * 创建显式平台线程线程池（非 VirtualThread）。
     *
     * @param threadNamePrefix 线程名前缀
     * @return 平台线程执行器
     */
    public static ExecutorService newPlatformThreadExecutor(String threadNamePrefix) {
        return newCachedThreadPool(threadNamePrefix);
    }

    /**
     * 判断当前 JVM 是否支持 VirtualThread。
     *
     * <p>通过尝试构造一个 VirtualThread 来探测，无副作用（线程不会启动）。
     *
     * @return 当前 JVM 支持 VirtualThread 返回 true
     */
    public static boolean isVirtualThreadSupported() {
        try {
            Thread.ofVirtual();
            return true;
        } catch (Exception | Error e) {
            return false;
        }
    }

    // ==================== TTL 上下文透传线程池 ====================

    /**
     * 将 ExecutorService 包装为 TransmittableThreadLocal (TTL) 透传线程池。
     *
     * <p>适用于使用 {@link com.alibaba.ttl.TransmittableThreadLocal} 的场景（如 RequestContext、
     * 链路 TraceId、租户 ID 等），确保父线程上下文正确传播到子线程。
     *
     * <p>典型用途：
     * <pre>{@code
     * ExecutorService rawPool = ExecutorUtils.newFixedThreadPool(10);
     * ExecutorService ttlPool = ExecutorUtils.toTtlThreadPool(rawPool);
     * // 或使用 builder 模式：
     * ExecutorService ttlPool = ExecutorUtils.builder()
     *     .corePoolSize(10).maxPoolSize(20)
     *     .ttlEnabled(true)
     *     .buildTtl();
     * }</pre>
     *
     * <p>依赖条件：classpath 上需存在 {@code transmittable-thread-local} 库（ydsz-common-core
     * 已声明为 optional 依赖）。
     *
     * @param executor 原始线程池
     * @return TTL 透传包装的线程池；若 TTL 库不可用则返回原始线程池
     * @since 2.2.0
     */
    public static ExecutorService toTtlThreadPool(ExecutorService executor) {
        if (executor == null) {
            return null;
        }
        try {
            return TtlExecutors.getTtlExecutorService(executor);
        } catch (NoClassDefFoundError e) {
            log.warn("TTL 库不可用，返回原始线程池；请在 classpath 中添加 transmittable-thread-local 依赖");
            return executor;
        }
    }

    /**
     * 创建固定大小 TTL 透传线程池（便捷方法）。
     *
     * @param nThreads 线程数
     * @return TTL 包装的固定线程池
     * @since 2.2.0
     */
    public static ExecutorService newTtlFixedThreadPool(int nThreads) {
        return toTtlThreadPool(newFixedThreadPool(nThreads));
    }

    /**
     * 创建固定大小 TTL 透传线程池（带线程名前缀）。
     *
     * @param nThreads         线程数
     * @param threadNamePrefix 线程名前缀
     * @return TTL 包装的固定线程池
     * @since 2.2.0
     */
    public static ExecutorService newTtlFixedThreadPool(int nThreads, String threadNamePrefix) {
        return toTtlThreadPool(newFixedThreadPool(nThreads, threadNamePrefix));
    }

    /**
     * 包装 Runnable 为 TTL 透传任务。
     *
     * <p>适用于直接向非 TTL 线程池提交单个任务时手动透传上下文。
     *
     * @param runnable 原始 Runnable
     * @return TTL 包装的 Runnable；若 TTL 库不可用则返回原始 Runnable
     * @since 2.2.0
     */
    public static Runnable toTtlRunnable(Runnable runnable) {
        if (runnable == null) {
            return null;
        }
        try {
            return TtlRunnable.get(runnable);
        } catch (NoClassDefFoundError e) {
            return runnable;
        }
    }

    /**
     * 包装 Callable 为 TTL 透传任务。
     *
     * @param callable 原始 Callable
     * @param <T>      返回类型
     * @return TTL 包装的 Callable；若 TTL 库不可用则返回原始 Callable
     * @since 2.2.0
     */
    public static <T> Callable<T> toTtlCallable(Callable<T> callable) {
        if (callable == null) {
            return null;
        }
        try {
            return TtlCallable.get(callable);
        } catch (NoClassDefFoundError e) {
            return callable;
        }
    }

    // ==================== 自定义线程池 ====================

    /**
     * 创建自定义线程池
      * @param corePoolSize corePoolSize
      * @param maximumPoolSize maximumPoolSize
      * @param keepAliveTime keepAliveTime
      * @param unit 时间单位
      * @param workQueue workQueue
      @return 处理结果
     */
    public static ThreadPoolExecutor newCustomThreadPool(
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            TimeUnit unit,
            BlockingQueue<Runnable> workQueue) {
        return newCustomThreadPool(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                workQueue,
                null,
                null
        );
    }

    /**
     * 创建自定义线程池
      * @param corePoolSize 核心线程数
      * @param maximumPoolSize 最大线程数
      * @param keepAliveTime 空闲线程存活时间
      * @param unit 时间单位
      * @param workQueue 工作队列
      * @param threadNamePrefix 线程名前缀
      * @param handler 拒绝策略
      @return 线程池实例
     */
    public static ThreadPoolExecutor newCustomThreadPool(
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            TimeUnit unit,
            BlockingQueue<Runnable> workQueue,
            String threadNamePrefix,
            RejectedExecutionHandler handler) {

        if (handler == null) {
            handler = new ThreadPoolExecutor.CallerRunsPolicy();
        }

        return new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                workQueue,
                createThreadFactory(threadNamePrefix),
                handler
        );
    }

    /**
     * 创建守护线程池
     * @param nThreads 线程数
     * @param threadNamePrefix 线程名前缀
     @return 线程池实例
     */
    public static ExecutorService newDaemonFixedThreadPool(int nThreads, String threadNamePrefix) {
        return new ThreadPoolExecutor(
                nThreads,
                nThreads,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(DEFAULT_QUEUE_CAPACITY),
                createDaemonThreadFactory(threadNamePrefix),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 创建优先级线程池
     *
     * @param nThreads   线程池大小
     * @param comparator 任务优先级比较器
     @return 线程池实例
     */
    public static ExecutorService newPriorityThreadPool(int nThreads, Comparator<Runnable> comparator) {
        BlockingQueue<Runnable> queue = new PriorityBlockingQueue<>(DEFAULT_QUEUE_CAPACITY,
                comparator);
        return new ThreadPoolExecutor(
                nThreads,
                nThreads,
                0L,
                TimeUnit.MILLISECONDS,
                queue,
                createThreadFactory("priority-pool-"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 创建定时线程池
     * @param corePoolSize 核心线程数
     @return 线程池实例
     */
    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize) {
        return newScheduledThreadPool(corePoolSize, null);
    }

    /**
     * 创建定时线程池
     * @param corePoolSize 核心线程数
     * @param threadNamePrefix 线程名前缀
     @return 定时线程池实例
     */
    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize, String threadNamePrefix) {
        return new ScheduledThreadPoolExecutor(
                corePoolSize,
                createThreadFactory(threadNamePrefix),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    // ==================== 线程工厂 ====================

    /**
     * 创建线程工厂
     * @param namePrefix namePrefix
     @return 定时线程池实例
     */
    public static ThreadFactory createThreadFactory(String namePrefix) {
        return createThreadFactory(namePrefix, false);
    }

    /**
     * 创建守护线程工厂
     * @param namePrefix namePrefix
     @return 线程工厂实例
     */
    public static ThreadFactory createDaemonThreadFactory(String namePrefix) {
        return createThreadFactory(namePrefix, true);
    }

    /**
     * 创建线程工厂
     *
     * @param namePrefix 线程名前缀，为空时使用默认格式
     * @param daemon     是否为守护线程
     @return 线程工厂实例
     */
    public static ThreadFactory createThreadFactory(String namePrefix, boolean daemon) {
        String finalPrefix = (namePrefix != null && !namePrefix.isEmpty())
                ? THREAD_NAME_PREFIX + namePrefix
                : THREAD_NAME_PREFIX + "pool-" + POOL_NUMBER.getAndIncrement();
        AtomicInteger threadNumber = new AtomicInteger(1);

        return r -> {
            Thread thread = new Thread(r);
            thread.setName(finalPrefix + threadNumber.getAndIncrement());
            thread.setDaemon(daemon);
            if (thread.getPriority() != Thread.NORM_PRIORITY) {
                thread.setPriority(Thread.NORM_PRIORITY);
            }
            // 设置未捕获异常处理器，避免异常被静默吞没
            thread.setUncaughtExceptionHandler(ExecutorUtils::handleUncaughtException);
            return thread;
        };
    }

    // ==================== 任务执行 ====================

    /**
     * 线程未捕获异常处理器
     *
     * <p>记录线程名和异常堆栈，避免 {@code execute()} 提交的任务异常被静默吞没。
     * {@code submit()} 提交的任务异常会被封装到 Future 中，不会触发此处理器。
     *
     * @param t 抛出未捕获异常的线程
     * @param e 异常
     */
    private static void handleUncaughtException(Thread t, Throwable e) {
        log.error("Uncaught exception in thread {}", t.getName(), e);
    }

    /**
     * Submit a task with actual timeout enforcement.
     * If the task does not complete within the specified timeout, it will be cancelled.
     * @param executor 执行器
     * @param task 任务
     * @param timeout 超时时间
     * @param unit 时间单位
     @return 处理结果
     * @param <T> 泛型参数类型
     */
    public static <T> T submitWithTimeout(ExecutorService executor, Callable<T> task, long timeout, TimeUnit unit) {
        if (executor == null || task == null) {
            return null;
        }
        try {
            Future<T> future = executor.submit(task);
            try {
                return future.get(timeout, unit);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new CompletionException("Task timed out after " + timeout + " " + unit, e);
            } catch (ExecutionException e) {
                throw new CompletionException(e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                throw new CompletionException("Task interrupted", e);
            }
        } catch (RejectedExecutionException e) {
            throw e;
        }
    }

    /**
     * 优雅关闭线程池
     * @param executor 执行器
     * @param timeout 超时时间
     * @param unit 时间单位
     @return 处理结果
     */
    public static boolean shutdownGracefully(ExecutorService executor, long timeout, TimeUnit unit) {
        if (executor == null) {
            return true;
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeout, unit)) {
                executor.shutdownNow();
                return executor.awaitTermination(timeout, unit);
            }
            return true;
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
// CHECKSTYLE.ON: RegexpSinglelineJava
