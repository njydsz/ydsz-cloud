package com.njydsz.pmis.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 工作流通知外发箱实体（P2-1）
 *
 * <p>本地消息表（Outbox Pattern），保证业务事务与通知投递的最终一致性。
 * 主事务内 INSERT 本表（status=PENDING），事务提交后由扫描任务异步投递到通知中心。
 * 对应表 {@code pmis_flow_notify_outbox}，由 {@link com.njydsz.pmis.workflow.scheduler.NotifyOutboxScanner}
 * 定时扫描，调用 NotificationClient / IM / 邮件 / 短信渠道完成 at-least-once 投递。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_notify_outbox")
public class FlowNotifyOutboxDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 事件类型: TASK_CREATED / TASK_COMPLETED / INSTANCE_TERMINATED 等 */
    private String eventType;

    /** 业务类型: WORKFLOW_TASK / WORKFLOW_INSTANCE / WORKFLOW_CC */
    private String bizType;

    /** 业务 ID（taskId / instanceId） */
    private String bizId;

    /** 流程实例 ID（便于按实例查询） */
    private String instanceId;

    /** 任务 ID（便于按任务查询） */
    private String taskId;

    /** JSON 载荷，由接收方解析 */
    private String payload;

    /** 投递通道: IN_APP / IM / EMAIL / SMS（逗号分隔，空表示按 event_type 默认） */
    private String targetChannels;

    /** 接收用户 ID 列表（逗号分隔，空表示由 payload 自行决定） */
    private String targetUserIds;

    /** 投递状态: PENDING / SENT / DEAD */
    private String status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 最大重试次数（默认 5） */
    private Integer maxRetries;

    /** 下次重试时间（指数退避） */
    private LocalDateTime nextRetryAt;

    /** 实际投递成功时间 */
    private LocalDateTime sentAt;

    /** 最近一次失败原因 */
    private String errorMsg;

    /** 链路追踪 ID */
    private String providerTraceId;
}
