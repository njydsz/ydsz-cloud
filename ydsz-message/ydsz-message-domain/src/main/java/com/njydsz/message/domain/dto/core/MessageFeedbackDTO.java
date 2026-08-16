package com.njydsz.message.domain.dto.core;

import lombok.Data;
import com.njydsz.common.safe.annotation.Xss;

/**
 * P1-4: 消息反馈请求 DTO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MessageFeedbackDTO {

    /** 消息 ID（关联 ydsz_msg_log.msg_id） */
    @Xss
    private String msgId;

    /** 站内通知 ID（可选） */
    @Xss
    private String notificationId;

    /** 用户 ID */
    @Xss
    private String userId;

    /** 评分: 1-5 分 */
    private Integer rating;

    /** 反馈类型 */
    @Xss
    private String feedbackType;

    /** 反馈内容 */
    private String content;
}
