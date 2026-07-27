package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * 消息用户反馈视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MsgFeedbackVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String msgId;
    private String notificationId;
    private String userId;
    private String channel;
    private String bizType;
    private Integer rating;
    private String feedbackType;
    private String content;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
