package com.njydsz.workflow.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * P2-2: 流程评论创建 DTO
 *
 * <p>用于发表评论或回复。一级评论不传 {@code parentCommentId}；
 * 回复时传入 {@code parentCommentId}（必填）和 {@code replyToUserId}（可选）。
 *
 * @author ydsz-team
 * @since 1.7.0
 */
@Data
public class FlowCommentCreateDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 流程实例 ID（必填） */
    @NotBlank(message = "实例 ID 不能为空")
    private String instanceId;

    /** 任务 ID（可选，任务级评论时传入） */
    private String taskId;

    /** 节点编码（可选） */
    private String nodeCode;

    /** 评论内容（必填，最长 2000 字符） */
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论内容最长 2000 字符")
    private String content;

    /** 父评论 ID（可选，一级评论为 null；回复时必填） */
    private String parentCommentId;

    /** 被回复人 ID（可选，回复某条评论时标记） */
    private String replyToUserId;

    /** 被回复人姓名（可选） */
    private String replyToUserName;
}
