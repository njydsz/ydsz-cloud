package com.njydsz.workflow.domain.entity;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 工作流事件订阅 DO
 *
 * <p>P0-1: BPMN 错误事件 / 消息事件运行时支持。
 *
 * <p>当流程推进到事件捕获节点（intermediateCatchEvent / boundaryEvent）时，
 * 插入一行 WAITING 记录，流程进入等待状态。外部系统通过 correlateMessage /
 * throwError API 触发事件，匹配后标记 COMPLETED 并推进流程。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_event_subscription")
public class FlowEventSubscriptionDO extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private String tenantId;

    /** 流程实例 ID */
    private String instanceId;

    /** 流程定义 ID */
    private String definitionId;

    /** 流程编码 */
    private String flowCode;

    /** 节点编码（事件捕获节点） */
    private String nodeCode;

    /** 节点名称 */
    private String nodeName;

    /** 事件类型：MESSAGE / ERROR / SIGNAL */
    private String eventType;

    /** 事件引用标识（messageRef / errorRef / signalRef） */
    private String eventRef;

    /** 消息关联键（业务级匹配，可空） */
    private String correlationKey;

    /** 边界事件关联的 userTask ID（中间事件为 null） */
    private String boundaryTaskId;

    /** 订阅状态：WAITING / COMPLETED / CANCELLED */
    private String subscriptionStatus;

    /** 触发时携带的业务数据 JSON */
    private String payload;

    /** 实际触发时间 */
    private LocalDateTime triggeredAt;

    /** 触发来源（API / SERVICE_TASK / BOUNDARY） */
    private String triggerSource;

    /** 取消原因 */
    private String cancelReason;

    /** 链路追踪 ID */
    private String providerTraceId;
}
