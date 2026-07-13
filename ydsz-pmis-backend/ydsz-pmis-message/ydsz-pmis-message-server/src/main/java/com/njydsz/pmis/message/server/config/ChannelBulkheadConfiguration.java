package com.njydsz.pmis.message.server.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * P1-9: 通道级 Bulkhead 线程隔离配置。
 *
 * <p>为每个通道类型分配独立的线程池，避免单通道故障（如 SMTP 超时）耗尽
 * 全局线程资源导致其他通道也被拖垮。
 *
 * <p>隔离策略：
 * <ul>
 *   <li>EMAIL: 核心线程 5，最大 20，队列 100</li>
 *   <li>SMS: 核心线程 10，最大 30，队列 200</li>
 *   <li>DINGTALK/WECOM/FEISHU: 核心线程 5，最大 15，队列 100</li>
 *   <li>INAPP/PUSH: 核心线程 3，最大 10，队列 50</li>
 *   <li>WEBHOOK: 核心线程 5，最大 15，队列 100</li>
 * </ul>
 *
 * <p>拒绝策略：CallerRunsPolicy（队列满时由调用线程执行，形成背压）
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Configuration
public class ChannelBulkheadConfiguration {

    /** 通道 → 线程池 映射 */
    private final Map<String, ExecutorService> channelExecutors = new ConcurrentHashMap<>();

    /**
     * 注册通道级线程池 Map。
     *
     * <p>各通道可通过 {@code channelExecutorMap.get(channelType)} 获取专属线程池。
     * 通道发送时提交到对应线程池执行，实现 Bulkhead 隔离。
     *
     * @return 通道类型 → 线程池 的 Map
     */
    @Bean
    public Map<String, ExecutorService> channelExecutorMap() {
        registerChannel("EMAIL", 5, 20, 100);
        registerChannel("SMS", 10, 30, 200);
        registerChannel("DINGTALK", 5, 15, 100);
        registerChannel("WECOM", 5, 15, 100);
        registerChannel("FEISHU", 5, 15, 100);
        registerChannel("INAPP", 3, 10, 50);
        registerChannel("PUSH", 3, 10, 50);
        registerChannel("WEBHOOK", 5, 15, 100);
        log.info("[Bulkhead] 通道级线程池初始化完成: channels={}", channelExecutors.keySet());
        return channelExecutors;
    }

    /**
     * 注册通道线程池。
     *
     * @param channelType  通道类型
     * @param coreThreads  核心线程数
     * @param maxThreads   最大线程数
     * @param queueCapacity 队列容量
     */
    private void registerChannel(String channelType, int coreThreads, int maxThreads, int queueCapacity) {
        ThreadFactory threadFactory = new ChannelThreadFactory(channelType);
        ExecutorService executor = new ThreadPoolExecutor(
                coreThreads, maxThreads, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        channelExecutors.put(channelType, executor);
    }

    /**
     * 通道级线程工厂。
     */
    static class ChannelThreadFactory implements ThreadFactory {
        private final String channelType;
        private final AtomicInteger counter = new AtomicInteger(0);

        ChannelThreadFactory(String channelType) {
            this.channelType = channelType;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "msg-" + channelType.toLowerCase() + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
