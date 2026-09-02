package com.njydsz.workflow.infra.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 流程抄送规则实体
 *
 * <p>对应数据库表 {@code ydsz_flow_cc_rule}，P0-3: 自动抄送规则配置， 区别于 {@link FlowCc}（具体抄送实例） —
 * 本表是「规则配置」，运行时按规则生成抄送记录。
 *
 * <p><b>典型场景：</b>
 *
 * <ul>
 *   <li>变更金额 &gt; 1万 → 自动抄送 CEO（{@code ruleType=USER, ruleTarget=ceoId}）
 *   <li>合同审批 → 自动抄送法务部所有成员（{@code ruleType=DEPT, ruleTarget=legal}）
 *   <li>项目立项 → 自动抄送发起人的部门负责人（{@code ruleType=SPEL, ruleTarget=${initiator.leaderId}}）
 * </ul>
 *
 * <p><b>规则类型（{@code ruleType}）：</b>
 *
 * <ul>
 *   <li>{@code USER}：指定用户（{@code ruleTarget} = userId）
 *   <li>{@code ROLE}：指定角色（{@code ruleTarget} = roleCode，展开为该角色下所有用户）
 *   <li>{@code DEPT}：指定部门（{@code ruleTarget} = deptId，展开为部门下所有用户）
 *   <li>{@code SPEL}：SpEL 表达式动态解析（{@code ruleTarget} = 表达式，结果为 userId 列表）
 * </ul>
 *
 * <p><b>触发时机：</b>由 {@code FlowCcRuleResolver} 在节点任务生成时调用， 匹配 {@code flowCode + nodeCode}
 * 规则并展开接收人，批量写入 {@link FlowCc}。
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>普通索引 {@code idx_flow_node}（{@code flow_code}, {@code node_code}）：按流程节点查询
 *   <li>普通索引 {@code idx_enabled}（{@code enabled}）：按启用状态筛选
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowCc 流程抄送（具体实例）
 * @see com.njydsz.workflow.server.resolver.FlowCcRuleResolver 抄送规则解析器
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_cc_rule")
public class FlowCcRule extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 流程编码（{@code NULL} = 所有流程生效） */
  private String flowCode;

  /** 节点编码（{@code NULL} = 该流程所有节点生效） */
  private String nodeCode;

  /** 规则类型：{@code USER} / {@code ROLE} / {@code DEPT} / {@code SPEL} */
  private String ruleType;

  /**
   * 规则目标（按 {@code ruleType} 解析：{@code USER} 传 userId / {@code ROLE} 传 roleCode / {@code DEPT} 传
   * deptId / {@code SPEL} 传表达式）
   */
  private String ruleTarget;

  /** 是否启用：{@code 0} 禁用 / {@code 1} 启用 */
  private Integer enabled;

  /** 链路追踪 ID */
  private String providerTraceId;
}
