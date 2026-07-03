package com.njydsz.pmis.workflow.service;

import com.njydsz.pmis.workflow.entity.EventOutboxDO;

import java.util.List;

/**
 * 事件 Outbox 服务（P2-1）
 *
 * <p>提供事件落库 + 扫描投递能力，保证业务事务与消息投递的最终一致性。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
public interface FlowEventOutboxService {

    /**
     * 写入 outbox（在主事务内同步执行，事务回滚则 outbox 也回滚）
     *
     * @param event 事件实体（status 默认 PENDING）
     * @return outbox ID
     */
    Long saveOutbox(EventOutboxDO event);

    /**
     * 扫描待投递事件并投递
     *
     * <p>由 @Scheduled 定时调用，单次扫描 limit 条，逐条投递。
     * 投递成功标 SENT，失败 retry_count++，超阈值标 DEAD。
     *
     * @param batchSize 单次扫描最大条数
     * @return 实际投递成功条数
     */
    int scanAndDeliver(int batchSize);

    /**
     * 查询死信列表（供后台人工重投）
     *
     * @param limit 最大条数
     * @return 死信事件列表
     */
    List<EventOutboxDO> listDeadEvents(int limit);

    /**
     * 人工重投死信事件
     *
     * @param id 事件 ID
     * @return 重投结果（true=已重新加入待投递队列）
     */
    boolean retryDeadEvent(Long id);
}
