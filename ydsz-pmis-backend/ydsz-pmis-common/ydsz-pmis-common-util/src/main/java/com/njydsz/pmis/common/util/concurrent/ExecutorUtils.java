package com.njydsz.pmis.common.util.concurrent;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Slf4j
public class ExecutorUtils {

    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();

    private static final int DEFAULT_MAX_POOL_SIZE = Math.max(CPU_CORES * 4, 64);
    private static final int DEFAULT_QUEUE_CAPACITY = 1024;

    private static final String THREAD_NAME_PREFIX = "ydsz-";
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
     * 创建 VirtualThread 线程池（Java 21+，IO 密集型场景）
     *
     * <p>适用于大量 IO 操作场景，如 HTTP 请求、数据库查询等。
     * 若当前 JVM 不支持 VirtualThread，则回退到缓存线程池。</p>
     */
    public static ExecutorService newVirtualThreadExecutor() {
        return newVirtualThreadExecutor(null);
    }

    /**
     * 创建 VirtualThread 线程池（Java 21+，IO 密集型场景）
     *
     * @param threadNamePrefix 线程名前缀
     */
    public static ExecutorService newVirtualThreadExecutor(String threadNamePrefix) {
        try {
            String name = threadNamePrefix != null ? threadNamePrefix : "virtual-";
            ThreadFactory factory = Thread.ofVirtual().name(name, 0).factory();
            return Executors.newThreadPerTaskExecutor(factory);
        } catch (NoSuchMethodError | UnsupportedOperationException e) {
            log.warn("VirtualThread not supported, fallback to cached thread pool");
            return newCachedThreadPool(threadNamePrefix);
        }
    }

