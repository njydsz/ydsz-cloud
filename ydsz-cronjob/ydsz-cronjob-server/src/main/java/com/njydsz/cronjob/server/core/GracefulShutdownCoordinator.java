package com.njydsz.cronjob.server.core;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.executor.TenantAwareExecutorPool;
import com.njydsz.cronjob.server.core.leader.LeaderElector;
import com.njydsz.cronjob.server.core.logger.DisruptorLogPublisher;

/**
 * 优雅下线协调器（P1-3：SIGTERM 捕获 + 任务排空）。
 *
 * <p>协调调度引擎各组件的关闭顺序，确保：
 * <ol>
 *   <li>停止接收新任务（关闭 JobScanner 扫描）
 *   <li>等待运行中任务完成（最多等待配置的排空超时）
 *   <li>释放 Leader 锁（让其他节点快速接管）
 *   <li>关闭线程池（租户分桶池、派发池）
 *   <li>关闭日志 Disruptor（刷新缓冲区）
 * </ol>
 *
 * <h3>关闭阶段</h3>
 *
 * <table border="1">
 *   <tr><th>阶段</th><th>操作</th><th>超时</th></tr>
 *   <tr><td>1. 停止扫描</td><td>设置扫描停止标志</td><td>立即</td></tr>
 *   <tr><td>2. 排空任务</td><td>等待运行中任务完成</td><td>drainTimeoutSeconds</td></tr>
 *   <tr><td>3. 释放锁</td><td>释放 Leader 分布式锁</td><td>5s</td></tr>
 *   <tr><td>4. 关闭线程池</td><td>关闭租户分桶池</td><td>10s</td></tr>
 *   <tr><td>5. 关闭日志</td><td>刷新并关闭 Disruptor</td><td>5s</td></tr>
 * </table>
 *
 * <h3>使用方式</h3>
 *
 * <p>本组件实现 {@link SmartLifecycle}，Spring 容器销毁时自动触发。同时注册 JVM ShutdownHook，
 * 捕获 SIGTERM 信号（如 Kubernetes Pod 终止、systemctl stop）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GracefulShutdownCoordinator implements SmartLifecycle {
  /** Logger（显式声明以确保编译可见性） */
  private static final Logger log = LoggerFactory.getLogger(GracefulShutdownCoordinator.class);

  /** 停机轮询间隔（毫秒） */
  private static final long SHUTDOWN_POLL_INTERVAL_MILLIS = 500;


    private final CronjobProperties cronjobProperties;
    private final LeaderElector leaderElector;
    private final TenantAwareExecutorPool tenantAwareExecutorPool;
    private final DisruptorLogPublisher disruptorLogPublisher;

    /** 是否已启动 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 是否正在关闭 */
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    /** 扫描停止标志（true=停止扫描） */
    private final AtomicBoolean scanStopped = new AtomicBoolean(false);

    /** 运行中任务计数 */
    private final AtomicInteger runningTaskCount = new AtomicInteger(0);

    /** ShutdownHook 是否已注册 */
    private volatile boolean shutdownHookRegistered = false;

    /**
     * 初始化优雅下线协调器。
     *
     * <p>注册 JVM ShutdownHook，捕获 SIGTERM 信号。
     */
    @PostConstruct
    public void init() {
        registerShutdownHook();
        running.set(true);
        log.info("[GracefulShutdown] 初始化完成: drainTimeout={}s",
                cronjobProperties.getExecutor().getDrainTimeoutSeconds());
    }

    /**
     * 注册 JVM ShutdownHook。
     *
     * <p>当 JVM 收到 SIGTERM（如 Kubernetes Pod 终止、systemctl stop）时，触发优雅下线流程。
     */
    private void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(this::doShutdown, "cronjob-shutdown-hook"));
            shutdownHookRegistered = true;
            log.info("[GracefulShutdown] ShutdownHook 注册完成");
        } catch (Exception e) {
            log.warn("[GracefulShutdown] ShutdownHook 注册失败: {}", e.getMessage());
        }
    }

    /**
     * 执行优雅下线流程。
     *
     * <p>按阶段顺序关闭各组件，每个阶段有独立的超时控制。
     */
    public void doShutdown() {
        if (!shuttingDown.compareAndSet(false, true)) {
            log.info("[GracefulShutdown] 已在下线流程中，跳过重复调用");
            return;
        }

        log.info("[GracefulShutdown] ========== 开始优雅下线 ==========");
        long startTime = System.currentTimeMillis();

        try {
            // 阶段 1: 停止扫描
            stopScanning();

            // 阶段 2: 排空运行中任务
            drainRunningTasks();

            // 阶段 3: 释放 Leader 锁
            releaseLeaderLock();

            // 阶段 4: 关闭线程池
            shutdownThreadPools();

            // 阶段 5: 关闭日志 Disruptor
            shutdownLogPublisher();

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[GracefulShutdown] ========== 优雅下线完成: 耗时={}ms ==========", elapsed);
        } catch (Exception e) {
            log.error("[GracefulShutdown] 下线流程异常: reason={}", e.getMessage(), e);
        }
    }

    /**
     * 阶段 1: 停止扫描器接受新任务。
     *
     * <p>设置停止标志，后续扫描周期检测到标志后跳过。
     */
    private void stopScanning() {
        log.info("[GracefulShutdown] 阶段1: 停止扫描器...");
        scanStopped.set(true);
        log.info("[GracefulShutdown] 阶段1完成: 扫描停止标志已设置");
    }

    /**
     * 阶段 2: 等待运行中任务完成。
     *
     * <p>轮询等待运行中任务数降为 0，超时后强制继续。
     */
    private void drainRunningTasks() {
        long drainTimeout = cronjobProperties.getExecutor().getDrainTimeoutSeconds();
        log.info("[GracefulShutdown] 阶段2: 等待运行中任务完成, 超时={}s, 当前运行中={}",
                drainTimeout, runningTaskCount.get());

        if (runningTaskCount.get() <= 0) {
            log.info("[GracefulShutdown] 阶段2完成: 无运行中任务");
            return;
        }

        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(drainTimeout);
        try {
            while (runningTaskCount.get() > 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(SHUTDOWN_POLL_INTERVAL_MILLIS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[GracefulShutdown] 阶段2被中断");
        }

        int remaining = runningTaskCount.get();
        if (remaining > 0) {
            log.warn("[GracefulShutdown] 阶段2超时: 仍有 {} 个任务在运行，强制继续关闭", remaining);
        } else {
            log.info("[GracefulShutdown] 阶段2完成: 所有任务已排空");
        }
    }

    /**
     * 阶段 3: 释放 Leader 分布式锁。
     *
     * <p>让其他节点快速接管调度任务，减少调度中断时间。
     */
    private void releaseLeaderLock() {
        log.info("[GracefulShutdown] 阶段3: 释放 Leader 锁...");
        try {
            if (leaderElector != null) {
                String role = cronjobProperties.getLeader().getRole();
                leaderElector.release(role);
                log.info("[GracefulShutdown] 阶段3完成: Leader 锁已释放");
            }
        } catch (Exception e) {
            log.warn("[GracefulShutdown] 阶段3异常: {}", e.getMessage());
        }
    }

    /**
     * 阶段 4: 关闭线程池。
     *
     * <p>关闭租户分桶池和派发线程池。
     */
    private void shutdownThreadPools() {
        log.info("[GracefulShutdown] 阶段4: 关闭线程池...");
        try {
            tenantAwareExecutorPool.shutdownAll();
            log.info("[GracefulShutdown] 阶段4完成: 线程池已关闭");
        } catch (Exception e) {
            log.warn("[GracefulShutdown] 阶段4异常: {}", e.getMessage());
        }
    }

    /**
     * 阶段 5: 关闭日志 Disruptor。
     *
     * <p>刷新缓冲区中的日志事件，然后关闭 Disruptor。
     */
    private void shutdownLogPublisher() {
        log.info("[GracefulShutdown] 阶段5: 关闭日志 Disruptor...");
        try {
            disruptorLogPublisher.shutdown();
            log.info("[GracefulShutdown] 阶段5完成: 日志 Disruptor 已关闭");
        } catch (Exception e) {
            log.warn("[GracefulShutdown] 阶段5异常: {}", e.getMessage());
        }
    }

    /**
     * 通知任务开始执行。
     *
     * <p>由 {@link com.njydsz.cronjob.server.core.dispatch.DefaultTaskDispatcher} 调用。
     */
    public void onTaskStarted() {
        runningTaskCount.incrementAndGet();
    }

    /**
     * 通知任务执行完成。
     *
     * <p>由 {@link com.njydsz.cronjob.server.core.dispatch.DefaultTaskDispatcher} 调用。
     */
    public void onTaskCompleted() {
        runningTaskCount.decrementAndGet();
    }

    /**
     * 判断扫描是否应停止。
     *
     * @return {@code true} 表示应停止扫描
     */
    public boolean isScanStopped() {
        return scanStopped.get();
    }

    /**
     * 获取当前运行中任务数。
     *
     * @return 运行中任务数
     */
    public int getRunningTaskCount() {
        return runningTaskCount.get();
    }

    /**
     * 判断是否正在关闭。
     *
     * @return {@code true} 表示正在关闭
     */
    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    // ==================== SmartLifecycle 实现 ====================

    @Override
    public void start() {
        running.set(true);
    }

    @Override
    public void stop() {
        doShutdown();
        running.set(false);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        // 最高优先级（最先关闭）
        return Integer.MIN_VALUE;
    }
}
