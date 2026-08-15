package com.njydsz.workflow.server.service;

/**
 * 审批人离职处理器。
 * <p>自动转办/委派给接收人。
 *
 * @author ydsz-team
 * @since 1.0.0
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
