package com.njydsz.system.server.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.model.OutboxMessage;

import lombok.extern.slf4j.Slf4j;

/**
 * 跨模块事件监听器 — System 模块订阅其他模块的领域事件。
 *
 * <p>当前订阅：
 * <ul>
 *   <li>{@code CONFIG_CHANGED} — 配置变更事件通知（用于跨实例缓存失效感知）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class CrossModuleEventListener {

    /**
     * 配置变更 — 记录跨实例配置变更通知
     *
     * @param message Outbox 消息
     */
    @Async
    @EventListener(condition = "#message.eventType == 'CONFIG_CHANGED'")
    public void onConfigChanged(OutboxMessage message) {
        log.info("[CrossModuleEventListener] 接收配置变更事件: configId={}", message.getAggregateId());
    }
}
