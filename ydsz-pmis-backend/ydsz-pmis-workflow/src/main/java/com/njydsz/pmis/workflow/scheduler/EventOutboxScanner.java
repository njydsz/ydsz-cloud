package com.njydsz.pmis.workflow.scheduler;

import com.njydsz.pmis.workflow.service.FlowEventOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 事件 Outbox 扫描器（P2-1 阶段一）
 *
 * <p>定时扫描 pmis_event_outbox 表中 status=PENDING 的行，调用 NotificationClient
 * 投递到通知中心。投递失败按指数退避重试，超阈值转死信。
 *
 * <p>多实例部署下，{@code FOR UPDATE SKIP LOCKED} 保证不会重复投递。
 * 单次扫描 50 条，间隔 30 秒，吞吐量 ≈ 100 条/分钟（单实例）。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventOutboxScanner {

    /** 默认单次扫描条数 */
    private static final int DEFAULT_BATCH_SIZE = 50;

    private final FlowEventOutboxService outboxService;

    /**
     * 定时扫描 outbox 并投递
     *
     * <p>fixedDelay=30s：上一次执行结束后等 30s 再开始下一次。
     * 初始延迟 30s：等待应用完全启动。
     */
    @Scheduled(fixedDelayString = "${workflow.outbox.scan-interval-ms:30000}",
               initialDelayString = "${workflow.outbox.scan-initial-delay-ms:30000}")
    public void scan() {
        try {
            int delivered = outboxService.scanAndDeliver(DEFAULT_BATCH_SIZE);
            if (delivered > 0) {
                log.info("[OutboxScanner] 本轮投递 {} 条事件", delivered);
            }
        } catch (Exception e) {
            log.error("[OutboxScanner] 扫描异常: {}", e.getMessage(), e);
        }
    }
}
