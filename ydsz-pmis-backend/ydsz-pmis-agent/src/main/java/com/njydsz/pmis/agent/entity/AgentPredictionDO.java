package com.njydsz.pmis.agent.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 智能体预测/推荐结果主表
 *
 * <p>5 类 Agent（风险预警/资源推荐/利润预测/赢率预测/工时异常）共用此表。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_agent_prediction")
public class AgentPredictionDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务编码：YYYYMMDD-{agentType}-{bizId} */
    private String taskCode;
    /** Agent 类型 */
    private String agentType;
    /** 关联业务类型：PROJECT/OPPORTUNITY/TIMESHEET/STAFF */
    private String bizType;
    /** 关联业务 ID */
    private Long bizId;
    /** 关联业务名称/编码（冗余） */
    private String bizRef;

    /** 输入数据快照（JSON） */
    private String inputSnapshot;
    /** 输出结果（JSON） */
    private String outputResult;
    /** 风险/告警等级 */
    private String alertLevel;
    /** 综合得分 0-100 */
    private BigDecimal score;
    /** 置信度 0-1 */
    private BigDecimal confidence;
    /** 建议措施（文本） */
    private String suggestion;
    /** 命中规则列表（JSON 数组） */
    private String matchedRules;
    /** 执行耗时（ms） */
    private Long costMs;
    /** 模型版本 */
    private String modelVersion;
    /** 执行状态 */
    private String status;
    /** 错误信息 */
    private String errorMsg;
    /** 调用人 ID（可空，系统触发为空） */
    private Long callerId;
    /** 调用人姓名 */
    private String callerName;
    /** 来源（MANUAL/SCHEDULED/EVENT） */
    private String source;

    /** 租户 ID */
    private Long tenantId;
    /** 第三方大模型 provider trace ID（用于审计/账单核对） */
    private String providerTraceId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标识（0 未删除，1 已删除） */
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
