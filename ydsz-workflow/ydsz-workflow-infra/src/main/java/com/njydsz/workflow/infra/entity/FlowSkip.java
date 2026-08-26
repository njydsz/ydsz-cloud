package com.njydsz.workflow.infra.entity;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 节点跳转关联实体
 *
 * <p>对应数据库表 {@code ydsz_flow_skip}，描述流程图中节点之间的<b>有向边</b>（BPMN 2.0
 * 中的 sequenceFlow）。
 *
 * <p><b>核心概念：</b>流程图本质上是有向图，本表存储「from node → to node」的连接关系。 每条 skip 记录对应设计器中的一条线（连线）。
 *
 * <p><b>跳转条件：</b>{@code skipCondition} 支持 SpEL 表达式（如 {@code ${amount > 10000}}），
 * 引擎执行时根据流程变量求值，匹配首个满足条件的 skip 推进。
 *
 * <p><b>坐标信息：</b>
 *
 * <ul>
 *   <li>{@code coordinate}：当前节点端点坐标（设计器渲染起点）
 *   <li>{@code coordinateNext}：下一节点端点坐标（设计器渲染终点）
 * </ul>
 *
 * <p><b>扩展字段（{@code ext}）：</b>存储 BPMN 解析后的派生信息， 如 {@code sourceRef}（源节点 ID）、{@code targetRef}（目标节点
 * ID）、 {@code sequenceFlowId}（BPMN 全局唯一 ID）等，便于与原始 BPMN XML 反查。
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>普通索引 {@code idx_definition}（{@code definition_id}）：按流程定义查询
 *   <li>普通索引 {@code idx_flow_code}（{@code flow_code}）：按流程编码查询
 *   <li>A9: {@code idx_source_node}（{@code source_node_code}）：源节点反查（REJECT 回退场景）
 * </ul>
 *
 * <p><b>DDL 变更：</b>新增 {@code source_node_code VARCHAR(64)} 列（对应 {@code
 * ydsz_flow_skip.source_node_code}），需在环境初始化脚本中添加：
 * {@code ALTER TABLE ydsz_flow_skip ADD COLUMN source_node_code VARCHAR(64) DEFAULT NULL
 * COMMENT '源节点编码';} 并建索引 {@code idx_source_node (source_node_code)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowNode 流程节点
 * @see FlowDefinition 流程定义
 * @see com.njydsz.workflow.domain.enums.FlowSkipType 跳转类型枚举
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_skip")
public class FlowSkip extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 所属流程定义 ID */
  private String definitionId;

  /** 流程编码（冗余字段） */
  private String flowCode;

  /** 跳转名称（线上标签，如「同意」「金额 > 1万」） */
  private String skipName;

  /** 跳转类型（{@link com.njydsz.workflow.domain.enums.FlowSkipType}.name） */
  private String skipType;

  /** 设计器坐标 JSON（当前节点端点） */
  private String coordinate;

  /** 跳转条件表达式（SpEL 或 {@code ${var}} 语法） */
  private String skipCondition;

  /** 下一节点编码 */
  private String nextNodeCode;

  /** A9: 源节点编码（独立列，替代 ext JSON 中的 sourceRef，便于索引与联查） */
  private String sourceNodeCode;

  /** 下一节点类型（{@link com.njydsz.workflow.domain.enums.FlowNodeType}.code） */
  private Integer nextNodeType;

  /** 下一节点坐标 */
  private String coordinateNext;

  /** 跳转路由集合 JSON */
  private String skipList;

  /** 扩展字段 JSON（存储 {@code sourceRef} / {@code sequenceFlowId} 等 BPMN 派生信息） */
  private String ext;

  /** 链路追踪 ID */
  private String providerTraceId;
}
