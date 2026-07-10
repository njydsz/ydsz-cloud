package com.njydsz.pmis.message.dto.core;

import lombok.Data;

/**
 * P1-4: 消息反馈请求 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Data
public class MessageFeedbackDTO {

    /** 消息 ID（关联 pmis_msg_log.msg_id） */
    private String msgId;

    /** 站内通知 ID（可选） */
    private String notificationId;

    /** 用户 ID */
    private String userId;

    /** 评分: 1-5 分 */
    private Integer rating;

    /** 反馈类型 */
    private String feedbackType;

    /** 反馈内容 */
    private String content;
}
