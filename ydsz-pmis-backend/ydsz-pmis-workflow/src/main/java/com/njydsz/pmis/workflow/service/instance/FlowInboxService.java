package com.njydsz.pmis.workflow.service.instance;

import java.util.List;

/**
 * 站内信服务（P2-4）
 *
 * <p>工作流模块本地站内信通道，作为外部通知中心服务的补充和降级方案。
 *
 * <p>核心能力：
 * <ul>
 *   <li>写入站内信到本地表（同步事务，确保不丢消息）</li>
 *   <li>分页查询用户站内信列表</li>
 *   <li>标记已读（单条/批量/全部已读）</li>
 *   <li>统计未读数</li>
 * </ul>
 *
 * <p>与 {@link FlowNotificationService} 的关系：
 * <ul>
 *   <li>{@link FlowNotificationService} 负责跨服务投递（Feign → notification 模块）</li>
 *   <li>本服务负责本地持久化，作为 Feign 不可用时的降级方案</li>
 *   <li>两者可并行使用：先写本地站内信，再异步投递到外部通知中心</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.9.0
 */
public interface FlowInboxService {

    /**
     * 发送站内信（写入本地表）。
     *
     * @param receiverId  接收人 ID
     * @param messageType 消息类型（TASK_CREATED / TASK_URGED / CC / MENTION 等）
     * @param title       通知标题
     * @param content     通知内容
     * @param instanceId  流程实例 ID（可空）
     * @param taskId      任务 ID（可空）
     * @param level       通知级别（INFO/WARN/ERROR/URGENT）
     * @param tenantId    租户 ID
     */
    void send(String receiverId, String messageType, String title, String content,
              String instanceId, String taskId, String level, String tenantId);

    /**
     * 分页查询用户站内信列表。
     *
     * @param receiverId 接收人 ID
     * @param tenantId   租户 ID
     * @param onlyUnread 是否只查未读
     * @param offset     偏移量
     * @param limit      每页大小
     * @return 站内信列表
     */
    List<com.njydsz.pmis.workflow.entity.FlowInboxDO> listInbox(String receiverId, String tenantId,
                                                                  boolean onlyUnread, int offset, int limit);

    /**
     * 统计用户未读站内信数。
     *
     * @param receiverId 接收人 ID
     * @param tenantId   租户 ID
     * @return 未读数
     */
    long countUnread(String receiverId, String tenantId);

    /**
     * 标记单条站内信为已读。
     *
     * @param inboxId    站内信 ID
     * @param receiverId 接收人 ID（安全校验）
     */
    void markRead(String inboxId, String receiverId);

    /**
     * 批量标记已读。
     *
     * @param inboxIds   站内信 ID 列表
     * @param receiverId 接收人 ID
     * @param tenantId   租户 ID
     * @return 成功标记数
     */
    int batchMarkRead(List<String> inboxIds, String receiverId, String tenantId);

    /**
     * 全部标记已读。
     *
     * @param receiverId 接收人 ID
     * @param tenantId   租户 ID
     * @return 成功标记数
     */
    int markAllRead(String receiverId, String tenantId);
}
