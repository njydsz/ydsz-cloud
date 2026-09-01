package com.njydsz.workflow.infra.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 流程自动触发规则实体
 *
 * <p>对应数据库表 {@code ydsz_flow_auto_trigger}，实现「流程触发流程」的自动化能力。 当源流程实例完成（终态）时，自动检查本表规则，按权重顺序执行条件匹配，
 * 满足条件的规则自动启动目标流程。
 *
 * <p><b>核心使用场景：</b>
 *
 * <ul>
 *   <li>「立项审批」通过 → 自动触发「合同审批」
 *   <li>「合同审批」通过 → 自动触发「财务付款审批」
 *   <li>「项目验收」通过 → 自动触发「项目结项审批」
 * </ul>
 *
 * <p><b>触发时机：</b>由 {@code FlowAutoTriggerListener} 监听 {@link FlowInstance} 终态事件， 按 {@code
 * sortOrder} 升序依次匹配，匹配成功则调用 {@code YdszWorkflowFacade.start} 启动目标流程。
 *
 * <p><b>条件表达式（{@code conditionExpression}）：</b>Aviator 语法，可访问源实例变量， 如 {@code amount > 100000 &&
 * projectType == "INFRA"}，为空则无条件触发。
 *
 * <p><b>变量传递：</b>目标流程默认继承源实例的 {@code variable} JSON，可通过 {@code variableMapping} 配置字段映射（暂存于 {@code
 * ext}，未来扩展）。
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>普通索引 {@code idx_source}（{@code source_flow_code}）：源流程规则查询
 *   <li>普通索引 {@code idx_target}（{@code target_flow_code}）：目标流程反查
 *   <li>普通索引 {@code idx_enabled}（{@code enabled}）：按启用状态筛选
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowInstance 流程实例
 * @see com.njydsz.workflow.server.listener.FlowAutoTriggerListener 自动触发监听器
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_auto_trigger")
public class FlowAutoTrigger extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 源流程编码（触发方） */
  private String sourceFlowCode;

  /** 目标流程编码（被触发方） */
  private String targetFlowCode;

  /** 条件表达式（Aviator 语法，为空则无条件触发） */
  @TableField("condition_expression")
  private String conditionExpression;

  /** 规则描述（说明触发场景与业务背景） */
  private String description;

  /** 是否启用：{@code 0} 禁用 / {@code 1} 启用 */
  private Integer enabled;

  /** 排序权重（升序执行） */
  @TableField("sort_order")
  private Integer sortOrder;
}
