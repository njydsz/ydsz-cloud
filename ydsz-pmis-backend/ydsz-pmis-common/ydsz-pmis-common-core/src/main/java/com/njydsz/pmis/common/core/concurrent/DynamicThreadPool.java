package com.njydsz.pmis.common.core.concurrent;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamicThreadPool {
    private static final Logger log = LoggerFactory.getLogger(DynamicThreadPool.class);
    private final String name;
    private final ThreadPoolExecutor executor;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger threadCounter = new AtomicInteger(0);
    private volatile int corePoolSize;
    private volatile int maximumPoolSize;
    private volatile long keepAliveSeconds;

    public DynamicThreadPool(String name, int corePoolSize, int maximumPoolSize, int queueCapacity) {
        this.name = name;
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.keepAliveSeconds = 60;
        this.executor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveSeconds, TimeUnit.SECONDS, new LinkedBlockingQueue<>(queueCapacity), r -> { Thread t = new Thread(r, name + threadCounter.incrementAndGet()); t.setDaemon(false); return t; }, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public void updateParameters(int newCore, int newMax) {
        if (!running.get()) return;
        if (newCore < 0 || newMax < 0 || newCore > newMax) throw new IllegalArgumentException("Invalid pool sizes");
        executor.setCorePoolSize(newCore);
        executor.setMaximumPoolSize(newMax);
        corePoolSize = newCore;
        maximumPoolSize = newMax;
    }

    public ThreadPoolExecutor getExecutor() { return executor; }
    public String getName() { return name; }
    public int getCorePoolSize() { return corePoolSize; }
    public int getMaximumPoolSize() { return maximumPoolSize; }
    public int getActiveCount() { return executor.getActiveCount(); }
    public int getQueueSize() { return executor.getQueue().size(); }
    public long getCompletedTaskCount() { return executor.getCompletedTaskCount(); }
    public void shutdown() { if (running.compareAndSet(true, false)) { executor.shutdown(); try { if (!executor.awaitTermination(30, TimeUnit.SECONDS)) executor.shutdownNow(); } catch (InterruptedException e) { executor.shutdownNow(); Thread.currentThread().interrupt(); } } }
}