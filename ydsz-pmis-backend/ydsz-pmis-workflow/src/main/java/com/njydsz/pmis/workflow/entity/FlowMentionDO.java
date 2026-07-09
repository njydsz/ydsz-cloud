package com.njydsz.pmis.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 审批 @提及记录 DO（P2-3）
 *
 * <p>存储审批评论中的 @提及 记录，被提及的用户可收到通知并查看相关审批。
 *
 * @author ydsz-pmis-team
 * @since 1.9.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_mention")
public class FlowMentionDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 流程实例 ID */
    private String instanceId;

    /** 任务 ID（可空） */
    private String taskId;

    /** 提及人 ID */
    private String mentionedBy;

    /** 提及人姓名 */
    private String mentionedByName;

    /** 被提及用户 ID */
    private String mentionedUserId;

    /** 评论内容（含 @标记） */
    private String comment;

    /** 是否已读 */
    private Boolean readStatus;

    /** 已读时间 */
    private java.time.LocalDateTime readAt;

    /** 租户 ID */
    private String tenantId;
}
