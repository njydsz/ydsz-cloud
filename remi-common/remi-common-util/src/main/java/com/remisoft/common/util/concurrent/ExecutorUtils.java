package com.remisoft.common.util.concurrent;

import java.util.Comparator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

import lombok.extern.slf4j.Slf4j;

/**
 * 线程池工具类
 *
 * <p>提供全面的线程池创建和管理方法，支持：
 * 1. 常规线程池（Fixed、Cached、Single）
 * 2. VirtualThread（Java 21+，IO 密集型场景）
 * 3. CPU 密集型有界队列线程池
 * 4. 自定义命名、守护线程、拒绝策略
 * </p>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public class ExecutorUtils {

    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();

    private static final int DEFAULT_MAX_POOL_SIZE = Math.max(CPU_CORES * 4, 64);
    private static final int DEFAULT_QUEUE_CAPACITY = 1024;

    private static final String THREAD_NAME_PREFIX = "remi-";
    private static final AtomicInteger POOL_NUMBER = new AtomicInteger(1);

    private ExecutorUtils() {
        throw new UnsupportedOperationException("ExecutorUtils is a utility class and cannot be instantiated");
    }

    // ==================== 常规线程池 ====================

    /**
     * 创建固定大小线程池
     */
    public static ExecutorService newFixedThreadPool(int nThreads) {
        return newFixedThreadPool(nThreads, null);
    }

    /**
     * 创建固定大小线程池
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
     */
    public static ExecutorService newCachedThreadPool() {
        return newCachedThreadPool(null);
    }

    /**
     * 创建缓存线程池
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
     */
    public static ExecutorService newSingleThreadExecutor() {
        return newSingleThreadExecutor(null);
    }

    /**
     * 创建单线程池
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
     */
    public static ExecutorService newCpuBoundThreadPool() {
        return newCpuBoundThreadPool(null);
    }

    /**
     * 创建 CPU 密集型线程池（核心数 + 1，有界队列）
     */
    public static ExecutorService newCpuBoundThreadPool(String threadNamePrefix) {
        return newCpuBoundThreadPool(threadNamePrefix, DEFAULT_QUEUE_CAPACITY);
    }

    /**
     * 创建 CPU 密集型线程池
     *
     * @param threadNamePrefix 线程名前缀
     * @param queueCapacity    队列容量
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
     * 避免运行时才发现性能特征与预期不符。如需自动回退请使用 {@link #newCacheThreadPoolCompat()}。
     *
     * @param threadNamePrefix 线程名前缀（不含序号）
     * @return VirtualThread 每任务一线程执行器
     * @throws UnsupportedOperationException 当前 JVM 不支持 VirtualThread 时抛出
     */
    public static ExecutorService newVirtualThreadExecutor(String threadNamePrefix) {
        if (!isVirtualThreadSupported()) {
            throw new UnsupportedOperationException(
                    "VirtualThread 需要 Java 21+，当前 JVM 版本不支持。请降级使用 newPlatformThreadExecutor() 或 newCacheThreadPoolCompat()");
        }
        String name = threadNamePrefix != null ? threadNamePrefix : "virtual-";
        ThreadFactory factory = Thread.ofVirtual()
                .name(name, 0)
                .uncaughtExceptionHandler(ExecutorUtils::handleUncaughtException)
                .factory();
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
     * 自动选择最优线程池实现的兼容 API（VirtualThread 不可用时静默回退到缓存线程池）。
     *
     * <p>保留旧版行为：优先尝试 VirtualThread，失败时回退。
     * 新项目建议直接使用 {@link #newVirtualThreadExecutor()} 或 {@link #newPlatformThreadExecutor()}，
     * 通过异常明确部署环境约束。
     *
     * @return VirtualThread 或缓存线程池实现
     * @deprecated 自 1.3.0 起建议使用 {@link #newVirtualThreadExecutor()}（显式 VT）或
     *             {@link #newPlatformThreadExecutor()}（显式平台线程），避免静默回退掩盖部署环境约束。
     */
    @Deprecated(since = "1.3.0", forRemoval = true)
    public static ExecutorService newCacheThreadPoolCompat() {
        try {
            String name = "virtual-";
            ThreadFactory factory = Thread.ofVirtual()
                    .name(name, 0)
                    .uncaughtExceptionHandler(ExecutorUtils::handleUncaughtException)
                    .factory();
            return Executors.newThreadPerTaskExecutor(factory);
        } catch (Exception | Error e) {
            log.warn("VirtualThread not supported, fallback to cached thread pool: {}", e.getMessage());
            return newCachedThreadPool(null);
        }
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

    // ==================== 自定义线程池 ====================

    /**
     * 创建自定义线程池
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
     */
    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize) {
        return newScheduledThreadPool(corePoolSize, null);
    }

    /**
     * 创建定时线程池
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
     */
    public static ThreadFactory createThreadFactory(String namePrefix) {
        return createThreadFactory(namePrefix, false);
    }

    /**
     * 创建守护线程工厂
     */
    public static ThreadFactory createDaemonThreadFactory(String namePrefix) {
        return createThreadFactory(namePrefix, true);
    }

    /**
     * 创建线程工厂
     *
     * @param namePrefix 线程名前缀，为空时使用默认格式
     * @param daemon     是否为守护线程
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
