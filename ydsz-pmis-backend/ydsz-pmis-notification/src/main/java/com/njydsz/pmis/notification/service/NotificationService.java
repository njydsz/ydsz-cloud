package com.njydsz.pmis.notification.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.notification.dto.NotificationQueryDTO;
import com.njydsz.pmis.notification.dto.NotificationSendDTO;
import com.njydsz.pmis.notification.entity.NotificationDO;

import java.util.List;

/**
 * 通知服务
 */
public interface NotificationService {

    /**
     * 发送通知（支持单接收/批量）
     */
    int send(NotificationSendDTO dto);

    /**
     * 发送通知 + 邮件投递（仅单接收人）
     *
     * <p>若 emailEnabled=true 且 receiverEmail 非空，则在站内通知入库后
     * 通过 Feign 调用 message 服务走 EMAIL 通道。
     *
     * @return EmailDispatchResult 邮件投递结果（成功/失败/降级）
     */
    EmailDispatchResult sendWithEmail(NotificationSendDTO dto);

    /**
     * 收件箱分页
     */
    Page<NotificationDO> inbox(Long userId, NotificationQueryDTO query);

    /**
     * 未读数量
     */
    long countUnread(Long userId);

    /**
     * 标记已读
     */
    boolean markRead(Long userId, Long id);

    /**
     * 全部标记已读
     */
    int markAllRead(Long userId);

    /**
     * 删除（逻辑）
     */
    void delete(Long userId, List<Long> ids);

    /**
     * 邮件投递结果
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    class EmailDispatchResult {
        /** 站内通知插入条数 */
        private int inboxCount;
        /** 邮件是否成功 */
        private boolean emailSent;
        /** 邮件供应商侧追踪 ID */
        private String providerTraceId;
        /** 邮件错误信息（成功时为 null） */
        private String emailError;
    }
}
