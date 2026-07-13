package com.njydsz.pmis.workflow.server.service;

/**
 * 三方审批双向同步服务
 *
 * <p>P2-6 (GAP-40): 本地→三方主动同步。
 * 当本地流程被终止/撤回时，主动调用三方平台"取消审批单"接口，保证三方侧审批单状态一致。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface FlowThirdPartySyncService {

    /**
     * 实例终止时同步回三方（取消三方审批单）
     *
     * @param instanceId 本地流程实例 ID（即三方 businessId）
     * @param reason     终止原因
     */
    void syncBackOnTerminate(String instanceId, String reason);

    /**
     * 实例撤回时同步回三方（取消三方审批单）
     *
     * @param instanceId 本地流程实例 ID
     * @param operatorId 撤回人
     */
    void syncBackOnRecall(String instanceId, String operatorId);
}
