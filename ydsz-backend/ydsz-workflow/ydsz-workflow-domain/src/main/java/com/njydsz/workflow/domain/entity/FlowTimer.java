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
 * 工作流定时器实体
 *
 * <p>对应数据库表 {@code ydsz_flow_timer}，P1-2: 中间定时器 / 边界定时器调度实体，
 * 对标 BPMN 2.0 中的 {@code IntermediateTimerEvent} / {@code BoundaryTimerEvent}。
 *
 * <p><b>核心设计要点：</b>
 * <ul>
 *   <li>每创建一个定时器节点实例，插入一行 {@code PENDING} 记录</li>
 *   <li>{@code cronjob} 每 30 秒扫描 {@code fire_at <= now() AND timer_status = 'PENDING'}</li>
 *   <li>触发后更新 {@code status = FIRED, fired_at = now()}，并由 {@code DefaultFlowAdvancer} 推进流程</li>
 *   <li>被依附的 userTask 完成时关闭对应 {@code BOUNDARY} 定时器（{@code CANCELLED}）</li>
 * </ul>
 *
 * <p><b>定时器类型（{@code timerType}）：</b>
 * <ul>
 *   <li>{@code INTERMEDIATE}：中间定时器，独立节点，到点后推进流程（延迟推进）</li>
 *   <li>{@code BOUNDARY}：边界定时器，依附在 userTask 上，到点后中断或触发分支</li>
 * </ul>
 *
 * <p><b>调度方式：</b>
 * <ul>
 *   <li><b>绝对时间</b>：{@code fireAt} 非空，{@code cycle} 为空，单次触发</li>
 *   <li><b>循环触发</b>：{@code cycle} 非空（{@code cron} 表达式），{@code fireAt} 标记首次触发</li>
 * </ul>
 *
 * <p><b>索引设计：</b>
 * <ul>
 *   <li>普通索引 {@code idx_fire_at}（{@code fire_at}）：扫描待触发定时器</li>
 *   <li>普通索引 {@code idx_instance}（{@code instance_id}）：实例定时器清单</li>
 *   <li>普通索引 {@code idx_status}（{@code timer_status}）：按状态查询</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowInstance 流程实例
 * @see com.njydsz.workflow.server.scheduler.FlowTimerScheduler 定时器调度器
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_flow_timer")
public class FlowTimer extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 流程实例 ID */
    private String instanceId;

    /** 流程定义 ID */
    private String definitionId;

    /** 流程编码（冗余） */
    private String flowCode;

    /** 节点编码 */
    private String nodeCode;

    /** 节点名称（冗余） */
    private String nodeName;

    /** 定时器类型：{@code INTERMEDIATE}（中间）/ {@code BOUNDARY}（边界） */
    private String timerType;

    /** 边界定时器关联的 userTask ID（{@code INTERMEDIATE} 为 {@code null}） */
    private String boundaryTaskId;

    /** 到点时间（cronjob 按此扫描） */
    private LocalDateTime fireAt;

    /** CRON 表达式（循环定时器，可空） */
    private String cycle;

    /** 状态：{@code PENDING} / {@code FIRED} / {@code CANCELLED} */
    private String timerStatus;

    /** 实际触发时间 */
    private LocalDateTime firedAt;

    /** 取消原因（userTask 完成时关闭） */
    private String cancelReason;

    /** 链路追踪 ID */
    private String providerTraceId;
}
