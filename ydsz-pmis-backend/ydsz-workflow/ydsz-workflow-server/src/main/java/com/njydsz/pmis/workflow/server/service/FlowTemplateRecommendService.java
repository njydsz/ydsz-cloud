package com.njydsz.workflow.server.service;

import java.util.List;
import java.util.Map;

/**
 * P2-2: 审批模板智能推荐服务
 *
 * <p>对标钉钉"推荐模板"能力。基于用户历史发起记录 + 业务类型匹配 + 热度排序，
 * 为用户推荐最可能需要的审批模板。
 *
 * <p>推荐策略：
 * <ol>
 *   <li>用户历史发起频率：统计用户过去 30 天发起的流程类型，按频次降序</li>
 *   <li>同部门热门模板：同部门其他用户常用的模板</li>
 *   <li>全局热门模板：按 use_count 降序的兜底推荐</li>
 *   <li>业务类型匹配：根据当前上下文（如项目管理模块）推荐相关分类模板</li>
 * </ol>
 *
 * @since 1.0.0
 */
public interface FlowTemplateRecommendService {

    /**
     * 智能推荐模板列表。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @param topN     返回数量（推荐 3-5）
     * @return 推荐模板列表，每项包含 templateCode/templateName/category/reason/score
     */
    List<Map<String, Object>> recommendTemplates(String userId, String tenantId, int topN);

    /**
     * 基于业务类型推荐模板。
     *
     * @param userId       用户 ID
     * @param tenantId     租户 ID
     * @param businessType 业务类型（如 PROJECT/PROCUREMENT/LEAVE）
     * @param topN         返回数量
     * @return 推荐模板列表
     */
    List<Map<String, Object>> recommendByBusinessType(String userId, String tenantId,
                                                        String businessType, int topN);
}
