package com.njydsz.workflow.infra.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 流程抄送实体
 *
 * <p>对应数据库表 {@code ydsz_flow_cc}，P0-3: 抄送中心（对标钉钉/飞书的"抄送我的"独立 Tab）。 CC 节点触发或人工抄送都会写入本表，区别于 {@link
 * FlowRunTask}（无需办理动作）。
 *
 * <p><b>抄送来源：</b>
 *
 * <ul>
 *   <li>{@code CC_NODE}：设计器中配置的抄送节点（{@code nodeType=5}）自动触发
 *   <li>{@code MANUAL_CC}：审批人「抄送」按钮人工触发
 *   <li>{@code AUTO_CC}：系统规则触发（如发起人抄送、跨部门审批抄送）
 * </ul>
 *
 * <p><b>与待办的区别：</b>
 *
 * <ul>
 *   <li><b>待办（{@link FlowRunTask}）</b>：需要办理动作（同意/拒绝），流程阻塞等待
 *   <li><b>抄送（{@code FlowCc}）</b>：仅通知，不阻塞流程，接收人可读可不读
 * </ul>
 *
 * <p><b>已读机制：</b>{@code readStatus} 由前端调用 {@code /api/v1/flow/cc/{id}/read} 标记为 {@code READ}， 同步记录
 * {@code readAt}。「抄送我的」列表默认按 {@code readStatus=UNREAD} 优先排序。
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>普通索引 {@code idx_cc_user}（{@code cc_user_id}）：「抄送我的」核心索引
 *   <li>普通索引 {@code idx_instance}（{@code instance_id}）：流程抄送时间线
 *   <li>普通索引 {@code idx_business}（{@code business_key}）：业务侧抄送关联
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowRunTask 流程待办（需要办理动作）
 * @see com.njydsz.workflow.server.service.FlowCcService 抄送服务
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_cc")
public class FlowCc extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 流程实例 ID */
  private String instanceId;

  /** 触发的任务 ID（CC 节点任务，可空） */
  private String taskId;

  /** 触发抄送的节点编码 */
  private String nodeCode;

  /** 节点名称（冗余） */
  private String nodeName;

  /** 流程编码 */
  private String flowCode;

  /** 流程名称（冗余） */
  private String flowName;

  /** 业务单据 ID */
  private String businessKey;

  /** 抄送接收人 ID */
  private String ccUserId;

  /** 抄送接收人姓名（冗余） */
  private String ccUserName;

  /** 抄送类型：{@code CC_NODE}（CC 节点）/ {@code MANUAL_CC}（人工抄送）/ {@code AUTO_CC}（系统规则） */
  private String ccType;

  /** 触发抄送的人 ID（CC 节点时为发起人/审批人，{@code AUTO_CC} 时为 {@code SYSTEM}） */
  private String triggerUserId;

  /** 触发抄送的人姓名（冗余） */
  private String triggerUserName;

  /** 抄送标题 */
  private String title;

  /** 抄送内容/意见（人工抄送时填写） */
  private String content;

  /** 已读状态：{@code UNREAD} / {@code READ} */
  private String readStatus;

  /** 已读时间（标记 {@code READ} 时由后端填充） */
  private LocalDateTime readAt;

  /** 链路追踪 ID */
  private String providerTraceId;
}
