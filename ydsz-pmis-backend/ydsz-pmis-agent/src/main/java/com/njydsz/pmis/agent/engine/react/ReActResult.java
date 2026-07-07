package com.njydsz.pmis.agent.engine.react;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * ReAct 推理循环执行结果（P1-2 落地）
 *
 * <p>封装整个 ReAct 循环的执行轨迹与最终输出，供 Agent 转换为 {@link com.njydsz.pmis.agent.engine.AgentResult}。
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
}
