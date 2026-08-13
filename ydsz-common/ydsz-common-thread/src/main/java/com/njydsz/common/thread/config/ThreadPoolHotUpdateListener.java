package com.njydsz.common.thread.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 线程池运行时动态参数调整器。
 *
 * <p>基于 Spring Cloud Configuration / Nacos 配置变更机制触发：
 * <ul>
 *   <li>{@code setCorePoolSize} / {@code setMaximumPoolSize} 可运行时调整</li>
 *   <li>{@code queueCapacity} 无法动态调整（阻塞队列不可 resize），新值记录后下次创建生效</li>
 *   <li>{@code threadNamePrefix} 运行时修改仅影响后续创建的新线程，已有线程名不变</li>
 *   <li>{@code rejectPolicy} 运行时可直接替换执行器持有的拒绝策略引用</li>
 * </ul>
 *
 * <p>两种触发方式（按需选择其一）：
 * <ol>
 *   <li>Spring Cloud {@code @RefreshScope} 或 {@code EnvironmentChangeEvent}：
 *       修改 application.yml / Nacos 配置后发布 {@code RefreshEvent}</li>
 *   <li>Nacos {@code @NacosConfigListener}：
 *       直接在 Nacos 管理台修改并推送</li>
 * </ol>
 *
 * <p>使用示例（Nacos 方式）：
 * <pre>{@code
 * // 在业务模块中创建该 Bean
 * &#64;Bean
 * public ThreadPoolHotUpdateListener threadPoolHotUpdateListener(
 *         ThreadPoolAutoConfiguration threadPoolAutoConfiguration) {
 *     return new ThreadPoolHotUpdateListener(threadPoolAutoConfiguration);
 * }
 * }</pre>
 *
 * <p>v1.3.0 新增：从 ydzs-cronjob 的 ThreadPoolHotUpdateListener 抽象为通用组件。
 *
 * <p>v1.3.1 变更：
 * <ul>
 *   <li>{@link #onContextReady(ContextRefreshedEvent)} 添加 {@link EventListener} 注解，
 *       应用启动完成后自动打印线程池注册摘要</li>
 *   <li>移除冗余的 {@code ReentrantReadWriteLock}，依赖 {@link ThreadPoolExecutor}
 *       内置的线程安全保证</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.3.0
 */
public class ThreadPoolHotUpdateListener {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolHotUpdateListener.class);

    private final ThreadPoolAutoConfiguration threadPoolAutoConfiguration;

    /**
     * 构造器，注入 ydsz-thread 的自动配置实例。
     *
     * @param threadPoolAutoConfiguration 线程池自动配置（提供 getExecutors() 运行时查询）
     */
    public ThreadPoolHotUpdateListener(ThreadPoolAutoConfiguration threadPoolAutoConfiguration) {
        this.threadPoolAutoConfiguration = threadPoolAutoConfiguration;
    }

    /**
     * 应用上下文刷新完成后回调，打印线程池注册摘要。
     *
     * <p>该方法由 Spring 容器在 {@link ContextRefreshedEvent} 发布时自动调用。
     *
     * @param event 上下文刷新事件
     */
    @EventListener(ContextRefreshedEvent.class)
    public void onContextReady(ContextRefreshedEvent event) {
        Map<String, ThreadPoolTaskExecutor> executors = threadPoolAutoConfiguration.getExecutors();
        log.info("[ThreadPoolHotUpdate] 热更新监听器就绪，当前共 {} 个平台线程池: {}",
                executors.size(), executors.keySet());
    }

    /**
     * 动态调整指定线程池的 coreSize 和 maxSize。
     *
     * <p>自动处理调序：先扩大 max 再调整 core（避免 core > max 异常）。
     *
     * @param poolName    线程池配置 key（如 "io"）
     * @param newCoreSize 新的核心线程数（必须 >= 1）
     * @param newMaxSize  新的最大线程数（必须 >= newCoreSize）
     */
    public void resizePool(String poolName, int newCoreSize, int newMaxSize) {
        ThreadPoolTaskExecutor executor = getExecutor(poolName);
        if (executor == null) {
            log.warn("[ThreadPoolHotUpdate] 线程池 [{}] 不存在，跳过调整", poolName);
            return;
        }
        if (newCoreSize < 1 || newMaxSize < 1 || newCoreSize > newMaxSize) {
            log.warn("[ThreadPoolHotUpdate] 参数非法: core={}, max={}, 跳过", newCoreSize, newMaxSize);
            return;
        }

        resizeInternal(executor, newCoreSize, newMaxSize, poolName);
    }

    /**
     * 动态调整指定线程池的拒绝策略。
     *
     * @param poolName  线程池配置 key（如 "io"）
     * @param newPolicy 新的拒绝策略
     */
    public void updateRejectPolicy(String poolName, ThreadPoolProperties.RejectPolicy newPolicy) {
        ThreadPoolTaskExecutor executor = getExecutor(poolName);
        if (executor == null) {
            log.warn("[ThreadPoolHotUpdate] 线程池 [{}] 不存在，跳过调整", poolName);
            return;
        }
        RejectedExecutionHandler newHandler = createRejectHandler(newPolicy);
        executor.setRejectedExecutionHandler(newHandler);
        log.info("[ThreadPoolHotUpdate] 线程池 [{}] 拒绝策略已更新为 {}", poolName, newPolicy);
    }

    /**
     * 动态更新 threadNamePrefix（仅影响新创建的线程）。
     *
     * @param poolName        线程池配置 key
     * @param newThreadPrefix 新的线程名前缀
     */
    public void updateThreadNamePrefix(String poolName, String newThreadPrefix) {
        ThreadPoolTaskExecutor executor = getExecutor(poolName);
        if (executor == null) {
            log.warn("[ThreadPoolHotUpdate] 线程池 [{}] 不存在，跳过调整", poolName);
            return;
        }
        executor.setThreadNamePrefix(newThreadPrefix);
        log.info("[ThreadPoolHotUpdate] 线程池 [{}] 线程名前缀已更新为 {}（仅影响新线程）",
                poolName, newThreadPrefix);
    }

    /**
     * 获取指定线程池的当前快照。
     *
     * @param poolName 线程池配置 key
     * @return 线程池快照信息
     */
    public ThreadPoolSnapshot snapshot(String poolName) {
        ThreadPoolTaskExecutor executor = getExecutor(poolName);
        if (executor == null) {
            return null;
        }
        try {
            ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
            return new ThreadPoolSnapshot(
                    poolName,
                    pool.getCorePoolSize(),
                    pool.getMaximumPoolSize(),
                    pool.getActiveCount(),
                    pool.getPoolSize(),
                    pool.getQueue().size(),
                    pool.getCompletedTaskCount()
            );
        } catch (Exception e) {
            log.warn("[ThreadPoolHotUpdate] 线程池 [{}] 快照获取失败: {}", poolName, e.getMessage());
            return null;
        }
    }

    /**
     * 获取所有线程池的快照。
     *
     * @return poolName → snapshot 的映射
     */
    public Map<String, ThreadPoolSnapshot> snapshotAll() {
        Map<String, ThreadPoolSnapshot> result = new LinkedHashMap<>();
        threadPoolAutoConfiguration.getExecutors().forEach((beanName, executor) -> {
            String poolName = resolvePoolName(beanName);
            if (poolName == null) {
                log.debug("[ThreadPoolHotUpdate] Bean [{}] 不是 ydsz-common-thread 管理的线程池，跳过", beanName);
                return;
            }
            try {
                ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
                result.put(poolName, new ThreadPoolSnapshot(
                        poolName,
                        pool.getCorePoolSize(),
                        pool.getMaximumPoolSize(),
                        pool.getActiveCount(),
                        pool.getPoolSize(),
                        pool.getQueue().size(),
                        pool.getCompletedTaskCount()
                ));
            } catch (Exception e) {
                log.warn("[ThreadPoolHotUpdate] 线程池 [{}] 快照获取失败: {}", poolName, e.getMessage());
            }
        });
        return result;
    }

    // ====================== private ======================

    private ThreadPoolTaskExecutor getExecutor(String poolName) {
        if (threadPoolAutoConfiguration == null) {
            log.warn("[ThreadPoolHotUpdate] ThreadPoolAutoConfiguration 未注入");
            return null;
        }
        Map<String, ThreadPoolTaskExecutor> executors = threadPoolAutoConfiguration.getExecutors();
        // 查找以 poolName + "Executor" 结尾的 Bean
        for (Map.Entry<String, ThreadPoolTaskExecutor> entry : executors.entrySet()) {
            String beanName = entry.getKey();
            if (beanName.endsWith(poolName + "Executor")) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 从 Bean 名称反推 poolName（配置 key）。
     *
     * <p>支持 beanNamePrefix 场景：去掉 "Executor" 后缀后，
     * 再尝试去掉已知的 beanNamePrefix 即可得到 poolName。
     *
     * @param beanName Bean 名称
     * @return poolName，若不匹配则返回 null
     */
    private String resolvePoolName(String beanName) {
        if (beanName == null || !beanName.endsWith("Executor")) {
            return null;
        }
        return beanName.substring(0, beanName.length() - "Executor".length());
    }

    private void resizeInternal(ThreadPoolTaskExecutor executor, int newCoreSize, int newMaxSize, String poolName) {
        try {
            ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
            int oldCore = pool.getCorePoolSize();
            int oldMax = pool.getMaximumPoolSize();

            if (newCoreSize == oldCore && newMaxSize == oldMax) {
                log.debug("[ThreadPoolHotUpdate] 线程池 [{}] 参数未变化, 跳过", poolName);
                return;
            }

            // 先扩大 max，再调整 core（避免 core > max 异常）
            if (newMaxSize > oldMax) {
                pool.setMaximumPoolSize(newMaxSize);
                pool.setCorePoolSize(newCoreSize);
            } else {
                pool.setCorePoolSize(newCoreSize);
                pool.setMaximumPoolSize(newMaxSize);
            }

            log.info("[ThreadPoolHotUpdate] 线程池 [{}] 已调整: core={}→{}, max={}→{}, active={}, queue={}",
                    poolName, oldCore, newCoreSize, oldMax, newMaxSize,
                    pool.getActiveCount(), pool.getQueue().size());
        } catch (Exception e) {
            log.error("[ThreadPoolHotUpdate] 线程池 [{}] 调整失败: {}", poolName, e.getMessage(), e);
        }
    }

    private RejectedExecutionHandler createRejectHandler(
            ThreadPoolProperties.RejectPolicy policy) {
        if (policy == null) {
            return new ThreadPoolExecutor.CallerRunsPolicy();
        }
        switch (policy) {
            case ABORT:
                return new ThreadPoolExecutor.AbortPolicy();
            case CALLER_RUNS:
                return new ThreadPoolExecutor.CallerRunsPolicy();
            case DISCARD_OLDEST:
                return new ThreadPoolExecutor.DiscardOldestPolicy();
            case DISCARD:
                return new ThreadPoolExecutor.DiscardPolicy();
            default:
                return new ThreadPoolExecutor.CallerRunsPolicy();
        }
    }

    // ====================== inner classes ======================

    /**
     * 线程池运行时快照。
     */
    public static class ThreadPoolSnapshot {

        private final String poolName;
        private final int corePoolSize;
        private final int maxPoolSize;
        private final int activeCount;
        private final int poolSize;
        private final int queueSize;
        private final long completedTaskCount;

        public ThreadPoolSnapshot(String poolName, int corePoolSize, int maxPoolSize,
                                   int activeCount, int poolSize, int queueSize,
                                   long completedTaskCount) {
            this.poolName = poolName;
            this.corePoolSize = corePoolSize;
            this.maxPoolSize = maxPoolSize;
            this.activeCount = activeCount;
            this.poolSize = poolSize;
            this.queueSize = queueSize;
            this.completedTaskCount = completedTaskCount;
        }

        public String getPoolName() {
            return poolName;
        }

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public int getActiveCount() {
            return activeCount;
        }

        public int getPoolSize() {
            return poolSize;
        }

        public int getQueueSize() {
            return queueSize;
        }

        public long getCompletedTaskCount() {
            return completedTaskCount;
        }

        @Override
        public String toString() {
            return String.format(
                    "ThreadPoolSnapshot{pool='%s', core=%d, max=%d, active=%d, poolSize=%d, queue=%d, completed=%d}",
                    poolName, corePoolSize, maxPoolSize, activeCount, poolSize, queueSize, completedTaskCount);
        }
    }
}
