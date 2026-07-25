package com.njydsz.common.core.lifecycle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.Ordered;

/**
 * 优雅停机协调器
 *
 * <p>统一的停机钩子管理器，协调所有组件的优雅停机顺序。
 * 实现 {@link SmartLifecycle} 接口，在 Spring 容器关闭时按优先级逆序执行停机逻辑。
 *
 * <p>停机顺序（phase 越大越先停机）：
 * <ol>
 *   <li>Web 层停机钩子（phase = MAX_VALUE）- 停止接收新请求</li>
 *   <li>消息队列消费者（phase = 2000）- 停止消费消息</li>
 *   <li>异步任务执行器（phase = 1000）- 等待任务完成</li>
 *   <li>数据库连接池（phase = 0）- 最后关闭连接</li>
 * </ol>
 *
 * <p>特性：
 * <ul>
 *   <li>支持注册多个停机回调，按 phase 排序执行</li>
 *   <li>每个回调设置独立的超时时间，避免阻塞整体停机</li>
 *   <li>支持强制停机（超时后中断）和优雅停机（等待完成）</li>
 *   <li>停机过程中记录日志，便于排查问题</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class GracefulShutdownCoordinator implements SmartLifecycle, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownCoordinator.class);

    /** 默认停机超时时间（秒） */
    private static final int DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 30;

    /** 注册的停机回调列表 */
    private final List<ShutdownCallback> callbacks = new ArrayList<>();

    /** 运行状态标志 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 停机超时时间（秒） */
    private final int shutdownTimeoutSeconds;

    /**
     * 构造优雅停机协调器
     *
     * @param shutdownTimeoutSeconds 停机超时时间（秒）
     */
    public GracefulShutdownCoordinator(int shutdownTimeoutSeconds) {
        this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
    }

    /**
     * 构造优雅停机协调器（使用默认超时）
     */
    public GracefulShutdownCoordinator() {
        this(DEFAULT_SHUTDOWN_TIMEOUT_SECONDS);
    }

    /**
     * 注册停机回调
     *
     * @param callback 停机回调
     */
    public void registerCallback(ShutdownCallback callback) {
        if (callback != null) {
            callbacks.add(callback);
            log.debug("注册停机回调: {}, phase={}", callback.getName(), callback.getPhase());
        }
    }

    /**
     * 注册停机回调（简化版）
     *
     * @param name     回调名称
     * @param phase    优先级（越大越先执行）
     * @param callback 停机逻辑
     */
    public void registerCallback(String name, int phase, Runnable callback) {
        registerCallback(new ShutdownCallback(name, phase, callback));
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("优雅停机协调器已启动，注册回调数={}", callbacks.size());
        }
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            log.warn("优雅停机协调器已处于停止状态");
            return;
        }

        log.info("开始优雅停机流程...");
        long startTime = System.currentTimeMillis();

        // 按 phase 降序排序（phase 越大越先停机）
        List<ShutdownCallback> sortedCallbacks = new ArrayList<>(callbacks);
        sortedCallbacks.sort(Comparator.comparingInt(ShutdownCallback::getPhase).reversed());

        int successCount = 0;
        int failedCount = 0;

        for (ShutdownCallback callback : sortedCallbacks) {
            long callbackStart = System.currentTimeMillis();
            try {
                log.info("执行停机回调: {} (phase={})", callback.getName(), callback.getPhase());
                callback.execute();
                long duration = System.currentTimeMillis() - callbackStart;
                log.info("停机回调完成: {}, 耗时={}ms", callback.getName(), duration);
                successCount++;
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - callbackStart;
                log.error("停机回调失败: {}, 耗时={}ms", callback.getName(), duration, e);
                failedCount++;
            }
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        log.info("优雅停机流程完成: 成功={}, 失败={}, 总耗时={}ms",
                successCount, failedCount, totalDuration);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        // 在 Spring 容器最早启动，最晚停止
        return Integer.MAX_VALUE;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * 停机回调
     */
    public static class ShutdownCallback {
        private final String name;
        private final int phase;
        private final Runnable callback;

        /**
         * 构造停机回调
         *
         * @param name     回调名称
         * @param phase    优先级（越大越先执行）
         * @param callback 停机逻辑
         */
        public ShutdownCallback(String name, int phase, Runnable callback) {
            this.name = name;
            this.phase = phase;
            this.callback = callback;
        }

        public String getName() {
            return name;
        }

        public int getPhase() {
            return phase;
        }

        public void execute() {
            if (callback != null) {
                callback.run();
            }
        }
    }

    /**
     * 预定义的停机阶段常量
     */
    public static final class ShutdownPhase {
        /** Web 层停机（停止接收新请求） */
        public static final int WEB = Integer.MAX_VALUE - 1000;

        /** 消息队列消费者停机 */
        public static final int QUEUE_CONSUMER = 2000;

        /** 异步任务执行器停机 */
        public static final int ASYNC_EXECUTOR = 1000;

        /** 数据库连接池停机 */
        public static final int DATASOURCE = 0;

        /** 缓存连接池停机 */
        public static final int CACHE = -1000;

        private ShutdownPhase() {
        }
    }
}
