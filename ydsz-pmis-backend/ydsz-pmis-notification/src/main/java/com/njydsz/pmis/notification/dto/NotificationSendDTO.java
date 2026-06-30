package com.njydsz.pmis.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知发送 DTO
 *
 * <p>支持单接收 / 批量接收：
 * 单接收：传 receiverId
 * 批量：传 receiverIds
 */
@Data
@Schema(description = "通知发送表单")
public class NotificationSendDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank
    private String title;

    private String content;

    /** INFO/WARN/ERROR/URGENT */
    private String level = "INFO";

    /** SYSTEM/WORKFLOW/ALERT/TODO */
    private String category = "SYSTEM";

    private Long senderId;

    /** 单接收 */
    private Long receiverId;

    /** 批量接收（优先于 receiverId） */
    private java.util.List<Long> receiverIds;

    /** 业务关联 */
    private String bizType;
    private String bizId;

    /** 过期时间（可选） */
    private LocalDateTime expiredAt;
}
