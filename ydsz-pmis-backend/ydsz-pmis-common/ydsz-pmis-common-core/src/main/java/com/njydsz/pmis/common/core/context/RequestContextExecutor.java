package com.njydsz.pmis.common.core.context;

import com.alibaba.ttl.threadpool.TtlExecutors;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;
import java.util.Collections;
import java.util.List;

/**
 * 请求上下文自动传播线程池执行器
 *
 * <p>基于 TransmittableThreadLocal + TtlExecutors 模式，自动在线程池任务中传播
 * RequestContext 上下文（userId、tenantId、traceId 等），无需手动 capture/restore。</p>
 *
 * <p><b>工作原理：</b></p>
 * <ul>
 *   <li>提交任务时，TtlExecutors 自动 capture 当前线程的上下文</li>
 *   <li>任务执行时，自动 restore 上下文到工作线程</li>
 *   <li>任务完成后，自动清理工作线程的上下文</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * // 方式一：包装现有线程池
 * ThreadPoolExecutor original = new ThreadPoolExecutor(
 *     4, 8, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
 * ExecutorService ttlExecutor = RequestContextExecutor.wrap(original);
 *
 * // 方式二：直接创建
 * ExecutorService executor = RequestContextExecutor.newFixedThreadPool(4, "biz-pool");
 *
 * // 提交任务，上下文自动传播
 * RequestContext.setUserId("user123");
 * CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
 *     // 自动获取 userId = "user123"
 *     return RequestContext.getUserId();
 * }, executor);
 * }</pre>
 *
 * <p><b>注意事项：</b></p>
 * <ul>
 *   <li>必须在应用关闭时调用 {@link #shutdown()} 或 {@link #shutdownNow()} 释放资源</li>
 *   <li>配合 RequestContext.clear() 使用，在请求入口处清理旧上下文</li>
 *   <li>支持 ScheduledExecutorService 的 TTL 包装</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Slf4j
public final class RequestContextExecutor {

    /** 底层被包装的 ExecutorService，实际执行任务的线程池 */
    private final ExecutorService delegate;
    /** 是否拥有底层线程池的所有权，为 true 时 shutdown/shutdownNow 会实际关闭线程池 */
    private final boolean ownsDelegate;

    private RequestContextExecutor(ExecutorService delegate, boolean ownsDelegate) {
        this.delegate = delegate;
        this.ownsDelegate = ownsDelegate;
    }

    /**
     * 包装现有 ExecutorService，使其自动传播 RequestContext
     *
     * <p>返回的 ExecutorService 会在任务提交时自动捕获当前线程的上下文，
     * 并在任务执行的工作线程中恢复上下文。</p>
     *
     * <p><b>注意：</b>此方法不接管原始 ExecutorService 的生命周期，
     * 调用方仍需负责关闭原始线程池。</p>
     *
     * @param executor 原始 ExecutorService
     * @return 包装后的 ExecutorService
     */
    public static ExecutorService wrap(ExecutorService executor) {
        if (executor == null) {
            throw new IllegalArgumentException("executor 不能为 null");
        }
        return TtlExecutors.getTtlExecutorService(executor);
    }

    /**
     * 包装现有 ScheduledExecutorService，使其自动传播 RequestContext
     *
     * @param scheduler 原始 ScheduledExecutorService
     * @return 包装后的 ScheduledExecutorService
     */
    public static ScheduledExecutorService wrapScheduled(ScheduledExecutorService scheduler) {
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler 不能为 null");
        }
        return TtlExecutors.getTtlScheduledExecutorService(scheduler);
    }

    /**
     * 包装现有 ThreadPoolExecutor，使其自动传播 RequestContext
     *
     * @param executor 原始 ThreadPoolExecutor
     * @return 包装后的 ExecutorService
     */
    public static ExecutorService wrap(ThreadPoolExecutor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("executor 不能为 null");
        }
        return TtlExecutors.getTtlExecutorService(executor);
    }

    /**
     * 创建一个固定大小线程池，自动传播 RequestContext
     *
     * @param nThreads 线程池大小
     * @param poolName 线程池名称（用于线程命名和日志）
     * @return RequestContextExecutor 实例
     */
    public static RequestContextExecutor newFixedThreadPool(int nThreads, String poolName) {
        ThreadFactory threadFactory = new RequestContextThreadFactory(poolName);
        ThreadPoolExecutor delegate = new ThreadPoolExecutor(
                nThreads, nThreads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), threadFactory);
        return new RequestContextExecutor(
                TtlExecutors.getTtlExecutorService(delegate), true);
    }

    /** 缓存线程池最大线程数上限：CPU核心数的4倍，防止OOM */
    private static final int MAX_CACHED_THREADS = Math.max(16, Runtime.getRuntime().availableProcessors() * 4);

    /**
     * 创建一个缓存线程池，自动传播 RequestContext
     *
     * @param poolName 线程池名称
     * @return RequestContextExecutor 实例
     */
    public static RequestContextExecutor newCachedThreadPool(String poolName) {
        ThreadFactory threadFactory = new RequestContextThreadFactory(poolName);
        ThreadPoolExecutor delegate = new ThreadPoolExecutor(
                0, MAX_CACHED_THREADS, 60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(), threadFactory);
        return new RequestContextExecutor(
                TtlExecutors.getTtlExecutorService(delegate), true);
    }

    /**
     * 创建一个单线程执行器，自动传播 RequestContext
     *
     * @param poolName 线程池名称
     * @return RequestContextExecutor 实例
     */
    public static RequestContextExecutor newSingleThreadExecutor(String poolName) {
        ThreadFactory threadFactory = new RequestContextThreadFactory(poolName);
        ThreadPoolExecutor delegate = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), threadFactory);
        return new RequestContextExecutor(
                TtlExecutors.getTtlExecutorService(delegate), true);
    }

    /**
     * 创建一个定时调度线程池，自动传播 RequestContext
     *
     * @param coreSize 核心线程数
     * @param poolName 线程池名称
     * @return ScheduledExecutorService 实例
     */
    public static ScheduledExecutorService newScheduledThreadPool(int coreSize, String poolName) {
        ThreadFactory threadFactory = new RequestContextThreadFactory(poolName);
        ScheduledThreadPoolExecutor delegate = new ScheduledThreadPoolExecutor(
                coreSize,
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        return TtlExecutors.getTtlScheduledExecutorService(delegate);
    }

    /**
     * 创建自定义配置的线程池，自动传播 RequestContext
     *
     * @param corePoolSize    核心线程数
     * @param maximumPoolSize 最大线程数
     * @param keepAliveTime   空闲线程存活时间
     * @param unit            时间单位
     * @param workQueue       工作队列
     * @param poolName        线程池名称
     * @return RequestContextExecutor 实例
     */
    public static RequestContextExecutor newThreadPool(int corePoolSize, int maximumPoolSize,
                                                        long keepAliveTime, TimeUnit unit,
                                                        BlockingQueue<Runnable> workQueue,
                                                        String poolName) {
        ThreadFactory threadFactory = new RequestContextThreadFactory(poolName);
        ThreadPoolExecutor delegate = new ThreadPoolExecutor(
                corePoolSize, maximumPoolSize, keepAliveTime, unit,
                workQueue, threadFactory);
        return new RequestContextExecutor(
                TtlExecutors.getTtlExecutorService(delegate), true);
    }

    /**
     * 提交任务
     *
     * @param task 要执行的任务
     * @return Future 表示任务的执行结果
     */
    public Future<?> submit(Runnable task) {
        return delegate.submit(task);
    }

    /**
     * 提交有返回值的任务
     *
     * @param task 要执行的任务
     * @param <T>  返回值类型
     * @return Future 表示任务的执行结果
     */
    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(task);
    }

    /**
     * 执行任务（无返回值）
     *
     * @param command 要执行的任务
     */
    public void execute(Runnable command) {
        delegate.execute(command);
    }

    /**
     * 关闭线程池
     *
     * <p>仅当此实例拥有线程池的所有权（通过 newXxx 方法创建）时才会执行关闭。</p>
     */
    public void shutdown() {
        if (ownsDelegate) {
            log.debug("【RequestContext】关闭线程池 | executor={}", delegate.getClass().getSimpleName());
            delegate.shutdown();
        }
    }

    /**
     * 立即关闭线程池
     *
     * <p>仅当此实例拥有线程池的所有权时才会执行关闭。</p>
     *
     * @return 等待执行的任务列表
     */
    public List<Runnable> shutdownNow() {
        if (ownsDelegate) {
            log.warn("【RequestContext】立即关闭线程池 | executor={}", delegate.getClass().getSimpleName());
            return delegate.shutdownNow();
        }
        return Collections.emptyList();
    }

    /**
     * 检查线程池是否已关闭
     */
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    /**
     * 检查所有任务是否已完成
     */
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    /**
     * 等待线程池终止
     *
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return true-已终止，false-超时
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    /**
     * 获取底层 ExecutorService
     *
     * <p>仅在需要直接操作底层执行器时使用。</p>
     */
    public ExecutorService unwrap() {
        return delegate;
    }

    /**
     * 线程工厂：为线程设置有意义的名称
     */
    private static final class RequestContextThreadFactory implements ThreadFactory {
        private final String poolName;
        private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();

        RequestContextThreadFactory(String poolName) {
            this.poolName = poolName;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = defaultFactory.newThread(r);
            thread.setName(poolName + "-" + thread.getName());
            return thread;
        }
    }
}
