package com.njydsz.system.server.listener;

import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.event.model.OutboxMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 跨模块事件监听器 — System 模块订阅其他模块的领域事件。
 *
 * <p>当前订阅：
 * <ul>
 *   <li>{@code CONFIG_CHANGED} — 配置变更事件通知，触发本地缓存清空（跨实例缓存一致性保障）</li>
 * </ul>
 *
 * <p><b>跨实例一致性：</b>在多实例部署下，实例 A 修改配置后通过 Outbox 发布事件，
 * 实例 B / C / ... 监听事件后清空本地 ydsz-common-cache 缓存，
 * 下次读取时自动从 DB 重新加载最新值。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossModuleEventListener {

    private final CacheManager cacheManager;

    /**
     * 配置变更 — 清空本地配置缓存（跨实例一致性保障）。
     *
     * <p>其他实例修改配置并通过 Outbox 发布 {@code CONFIG_CHANGED} 事件后，
     * 本实例通过清空 {@link CacheConstants#SYSTEM_CONFIG_CACHE} 缓存，
     * 使下次读取自动回源到 DB 获取最新值。
     *
     * @param message Outbox 消息
     */
    @Async
    @EventListener(condition = "#message.eventType == 'CONFIG_CHANGED'")
    public void onConfigChanged(OutboxMessage message) {
        log.info("[CrossModuleEventListener] 接收配置变更事件，清空本地配置缓存: configId={}",
                message.getAggregateId());
        cacheManager.getCache(CacheConstants.SYSTEM_CONFIG_CACHE).clear();
    }
}
