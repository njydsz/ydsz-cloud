package com.njydsz.message.domain.dto.core;

import com.njydsz.common.safe.annotation.Xss;


import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 站内通知发送 DTO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class NotificationSendDTO {

    /** 接收人 ID(单发) */
    @Xss
    private String receiverId;

    /** 接收人 ID 列表(群发) */
    private List<String> receiverIds;

    /** 通知标题 */
    @Xss
    private String title;

    /** 通知内容 */
    private String content;

    /** 通知级别: INFO/WARN/ERROR/URGENT */
    @Xss
    private String level;

    /** 通知分类 */
    @Xss
    private String category;

    /** 发送优先级 */
    @Xss
    private String priority;

    /** 发送人 ID(系统通知为 SYSTEM) */
    @Xss
    private String senderId;

    /** 业务类型 */
    @Xss
    private String bizType;

    /** 业务单据 ID */
    @Xss
    private String bizId;

    /** 聚合组 */
    @Xss
    private String messageGroup;

    /** 点击跳转 URL */
    @Xss
    private String actionUrl;

    /** 跳转按钮文案 */
    @Xss
    private String actionText;

    /** 通知图标标识 */
    @Xss
    private String icon;

    /** 扩展字段 JSON */
    @Xss
    private String extra;

    /** 来源模块 */
    @Xss
    private String sourceModule;

    /** 过期时间 */
    private LocalDateTime expiredAt;
}
