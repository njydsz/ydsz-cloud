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
 * 工作流事件订阅实体
 *
 * <p>对应数据库表 {@code ydsz_flow_event_subscription}，P0-1: BPMN 错误事件 / 消息事件运行时支持， 对标 BPMN 2.0 中的 {@code
 * intermediateCatchEvent} / {@code boundaryEvent}。
 *
 * <p><b>核心机制：</b> 当流程推进到事件捕获节点（{@code intermediateCatchEvent} / {@code boundaryEvent}）时， 插入一行
 * {@code WAITING} 记录，流程进入等待状态。外部系统通过 {@code correlateMessage} / {@code throwError} API 触发事件， 匹配后标记
 * {@code COMPLETED} 并推进流程。
 *
 * <p><b>事件类型（{@code eventType}）：</b>
 *
 * <ul>
 *   <li>{@code MESSAGE}：消息事件（{@code messageRef}，跨流程传递消息）
 *   <li>{@code ERROR}：错误事件（{@code errorRef}，异常抛出触发）
 *   <li>{@code SIGNAL}：信号事件（{@code signalRef}，广播式触发）
 * </ul>
 *
 * <p><b>匹配机制：</b>
 *
 * <ul>
 *   <li>精确匹配：{@code eventRef + correlationKey}（业务单据 ID、流程实例 ID 等）
 *   <li>广播匹配：{@code SIGNAL} 类型可省略 {@code correlationKey}，匹配所有同 {@code eventRef} 的订阅
 * </ul>
 *
 * <p><b>触发来源（{@code triggerSource}）：</b>{@code API}（外部系统调用）/ {@code SERVICE_TASK}（服务任务抛出）/ {@code
 * BOUNDARY}（边界事件超时触发）。
 *
 * <p><b>索引设计：</b>
 *
 * <ul>
 *   <li>普通索引 {@code idx_instance}（{@code instance_id}）：实例事件订阅清单
 *   <li>普通索引 {@code idx_event_ref}（{@code event_ref}）：事件引用查询
 *   <li>普通索引 {@code idx_status}（{@code subscription_status}）：按状态筛选
 *   <li>普通索引 {@code idx_correlation}（{@code correlation_key}）：业务级匹配
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowInstance 流程实例
 * @see com.njydsz.workflow.server.service.FlowEventService 事件服务
 */
@Data
@SuperBuilder
@SuppressWarnings("unchecked")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_event_subscription")
public class FlowEventSubscription extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 流程实例 ID */
  private String instanceId;

  /** 流程定义 ID */
  private String definitionId;

  /** 流程编码（冗余） */
  private String flowCode;

  /** 节点编码（事件捕获节点） */
  private String nodeCode;

  /** 节点名称（冗余） */
  private String nodeName;

  /** 事件类型：{@code MESSAGE} / {@code ERROR} / {@code SIGNAL} */
  private String eventType;

  /** 事件引用标识（{@code messageRef} / {@code errorRef} / {@code signalRef}） */
  private String eventRef;

  /** 消息关联键（业务级匹配，可空） */
  private String correlationKey;

  /** 边界事件关联的 userTask ID（中间事件为 {@code null}） */
  private String boundaryTaskId;

  /** 订阅状态：{@code WAITING} / {@code COMPLETED} / {@code CANCELLED} */
  private String subscriptionStatus;

  /** 触发时携带的业务数据 JSON */
  private String payload;

  /** 实际触发时间 */
  private LocalDateTime triggeredAt;

  /** 触发来源（{@code API} / {@code SERVICE_TASK} / {@code BOUNDARY}） */
  private String triggerSource;

  /** 取消原因 */
  private String cancelReason;

  /** 链路追踪 ID */
  private String providerTraceId;
}
