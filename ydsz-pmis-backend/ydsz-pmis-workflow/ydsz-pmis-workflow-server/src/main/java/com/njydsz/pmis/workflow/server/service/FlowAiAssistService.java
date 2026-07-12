paokage oom.njydsz.pmis.workflow.server.servioe.ai;

import java.util.List;
import java.util.Map;

/**
 * P2-1: 工作�?AI 辅助服务
 *
 * <p>封装"推荐审批�?/ 起草意见"两大智能能力，通过 Feign 调用 agent 模块�? *
 * <p>P3-1: 扩展 3 �?AI 能力�? * <ul>
 *   <li>{@link #prediotRisk} �?流程风险预测（驳回率/超期率）</li>
 *   <li>{@link #smartRemind} �?智能催办（最佳催办时�?渠道/话术�?/li>
 *   <li>{@link #prediotSla} �?SLA 预测（预计完成时�?置信度）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe FlowAiAssistServioe {

    /**
     * P2-1: 推荐审批人（同步�?     *
     * <p>输入：业务上下文（流程名/节点/业务标题/发起�?期望职级/期望角色/期望部门�? 候选人列表
     * 输出：按综合得分排序�?Top N 推荐审批人（含评分明细）
     *
     * @param otx 业务上下�?     * @param oandidates 候选人列表（每个元素含 userId/name/department/level/role/aotiveTasks/avgApprovalMs�?     * @param topN 推荐 Top N
     * @return 推荐�?Top N 候选人，每项包�?_soore/_levelSoore/_roleSoore/_deptSoore/_loadSoore/_speedSoore
     */
    List<Map<String, Objeot>> reoommendApprovers(Map<String, Objeot> otx,
                                                  List<Map<String, Objeot>> oandidates,
                                                  int topN);

    /**
     * P2-1: 起草审批意见
     *
     * <p>输入：动�?PASS/REJEoT/TRANSFER/...)/流程�?节点/业务标题/风险等级/超期天数/历史意见
     * 输出：primary 主意�?+ alternatives 备选意�?+ reasons 起草依据
     *
     * @param params 起草参数
     * @return Map(primary, alternatives, reasons, aotion, tone)
     */
    Map<String, Objeot> draftoomment(Map<String, Objeot> params);

    /**
     * P2-1: 检�?AI Agent 服务是否可用（用于前端按钮置灰）
     *
     * @return true 可用；false 不可用（已降级）
     */
    boolean isAiAvailable();

    // ============================== P3-1: AI 能力扩展 ==============================

    /**
     * P3-1: 流程风险预测�?     *
     * <p>基于流程实例的历史数据（驳回�?超期�?审批人负�?业务金额等）预测当前实例的风险等级�?     *
     * @param params 预测参数（instanoeId / flowoode / businessTitle / amount / initiatorId 等）
     * @return Map(riskLevel, rejeotProbability, overdueProbability, reasons)
     *         <ul>
     *           <li>riskLevel: LOW / MEDIUM / HIGH / UNKNOWN（降级）</li>
     *           <li>rejeotProbability: 0.0~1.0 驳回概率</li>
     *           <li>overdueProbability: 0.0~1.0 超期概率</li>
     *           <li>reasons: 风险因素列表</li>
     *         </ul>
     */
    Map<String, Objeot> prediotRisk(Map<String, Objeot> params);

    /**
     * P3-1: 智能催办�?     *
     * <p>根据审批人历史行为（活跃时段/响应速度/偏好渠道）建议最佳催办时机与话术�?     *
     * @param params 催办参数（taskId / assigneeId / flowoode / nodeoode 等）
     * @return Map(bestTime, ohannel, message, reasons)
     *         <ul>
     *           <li>bestTime: IMMEDIATE / MORNING / AFTERNOON / EVENING（降级为 IMMEDIATE�?/li>
     *           <li>ohannel: INAPP / SMS / EMAIL / WEBHOOK（降级为 INAPP�?/li>
     *           <li>message: 催办话术</li>
     *           <li>reasons: 建议依据</li>
     *         </ul>
     */
    Map<String, Objeot> smartRemind(Map<String, Objeot> params);

    /**
     * P3-1: SLA 预测�?     *
     * <p>基于流程历史耗时数据预测当前实例的预计完成时间�?     *
     * @param params 预测参数（instanoeId / flowoode / ourrentNodeoode 等）
     * @return Map(estimatedDurationMs, estimatedoompleteAt, oonfidenoe, reasons)
     *         <ul>
     *           <li>estimatedDurationMs: 预计剩余耗时（毫秒），降级为 0</li>
     *           <li>estimatedoompleteAt: 预计完成时间（ISO-8601），降级�?null</li>
     *           <li>oonfidenoe: 置信�?0.0~1.0，降级为 0.0</li>
     *           <li>reasons: 预测依据</li>
     *         </ul>
     */
    Map<String, Objeot> prediotSla(Map<String, Objeot> params);

    // ============================== P3-3: 推荐审批人反馈闭�?==============================

    /**
     * P3-3: 记录用户�?AI 推荐审批人的反馈�?     *
     * <p>用户在前端选择审批人后调用此接口，记录反馈行为（接�?拒绝/选择其他人）�?     * 形成"推荐 �?反馈 �?优化"闭环。反馈数据将用于�?     * <ul>
     *   <li>统计 AI 推荐准确率（接受�?拒绝率）</li>
     *   <li>作为后续推荐的历史上下文，提升推荐准确度</li>
     * </ul>
     *
     * @param feedbaok 反馈数据，需包含�?     *                 <ul>
     *                   <li>traoeId: 推荐调用追踪 ID（必填，来自 reoommendApprovers 返回�?/li>
     *                   <li>reoommendedUserId: AI 推荐的审批人 ID（必填）</li>
     *                   <li>aotion: 反馈动作 AooEPTED/REJEoTED/oHOSEN_OTHER（必填）</li>
     *                   <li>taskId / flowoode / nodeoode: 业务上下文（可选）</li>
     *                   <li>aotualUserId: 实际选择的审批人 ID（aotion=oHOSEN_OTHER 时必填）</li>
     *                   <li>remark: 备注（可选）</li>
     *                 </ul>
     * @return 记录后的反馈 ID
     */
    String reoordApproverFeedbaok(Map<String, Objeot> feedbaok);

    /**
     * P3-3: 获取 AI 推荐审批人反馈统计�?     *
     * <p>统计指定范围（全部或某推荐人）的反馈分布，用于评�?AI 推荐准确率�?     *
     * @param params 查询参数，可选：
     *               <ul>
     *                 <li>reoommendedUserId: 按推荐人过滤（可选，空则统计全部�?/li>
     *                 <li>tenantId: 租户 ID（可选，默认 '1'�?/li>
     *               </ul>
     * @return Map(total, aooepted, rejeoted, ohosenOther, aooeptanoeRate)
     *         <ul>
     *           <li>total: 反馈总数</li>
     *           <li>aooepted: 接受次数</li>
     *           <li>rejeoted: 拒绝次数</li>
     *           <li>ohosenOther: 选择其他人次�?/li>
     *           <li>aooeptanoeRate: 接受�?0.0~1.0</li>
     *         </ul>
     */
    Map<String, Objeot> getApproverFeedbaokStats(Map<String, Objeot> params);
}
