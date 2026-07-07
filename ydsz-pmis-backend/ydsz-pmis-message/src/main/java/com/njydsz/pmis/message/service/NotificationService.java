package com.njydsz.pmis.message.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.message.dto.NotificationQueryDTO;
import com.njydsz.pmis.message.dto.NotificationSendDTO;
import com.njydsz.pmis.message.entity.MsgNotificationDO;

import java.util.List;

/**
 * 站内通知服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface NotificationService {

    /**
     * 发送站内通知(单发或群发)
     *
     * @param dto 发送参数
     * @return 发送数量
     */
    int send(NotificationSendDTO dto);

    /**
     * 查询用户收件箱(分页)
     *
     * @param userId 用户 ID
     * @param query  查询参数
     * @return 分页结果
     */
    Page<MsgNotificationDO> inbox(String userId, NotificationQueryDTO query);

    /**
     * 统计用户未读通知数
     *
     * @param userId 用户 ID
     * @return 未读数量
     */
    long countUnread(String userId);

    /**
     * 标记单条通知为已读
     *
     * @param userId 用户 ID
     * @param id     通知 ID
     * @return true 表示标记成功
     */
    boolean markRead(String userId, String id);

    /**
     * 标记该用户所有未读通知为已读
     *
     * @param userId 用户 ID
     * @return 影响行数
     */
    int markAllRead(String userId);

    /**
     * 删除指定通知(逻辑删除)
     *
     * @param userId 用户 ID
     * @param ids    通知 ID 列表
     */
    void delete(String userId, List<String> ids);

    /**
     * 撤回单条通知
     *
     * @param userId         用户 ID
     * @param id             通知 ID
     * @return true 表示撤回成功
     */
    boolean recall(String userId, String id);
}
