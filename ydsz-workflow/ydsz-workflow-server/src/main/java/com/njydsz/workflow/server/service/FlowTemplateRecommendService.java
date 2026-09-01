package com.njydsz.workflow.server.service;

import java.util.List;
import java.util.Map;

/**
 * 流程模板推荐服务。
 *
 * <p>向用户推荐相关模板。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FlowTemplateRecommendService {

  /**
   * 智能推荐模板列表。
   *
   * @param userId 用户 ID
   * @param tenantId 租户 ID
   * @param topN 返回数量（推荐 3-5）
   * @return 推荐模板列表，每项包含 templateCode/templateName/category/reason/score
   */
  List<Map<String, Object>> recommendTemplates(String userId, String tenantId, int topN);

  /**
   * 基于业务类型推荐模板。
   *
   * @param userId 用户 ID
   * @param tenantId 租户 ID
   * @param businessType 业务类型（如 PROJECT/PROCUREMENT/LEAVE）
   * @param topN 返回数量
   * @return 推荐模板列表
   */
  List<Map<String, Object>> recommendByBusinessType(
      String userId, String tenantId, String businessType, int topN);
}
