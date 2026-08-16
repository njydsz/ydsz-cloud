package com.njydsz.workflow.server.service;

/**
 * 第三方审批同步服务。
 *
 * <p>IM 审批状态同步到本系统。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowThirdPartySyncService {

  /**
   * 实例终止时同步回三方（取消三方审批单）
   *
   * @param instanceId 本地流程实例 ID（即三方 businessId）
   * @param reason 终止原因
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
