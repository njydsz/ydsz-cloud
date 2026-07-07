package com.njydsz.pmis.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知发送 DTO
 *
 * <p>支持单接收 / 批量接收：
 * 单接收：传 receiverId
 * 批量：传 receiverIds
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "通知发送表单")
public class NotificationSendDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 通知标题 */
    @NotBlank
    private String title;

    /** 通知内容 */
    private String content;

    /** INFO/WARN/ERROR/URGENT */
    private String level = "INFO";

    /** SYSTEM/WORKFLOW/ALERT/TODO */
    private String category = "SYSTEM";

    /** 发送人 ID（系统通知为 null） */
    private String senderId;

    /** 单接收 */
    private String receiverId;

    /** 批量接收（优先于 receiverId） */
    private List<String> receiverIds;

    /** 业务关联 */
    private String bizType;

    /** 业务单据 ID */
    private String bizId;

    /** 过期时间（可选） */
    private LocalDateTime expiredAt;

    /** 是否同时发送邮件（仅对单接收人有效，需 receiverEmail 非空） */
    private Boolean emailEnabled = Boolean.FALSE;

    /** 接收人邮箱（emailEnabled=true 时必填，亦可由系统根据 receiverId 自动解析） */
    private String receiverEmail;
}
