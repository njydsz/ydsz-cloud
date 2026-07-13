package com.njydsz.pmis.common.feign.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 通知发送 Feign DTO（跨模块传输专用）
 *
 * <p>与 system 模块的 NotificationSendDTO 字段对齐，
 * 避免 common 模块依赖 system 模块。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Data
public class NotificationFeignDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 通知标题 */
    private String title;
    /** 通知内容 */
    private String content;
    /** INFO/WARN/ERROR/URGENT */
    private String level;
    /** SYSTEM 系统 / WORKFLOW 流程 / ALERT 告警 / TO_DO 待办 通知分类 */
    private String category;
    /** 发送人 ID */
    private String senderId;
    /** 单接收 */
    private String receiverId;
    /** 批量接收 */
    private List<Long> receiverIds;
    /** 业务关联 */
    private String bizType;
    /** 业务单据 ID */
    private String bizId;
    /** 过期时间 */
    private LocalDateTime expiredAt;
    /** 是否同时发送邮件 */
    private Boolean emailEnabled;
    /** 接收人邮箱 */
    private String receiverEmail;
}
