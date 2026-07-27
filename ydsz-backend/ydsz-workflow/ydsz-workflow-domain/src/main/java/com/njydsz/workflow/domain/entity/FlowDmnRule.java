package com.njydsz.workflow.domain.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * P0-1: DMN 决策规则行 DO
 *
 * <p>每条规则对应决策表中的一行，包含输入条件单元格和输出值单元格。
 * 输入条件为逗号分隔的比较表达式列表（与 inputDefinitions 一一对应），
 * 输出值为逗号分隔的值列表（与 outputDefinitions 一一对应）。
 *
 * <p>输入条件格式示例：
 * <pre>
 *   ["&gt;=10000", "&lt;50000"]  — 金额 >= 10000 且 < 50000
 *   ["-", "engineering"]       — 第一个输入任意，第二个等于 engineering
 *   ["&gt;=50000", "-"]         — 金额 >= 50000，第二个输入任意
 * </pre>
 *
 * <p>输出值格式示例：
 * <pre>
 *   ["LEVEL_3", "user:1001"]  — 审批层级=LEVEL_3，审批人=user:1001
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_dmn_rule")
public class FlowDmnRule extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 所属决策表 ID */
    private String decisionId;

    /** 规则序号（从 1 开始，决定匹配顺序） */
    private Integer ruleOrder;

    /**
     * 输入条件 JSON — 与决策表 inputDefinitions 一一对应
     *
     * <p>格式: {@code [">=10000", "<50000"]}
     * "-" 表示该列不做限制（通配）
     */
    private String inputEntries;

    /**
     * 输出值 JSON — 与决策表 outputDefinitions 一一对应
     *
     * <p>格式: {@code ["LEVEL_3", "user:1001"]}
     */
    private String outputEntries;

    /** 规则备注（可空） */
    private String remark;

    /** 是否启用（0=禁用 / 1=启用） */
    private Integer enabled;

    /** 链路追踪 ID */
    private String providerTraceId;
}
