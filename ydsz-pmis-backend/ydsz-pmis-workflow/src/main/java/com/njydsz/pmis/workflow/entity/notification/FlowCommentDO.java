package com.njydsz.pmis.workflow.entity.notification;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * P2-2: 流程评论 DO
 *
 * <p>对标钉钉/飞书审批评论区。独立于 {@link FlowAuditLogDO}（审计日志是操作轨迹、不可变），
 * 评论是讨论（可回复、可删除），关注点正交。
 *
 * <p>支持多级回复：
 * <ul>
 *   <li>一级评论：{@code parentCommentId = null}</li>
 *   <li>二级及以下回复：{@code parentCommentId} 指向父评论 ID，
 *       {@code replyToUserId} 标记被回复人（同一父评论下可回复不同人）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_flow_comment")
public class FlowCommentDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 关联流程实例 ID */
    private String instanceId;

    /** 关联任务 ID（实例级评论可为空） */
    private String taskId;

    /** 关联节点编码 */
    private String nodeCode;

    /** 评论人 ID */
    private String userId;

    /** 评论人姓名（冗余） */
    private String userName;

    /** 评论内容 */
    private String content;

    /** 评论类型：COMMENT / QUESTION / REPLY（默认 COMMENT） */
    private String type;

    /** 父评论 ID（一级评论为 null） */
    private String parentCommentId;

    /** 被回复人 ID（回复某条评论时标记，一级评论为 null） */
    private String replyToUserId;

    /** 被回复人姓名（冗余） */
    private String replyToUserName;

    /** 链路追踪 ID */
    private String providerTraceId;
}
