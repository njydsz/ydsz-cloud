package com.njydsz.pmis.agent.orchestration;

import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 编排结果
 *
 * <p>汇总 4 类输出：模式 / 子 Agent 结果 / 融合结果 / 决策路径。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class OrchestrationResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 使用的编排模式 */
    private OrchestrationMode mode;
    /** 各 Agent 的子结果（agentType -> result） */
    private Map<String, AgentResult> agentResults;
    /** 融合后的最终 AgentResult（业务侧消费） */
    private AgentResult finalResult;
    /** 决策路径追踪（黑板 trace 序列化） */
    private List<AgentBlackboard.TraceEntry> trace;
    /** 总耗时 ms */
    private long totalCostMs;
    /** 触发的 Agent 数量 */
    private int agentCount;
    /** 实际触发的 Agent 类型（与声明顺序可能不同，CASCADE 可能提前终止） */
    private List<String> executedAgents;
    /** 备注（如级联提前终止原因） */
    private String note;

    /**
     * 等级转字符串（兼容：枚举 null → "NORMAL"）
     */
    public static String safeLevel(AgentAlertLevel l) {
        return l == null ? "NORMAL" : l.getCode();
    }

    /**
     * BigDecimal 安全 toString
     */
    public static String safeBd(BigDecimal b) {
        return b == null ? "0.00" : b.setScale(2, java.math.RoundingMode.HALF_UP).toString();
    }
}
