package com.njydsz.pmis.workflow.flow.service;

import java.util.List;
import java.util.Map;

/**
 * P2-1: 工作流 AI 辅助服务
 *
 * <p>封装"推荐审批人 / 起草意见"两大智能能力，通过 Feign 调用 agent 模块。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface FlowAiAssistService {

    /**
     * P2-1: 推荐审批人（同步）
     *
     * <p>输入：业务上下文（流程名/节点/业务标题/发起人/期望职级/期望角色/期望部门）+ 候选人列表
     * 输出：按综合得分排序的 Top N 推荐审批人（含评分明细）
     *
     * @param ctx 业务上下文
     * @param candidates 候选人列表（每个元素含 userId/name/department/level/role/activeTasks/avgApprovalMs）
     * @param topN 推荐 Top N
     * @return 推荐的 Top N 候选人，每项包含 _score/_levelScore/_roleScore/_deptScore/_loadScore/_speedScore
     */
    List<Map<String, Object>> recommendApprovers(Map<String, Object> ctx,
                                                  List<Map<String, Object>> candidates,
                                                  int topN);

    /**
     * P2-1: 起草审批意见
     *
     * <p>输入：动作(PASS/REJECT/TRANSFER/...)/流程名/节点/业务标题/风险等级/超期天数/历史意见
     * 输出：primary 主意见 + alternatives 备选意见 + reasons 起草依据
     *
     * @param params 起草参数
     * @return Map(primary, alternatives, reasons, action, tone)
     */
    Map<String, Object> draftComment(Map<String, Object> params);

    /**
     * P2-1: 检查 AI Agent 服务是否可用（用于前端按钮置灰）
     *
     * @return true 可用；false 不可用（已降级）
     */
    boolean isAiAvailable();
}
