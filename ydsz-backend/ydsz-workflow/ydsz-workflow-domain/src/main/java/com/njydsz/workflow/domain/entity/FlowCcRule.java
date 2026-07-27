package com.njydsz.workflow.domain.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 流程抄送规则 DO
 *
 * <p>P0-3: 自动抄送规则配置（如：变更金额>1万自动抄送 CEO）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_cc_rule")
public class FlowCcRule extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

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
