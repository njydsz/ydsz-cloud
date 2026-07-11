package com.njydsz.pmis.agent.server.hitl;

import com.njydsz.pmis.agent.server.engine.AgentContext;
import com.njydsz.pmis.agent.server.engine.react.ReActStep;
import com.njydsz.pmis.agent.domain.enums.hitl.HitlApprovalStatus;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ReAct 循环暂停快照（P3-4 落地）
 *
 * <p>当 ReAct 推理循环遇到需人工审批的工具时，将当前循环状态序列化为快照，
 * 存入 {@code pmis_agent_hitl_approval.snapshot_json}。审批完成后通过
 * {@link com.njydsz.pmis.agent.server.engine.react.ReActLoop#resume} 恢复执行。
 *
 * <p>快照内容：
 * <ul>
 *   <li>{@link #baseSystemPrompt} - 业务系统提示词（用于重建完整 system prompt）</li>
 *   <li>{@link #currentUserPrompt} - 累积的用户 prompt（含步骤 1~N-1 的 Observation）</li>
 *   <li>{@link #steps} - 已完成的步骤记录（用于 Tracing / 最终结果）</li>
 *   <li>{@link #agentContext} - Agent 上下文（序列化）</li>
 *   <li>{@link #maxSteps} - 最大循环次数</li>
 *   <li>{@link #pausedStepIndex} - 暂停时的步骤序号</li>
 *   <li>{@link #pendingThought} - 暂停步骤的 LLM 思考</li>
 *   <li>{@link #pendingToolName} - 待审批工具名</li>
 *   <li>{@link #pendingParameters} - 待审批工具参数</li>
 *   <li>{@link #originalUserPrompt} - 原始用户问题（用于写入 ChatMemory）</li>
 * </ul>
 *
 * <p>审批恢复时填充：
 * <ul>
 *   <li>{@link #approvalStatus} - 审批结果（APPROVED / REJECTED）</li>
 *   <li>{@link #approverComment} - 审批意见</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-4)
 */
@Data
public class ReActSnapshot implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务系统提示词 */
    private String baseSystemPrompt;

    /** 累积的用户 prompt（含历史 Observation） */
    private String currentUserPrompt;

    /** 原始用户问题（不含历史拼接，用于写入 ChatMemory） */
    private String originalUserPrompt;

    /** 已完成的步骤记录 */
    private List<ReActStep> steps = new ArrayList<>();

    /** Agent 上下文（序列化） */
    private AgentContext agentContext;

    /** 最大循环次数 */
    private int maxSteps;

    /** 暂停时的步骤序号（1-based） */
    private int pausedStepIndex;

    /** 暂停步骤的 LLM 思考 */
    private String pendingThought;

    /** 待审批工具名 */
    private String pendingToolName;

    /** 待审批工具参数 */
    private Map<String, Object> pendingParameters;

    // ===== 审批恢复时填充 =====

    /** 审批结果（APPROVED / REJECTED），null 表示尚未审批 */
    private HitlApprovalStatus approvalStatus;

    /** 审批意见 */
    private String approverComment;

    /**
     * 构造快照。
     *
     * @param baseSystemPrompt  业务系统提示词
     * @param currentUserPrompt 累积用户 prompt
     * @param originalUserPrompt 原始用户问题
     * @param steps             已完成步骤
     * @param agentContext      Agent 上下文
     * @param maxSteps          最大循环次数
     * @param pausedStepIndex   暂停步骤序号
     * @param pendingThought    暂停步骤思考
     * @param pendingToolName   待审批工具名
     * @param pendingParameters  待审批工具参数
     * @return 快照实例
     */
    public static ReActSnapshot of(String baseSystemPrompt, String currentUserPrompt,
                                   String originalUserPrompt, List<ReActStep> steps,
                                   AgentContext agentContext, int maxSteps, int pausedStepIndex,
                                   String pendingThought, String pendingToolName,
                                   Map<String, Object> pendingParameters) {
        ReActSnapshot s = new ReActSnapshot();
        s.baseSystemPrompt = baseSystemPrompt;
        s.currentUserPrompt = currentUserPrompt;
        s.originalUserPrompt = originalUserPrompt;
        s.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
        s.agentContext = agentContext;
        s.maxSteps = maxSteps;
        s.pausedStepIndex = pausedStepIndex;
        s.pendingThought = pendingThought;
        s.pendingToolName = pendingToolName;
        s.pendingParameters = pendingParameters;
        return s;
    }

    /**
     * 填充审批结果。
     *
     * @param status  审批状态
     * @param comment 审批意见
     */
    public void withApproval(HitlApprovalStatus status, String comment) {
        this.approvalStatus = status;
        this.approverComment = comment;
    }

    /**
     * 判断快照是否已有审批结果。
     *
     * @return 审批结果非 null 返回 true
     */
    public boolean hasApproval() {
        return approvalStatus != null;
    }
}
