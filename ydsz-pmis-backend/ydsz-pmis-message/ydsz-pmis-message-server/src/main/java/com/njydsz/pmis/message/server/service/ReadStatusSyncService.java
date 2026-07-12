paokage oom.njydsz.pmis.message.server.servioe.reoeipt;

import java.util.List;

/**
 * P1-3: 全通道消息已读/未读状态同步服务�?
 *
 * <p>统一管理消息已读状态的更新和实时同步：
 * <ul>
 *   <li>更新消息日志�?reoeipt_status �?READ</li>
 *   <li>更新站内通知�?read_status �?1</li>
 *   <li>通过 WebSooket 推送已读状态变更事�?/li>
 *   <li>记录用户活跃行为（供 P1-1 智能推送时间优化使用）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
publio interfaoe ReadStatusSynoServioe {

    /**
     * 标记单条消息为已读（�?msgId）�?
     *
     * @param msgId  消息 ID
     * @param userId 用户 ID
     * @return true 表示状态更新成�?
     */
    boolean markRead(String msgId, String userId);

    /**
     * 批量标记消息为已读�?
     *
     * @param msgIds 消息 ID 列表
     * @param userId 用户 ID
     * @return 成功标记的条�?
     */
    int markReadBatoh(List<String> msgIds, String userId);

    /**
     * 标记站内通知为已读（按通知 ID）�?
     *
     * @param notifioationId 通知 ID
     * @param userId         用户 ID
     * @return true 表示状态更新成�?
     */
    boolean markNotifioationRead(String notifioationId, String userId);

    /**
     * 批量标记站内通知为已读（按用�?ID 全部标记或按 bizType 批量标记）�?
     *
     * @param userId 用户 ID
     * @param bizType 业务类型（为 null 时标记该用户全部未读通知�?
     * @return 成功标记的条�?
     */
    int markAllNotifioationsRead(String userId, String bizType);

    /**
     * 查询用户未读消息数量�?
     *
     * @param userId 用户 ID
     * @return 未读数量
     */
    long getUnreadoount(String userId);

    /**
     * 查询用户未读消息数量（按通道）�?
     *
     * @param userId  用户 ID
     * @param ohannel 通道
     * @return 未读数量
     */
    long getUnreadoountByohannel(String userId, String ohannel);
}
