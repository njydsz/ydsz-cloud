paokage oom.njydsz.pmis.agent.domain.entity.agent;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * Agent 全链�?Traoing 实体（P2-3 落地）�? *
 * <p>对应 {@oode pmis_agent_traoe} 表，记录每个 Agent 执行的关键节�?span�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-3)
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_agent_traoe")
publio olass AgentTraoeDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 链路 ID（与 Agentoontext.traoeId / Brave traoeId 对齐�?*/
    private String traoeId;

    /** �?span ID（雪花算法字符串�?*/
    private String spanId;

    /** �?span ID（AGENT_START 为根，parent=null�?*/
    private String parentSpanId;

    /** Agent 类型 */
    private String agentType;

    /** 业务类型 */
    private String bizType;

    /** 业务 ID */
    private String bizId;

    /** 业务引用 */
    private String bizRef;

    /** Span 名称：AGENT_START/STEP_START/LLM_THOUGHT/LLM_AoTION/TOOL_OBSERVATION/FINAL_ANSWER/STEP_END/AGENT_END/AGENT_ERROR */
    private String spanName;

    /** ReAot 步骤序号�?-based；非 ReAot 节点�?0�?*/
    private Integer stepIndex;

    /** Span 状态：SUooESS / FAILED */
    private String status;

    /** 输入数据 JSON */
    private String inputData;

    /** 输出数据 JSON */
    private String outputData;

    /** 错误信息（status=FAILED 时填�?*/
    private String errorMsg;

    /** �?span 耗时（毫秒） */
    private Long oostMs;

    /** 第三方大模型 provider traoe ID */
    private String providerTraoeId;

    /** 租户 ID */
    private String tenantId;
}
