package com.njydsz.pmis.literule.server.approval;

/**
 * 审批记录持久化仓库（SPI，P1-3 多级审批流）
 *
 * <p>由消费方（如 execution 模块）提供实现，将审批记录落库。
 * {@link RuleApprovalService} 内部默认使用内存 Map 存储，当消费方提供
 * 此 SPI 实现时，会委托给该实现进行持久化。
 *
 * <p>所有方法允许返回 null 或空操作（ noop ），由调用方处理。
 *
 * @since 1.7.0
 */
public interface ApprovalRecordRepository {

    /**
     * 保存或更新审批记录
     *
     * @param record 审批记录
     */
    void save(ApprovalRecord record);

    /**
     * 根据规则编码查询审批记录
     *
     * @param ruleCode 规则编码
     * @return 审批记录；不存在返回 null
     */
    ApprovalRecord findByRuleCode(String ruleCode);

    /**
     * 根据规则编码删除审批记录
     *
     * @param ruleCode 规则编码
     */
    void deleteByRuleCode(String ruleCode);
}
