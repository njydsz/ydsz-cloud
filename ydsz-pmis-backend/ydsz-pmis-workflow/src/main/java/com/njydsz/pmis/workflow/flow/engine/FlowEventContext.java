package com.njydsz.pmis.workflow.flow.engine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 流程事件上下文元数据
 *
 * <p>P2-37: 携带 operatorId/operatedAt/traceId/tenantId 等上下文信息，
 * 供监听器获取完整的事件元数据，对标用友 BPM / 钉钉审批的事件通知能力。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FlowEventContext {
    /** 流程实例 ID */
    private Long instanceId;
    /** 任务 ID */
    private Long taskId;
    /** 操作人 ID */
    private Long operatorId;
    /** 操作动作（PASS/REJECT/TERMINATE/SUSPEND 等） */
    private String action;
    /** 租户 ID */
    private String tenantId;
    /** 链路追踪 ID */
    private String traceId;
    /** 操作时间 */
    private LocalDateTime operatedAt;
}
