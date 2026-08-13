package com.njydsz.common.thread.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * @Bean
 * public ThreadPoolHotUpdateListener threadPoolHotUpdateListener(
 *         ThreadPoolAutoConfiguration threadPoolAutoConfiguration) {
 *     return new ThreadPoolHotUpdateListener(threadPoolAutoConfiguration, "${ydsz.config.data-id}");
 * }
 * }</pre>
 *
 * <p>v1.3.0 新增：从 ydzs-cronjob 的 ThreadPoolHotUpdateListener 抽象为通用组件。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
public class ThreadPoolHotUpdateListener {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolHotUpdateListener.class);

    private final ThreadPoolAutoConfiguration threadPoolAutoConfiguration;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 默认构造器，注入 ydzs-thread 的自动配置实例。
     *
     * @param threadPoolAutoConfiguration 线程池自动配置（提供 getExecutors() 运行时查询）
     */
    public ThreadPoolHotUpdateListener(ThreadPoolAutoConfiguration threadPoolAutoConfiguration) {
        this.threadPoolAutoConfiguration = threadPoolAutoConfiguration;
    }

    /**
     * 在线程池初始化完成后打印注册摘要，便于确认热更新监听器已就绪。
     *
     * <p>该方法由 Spring 容器在 ContextRefreshedEvent 时回调。
     */
    public void onContextReady() {
        Map<String, ThreadPoolTaskExecutor> executors = threadPoolAutoConfiguration.getExecutors();
        log.info("[ThreadPoolHotUpdate] 热更新监听器就绪，当前共 {} 个平台线程池: {}",
                executors.size(), executors.keySet());
    }

    /**
     * 动态调整指定线程池的 coreSize 和 maxSize。
     *
     * <p>自动处理调序：先扩大 max 再调整 core（避免 core > max 异常）。
     *
     * @param poolName      线程_POOL 配置 key（如 "io"）
     * @param newCoreSize   新的核心线程数
     * @param newMaxSize    新的最大线程数
     */
    public void resizePool(String poolName, int newCoreSize, int newMaxSize) {
        lock.writeLock().lock();
        try {
            ThreadPoolTaskExecutor executor = getExecutor(poolName);
            if (executor == null) {
                log.warn("[ThreadPoolHotUpdate] 线程池 [{}] 不存在，跳过调整", poolName);
                return;
            }
            if (newCoreSize < 1 || newMaxSize < 1 || newCoreSize > newMaxSize) {
                log.warn("[ThreadPoolHotUpdate] 参数非法: core={} max={}, 跳过", newCoreSize, newMaxSize);
                return;
            }

            resizeInternal(executor, newCoreSize, newMaxSize, poolName);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 动态调整指定线程池的拒绝策略。
     *
     * @param poolName    线程池配置 key（如 "io"）
     * @param newPolicy   新的拒绝策略
     */
    public void updateRejectPolicy(String poolName, ThreadPoolProperties.RejectPolicy newPolicy) {
        lock.writeLock().lock();
        try {
            ThreadPoolTaskExecutor executor = getExecutor(poolName);
            if (executor == null) {
                log.warn("[ThreadPoolHotUpdate] 线程池 [{}] 不存在，跳过调整", poolName);
                return;
            }
            RejectedExecutionHandler newHandler = createRejectHandler(newPolicy);
            executor.setRejectedExecutionHandler(newHandler);
            log.info("[ThreadPoolHotUpdate] 线程池 [{}] 拒绝策略已更新为 {}", poolName, newPolicy);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 动态更新 threadNamePrefix（仅影响新创建的线程）。
     *
     * @param poolName          线程池配置 key
     * @param newThreadPrefix   新的线程名前缀
     */
    public void updateThreadNamePrefix(String poolName, String newThreadPrefix) {
        lock.writeLock().lock();
        try {
            ThreadPoolTaskExecutor executor = getExecutor(poolName);
            if (executor == null) {
                log.warn("[ThreadPoolHotUpdate] 线程池 [{}] 不存在，跳过调整", poolName);
                return;
            }
            executor.setThreadNamePrefix(newThreadPrefix);
            log.info("[ThreadPoolHotUpdate] 线程池 [{}] 线程名前缀已更新为 {}（仅影响新线程）",
                    poolName, newThreadPrefix);
        } finally {
            lock.writeLock().unlock();
        }
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
     * @return poolName → snapshot
     */
    public Map<String, ThreadPoolSnapshot> snapshotAll() {
        Map<String, ThreadPoolSnapshot> result = new LinkedHashMap<>();
        threadPoolAutoConfiguration.getExecutors().forEach((beanName, executor) -> {
            // 从 beanName 反推 poolName （去掉 "Executor" 后缀）
            String poolName = beanName.endsWith("Executor")
                    ? beanName.substring(0, beanName.length() - "Executor".length())
                    : beanName;
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
        String beanName = poolName + "Executor";
        return executors.get(beanName);
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

        public String getPoolName() { return poolName; }
        public int getCorePoolSize() { return corePoolSize; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public int getActiveCount() { return activeCount; }
        public int getPoolSize() { return poolSize; }
        public int getQueueSize() { return queueSize; }
        public long getCompletedTaskCount() { return completedTaskCount; }

        @Override
        public String toString() {
            return String.format("ThreadPoolSnapshot{pool='%s', core=%d, max=%d, active=%d, poolSize=%d, queue=%d, completed=%d}",
                    poolName, corePoolSize, maxPoolSize, activeCount, poolSize, queueSize, completedTaskCount);
        }
    }
}
