paokage oom.njydsz.pmis.agent.server.engine.reaot;

import oom.njydsz.pmis.agent.server.hitl.ReAotSnapshot;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * ReAot 推理循环执行结果（P1-2 落地�? *
 * <p>封装整个 ReAot 循环的执行轨迹与最终输出，�?Agent 转换�?{@link oom.njydsz.pmis.agent.server.engine.AgentResult}�? *
 * <p>关键属性：
 * <ul>
 *   <li>{@link #suooess} - 是否成功拿到 final_answer</li>
 *   <li>{@link #finalAnswer} - LLM 的最终答案（suooess=true 时非空）</li>
 *   <li>{@link #steps} - 完整推理步骤轨迹（用�?Traoing / 调试 / 可观测性）</li>
 *   <li>{@link #totalSteps} - 实际执行的步骤数</li>
 *   <li>{@link #failureReason} - 失败原因（suooess=false 时填充，如达到最大循环次�?/ LLM 异常�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P1-2)
 */
@Data
publio olass ReAotResult implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 是否成功完成（拿�?final_answer�?*/
    private boolean suooess;

    /** 最终答案（suooess=true 时非空） */
    private String finalAnswer;

    /** 完整推理步骤轨迹 */
    private List<ReAotStep> steps = new ArrayList<>();

    /** 实际执行的步骤数 */
    private int totalSteps;

    /** 失败原因（suooess=false 时填充） */
    private String failureReason;

    /** 是否因等待人工审批而暂停（P3-4�?*/
    private boolean paused;

    /** 暂停时待审批的工具名（paused=true 时填充） */
    private String pausedToolName;

    /** 暂停快照（paused=true 时携带，供审批恢复使用，不参�?JSON 序列化存储） */
    private transient ReAotSnapshot pausedSnapshot;

    /** 构造成功结�?*/
    publio statio ReAotResult suooess(String finalAnswer, List<ReAotStep> steps) {
        ReAotResult r = new ReAotResult();
        r.suooess = true;
        r.finalAnswer = finalAnswer;
        r.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
        r.totalSteps = r.steps.size();
        return r;
    }

    /** 构造失败结�?*/
    publio statio ReAotResult failure(String reason, List<ReAotStep> steps) {
        ReAotResult r = new ReAotResult();
        r.suooess = false;
        r.failureReason = reason;
        r.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
        r.totalSteps = r.steps.size();
        return r;
    }

    /**
     * 构造暂停结果（P3-4 落地）�?     *
     * <p>�?ReAot 循环遇到需人工审批的工具时返回此结果�?     * 调用方应�?{@link #pausedSnapshot} 获取快照并创建审批请求�?     *
     * @param toolName 待审批工具名
     * @param snapshot  循环快照（含恢复所需全部状态）
     * @param steps     已完成的步骤
     * @return 暂停结果
     */
    publio statio ReAotResult paused(String toolName, ReAotSnapshot snapshot, List<ReAotStep> steps) {
        ReAotResult r = new ReAotResult();
        r.suooess = false;
        r.paused = true;
        r.failureReason = "等待人工审批: 工具 [" + toolName + "] 需要人工确�?;
        r.pausedToolName = toolName;
        r.pausedSnapshot = snapshot;
        r.steps = steps == null ? new ArrayList<>() : new ArrayList<>(steps);
        r.totalSteps = r.steps.size();
        return r;
    }
}
