package com.njydsz.pmis.workflow.domain.entity.dmn;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.domain.entity.VersionableDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * P0-1: DMN 决策表 DO
 *
 * <p>对标 BPMN 2.0 DMN (Decision Model and Notation) 规范中的决策表概念。
 * 每条决策表包含若干输入列（inputExpressions）和输出列（outputLabels），
 * 以及一组规则行（{@link FlowDmnRuleDO}），按顺序匹配第一条命中的规则输出结果。
 *
 * <p>使用场景：
 * <ul>
 *   <li>排他网关/包容网关的路由条件由 DMN 决策表驱动，替代硬编码 SpEL 表达式</li>
 *   <li>审批人推荐规则（金额区间 → 审批层级）</li>
 *   <li>SLA 超时阈值动态决策（业务类型 + 金额 → 超时分钟数）</li>
 * </ul>
 *
 * <p>hitPolicy 说明：
 * <ul>
 *   <li>UNIQUE — 仅一条规则命中（类似排他网关）</li>
 *   <li>FIRST — 按顺序取第一条命中（默认）</li>
 *   <li>ANY — 多条命中时输出必须相同</li>
 *   <li>COLLECT — 收集所有命中规则的输出（类似包容网关）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_dmn_decision")
public class FlowDmnDecisionDO extends VersionableDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 决策表编码（全局唯一，如 risk_level_decision） */
    private String decisionCode;

    /** 决策表名称 */
    private String decisionName;

    /** 关联流程编码（可空，空表示通用决策表） */
    private String flowCode;

    /** 关联节点编码（可空，指定该决策表绑定的节点） */
    private String nodeCode;

    /** 击中策略: UNIQUE / FIRST / ANY / COLLECT */
    private String hitPolicy;

    /**
     * 输入定义 JSON — 描述输入列
     *
     * <p>格式: {@code [{"name":"amount","label":"金额","type":"number","expression":"amount"},
     * {"name":"deptType","label":"部门类型","type":"string","expression":"deptType"}]}
     */
    private String inputDefinitions;

    /**
     * 输出定义 JSON — 描述输出列
     *
     * <p>格式: {@code [{"name":"level","label":"审批层级","type":"string"},
     * {"name":"approver","label":"审批人","type":"string"}]}
     */
    private String outputDefinitions;

    /** 状态: DRAFT / PUBLISHED / DEPRECATED */
    private String status;

    /** 版本号（每次发布递增） */
    private Integer decisionVersion;

    /** 备注 */
    private String remark;

    /** 租户 ID */
    private String tenantId;

    /** 链路追踪 ID */
    private String providerTraceId;
}
