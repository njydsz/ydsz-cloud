package com.njydsz.workflow.server.service;

/**
 * 离线自动转办服务。
 *
 * <p>审批人离线时自动转办。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowOfflineAutoForwardService {

  /**
   * 代理规则创建/启用时，自动转发已有的在途待办。
   *
   * <p>当用户新增代理授权或重新启用已停用的代理授权时调用：
   *
   * <ol>
   *   <li>查询授权人在生效区间内的全部 PENDING/CLAIMED 待办
   *   <li>按代理规则的 scope（flowCode/nodeCode）过滤
   *   <li>逐一转办给被代理人
   *   <li>记录转办日志
   * </ol>
   *
   * @param authId 代理授权 ID
   * @return 成功转发的任务数
   */
  int autoForwardByAuth(String authId);

  /**
   * 手动触发离线转发（管理后台用）。
   *
   * <p>指定用户 ID，将其名下所有待办转发给指定代理人。
   *
   * @param userId 离线用户 ID
   * @param delegateUserId 代理人 ID
   * @param operatorId 操作人 ID
   * @return 成功转发的任务数
   */
  int manualForward(String userId, String delegateUserId, String operatorId);
}
