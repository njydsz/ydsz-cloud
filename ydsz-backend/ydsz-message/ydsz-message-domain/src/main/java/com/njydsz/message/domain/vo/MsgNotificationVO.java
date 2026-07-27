package com.njydsz.message.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 站内通知视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class MsgNotificationVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String title;
    private String content;
    private String level;
    private String category;
    private String priority;
    private String senderId;
    private String receiverId;
    private String bizType;
    private String bizId;
    private String messageGroup;
    private String batchId;
    private String actionUrl;
    private String actionText;
    private String icon;
    private String extra;
    private String sourceModule;
    private Integer readStatus;
    private LocalDateTime readTime;
    private String recallStatus;
    private LocalDateTime recallAt;
    private LocalDateTime expiredAt;
    private String mentionUserIds;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
