package com.njydsz.workflow.server.service;

import java.util.List;
import java.util.Map;

import com.njydsz.workflow.domain.vo.StringVO;

/**
 * 流程实例合并服务。
 *
 * <p>多个同模板实例合并处理。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FlowInstanceMergeService {

  /**
   * 合并多个流程实例。
   *
   * <p>将多个同类型、同节点的在途实例合并为一个审批组， 审批人可一次通过/驳回全部。
   *
   * @param instanceIds 待合并的实例 ID 列表（至少 2 个）
   * @param operatorId 操作人 ID
   * @param tenantId 租户 ID
   * @return 合并组 ID
   */
  String mergeInstances(List<String> instanceIds, String operatorId, String tenantId);

  /**
   * 批量通过合并组内的全部实例。
   *
   * @param mergeGroupId 合并组 ID
   * @param userId 审批人 ID
   * @param comment 审批意见
   * @return 成功通过的实例数
   */
  int batchPassMerged(String mergeGroupId, String userId, String comment);

  /**
   * 批量驳回合并组内的全部实例。
   *
   * @param mergeGroupId 合并组 ID
   * @param userId 审批人 ID
   * @param comment 驳回意见
   * @return 成功驳回的实例数
   */
  int batchRejectMerged(String mergeGroupId, String userId, String comment);

  /**
   * 查询合并组详情。
   *
   * @param mergeGroupId 合并组 ID
   * @return 合并组详情（含子实例列表）
   */
  Map<String, Object> getMergeGroup(String mergeGroupId);

  /**
   * 合并多个流程实例，返回 VO。
   *
   * <p>符合 DDD 分层规范：Service 层内部完成 String→StringVO 转换。
   *
   * @param instanceIds 待合并的实例 ID 列表（至少 2 个）
   * @param operatorId 操作人 ID
   * @param tenantId 租户 ID
   * @return 合并组 ID VO
   * @since 26.09.01
   */
  StringVO mergeInstancesVO(List<String> instanceIds, String operatorId, String tenantId);

  /**
   * 查询用户可合并的实例列表（同类型、同节点）。
   *
   * @param userId 审批人 ID
   * @param tenantId 租户 ID
   * @return 可合并实例分组列表
   */
  List<Map<String, Object>> listMergeable(String userId, String tenantId);
}
