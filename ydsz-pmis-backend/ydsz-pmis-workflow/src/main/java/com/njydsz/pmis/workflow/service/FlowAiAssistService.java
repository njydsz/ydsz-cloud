package com.njydsz.pmis.workflow.service;

import java.util.List;
import java.util.Map;

/**
 * P2-1: 工作流 AI 辅助服务
 *
 * <p>封装"推荐审批人 / 起草意见"两大智能能力，通过 Feign 调用 agent 模块。
 *
 * <p>P3-1: 扩展 3 项 AI 能力：
 * <ul>
 *   <li>{@link #predictRisk} — 流程风险预测（驳回率/超期率）</li>
 *   <li>{@link #smartRemind} — 智能催办（最佳催办时机/渠道/话术）</li>
 *   <li>{@link #predictSla} — SLA 预测（预计完成时间/置信度）</li>
 * </ul>
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

    // ============================== P3-1: AI 能力扩展 ==============================

    /**
     * P3-1: 流程风险预测。
     *
     * <p>基于流程实例的历史数据（驳回率/超期率/审批人负载/业务金额等）预测当前实例的风险等级。
     *
     * @param params 预测参数（instanceId / flowCode / businessTitle / amount / initiatorId 等）
     * @return Map(riskLevel, rejectProbability, overdueProbability, reasons)
     *         <ul>
     *           <li>riskLevel: LOW / MEDIUM / HIGH / UNKNOWN（降级）</li>
     *           <li>rejectProbability: 0.0~1.0 驳回概率</li>
     *           <li>overdueProbability: 0.0~1.0 超期概率</li>
     *           <li>reasons: 风险因素列表</li>
     *         </ul>
     */
    Map<String, Object> predictRisk(Map<String, Object> params);

    /**
     * P3-1: 智能催办。
     *
     * <p>根据审批人历史行为（活跃时段/响应速度/偏好渠道）建议最佳催办时机与话术。
     *
     * @param params 催办参数（taskId / assigneeId / flowCode / nodeCode 等）
     * @return Map(bestTime, channel, message, reasons)
     *         <ul>
     *           <li>bestTime: IMMEDIATE / MORNING / AFTERNOON / EVENING（降级为 IMMEDIATE）</li>
     *           <li>channel: INAPP / SMS / EMAIL / WEBHOOK（降级为 INAPP）</li>
     *           <li>message: 催办话术</li>
     *           <li>reasons: 建议依据</li>
     *         </ul>
     */
    Map<String, Object> smartRemind(Map<String, Object> params);

    /**
     * P3-1: SLA 预测。
     *
     * <p>基于流程历史耗时数据预测当前实例的预计完成时间。
     *
     * @param params 预测参数（instanceId / flowCode / currentNodeCode 等）
     * @return Map(estimatedDurationMs, estimatedCompleteAt, confidence, reasons)
     *         <ul>
     *           <li>estimatedDurationMs: 预计剩余耗时（毫秒），降级为 0</li>
     *           <li>estimatedCompleteAt: 预计完成时间（ISO-8601），降级为 null</li>
     *           <li>confidence: 置信度 0.0~1.0，降级为 0.0</li>
     *           <li>reasons: 预测依据</li>
     *         </ul>
     */
    Map<String, Object> predictSla(Map<String, Object> params);

    // ============================== P3-3: 推荐审批人反馈闭环 ==============================

    /**
     * P3-3: 记录用户对 AI 推荐审批人的反馈。
     *
     * <p>用户在前端选择审批人后调用此接口，记录反馈行为（接受/拒绝/选择其他人），
     * 形成"推荐 → 反馈 → 优化"闭环。反馈数据将用于：
     * <ul>
     *   <li>统计 AI 推荐准确率（接受率/拒绝率）</li>
     *   <li>作为后续推荐的历史上下文，提升推荐准确度</li>
     * </ul>
     *
     * @param feedback 反馈数据，需包含：
     *                 <ul>
     *                   <li>traceId: 推荐调用追踪 ID（必填，来自 recommendApprovers 返回）</li>
     *                   <li>recommendedUserId: AI 推荐的审批人 ID（必填）</li>
     *                   <li>action: 反馈动作 ACCEPTED/REJECTED/CHOSEN_OTHER（必填）</li>
     *                   <li>taskId / flowCode / nodeCode: 业务上下文（可选）</li>
     *                   <li>actualUserId: 实际选择的审批人 ID（action=CHOSEN_OTHER 时必填）</li>
     *                   <li>remark: 备注（可选）</li>
     *                 </ul>
     * @return 记录后的反馈 ID
     */
    String recordApproverFeedback(Map<String, Object> feedback);

    /**
     * P3-3: 获取 AI 推荐审批人反馈统计。
     *
     * <p>统计指定范围（全部或某推荐人）的反馈分布，用于评估 AI 推荐准确率。
     *
     * @param params 查询参数，可选：
     *               <ul>
     *                 <li>recommendedUserId: 按推荐人过滤（可选，空则统计全部）</li>
     *                 <li>tenantId: 租户 ID（可选，默认 '1'）</li>
     *               </ul>
     * @return Map(total, accepted, rejected, chosenOther, acceptanceRate)
     *         <ul>
     *           <li>total: 反馈总数</li>
     *           <li>accepted: 接受次数</li>
     *           <li>rejected: 拒绝次数</li>
     *           <li>chosenOther: 选择其他人次数</li>
     *           <li>acceptanceRate: 接受率 0.0~1.0</li>
     *         </ul>
     */
    Map<String, Object> getApproverFeedbackStats(Map<String, Object> params);
}
