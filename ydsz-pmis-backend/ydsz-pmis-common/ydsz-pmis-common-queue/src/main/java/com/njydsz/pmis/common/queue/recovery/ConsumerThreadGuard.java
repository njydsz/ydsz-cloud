package com.njydsz.pmis.common.queue.recovery;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 消费线程崩溃恢复守卫
 *
 * <p>监控消费者任务运行状态，当任务因异常终止时自动重启。
 * 支持最大重启次数限制，超过阈值后触发告警并停止恢复。
 *
 * <p><b>线程池模式（推荐）：</b>
 * 当传入 {@link ExecutorService} 时，任务由 Spring 管理的线程池执行，
 * 避免直接创建裸线程，支持优雅停机。
 *
 * <p><b>裸线程模式（已弃用）：</b>
 * 当未传入 {@link ExecutorService} 时，内部会创建守护线程执行任务，
 * 仅用于向后兼容，不推荐在新代码中使用。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * ConsumerThreadGuard guard = new ConsumerThreadGuard("redis-stream", 10, executor);
 * guard.start(() -> consumeLoop(handler));
 *
 * guard.stop();
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
public class ConsumerThreadGuard {

    private static final long DEFAULT_RESTART_BACKOFF_MS = 2000L;
    private static final long MAX_BACKOFF_MS = 30000L;

    private final String name;
    private final int maxRestarts;
    private final ExecutorService executor;
    private final AtomicBoolean running;
    private final AtomicInteger restartCount;
    private volatile Future<?> currentFuture;
    private volatile Thread fallbackThread;

    /**
     * 创建消费者线程守卫（推荐，使用 Spring 管理的线程池）
     *
     * @param name         守卫名称，用于日志标识
     * @param maxRestarts  最大重启次数
     * @param executor     异步任务执行器
     */
    public ConsumerThreadGuard(String name, int maxRestarts, ExecutorService executor) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("守卫名称不能为空");
        }
        if (maxRestarts <= 0) {
            throw new IllegalArgumentException("最大重启次数必须大于0");
        }
        this.name = name;
        this.maxRestarts = maxRestarts;
        this.executor = executor;
        this.running = new AtomicBoolean(false);
        this.restartCount = new AtomicInteger(0);
    }

    /**
     * 启动消费者任务并开启监控
     *
     * @param task 消费者任务，通常内部包含循环消费逻辑
     */
    public void start(Runnable task) {
        if (!running.compareAndSet(false, true)) {
            log.warn("[ConsumerGuard] 消费者已在运行，name={}", name);
            return;
        }
        restartCount.set(0);
        launch(task);
    }

    /**
     * 停止消费者任务
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (currentFuture != null) {
            currentFuture.cancel(true);
        }
        if (fallbackThread != null && fallbackThread.isAlive()) {
            fallbackThread.interrupt();
        }
        log.info("[ConsumerGuard] 消费者已停止，name={}, totalRestarts={}", name, restartCount.get());
    }

    /**
     * 获取当前运行状态
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 获取已重启次数
     */
    public int getRestartCount() {
        return restartCount.get();
    }

    private void launch(Runnable task) {
        if (executor != null) {
            currentFuture = executor.submit(() -> runWithRecovery(task));
            log.info("[ConsumerGuard] 消费者任务已提交到线程池，name={}", name);
        } else {
            Thread t = new Thread(() -> runWithRecovery(task), "remi-queue-guard-" + name);
            t.setDaemon(true);
            fallbackThread = t;
            t.start();
            log.warn("[ConsumerGuard] 消费者任务使用裸线程运行，name={}（不推荐）", name);
        }
    }

    private void runWithRecovery(Runnable task) {
        while (running.get()) {
            try {
                task.run();

                if (!running.get()) {
                    return;
                }

                // 任务正常结束但仍在运行，按异常退出处理并重启
                int count = restartCount.incrementAndGet();
                if (count > maxRestarts) {
                    log.error("[ConsumerGuard] 超过最大重启次数，停止恢复，name={}, maxRestarts={}", name, maxRestarts);
                    running.set(false);
                    return;
                }

                long backoff = calcBackoff(count);
                log.warn("[ConsumerGuard] 消费者任务正常退出，准备第{}次重启，name={}, 延迟={}ms", count, name, backoff);
                sleepQuietly(backoff);
            } catch (Exception e) {
                if (!running.get()) {
                    return;
                }

                log.error("[ConsumerGuard] 消费者任务异常，name={}", name, e);
                int count = restartCount.incrementAndGet();
                if (count > maxRestarts) {
                    log.error("[ConsumerGuard] 超过最大重启次数，停止恢复，name={}, maxRestarts={}", name, maxRestarts);
                    running.set(false);
                    return;
                }

                long backoff = calcBackoff(count);
                log.warn("[ConsumerGuard] 消费者任务异常，准备第{}次重启，name={}, 延迟={}ms", count, name, backoff);
                sleepQuietly(backoff);
            }
        }
    }

    private long calcBackoff(int restartCount) {
        long backoff = DEFAULT_RESTART_BACKOFF_MS * (long) Math.pow(2, Math.min(restartCount - 1, 5));
        return Math.min(backoff, MAX_BACKOFF_MS);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
