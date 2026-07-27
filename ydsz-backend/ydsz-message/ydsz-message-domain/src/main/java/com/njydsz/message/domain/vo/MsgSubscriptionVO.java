package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 订阅关系视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MsgSubscriptionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String userId;
    private String topicCode;
    private String channel;
    private String status;
    private String roleScope;
    private String extra;
    private LocalDateTime unsubscribedAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
