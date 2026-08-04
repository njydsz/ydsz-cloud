package com.njydsz.cronjob.server.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.model.OutboxMessage;

import lombok.extern.slf4j.Slf4j;

/**
 * 跨模块事件监听器 — Cronjob 模块订阅其他模块的领域事件。
 *
 * <p>当前订阅：
 * <ul>
 *   <li>{@code FILE_UPLOADED} — 文件上传完成时可触发文件处理类定时任务</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class CrossModuleEventListener {

    /**
     * 文件上传完成 — 可触发文件处理类定时任务
     *
     * @param message Outbox 消息
     */
    @Async
    @EventListener(condition = "#message.eventType == 'FILE_UPLOADED'")
    public void onFileUploaded(OutboxMessage message) {
        log.info("[CrossModuleEventListener] 接收文件上传事件: fileId={}", message.getAggregateId());
    }
}
