package com.njydsz.pmis.workflow.entity.integration;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 工作流定时器 DO
 *
 * <p>P1-2: 中间定时器 / 边界定时器调度实体。
 *
 * <p>设计要点：
 * <ul>
 *   <li>每创建一个定时器节点实例，插入一行 PENDING 记录</li>
 *   <li>cronjob 每 30s 扫描 fire_at &lt;= now() AND timer_status = 'PENDING'</li>
 *   <li>触发后更新 status = FIRED, fired_at = now()，并由 DefaultFlowAdvancer 推进流程</li>
 *   <li>被依附的 userTask 完成时关闭对应 BOUNDARY 定时器（CANCELLED）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_timer")
public class FlowTimerDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 流程实例 ID */
    private String instanceId;

    /** 流程定义 ID */
    private String definitionId;

    /** 流程编码 */
    private String flowCode;

    /** 节点编码 */
    private String nodeCode;

    /** 节点名称 */
    private String nodeName;

    /** 定时器类型：INTERMEDIATE 中间 / BOUNDARY 边界 */
    private String timerType;

    /** 边界定时器关联的 userTask ID（INTERMEDIATE 为 null） */
    private String boundaryTaskId;

    /** 到点时间（cronjob 按此扫描） */
    private LocalDateTime fireAt;

    /** CRON 表达式（循环定时器，可空） */
    private String cycle;

    /** 状态：PENDING / FIRED / CANCELLED */
    private String timerStatus;

    /** 实际触发时间 */
    private LocalDateTime firedAt;

    /** 取消原因（userTask 完成时关闭） */
    private String cancelReason;

    /** 链路追踪 ID */
    private String providerTraceId;
}
