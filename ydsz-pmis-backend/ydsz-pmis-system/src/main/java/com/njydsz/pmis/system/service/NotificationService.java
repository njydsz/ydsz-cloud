package com.njydsz.pmis.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.system.dto.NotificationQueryDTO;
import com.njydsz.pmis.system.dto.NotificationSendDTO;
import com.njydsz.pmis.system.entity.NotificationDO;

import java.util.List;

/**
 * 通知服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface NotificationService {

    /**
     * 发送通知（支持单接收/批量）
     *
     * @param dto 通知发送表单
     * @return 实际插入条数
     */
    int send(NotificationSendDTO dto);

    /**
     * 发送通知 + 邮件投递（仅单接收人）
     *
     * <p>若 emailEnabled=true 且 receiverEmail 非空，则在站内通知入库后
     * 通过 Feign 调用 message 服务走 EMAIL 通道。
     *
     * @param dto 通知发送表单（仅支持单接收人）
     * @return EmailDispatchResult 邮件投递结果（成功/失败/降级）
     */
    EmailDispatchResult sendWithEmail(NotificationSendDTO dto);

    /**
     * 收件箱分页
     *
     * @param userId 接收人 ID
     * @param query  查询条件（分类/级别/已读状态）
     * @return 通知分页结果
     */
    Page<NotificationDO> inbox(String userId, NotificationQueryDTO query);

    /**
     * 未读数量
     *
     * @param userId 接收人 ID
     * @return 未读通知数
     */
    long countUnread(String userId);

    /**
     * 标记已读
     *
     * @param userId 接收人 ID
     * @param id     通知 ID
     * @return 是否标记成功（通知不存在或不属于该用户时返回 false）
     */
    boolean markRead(String userId, String id);

    /**
     * 全部标记已读
     *
     * @param userId 接收人 ID
     * @return 实际标记条数
     */
    int markAllRead(String userId);

    /**
     * 删除（逻辑）
     *
     * @param userId 接收人 ID（仅允许删除属于自己的通知）
     * @param ids    通知 ID 列表
     */
    void delete(String userId, List<String> ids);

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
