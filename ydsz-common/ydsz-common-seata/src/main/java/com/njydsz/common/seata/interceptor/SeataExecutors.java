package com.njydsz.common.seata.interceptor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Seata 感知的线程池工厂
 *
 * <p>提供便捷方法创建已注入 {@link SeataTaskDecorator} 的线程池，
 * 确保事务上下文在异步任务中的正确传递。
 *
 * <p><b>P1-1 修复</b>：解决异步线程池中 XID 丢失问题，用户通过本工厂创建线程池
 * 无需手动配置 TaskDecorator。
 *
 * <p>使用方式：
 * <pre>{@code
 * // 创建 Seata 感知的固定大小线程池
 * ExecutorService executor = SeataExecutors.newFixedThreadPool(10, "seata-async");
 *
 * // 使用
 * executor.submit(() -> {
 *     // 异步任务中可正常获取 XID
 *     String xid = XidContextHolder.getXid();
 *     // 执行业务逻辑...
 * });
 * }</pre>
 *
 * <p>也支持包装已有的 Spring ThreadPoolTaskExecutor：
 * <pre>{@code
 * @Bean("asyncExecutor")
 * public ThreadPoolTaskExecutor asyncExecutor() {
 *     ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *     executor.setCorePoolSize(10);
 *     executor.setMaxPoolSize(20);
 *     executor.setQueueCapacity(500);
 *     executor.setThreadNamePrefix("seata-async-");
 *     // 注入 SeataTaskDecorator
 *     executor.setTaskDecorator(new SeataTaskDecorator());
 *     executor.initialize();
 *     return executor;
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.3.0
 */
public final class SeataExecutors {

    /** 默认线程池核心线程数 */
    private static final int DEFAULT_CORE_SIZE = Runtime.getRuntime().availableProcessors();

    private SeataExecutors() {
        // 工具类，禁止实例化
    }

    /**
     * 创建 Seata 感知的固定大小线程池
     *
     * @param nThreads 线程数
     * @param threadNamePrefix 线程名前缀
     * @return 已包装 SeataTaskDecorator 的线程池
     */
    public static ExecutorService newFixedThreadPool(int nThreads, String threadNamePrefix) {
        return java.util.concurrent.Executors.newFixedThreadPool(nThreads, r -> {
            Thread t = new Thread(r, threadNamePrefix + "-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 创建 Seata 感知的单线程线程池
     *
     * @param threadNamePrefix 线程名前缀
     * @return 已包装 SeataTaskDecorator 的线程池
     */
    public static ExecutorService newSingleThreadExecutor(String threadNamePrefix) {
        return new SeataDecoratorExecutorService(
                Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, threadNamePrefix + "-0");
                    t.setDaemon(true);
                    return t;
                }));
    }

    /**
     * 创建 Seata 感知的可缓存线程池
     *
     * @param threadNamePrefix 线程名前缀
     * @return 已包装 SeataTaskDecorator 的线程池
     */
    public static ExecutorService newCachedThreadPool(String threadNamePrefix) {
        return new SeataDecoratorExecutorService(
                Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, threadNamePrefix + "-" + System.nanoTime());
                    t.setDaemon(true);
                    return t;
                }));
    }

    /**
     * 创建 Seata 感知的自定义线程池
     *
     * @param corePoolSize    核心线程数
     * @param maximumPoolSize 最大线程数
     * @param keepAliveTime   空闲线程存活时间
     * @param unit            时间单位
     * @param workQueue       工作队列
     * @param threadNamePrefix 线程名前缀
     * @return 已包装 SeataTaskDecorator 的线程池
     */
    public static ExecutorService newThreadPool(
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            TimeUnit unit,
            java.util.concurrent.BlockingQueue<Runnable> workQueue,
            String threadNamePrefix) {
        return new SeataDecoratorExecutorService(
                new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                        r -> {
                            Thread t = new Thread(r, threadNamePrefix + "-" + System.nanoTime());
                            t.setDaemon(true);
                            return t;
                        }));
    }

    /**
     * 将 Executor 包装为 Seata 感知的执行器
     *
     * <p>用于在无法直接创建线程池的场景下，装饰已有的 Executor。
     *
     * @param executor 原始执行器
     * @return 包装后的 Seata 感知执行器
     */
    public static Executor decorator(Executor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("Executor must not be null");
        }
        return new SeataDecoratorExecutor(executor);
    }
}
