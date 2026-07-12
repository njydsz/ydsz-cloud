package com.njydsz.pmis.common.util;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池工具类
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class ExecutorUtils {

    private ExecutorUtils() {
    }

    /**
     * 创建固定大小线程池
     *
     * @param poolSize 线程数
     * @param namePrefix 线程名前缀
     * @return 线程池
     */
    public static ExecutorService newFixedThreadPool(int poolSize, String namePrefix) {
        return new ThreadPoolExecutor(
                poolSize, poolSize, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new NamedThreadFactory(namePrefix)
        );
    }

    /**
     * 创建缓存线程池
     *
     * @param namePrefix 线程名前缀
     * @return 线程池
     */
    public static ExecutorService newCachedThreadPool(String namePrefix) {
        return new ThreadPoolExecutor(
                0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new NamedThreadFactory(namePrefix)
        );
    }

    /**
     * 创建定时任务线程池
     *
     * @param poolSize   线程数
     * @param namePrefix 线程名前缀
     * @return 定时任务线程池
     */
    public static ScheduledExecutorService newScheduledThreadPool(int poolSize, String namePrefix) {
        return new ScheduledThreadPoolExecutor(poolSize, new NamedThreadFactory(namePrefix));
    }

    /**
     * 安全关闭线程池
     *
     * @param executor 线程池
     * @param timeout  超时时间
     * @param unit     时间单位
     */
    public static void shutdownGracefully(ExecutorService executor, long timeout, TimeUnit unit) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeout, unit)) {
                executor.shutdownNow();
                executor.awaitTermination(timeout, unit);
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 创建虚拟线程执行器（Java 21+）
     *
     * <p>使用虚拟线程池，每个任务在一个虚拟线程中执行，
     * 线程名以 namePrefix 开头，便于排查问题。
     *
     * @param namePrefix 线程名前缀
     * @return 基于虚拟线程的 ExecutorService
     */
    public static ExecutorService newVirtualThreadExecutor(String namePrefix) {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name(namePrefix, 0).factory()
        );
    }

    /**
     * 命名线程工厂
     */
    public static class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);
        private final String namePrefix;
        private final boolean daemon;

        public NamedThreadFactory(String namePrefix) {
            this(namePrefix, false);
        }

        public NamedThreadFactory(String namePrefix, boolean daemon) {
            this.namePrefix = namePrefix;
            this.daemon = daemon;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, namePrefix + "-" + counter.incrementAndGet());
            thread.setDaemon(daemon);
            return thread;
        }
    }
}
