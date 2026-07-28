package com.njydsz.project.server.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.json.YdszJson;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 跨模块事件监听器 — Project 模块订阅其他模块的领域事件。
 *
 * <p>当前订阅：
 * <ul>
 *   <li>{@code USER_LOGIN} — 用户登录时刷新项目列表缓存</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossModuleEventListener {

    /**
     * 用户登录 — 可用于刷新项目经理的项目缓存预热
     *
     * @param message Outbox 消息
     */
    @Async
    @EventListener(condition = "#message.eventType == 'USER_LOGIN'")
    public void onUserLogin(OutboxMessage message) {
        log.info("[CrossModuleEventListener] 接收用户登录事件: aggregateId={}",
                message.getAggregateId());
        try {
            var payload = YdszJson.parseMap(message.getPayload());
            String userId = message.getAggregateId();
            log.debug("[CrossModuleEventListener] 用户登录项目缓存预热: userId={}", userId);
        } catch (Exception e) {
            log.error("[CrossModuleEventListener] 用户登录事件处理异常: userId={}",
                    message.getAggregateId(), e);
        }
    }
}