    /**
     * 判断当前 JVM 是否支持 VirtualThread
     */
    public static boolean isVirtualThreadSupported() {
        try {
            Thread.ofVirtual();
            return true;
        } catch (NoSuchMethodError | UnsupportedOperationException e) {
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static ExecutorService newPriorityThreadPool(int nThreads, Comparator<Runnable> comparator) {
        BlockingQueue<Runnable> queue = new PriorityBlockingQueue<>(DEFAULT_QUEUE_CAPACITY,
                (Comparator) new RunnableComparatorAdapter(comparator));
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
     * 将 Comparator&lt;Runnable&gt; 适配为可直接比较 Runnable 的 Comparator
     * 由于 PriorityBlockingQueue 中的元素需要可比较，此适配器确保任意 Runnable 都能安全比较
     */
    private static class RunnableComparatorAdapter implements Comparator<Runnable> {
        private final Comparator<Runnable> delegate;

        RunnableComparatorAdapter(Comparator<Runnable> delegate) {
            this.delegate = delegate;
        }

        @Override
        public int compare(Runnable o1, Runnable o2) {
            return delegate.compare(o1, o2);
        }
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
            return thread;
        };
    }

    // ==================== 拒绝策略 ====================

    /**
     * 创建中止策略
     */
    public static RejectedExecutionHandler createAbortPolicy() {
        return new ThreadPoolExecutor.AbortPolicy();
    }

    /**
     * 创建调用者运行策略
     */
    public static RejectedExecutionHandler createCallerRunsPolicy() {
        return new ThreadPoolExecutor.CallerRunsPolicy();
    }

    /**
     * 创建丢弃策略
     */
    public static RejectedExecutionHandler createDiscardPolicy() {
        return new ThreadPoolExecutor.DiscardPolicy();
    }

    /**
     * 创建丢弃最旧任务策略
     */
    public static RejectedExecutionHandler createDiscardOldestPolicy() {
        return new ThreadPoolExecutor.DiscardOldestPolicy();
    }

    /**
     * 创建自定义拒绝策略
     */
    public static RejectedExecutionHandler createCustomPolicy(RejectedExecutionHandler handler) {
        return handler != null ? handler : createCallerRunsPolicy();
    }

    // ==================== 任务执行 ====================

    /**
     * 执行任务（不返回结果）
     */
    public static void execute(ExecutorService executor, Runnable task) {
        if (executor != null && task != null) {
            executor.execute(task);
        }
    }

    /**
     * 提交任务（返回 Future）
     */
    public static <T> Future<T> submit(ExecutorService executor, Callable<T> task) {
        if (executor != null && task != null) {
            return executor.submit(task);
        }
        return null;
    }

    /**
     * 提交任务（返回 Future）
     */
    public static Future<?> submit(ExecutorService executor, Runnable task) {
        if (executor != null && task != null) {
            return executor.submit(task);
        }
        return null;
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
     * 批量执行任务
     */
    public static <T> List<Future<T>> invokeAll(
            ExecutorService executor,
            Collection<? extends Callable<T>> tasks) throws InterruptedException {
        if (executor != null && tasks != null) {
            return executor.invokeAll(tasks);
        }
        return new ArrayList<>();
    }

    /**
     * 批量执行任务（带超时）
     */
    public static <T> List<Future<T>> invokeAll(
            ExecutorService executor,
            Collection<? extends Callable<T>> tasks,
            long timeout,
            TimeUnit unit) throws InterruptedException {
        if (executor != null && tasks != null) {
            return executor.invokeAll(tasks, timeout, unit);
        }
        return new ArrayList<>();
    }

    /**
     * 执行任意一个任务并返回结果
     */
    public static <T> T invokeAny(
            ExecutorService executor,
            Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        if (executor != null && tasks != null) {
            return executor.invokeAny(tasks);
        }
        return null;
    }

    /**
     * 执行任意一个任务并返回结果（带超时）
     */
    public static <T> T invokeAny(
            ExecutorService executor,
            Collection<? extends Callable<T>> tasks,
            long timeout,
            TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (executor != null && tasks != null) {
            return executor.invokeAny(tasks, timeout, unit);
        }
        return null;
    }

    // ==================== 线程池管理 ====================

    /**
     * 关闭线程池
     */
    public static void shutdown(ExecutorService executor) {
        if (executor != null) {
            executor.shutdown();
        }
    }

    /**
     * 立即关闭线程池
     */
    public static List<Runnable> shutdownNow(ExecutorService executor) {
        if (executor != null) {
            return executor.shutdownNow();
        }
        return new ArrayList<>();
    }

    /**
     * 等待线程池终止
     */
    public static boolean awaitTermination(ExecutorService executor, long timeout, TimeUnit unit) throws InterruptedException {
        if (executor != null) {
            return executor.awaitTermination(timeout, unit);
        }
        return true;
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

    // ==================== 监控 ====================

    /**
     * 获取线程池大小
     */
    public static int getPoolSize(ThreadPoolExecutor executor) {
        return executor != null ? executor.getPoolSize() : 0;
    }

    /**
     * 获取活跃线程数
     */
    public static int getActiveCount(ThreadPoolExecutor executor) {
        return executor != null ? executor.getActiveCount() : 0;
    }

    /**
     * 获取已完成任务数
     */
    public static long getCompletedTaskCount(ThreadPoolExecutor executor) {
        return executor != null ? executor.getCompletedTaskCount() : 0;
    }

    /**
     * 获取任务总数
     */
    public static long getTaskCount(ThreadPoolExecutor executor) {
        return executor != null ? executor.getTaskCount() : 0;
    }

    /**
     * 获取队列大小
     */
    public static int getQueueSize(ThreadPoolExecutor executor) {
        return executor != null ? executor.getQueue().size() : 0;
    }

    /**
     * 获取队列剩余容量
     */
    public static int getQueueRemainingCapacity(ThreadPoolExecutor executor) {
        return executor != null ? executor.getQueue().remainingCapacity() : 0;
    }

    /**
     * 判断线程池是否已关闭
     */
    public static boolean isShutdown(ExecutorService executor) {
        return executor != null && executor.isShutdown();
    }

    /**
     * 判断线程池是否已终止
     */
    public static boolean isTerminated(ExecutorService executor) {
        return executor != null && executor.isTerminated();
    }

    /**
     * 获取线程池状态信息
     */
    public static String getPoolStatus(ThreadPoolExecutor executor) {
        if (executor == null) {
            return "null";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Pool Status:\n");
        sb.append("  Pool Size: ").append(executor.getPoolSize()).append("\n");
        sb.append("  Active Count: ").append(executor.getActiveCount()).append("\n");
        sb.append("  Completed Task Count: ").append(executor.getCompletedTaskCount()).append("\n");
        sb.append("  Task Count: ").append(executor.getTaskCount()).append("\n");
        sb.append("  Queue Size: ").append(executor.getQueue().size()).append("\n");
        sb.append("  Is Shutdown: ").append(executor.isShutdown()).append("\n");
        sb.append("  Is Terminated: ").append(executor.isTerminated()).append("\n");
        return sb.toString();
    }

    // ==================== 队列工厂 ====================

    /**
     * 创建有界队列
     */
    public static <T> BlockingQueue<T> newBoundedQueue(int capacity) {
        return new ArrayBlockingQueue<>(capacity);
    }

    /**
     * 创建无界队列
     */
    public static <T> BlockingQueue<T> newUnboundedQueue() {
        return new LinkedBlockingQueue<>();
    }

    /**
     * 创建无界队列（初始容量）
     */
    public static <T> BlockingQueue<T> newUnboundedQueue(int initialCapacity) {
        return new LinkedBlockingQueue<>(initialCapacity);
    }

    /**
     * 创建同步队列
     */
    public static <T> BlockingQueue<T> newSynchronousQueue() {
        return new SynchronousQueue<>();
    }

    /**
     * 创建优先级队列
     */
    public static <T extends Comparable<? super T>> BlockingQueue<T> newPriorityQueue() {
        return new PriorityBlockingQueue<>();
    }

    /**
     * 创建优先级队列（初始容量）
     */
    public static <T extends Comparable<? super T>> BlockingQueue<T> newPriorityQueue(int initialCapacity) {
        return new PriorityBlockingQueue<>(initialCapacity);
    }

    /**
     * 创建延迟队列
     */
    public static <T extends Delayed> BlockingQueue<T> newDelayQueue() {
        return new DelayQueue<>();
    }

    // ==================== 辅助方法 ====================

    /**
     * 睡眠（不抛出异常）
     */
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 睡眠（不抛出异常）
     */
    public static void sleep(long millis, int nanos) {
        try {
            Thread.sleep(millis, nanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 等待（不抛出异常）
     */
    public static void join(Thread thread, long millis) {
        if (thread == null) {
            return;
        }

        try {
            thread.join(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 等待（不抛出异常）
     */
    public static void join(Thread thread) {
        if (thread == null) {
            return;
        }

        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
