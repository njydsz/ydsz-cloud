package com.njydsz.workflow.domain.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;
import com.njydsz.common.json.annotation.JsonView;
import com.njydsz.workflow.domain.vo.FlowViewsVO;

/**
 * 流程定义实体
 *
 * <p>对应数据库表 {@code ydsz_flow_definition}，存储流程模板元数据。
 * 流程定义是工作流引擎的「模板层」，描述一类流程的结构（节点、流转、审批人）， 每次发起流程都基于某版本的流程定义生成流程实例。
 *
 * <p><b>核心字段：</b>
 *
 * <ul>
 *   <li>{@code flowCode}：流程编码（业务语义，如 {@code project_initiation} / {@code contract_change}）
 *   <li>{@code flowVersion}：流程版本号（{@code v1} / {@code v2}），支持版本灰度发布
 *   <li>{@code modelValue}：设计器模型（{@code CLASSICS} 经典 / {@code MIMIC} 仿钉钉）
 *   <li>{@code formCustom}：审批表单是否自定义（{@code Y/N}）
 *   <li>{@code activityStatus}：激活状态（{@code 0} 挂起 / {@code 1} 激活）
 *   <li>{@code isPublish}：发布状态（{@code 0} 未发布 / {@code 1} 已发布 / {@code 9} 失效）
 *   <li>{@code listenerType} / {@code listenerPath}：流程监听器配置（Spring Bean 路径）
 *   <li>{@code ext}：扩展字段 JSON（业务侧自定义元数据）
 * </ul>
 *
 * <p><b>P3-1 灰度发布：</b>
 *
 * <ul>
 *   <li>{@code canaryPercent}：灰度比例（{@code 0-100}，{@code 0}=全量稳定版，{@code 100}=全量灰度版）
 *   <li>{@code canaryStatus}：灰度状态（{@code NONE} / {@code CANARYING} / {@code PROMOTED} / {@code
 *       ROLLED_BACK}）
 *   <li>{@code canaryStrategy}：灰度切流策略（{@code USER_HASH} / {@code RANDOM} / {@code WHITELIST}）
 *   <li>{@code canaryRolloutLog}：灰度发布历史（JSON 数组）
 * </ul>
 *
 * <p><b>P2-4 协同编辑锁定：</b>
 *
 * <ul>
 *   <li>{@code lockedBy}：当前持锁人 ID（{@code NULL}=未锁定）
 *   <li>{@code lockedAt}：加锁时间（超过 {@code workflow.designer.lock-timeout-minutes} 默认 30 分钟可强制抢占）
 * </ul>
 *
 * <p><b>索引设计：</b>唯一索引 {@code uk_flow_code_version}（{@code flow_code, flow_version}）， 普通索引 {@code
 * idx_category}（{@code category}）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowInstance 流程实例
 * @see FlowNode 流程节点
 * @see com.njydsz.workflow.server.service.FlowDefinitionService 流程定义 Service
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_definition")
public class FlowDefinition extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * 流程编码（业务语义）。
   *
   * <p>建议使用 snake_case 命名（如 {@code project_initiation} / {@code contract_change}），
   * 同一编码可对应多个版本（{@code flowVersion}），由 {@code uk_flow_code_version} 唯一索引保证。
   */
  @JsonView(FlowViewsVO.Summary.class)
  private String flowCode;

  /** 流程名称（前端展示） */
  @JsonView(FlowViewsVO.Summary.class)
  private String flowName;

  /** 流程类别（用于分类筛选，如「项目类」「合同类」「人事类」） */
  @JsonView(FlowViewsVO.Summary.class)
  private String category;

  /**
   * 流程版本号。
   *
   * <p>建议使用 {@code v1} / {@code v2} 格式或语义版本号（{@code 26.09.01} / {@code 1.1.0}）。 同一 {@code flowCode}
   * 下的不同版本独立发布，支持灰度切换。
   */
  @TableField("flow_version")
  @JsonView(FlowViewsVO.Summary.class)
  private String flowVersion;

  /**
   * 设计器模型。
   *
   * <p>取值：{@code CLASSICS} 经典（横向流转图）/ {@code MIMIC} 仿钉钉（纵向审批面板）。
   */
  @JsonView(FlowViewsVO.Detail.class)
  private String modelValue;

  /**
   * 审批表单是否自定义。
   *
   * <p>{@code Y}=使用自定义表单（{@code formPath} 指向 Vue 组件路径）， {@code N}=使用系统内置表单（基于 {@code
   * ydsz_form_definition}）。
   */
  @JsonView(FlowViewsVO.Detail.class)
  private String formCustom;

  /**
   * 审批表单路径。
   *
   * <p>当 {@code formCustom=Y} 时，指向 Vue 组件路径（如 {@code workflow/forms/ProjectInitiationForm.vue}）； 当
   * {@code formCustom=N} 时，存储表单定义 ID。
   */
  @JsonView(FlowViewsVO.Detail.class)
  private String formPath;

  /**
   * 激活状态。
   *
   * <p>{@code 0}=挂起（不可发起新实例，但已运行实例不受影响）， {@code 1}=激活（正常接收新实例）。
   */
  @JsonView(FlowViewsVO.Summary.class)
  private Integer activityStatus;

  /**
   * 发布状态。
   *
   * <p>{@code 0}=未发布（草稿态，设计师可见但不可发起）， {@code 1}=已发布（可发起流程实例）， {@code 9}=失效（已废弃，不可再发起，老实例仍可继续运行）。
   */
  @TableField("is_publish")
  @JsonView(FlowViewsVO.Summary.class)
  private Integer isPublish;

  /**
   * 监听器类型。
   *
   * <p>取值：{@code NONE}=无监听器，{@code GLOBAL}=全局监听器，{@code FLOW}=流程级监听器。 监听器由 {@code FlowListener}
   * 接口实现，由引擎在事件点回调。
   */
  @JsonView(FlowViewsVO.Detail.class)
  private String listenerType;

  /**
   * 监听器 Spring Bean 路径。
   *
   * <p>如 {@code projectFlowListener}，由 Spring 容器在流程启动时通过 Bean 名称查找。
   */
  @JsonView(FlowViewsVO.Detail.class)
  private String listenerPath;

  /**
   * 扩展字段 JSON。
   *
   * <p>业务侧自定义元数据（如超时配置、抄送规则、审批人默认值等），以 JSON 字符串存储。
   */
  @JsonView(FlowViewsVO.Detail.class)
  private String ext;

  /** 流程描述（说明流程的业务用途与适用场景） */
  @JsonView(FlowViewsVO.Detail.class)
  private String description;

  /**
   * 链路追踪 ID。
   *
   * <p>由上游业务系统传入（如 {@code project_initiation} 立项创建时），便于跨系统链路追踪。 与 {@code ydsz_provider_trace_id}
   * 全链路追踪协议对齐。
   */
  @JsonView(FlowViewsVO.Detail.class)
  private String providerTraceId;

  // ============================== P3-1: 灰度发布 ==============================

  /**
   * 灰度比例 0-100。
   *
   * <ul>
   *   <li>0 — 全量走稳定版（不灰度）
   *   <li>100 — 全量走灰度版（已完成全量发布）
   *   <li>1-99 — 按 canaryStrategy 切流
   * </ul>
   */
  private Integer canaryPercent;

  /**
   * 灰度状态：
   *
   * <ul>
   *   <li>NONE — 未启用灰度
   *   <li>CANARYING — 灰度中
   *   <li>PROMOTED — 已全量（灰度版晋升为稳定版）
   *   <li>ROLLED_BACK — 已回滚
   * </ul>
   */
  private String canaryStatus;

  /**
   * 灰度切流策略：
   *
   * <ul>
   *   <li>USER_HASH — 按发起人 ID 取模，相同发起人始终走同一版本（一致性）
   *   <li>RANDOM — 每次随机
   *   <li>WHITELIST — 强制白名单内走灰度（其他走稳定版）
   * </ul>
   */
  private String canaryStrategy;

  /**
   * 灰度发布历史，JSON 数组。
   *
   * <pre>
   *   [{operatorId,operatorName,fromPercent,toPercent,operateAt,note}]
   * </pre>
   */
  private String canaryRolloutLog;

  /** 乐观锁版本号由 MpBaseEntity 继承，无需在此声明 */

  // ============================== P2-4: 设计器协同编辑锁定 ==============================

  /**
   * P2-4: 当前持锁人 ID（设计器协同编辑锁定，NULL=未锁定）。
   *
   * <p>对标钉钉/飞书流程设计器"编辑锁定"机制，避免多人同时编辑导致冲突。
   */
  private String lockedBy;

  /**
   * P2-4: 加锁时间（用于超时自动释放判断）。
   *
   * <p>超过 {@code workflow.designer.lock-timeout-minutes}（默认 30 分钟）后， 其他用户可强制抢占锁。
   */
  private LocalDateTime lockedAt;
}
