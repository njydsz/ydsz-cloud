package com.remisoft.message.server.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.remisoft.common.thread.config.ThreadPoolAutoConfiguration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P1-9: 通道级 Bulkhead 线程隔离配置。
 *
 * <p>为每个通道类型分配独立的线程池，避免单通道故障（如 SMTP 超时）耗尽
 * 全局线程资源导致其他通道也被拖垮。
 *
 * <p>P0-1: 线程池由 remi-common-thread 统一创建管理（配置化 + Micrometer 指标 + 优雅关闭），
 * 本类仅负责将已注册的线程池按通道类型组装为 Map 供业务使用。
 *
 * <p>通道线程池配置见 application.yml: remi.thread.pools.msgEmail.*, msgSms.*, ...
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ChannelBulkheadConfiguration {

    private final ThreadPoolAutoConfiguration threadPoolAutoConfiguration;

    /** 通道类型 → 线程池 Bean 名称映射 */
    private static final Map<String, String> CHANNEL_POOL_NAMES = Map.of(
            "EMAIL", "msgEmail",
            "SMS", "msgSms",
            "DINGTALK", "msgDingtalk",
            "WECOM", "msgWecom",
            "WECOM_APP", "msgWecomApp",
            "FEISHU", "msgFeishu",
            "INAPP", "msgInapp",
            "PUSH", "msgPush",
            "WEBHOOK", "msgWebhook"
    );

    /**
     * 组装通道级线程池 Map。
     *
     * <p>从 common-thread 已注册的线程池中查找各通道对应的执行器，
     * 提取底层 ThreadPoolExecutor 供业务使用。
     *
     * @return 通道类型 → 线程池 的 Map
     */
    @Bean
    public Map<String, ExecutorService> channelExecutorMap() {
        Map<String, ThreadPoolTaskExecutor> registered = threadPoolAutoConfiguration.getExecutors();
        Map<String, ExecutorService> result = new ConcurrentHashMap<>();

        CHANNEL_POOL_NAMES.forEach((channelType, poolName) -> {
            ThreadPoolTaskExecutor executor = registered.get(poolName);
            if (executor != null) {
                try {
                    ThreadPoolExecutor underlying = executor.getThreadPoolExecutor();
                    result.put(channelType, underlying);
                    log.info("[Bulkhead] 通道 [{}] 线程池已绑定 (pool={})", channelType, poolName);
                } catch (Exception e) {
                    log.warn("[Bulkhead] 通道 [{}] 线程池底层提取失败: {}", channelType, e.getMessage());
                }
            } else {
                log.warn("[Bulkhead] 通道 [{}] 线程池未在 common-thread 中注册 (poolName={})，请检查 remi.thread.pools 配置",
                        channelType, poolName);
            }
        });

        log.info("[Bulkhead] 通道级线程池初始化完成: channels={}", result.keySet());
        return result;
    }
}
