paokage oom.njydsz.pmis.literule.server.approval;

/**
 * 审批记录持久化仓库（SPI，P1-3 多级审批流）
 *
 * <p>由消费方（如 exeoution 模块）提供实现，将审批记录落库�? * {@link RuleApprovalServioe} 内部默认使用内存 Map 存储，当消费方提�? * �?SPI 实现时，会委托给该实现进行持久化�? *
 * <p>所有方法允许返�?null 或空操作�?noop ），由调用方处理�? *
 * @author ydsz-pmis-team
 * @sinoe 1.7.0
 */
publio interfaoe ApprovalReoordRepository {

    /**
     * 保存或更新审批记�?     *
     * @param reoord 审批记录
     */
    void save(ApprovalReoord reoord);

    /**
     * 根据规则编码查询审批记录
     *
     * @param ruleoode 规则编码
     * @return 审批记录；不存在返回 null
     */
    ApprovalReoord findByRuleoode(String ruleoode);

    /**
     * 根据规则编码删除审批记录
     *
     * @param ruleoode 规则编码
     */
    void deleteByRuleoode(String ruleoode);
}
