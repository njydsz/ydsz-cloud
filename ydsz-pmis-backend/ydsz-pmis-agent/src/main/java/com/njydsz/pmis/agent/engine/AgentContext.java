package com.njydsz.pmis.agent.engine;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Agent 输入上下文（统一模型）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
public class AgentContext {
    /** 业务类型 */
    private String bizType;
    /** 业务 ID */
    private String bizId;
    /** 业务引用（编码/名称） */
    private String bizRef;
    /** 调用人 ID */
    private String callerId;
    /** 调用人姓名 */
    private String callerName;
    /** 来源 */
    private String source;
    /** 业务自定义输入参数（按 Agent 自行解释） */
    private Map<String, Object> params;
    /** 链路追踪 ID（批次 22 增强） */
    private String traceId;
    /** 第三方大模型 provider trace ID（用于审计/账单核对） */
    private String providerTraceId;

    /** 7 参构造器（兼容历史调用方） */
    public AgentContext(String bizType, String bizId, String bizRef,
                        String callerId, String callerName, String source,
                        Map<String, Object> params) {
        this.bizType = bizType;
        this.bizId = bizId;
        this.bizRef = bizRef;
        this.callerId = callerId;
        this.callerName = callerName;
        this.source = source;
        this.params = params;
    }

    /** 9 参构造器（批次 22 全量构造） */
    public AgentContext(String bizType, String bizId, String bizRef,
                        String callerId, String callerName, String source,
                        Map<String, Object> params, String traceId, String providerTraceId) {
        this(bizType, bizId, bizRef, callerId, callerName, source, params);
        this.traceId = traceId;
        this.providerTraceId = providerTraceId;
    }
}
