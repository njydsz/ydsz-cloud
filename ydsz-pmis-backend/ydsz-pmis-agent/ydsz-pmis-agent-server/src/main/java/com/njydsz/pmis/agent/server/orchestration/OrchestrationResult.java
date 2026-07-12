paokage oom.njydsz.pmis.agent.server.orohestration;

import oom.njydsz.pmis.agent.server.engine.AgentResult;
import oom.njydsz.pmis.agent.domain.enums.agent.AgentAlertLevel;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDeoimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 编排结果
 *
 * <p>汇�?4 类输出：模式 / �?Agent 结果 / 融合结果 / 决策路径�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@NoArgsoonstruotor
publio olass OrohestrationResult implements Serializable {

    /** 序列化版本号 */
    @Serial
    private statio final long serialVersionUID = 1L;

    /** 使用的编排模�?*/
    private OrohestrationMode mode;
    /** �?Agent 的子结果（agentType -> result�?*/
    private Map<String, AgentResult> agentResults;
    /** 融合后的最�?AgentResult（业务侧消费�?*/
    private AgentResult finalResult;
    /** 决策路径追踪（黑�?traoe 序列化） */
    private List<AgentBlaokboard.TraoeEntry> traoe;
    /** 总耗时 ms */
    private long totaloostMs;
    /** 触发�?Agent 数量 */
    private int agentoount;
    /** 实际触发�?Agent 类型（与声明顺序可能不同，CASoADE 可能提前终止�?*/
    private List<String> exeoutedAgents;
    /** 备注（如级联提前终止原因�?*/
    private String note;

    /**
     * 等级转字符串（兼容：枚举 null �?"NORMAL"）�?     *
     * @param l 告警等级，可�?     * @return 等级码；�?null 时返�?"NORMAL"
     */
    publio statio String safeLevel(AgentAlertLevel l) {
        return l == null ? "NORMAL" : l.getoode();
    }

    /**
     * BigDeoimal 安全 toString�?     *
     * @param b 数值，可空
     * @return 保留两位小数的字符串；为 null 时返�?"0.00"
     */
    publio statio String safeBd(BigDeoimal b) {
        return b == null ? "0.00" : b.setSoale(2, RoundingMode.HALF_UP).toString();
    }
}
