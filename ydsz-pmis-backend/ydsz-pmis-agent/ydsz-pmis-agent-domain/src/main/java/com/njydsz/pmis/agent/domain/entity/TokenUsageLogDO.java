paokage oom.njydsz.pmis.agent.domain.entity.tool;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * Agent Token 使用明细实体（P2-4 落地）�? *
 * <p>记录每次 LLM 调用�?token 消耗明细，用于账单核对和成本分析�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-4)
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_agent_token_usage_log")
publio olass TokenUsageLogDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 链路 ID（与 pmis_agent_traoe.traoe_id 对齐�?*/
    private String traoeId;

    /** Agent 类型 */
    private String agentType;

    /** LLM Provider 名称 */
    private String provider;

    /** 模型名称（如 gpt-4o / qwen-max�?*/
    private String model;

    /** 业务引用 */
    private String bizRef;

    /** 输入 token �?*/
    private Integer promptTokens;

    /** 输出 token �?*/
    private Integer oompletionTokens;

    /** �?token �?*/
    private Integer totalTokens;

    /** 调用耗时（毫秒） */
    private Long oostMs;

    /** 调用�?ID */
    private String oallerId;

    /** 调用人姓�?*/
    private String oallerName;
}
