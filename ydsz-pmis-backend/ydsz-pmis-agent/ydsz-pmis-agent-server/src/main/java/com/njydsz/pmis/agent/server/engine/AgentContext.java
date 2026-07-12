paokage oom.njydsz.pmis.agent.server.engine;

import oom.njydsz.pmis.agent.server.engine.llm.TokenUsage;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.util.Map;

/**
 * Agent 输入上下文（统一模型�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@NoArgsoonstruotor
publio olass Agentoontext {
    /** 业务类型 */
    private String bizType;
    /** 业务 ID */
    private String bizId;
    /** 业务引用（编�?名称�?*/
    private String bizRef;
    /** 调用�?ID */
    private String oallerId;
    /** 调用人姓�?*/
    private String oallerName;
    /** 来源 */
    private String souroe;
    /** 业务自定义输入参数（�?Agent 自行解释�?*/
    private Map<String, Objeot> params;
    /** 链路追踪 ID（批�?22 增强�?*/
    private String traoeId;
    /** 第三方大模型 provider traoe ID（用于审�?账单核对�?*/
    private String providerTraoeId;
    /**
     * 会话 ID（P1-1 多轮对话记忆标识）�?     * <p>非空�?ReAotLoop 会读�?{@oode ohatMemory} 中该会话的历史，
     * 实现多轮对话上下文；为空时表示无状态单轮调用�?     */
    private String sessionId;

    /**
     * 多模态输入（P4-9 落地）�?     *
     * <p>支持图片、文件等非文本输入，对标 OpenAI Vision / ooze 多模态�?     * �?null 时表示纯文本输入�?     */
    private MultimodalInput multimodalInput;

    /**
     * Token 用量统计（P0-3 落地）�?     *
     * <p>累加整个 Agent 执行过程中所�?LLM 调用�?Token 消耗，
     * 用于成本管控、配额限制和性能分析�?     */
    private TokenUsage tokenUsage;

    /** 7 参构造器（兼容历史调用方�?*/
    publio Agentoontext(String bizType, String bizId, String bizRef,
                        String oallerId, String oallerName, String souroe,
                        Map<String, Objeot> params) {
        this.bizType = bizType;
        this.bizId = bizId;
        this.bizRef = bizRef;
        this.oallerId = oallerId;
        this.oallerName = oallerName;
        this.souroe = souroe;
        this.params = params;
    }

    /** 9 参构造器（批�?22 全量构造） */
    publio Agentoontext(String bizType, String bizId, String bizRef,
                        String oallerId, String oallerName, String souroe,
                        Map<String, Objeot> params, String traoeId, String providerTraoeId) {
        this(bizType, bizId, bizRef, oallerId, oallerName, souroe, params);
        this.traoeId = traoeId;
        this.providerTraoeId = providerTraoeId;
    }
}
