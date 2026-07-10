package com.njydsz.pmis.message.dto.core;


import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内通知发送 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class NotificationSendDTO {

    /** 接收人 ID(单发) */
    private String receiverId;

    /** 接收人 ID 列表(群发) */
    private List<String> receiverIds;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 通知级别: INFO/WARN/ERROR/URGENT */
    private String level;

    /** 通知分类 */
    private String category;

    /** 发送优先级 */
    private String priority;

    /** 发送人 ID(系统通知为 SYSTEM) */
    private String senderId;

    /** 业务类型 */
    private String bizType;

    /** 业务单据 ID */
    private String bizId;

    /** 聚合组 */
    private String messageGroup;

    /** 点击跳转 URL */
    private String actionUrl;

    /** 跳转按钮文案 */
    private String actionText;

    /** 通知图标标识 */
    private String icon;

    /** 扩展字段 JSON */
    private String extra;

    /** 来源模块 */
    private String sourceModule;

    /** 过期时间 */
    private LocalDateTime expiredAt;
}
