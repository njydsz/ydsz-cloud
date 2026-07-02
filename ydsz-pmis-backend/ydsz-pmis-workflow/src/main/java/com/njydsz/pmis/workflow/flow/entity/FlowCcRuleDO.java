package com.njydsz.pmis.workflow.flow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 流程抄送规则 DO
 *
 * <p>P0-3: 自动抄送规则配置（如：变更金额>1万自动抄送 CEO）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_cc_rule")
public class FlowCcRuleDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 流程编码 */
    private String flowCode;

    /** 节点编码 */
    private String nodeCode;

    /** 规则类型：USER/ROLE/DEPT/SPEL */
    private String ruleType;

    /** 规则目标 */
    private String ruleTarget;

    /** 是否启用 */
    private Integer enabled;

    /** 链路追踪 ID */
    private String providerTraceId;
}
