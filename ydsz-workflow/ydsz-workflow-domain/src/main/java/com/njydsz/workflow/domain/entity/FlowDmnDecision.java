package com.njydsz.workflow.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * P0-1: DMN 决策表实体
 *
 * <p>对应数据库表 {@code ydsz_flow_dmn_decision}，对标 BPMN 2.0 DMN（Decision Model and Notation）规范中的决策表概念。
 * 每条决策表包含若干输入列（{@code inputExpressions}）和输出列（{@code outputLabels}）， 以及一组规则行（{@link FlowDmnRule}），按
 * {@code hitPolicy} 匹配规则并输出结果。
 *
 * <p><b>核心使用场景：</b>
 *
 * <ul>
 *   <li>排他网关/包容网关的路由条件由 DMN 决策表驱动，替代硬编码 SpEL 表达式
 *   <li>审批人推荐规则（金额区间 → 审批层级）
 *   <li>SLA 超时阈值动态决策（业务类型 + 金额 → 超时分钟数）
 * </ul>
 *
 * <p><b>击中策略（{@code hitPolicy}）：</b>
 *
 * <ul>
 *   <li>{@code UNIQUE}：仅一条规则命中（类似排他网关）
 *   <li>{@code FIRST}：按顺序取第一条命中（默认）
 *   <li>{@code ANY}：多条命中时输出必须相同
 *   <li>{@code COLLECT}：收集所有命中规则的输出（类似包容网关）
 * </ul>
 *
 * <p><b>状态机（{@code status}）：</b>{@code DRAFT} → {@code PUBLISHED} → {@code DEPRECATED}， 同一 {@code
 * decisionCode} 同一时间仅允许一个 {@code PUBLISHED} 版本生效。
 *
 * <p><b>输入/输出定义 JSON 格式：</b>
 *
 * <pre>
 *   inputs:  [{"name":"amount","label":"金额","type":"number","expression":"amount"}, ...]
 *   outputs: [{"name":"level","label":"审批层级","type":"string"}, ...]
 * </pre>
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>唯一索引 {@code uk_decision_code_version}（{@code decision_code}, {@code decision_version}）
 *   <li>普通索引 {@code idx_flow_node}（{@code flow_code}, {@code node_code}）：按流程节点查询
 *   <li>普通索引 {@code idx_status}（{@code status}）：按状态筛选
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowDmnRule 决策表规则行
 * @see com.njydsz.workflow.server.engine.DmnDecisionEngine DMN 决策引擎
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_dmn_decision")
public class FlowDmnDecision extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 决策表编码（全局唯一，如 {@code risk_level_decision}） */
  private String decisionCode;

  /** 决策表名称 */
  private String decisionName;

  /** 关联流程编码（{@code null} 表示通用决策表） */
  private String flowCode;

  /** 关联节点编码（{@code null}，指定该决策表绑定的节点） */
  private String nodeCode;

  /** 击中策略：{@code UNIQUE} / {@code FIRST} / {@code ANY} / {@code COLLECT} */
  private String hitPolicy;

  /**
   * 输入定义 JSON，描述输入列。
   *
   * <p>格式：{@code [{"name":"amount","label":"金额","type":"number","expression":"amount"}, ...]}
   */
  private String inputDefinitions;

  /**
   * 输出定义 JSON，描述输出列。
   *
   * <p>格式：{@code [{"name":"level","label":"审批层级","type":"string"}, ...]}
   */
  private String outputDefinitions;

  /** 状态：{@code DRAFT} / {@code PUBLISHED} / {@code DEPRECATED} */
  private String status;

  /** 版本号（每次发布递增，同 {@code decisionCode} 下唯一） */
  private Integer decisionVersion;

  /** 备注（说明决策表的业务背景） */
  private String remark;

  /** 链路追踪 ID */
  private String providerTraceId;
}
