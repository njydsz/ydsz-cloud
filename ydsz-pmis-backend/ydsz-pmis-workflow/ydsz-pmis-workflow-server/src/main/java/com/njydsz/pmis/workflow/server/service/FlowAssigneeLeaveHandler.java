package com.njydsz.pmis.workflow.server.service;

/**
 * 审批人离职/调岗自动处理服务（P1-1）
 *
 * <p>当审批人离职或调岗时，系统自动将其名下待办任务转交给替代人。
 * 替代人解析优先级：
 * <ol>
 *   <li>显式指定的替代人（如有）</li>
 *   <li>有效的长期授权委派（FlowDelegateAuth）</li>
 *   <li>直属上级（通过 FlowAssigneeResolver 查询）</li>
 *   <li>流程管理员兜底</li>
 * </ol>
 *
 * @since 1.9.0
 */
public interface FlowAssigneeLeaveHandler {

    /**
     * 处理审批人离职/调岗，自动转交待办任务。
     *
     * @param userId            离职/调岗用户 ID
     * @param leaveType         类型：RESIGN（离职）/ TRANSFER（调岗）
     * @param replacementUserId 指定替代人 ID（可空，空时自动解析）
     * @param operatorId        操作人 ID
     * @return 成功转交的任务数
     */
    int handleLeave(String userId, String leaveType, String replacementUserId, String operatorId);
}
