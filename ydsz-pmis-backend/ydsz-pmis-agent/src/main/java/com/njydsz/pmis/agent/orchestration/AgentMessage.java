package com.njydsz.pmis.agent.orchestration;

import com.njydsz.pmis.agent.engine.AgentResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * Agent 间消息
 *
 * <p>编排过程中 Agent 之间的输入/输出消息。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 消息类型：INPUT(初始输入) / OUTPUT(单个 Agent 输出) / CONTROL(控制指令) */
    private String type;
    /** 发送方 Agent（"COORDINATOR" 表示协调器） */
    private String from;
    /** 接收方 Agent（null 表示广播给黑板） */
    private String to;
    /** 关联的 Agent 结果（OUTPUT 类型时填） */
    private AgentResult result;
    /** 自由载荷 */
    private Map<String, Object> payload;
    /** 时间戳 */
    private long ts;

    public static AgentMessage input(String from, Map<String, Object> payload) {
        return new AgentMessage("INPUT", from, null, null, payload, System.currentTimeMillis());
    }

    public static AgentMessage output(String from, AgentResult result) {
        return new AgentMessage("OUTPUT", from, null, result, null, System.currentTimeMillis());
    }

    public static AgentMessage control(String from, String to, Map<String, Object> payload) {
        return new AgentMessage("CONTROL", from, to, null, payload, System.currentTimeMillis());
    }
}
