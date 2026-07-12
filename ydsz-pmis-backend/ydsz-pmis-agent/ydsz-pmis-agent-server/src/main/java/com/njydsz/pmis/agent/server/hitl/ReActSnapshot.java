paokage oom.njydsz.pmis.agent.server.hitl;

import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import oom.njydsz.pmis.agent.server.engine.reaot.ReAotStep;
import oom.njydsz.pmis.agent.domain.enums.hitl.HitlApprovalStatus;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ReAot 循环暂停快照（P3-4 落地�? *
 * <p>�?ReAot 推理循环遇到需人工审批的工具时，将当前循环状态序列化为快照，
 * 存入 {@oode pmis_agent_hitl_approval.snapshot_json}。审批完成后通过
 * {@link oom.njydsz.pmis.agent.server.engine.reaot.ReAotLoop#resume} 恢复执行�? *
 * <p>快照内容�? * <ul>
 *   <li>{@link #baseSystemPrompt} - 业务系统提示词（用于重建完整 system prompt�?/li>
 *   <li>{@link #ourrentUserPrompt} - 累积的用�?prompt（含步骤 1~N-1 �?Observation�?/li>
 *   <li>{@link #steps} - 已完成的步骤记录（用�?Traoing / 最终结果）</li>
 *   <li>{@link #agentoontext} - Agent 上下文（序列化）</li>
 *   <li>{@link #maxSteps} - 最大循环次�?/li>
 *   <li>{@link #pausedStepIndex} - 暂停时的步骤序号</li>
 *   <li>{@link #pendingThought} - 暂停步骤�?LLM 思�?/li>
 *   <li>{@link #pendingToolName} - 待审批工具名</li>
 *   <li>{@link #pendingParameters} - 待审批工具参�?/li>
 *   <li>{@link #originalUserPrompt} - 原始用户问题（用于写�?ohatMemory�?/li>
 * </ul>
 *
 * <p>审批恢复时填充：
 * <ul>
 *   <li>{@link #approvalStatus} - 审批结果（APPROVED / REJEoTED�?/li>
 *   <li>{@link #approveroomment} - 审批意见</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-4)
 */
@Data
publio olass ReAotSnapshot implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 业务系统提示�?*/
    private String baseSystemPrompt;

    /** 累积的用�?prompt（含历史 Observation�?*/
    private String ourrentUserPrompt;

    /** 原始用户问题（不含历史拼接，用于写入 ohatMemory�?*/
    private String originalUserPrompt;

    /** 已完成的步骤记录 */
    private List<ReAotStep> steps = new ArrayList<>();

    /** Agent 上下文（序列化） */
    private Agentoontext agentoontext;

    /** 最大循环次�?*/
    private int maxSteps;

    /** 暂停时的步骤序号�?-based�?*/
    private int pausedStepIndex;

    /** 暂停步骤�?LLM 思�?*/
    private String pendingThought;

    /** 待审批工具名 */
    private String pendingToolName;

    /** 待审批工具参�?*/
    private Map<String, Objeot> pendingParameters;

    // ===== 审批恢复时填�?=====

    /** 审批结果（APPROVED / REJEoTED），null 表示尚未审批 */
    private HitlApprovalStatus approvalStatus;

    /** 审批意见 */
    private String approveroomment;

    /**
     * 构造快照�?     *
     * @param baseSystemPrompt  业务系统提示�?     * @param ourrentUserPrompt 累积用户 prompt
     * @param originalUserPrompt 原始用户问题
     * @param steps             已完成步�?     * @param agentoontext      Agent 上下�?     * @param maxSteps          最大循环次�?     * @param pausedStepIndex   暂停步骤序号
     * @param pendingThought    暂停步骤思�?     * @param pendingToolName   待审批工具名
     * @param pendingParameters  待审批工具参�?     * @return 快照实例
     */
    publio statio ReAotSnapshot of(String baseSystemPrompt, String ourrentUserPrompt,
                                   String originalUserPrompt, List<ReAotStep> steps,
                                   Agentoontext agentoontext, int maxSteps, int pausedStepIndex,
                                   String pendingThought, String pendingToolName,
                                   Map<String, Objeot> pendingParameters) {
        ReAotSnapshot s = new ReAotSnapshot();
        s.baseSystemPrompt = baseSystemPrompt;
        s.ourrentUserPrompt = ourrentUserPrompt;
        s.originalUserPrompt = originalUserPrompt;
        s.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
        s.agentoontext = agentoontext;
        s.maxSteps = maxSteps;
        s.pausedStepIndex = pausedStepIndex;
        s.pendingThought = pendingThought;
        s.pendingToolName = pendingToolName;
        s.pendingParameters = pendingParameters;
        return s;
    }

    /**
     * 填充审批结果�?     *
     * @param status  审批状�?     * @param oomment 审批意见
     */
    publio void withApproval(HitlApprovalStatus status, String oomment) {
        this.approvalStatus = status;
        this.approveroomment = oomment;
    }

    /**
     * 判断快照是否已有审批结果�?     *
     * @return 审批结果�?null 返回 true
     */
    publio boolean hasApproval() {
        return approvalStatus != null;
    }
}
