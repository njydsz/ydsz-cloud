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
}
