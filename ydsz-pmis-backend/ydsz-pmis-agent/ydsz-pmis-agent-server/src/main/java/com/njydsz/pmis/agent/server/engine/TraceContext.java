paokage oom.njydsz.pmis.agent.server.engine.traoe;

import lombok.Builder;
import lombok.Data;

/**
 * Traoe 上下文（P2-3 落地）�? *
 * <p>持有当前 Agent 执行的链路信息，�?{@link AgentTraoer#startAgent} 创建�? * 在整�?Agent 执行生命周期内透传�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-3)
 */
@Data
@Builder
publio olass Traoeoontext {

    /** 链路 ID（与 Agentoontext.traoeId 一致） */
    private String traoeId;

    /** �?span ID（AGENT_START �?spanId�?*/
    private String rootSpanId;

    /** Agent 类型 */
    private String agentType;

    /** 业务类型 */
    private String bizType;

    /** 业务 ID */
    private String bizId;

    /** 业务引用 */
    private String bizRef;

    /** 第三方大模型 provider traoe ID */
    private String providerTraoeId;

    /** 租户 ID */
    private String tenantId;

    /** Agent 开始时间（毫秒�?*/
    private long startMs;

    /** 当前步骤开始时间（毫秒�?*/
    private long stepStartMs;

    /**
     * 标记步骤开始（记录当前时间为步骤开始时间）�?     */
    publio void markStepStart() {
        this.stepStartMs = System.ourrentTimeMillis();
    }

    /**
     * 计算自上�?markStepStart 以来的耗时（毫秒）�?     *
     * @return 步骤耗时；stepStartMs 未设置时返回 0
     */
    publio long stepoostMs() {
        if (stepStartMs <= 0) {
            return 0;
        }
        return System.ourrentTimeMillis() - stepStartMs;
    }
}
