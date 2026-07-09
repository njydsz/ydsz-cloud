package com.njydsz.pmis.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 站内信 DO（P2-4）
 *
 * <p>工作流模块本地站内信存储，作为外部通知中心服务的补充和降级方案。
 * 当通知中心 Feign 不可用时，站内信直接写入本地表，确保通知不丢失。
 *
 * <p>消息类型：
 * <ul>
 *   <li>TASK_CREATED — 新待办通知</li>
 *   <li>TASK_COMPLETED — 任务完成通知</li>
 *   <li>TASK_URGED — 催办通知</li>
 *   <li>INSTANCE_COMPLETED — 流程完成通知</li>
 *   <li>INSTANCE_REJECTED — 流程驳回通知</li>
 *   <li>CC — 抄送通知</li>
 *   <li>MENTION — @提及通知</li>
 *   <li>SLA_TIMEOUT — SLA 超时通知</li>
 *   <li>DIGEST — 聚合摘要通知</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.9.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_inbox")
public class FlowInboxDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 接收人 ID */
    private String receiverId;

    /** 接收人姓名 */
    private String receiverName;

    /** 消息类型 */
    private String messageType;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 流程实例 ID */
    private String instanceId;

    /** 任务 ID */
    private String taskId;

    /** 业务类型 */
    private String bizType;

    /** 通知级别（INFO/WARN/ERROR/URGENT） */
    private String level;

    /** 是否已读 */
    private Boolean readStatus;

    /** 已读时间 */
    private java.time.LocalDateTime readAt;

    /** 扩展参数 JSON */
    private String extra;

    /** 租户 ID */
    private String tenantId;
}
