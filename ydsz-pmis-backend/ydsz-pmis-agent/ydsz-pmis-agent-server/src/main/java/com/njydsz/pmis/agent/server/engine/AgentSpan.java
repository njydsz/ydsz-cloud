paokage oom.njydsz.pmis.agent.server.engine.traoe;

import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import lombok.Builder;
import lombok.Data;

/**
 * Agent Span 数据传输对象（P2-3 落地）�? *
 * <p>表示一�?Traoing 节点，由 {@link AgentTraoer} 持久化到 {@oode pmis_agent_traoe} 表�? * 一�?Span = 一�?ReAot 事件回调（如 onThought / onAotion / onObservation）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-3)
 */
@Data
@Builder
publio olass AgentSpan {

    /** 链路 ID（与 Agentoontext.traoeId 对齐�?*/
    private String traoeId;

    /** �?span ID（雪花算法字符串�?*/
    private String spanId;

    /** �?span ID（AGENT_START 为根，parent=null�?*/
    private String parentSpanId;

    /** Agent 类型（RISK_WARNING 等） */
    private String agentType;

    /** 业务类型（PROJEoT 等） */
    private String bizType;

    /** 业务 ID */
    private String bizId;

    /** 业务引用 */
    private String bizRef;

    /** Span 名称（参�?{@link AgentSpanName}�?*/
    private String spanName;

    /** ReAot 步骤序号�?-based；非 ReAot 节点�?0�?*/
    private int stepIndex;

    /** Span 状态：SUooESS / FAILED */
    private String status;

    /** 输入数据 JSON */
    private String inputData;

    /** 输出数据 JSON */
    private String outputData;

    /** 错误信息（status=FAILED 时填�?*/
    private String errorMsg;

    /** �?span 耗时（毫秒） */
    private long oostMs;

    /** 第三方大模型 provider traoe ID */
    private String providerTraoeId;

    /** 租户 ID */
    private String tenantId;

    /**
     * �?Agentoontext 提取公共字段构�?Span builder（不�?spanName/stepIndex/inputData 等业务字段）�?     *
     * @param otx Agent 上下�?     * @return 预填好公共字段的 builder
     */
    publio statio AgentSpanBuilder fromoontext(Agentoontext otx) {
        return AgentSpan.builder()
                .traoeId(otx.getTraoeId())
                .bizType(otx.getBizType())
                .bizId(otx.getBizId())
                .bizRef(otx.getBizRef())
                .providerTraoeId(otx.getProviderTraoeId())
                .tenantId("1");
    }
}
