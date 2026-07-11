package com.njydsz.pmis.agent.server.engine.react;

import com.njydsz.pmis.agent.server.hitl.ReActSnapshot;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * ReAct 推理循环执行结果（P1-2 落地）
 *
 * <p>封装整个 ReAct 循环的执行轨迹与最终输出，供 Agent 转换为 {@link com.njydsz.pmis.agent.server.engine.AgentResult}。
 *
 * <p>关键属性：
 * <ul>
 *   <li>{@link #success} - 是否成功拿到 final_answer</li>
 *   <li>{@link #finalAnswer} - LLM 的最终答案（success=true 时非空）</li>
 *   <li>{@link #steps} - 完整推理步骤轨迹（用于 Tracing / 调试 / 可观测性）</li>
 *   <li>{@link #totalSteps} - 实际执行的步骤数</li>
 *   <li>{@link #failureReason} - 失败原因（success=false 时填充，如达到最大循环次数 / LLM 异常）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-2)
 */
@Data
public class ReActResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否成功完成（拿到 final_answer） */
    private boolean success;

    /** 最终答案（success=true 时非空） */
    private String finalAnswer;

    /** 完整推理步骤轨迹 */
    private List<ReActStep> steps = new ArrayList<>();

    /** 实际执行的步骤数 */
    private int totalSteps;

    /** 失败原因（success=false 时填充） */
    private String failureReason;

    /** 是否因等待人工审批而暂停（P3-4） */
    private boolean paused;

    /** 暂停时待审批的工具名（paused=true 时填充） */
    private String pausedToolName;

    /** 暂停快照（paused=true 时携带，供审批恢复使用，不参与 JSON 序列化存储） */
    private transient ReActSnapshot pausedSnapshot;

    /** 构造成功结果 */
    public static ReActResult success(String finalAnswer, List<ReActStep> steps) {
        ReActResult r = new ReActResult();
        r.success = true;
        r.finalAnswer = finalAnswer;
        r.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
        r.totalSteps = r.steps.size();
        return r;
    }

    /** 构造失败结果 */
    public static ReActResult failure(String reason, List<ReActStep> steps) {
        ReActResult r = new ReActResult();
        r.success = false;
        r.failureReason = reason;
        r.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
        r.totalSteps = r.steps.size();
        return r;
    }

    /**
     * 构造暂停结果（P3-4 落地）。
     *
     * <p>当 ReAct 循环遇到需人工审批的工具时返回此结果。
     * 调用方应从 {@link #pausedSnapshot} 获取快照并创建审批请求。
     *
     * @param toolName 待审批工具名
     * @param snapshot  循环快照（含恢复所需全部状态）
     * @param steps     已完成的步骤
     * @return 暂停结果
     */
    public static ReActResult paused(String toolName, ReActSnapshot snapshot, List<ReActStep> steps) {
        ReActResult r = new ReActResult();
        r.success = false;
        r.paused = true;
        r.failureReason = "等待人工审批: 工具 [" + toolName + "] 需要人工确认";
        r.pausedToolName = toolName;
        r.pausedSnapshot = snapshot;
        r.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
        r.totalSteps = r.steps.size();
        return r;
    }
}
