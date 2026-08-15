package com.njydsz.common.util.concurrent;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;

/**
 * 有界虚拟线程调度器——解决无节制创建虚拟线程导致的背压问题。
 *
 * <p>JDK 21 的 {@code Executors.newVirtualThreadPerTaskExecutor()} 会为每任务创建新虚拟线程。
 * IO 密集型场景下虚拟线程数量可能失控（百万级），超出下游系统承载能力。
 * 本调度器在虚拟线程之上叠加有界并发控制，实现：
 * <ul>
 *   <li>背压传导：并发达到上限时阻塞提交方池（而非无限创建线程）</li>
 *   <li>虚拟线程轻量优势：每个任务仍使用虚拟线程，避免平台线程上下文切换开销</li>
 *   <li>公平性：Semaphore 公平模式保证先提交的任务先执行</li>
 * </ul>
 *
 * <p><b>预测未来场景：</b>
 * <ul>
 *   <li>AI Agent 批量调用（同时调用多个 LLM，但限制并发放入下游配额）</li>
 *   <li>批量 HTTP 请求（爬虫/API 调用，限制并发避免目标服务过载）</li>
 *   <li>数据库批量写入（控制并发连接数）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 *   // 限制最多 100 个虚拟线程并发
 *   BoundedVirtualThreadScheduler scheduler = new BoundedVirtualThreadScheduler(100);
 *
 *   // 提交任务（背压模式：超过并发上限时阻塞提交方）
 *   scheduler.submit(() -> httpClient.call(url));
 *
 *   // 提交带返回值的任务
 *   Future&lt;String&gt; future = scheduler.submitWithResult(() -> httpClient.get(url));
 * }</pre>
 *
 * @author ydsz-team
 * @since 3.0.0
 * @see java.util.concurrent.Semaphore
 */
public final class BoundedVirtualThreadScheduler {

    private final Semaphore concurrencyLimiter;
    private final ThreadFactory threadFactory;
    private final int maxConcurrency;
    private volatile boolean shutdown;

    /**
     * 构造有界虚拟线程调度器。
     *
     * @param maxConcurrency 最大并发虚拟线程数（建议值：IO 密集场景 100-1000，CPU 密集场景 ≤ CPU 核心数）
     * @return 处理后的结果
     */
    public BoundedVirtualThreadScheduler(int maxConcurrency) {
        this(maxConcurrency, true);
    }

    /**
     * 构造有界虚拟线程调度器。
     *
     * @param maxConcurrency 最大并发虚拟线程数
     * @param fair           是否使用公平模式（true = 先提交先执行）
     * @return 处理后的结果
     */
    public BoundedVirtualThreadScheduler(int maxConcurrency, boolean fair) {
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("maxConcurrency must be positive, got " + maxConcurrency);
        }
        this.maxConcurrency = maxConcurrency;
        this.concurrencyLimiter = new Semaphore(maxConcurrency, fair);
        this.threadFactory = Thread.ofVirtual().factory();
    }

    /**
     * 提交 Runnable 任务（背压模式：达到并发上限时阻塞提交方）。
     *
     * @param task 要执行的任务
     * @throws InterruptedException   获取信号量许可被中断时
     * @throws RejectedExecutionException 调度器已关闭时
     */
    public void submit(Runnable task) throws InterruptedException {
        Objects.requireNonNull(task, "task must not be null");
        if (shutdown) {
            throw new RejectedExecutionException("Scheduler has been shut down");
        }
        concurrencyLimiter.acquire();
        threadFactory.newThread(() -> {
            try {
                task.run();
            } finally {
                concurrencyLimiter.release();
            }
        }).start();
    }

    /**
     * 提交 Callable 任务并返回 Future 结果。
     *
     * @param task 要执行的有返回值任务
     * @return Future 对象，可获取结果或抛出异常
     * @throws InterruptedException   获取信号量许可被中断时
     * @throws RejectedExecutionException 调度器已关闭时
     * @param T 泛型参数类型
     */
    public <T> Future<T> submitWithResult(Callable<T> task) throws InterruptedException {
        Objects.requireNonNull(task, "task must not be null");
        if (shutdown) {
            throw new RejectedExecutionException("Scheduler has been shut down");
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        concurrencyLimiter.acquire();
        threadFactory.newThread(() -> {
            try {
                T result = task.call();
                future.complete(result);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            } finally {
                concurrencyLimiter.release();
            }
        }).start();
        return future;
    }

    /**
     * 关闭调度器（不再接受新任务，不中断正在执行的任务）。
     */
    public void shutdown() {
        this.shutdown = true;
    }

    /**
     * 获取当前可用并发许可数（可用于健康检查或监控）。
      * @return 处理后的结果
     */
    public int availablePermits() {
        return concurrencyLimiter.availablePermits();
    }

    /**
     * 获取当前正在使用的并发数。
     * @return 处理后的结果
     */
    public int activeCount() {
        return maxConcurrency - concurrencyLimiter.availablePermits();
    }

    @Override
    public String toString() {
        return "BoundedVirtualThreadScheduler{" +
                "availablePermits=" + concurrencyLimiter.availablePermits() +
                ", shutdown=" + shutdown + '}';
    }
}







