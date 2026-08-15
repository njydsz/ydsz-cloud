package com.njydsz.common.event.admin;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.event.model.OutboxStatus;
import com.njydsz.common.event.repository.OutboxRepository;

/**
 * Outbox 管理运维服务
 *
 * <p>提供死信（DEAD_LETTER）管理能力，包括——
 * <ul>
 *   <li>分页查询死信列表（按时间 / 事件类型过滤）</li>
 *   <li>手动重试（将 DEAD_LETTER 重置为 PENDING）</li>
 *   <li>安全删除（仅允许删除 SENT / DEAD_LETTER 状态的消息）</li>
 *   <li>队列深度统计</li>
 * </ul>
 *
 * <p><b>安全约束：</b>
 * <ul>
 *   <li>禁止删除 PENDING / PROCESSING 状态的消息（避免消息丢失）</li>
 *   <li>重试操作通过 CAS 更新确保幂等（仅 DEAD_LETTER 可被重置）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.6.0
 */
@Service
public class OutboxAdminService {

    /** 日志实例 */
    private static final Logger log = LoggerFactory.getLogger(OutboxAdminService.class);

    /** Outbox 仓储 */
    private final OutboxRepository outboxRepository;

    /**
     * 构造函数
     *
     * @param outboxRepository Outbox 仓储
     */
    public OutboxAdminService(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    /**
     * 分页查询死信消息
     *
     * <p>仅返回 {@link OutboxStatus#DEAD_LETTER} 状态的消息，按创建时间倒序排列。
     *
     * @param page 页码（从 0 开始）
     * @param size 每页大小（最大 200）
     * @param eventTypeFilter 事件类型过滤（可为 null，表示不加过滤）
     * @return 死信消息分页结果
     */
    public Page<OutboxMessage> listDeadLetters(int page, int size, String eventTypeFilter) {
        int validatedSize = Math.min(Math.max(size, 1), 200);
        Pageable pageable = PageRequest.of(Math.max(page, 0), validatedSize);
        return outboxRepository.findByStatus(OutboxStatus.DEAD_LETTER, pageable, eventTypeFilter);
    }

    /**
     * 手动重试死信消息
     *
     * <p>将指定消息从 {@link OutboxStatus#DEAD_LETTER} 重置为 {@link OutboxStatus#PENDING}，
     * 使其被轮询器重新投递。操作通过 CAS 更新确保幂等——仅当消息当前状态为
     * DEAD_LETTER 时才成功。
     *
     * @param messageId 消息 ID
     * @return true 表示重置成功，false 表示消息不存在或状态不是 DEAD_LETTER
     */
    @Transactional
    public boolean retryDeadLetter(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return false;
        }
        int affected = outboxRepository.resetToPending(messageId, OutboxStatus.DEAD_LETTER);
        if (affected > 0) {
            log.info("Dead letter message reset to PENDING: id={}", messageId);
            return true;
        }
        log.debug("Dead letter retry ignored (not found or not DEAD_LETTER): id={}", messageId);
        return false;
    }

    /**
     * 批量重试所有死信消息
     *
     * @param eventTypeFilter 事件类型过滤（可为 null，表示不加过滤）
     * @return 重置的消息数量
     */
    @Transactional
    public int retryAllDeadLetters(String eventTypeFilter) {
        int count = outboxRepository.resetAllToPending(OutboxStatus.DEAD_LETTER, eventTypeFilter);
        log.info("Bulk dead letter retry: count={}, filter={}", count, eventTypeFilter);
        return count;
    }

    /**
     * 安全删除已终态消息
     *
     * <p>仅允许删除 {@link OutboxStatus#SENT} 或 {@link OutboxStatus#DEAD_LETTER} 状态的消息。
     * 禁止删除 PENDING / PROCESSING 状态的消息以避免消息丢失。
     *
     * @param messageId 消息 ID
     * @return true 表示删除成功，false 表示消息不存在或非终态
     */
    @Transactional
    public boolean deleteTerminatedMessage(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return false;
        }
        int affected = outboxRepository.deleteIfTerminal(messageId,
                List.of(OutboxStatus.SENT, OutboxStatus.DEAD_LETTER));
        if (affected > 0) {
            log.info("Terminated outbox message deleted: id={}", messageId);
            return true;
        }
        return false;
    }

    /**
     * 物理清理超期的 SENT 消息
     *
     * <p>删除 sentAt 早于指定时间的 SENT 消息，用于定期维护。
     *
     * @param retentionDays 保留天数（删除早于此天数的消息）
     * @return 删除的消息数量
     */
    public int cleanupSentMessages(int retentionDays) {
        if (retentionDays <= 0) {
            return 0;
        }
        Instant cutoff = Instant.now().minusSeconds(retentionDays * 86400L);
        int deleted = outboxRepository.deleteSentBefore(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} sent outbox messages older than {} days", deleted, retentionDays);
        }
        return deleted;
    }

    /**
     * 获取队列深度统计
     *
     * <p>返回各状态的消息数量，供监控面板展示。
     *
     * @return 状态 → 数量
     */
    public Map<String, Long> getQueueStatistics() {
        return outboxRepository.countByStatus();
    }
}
