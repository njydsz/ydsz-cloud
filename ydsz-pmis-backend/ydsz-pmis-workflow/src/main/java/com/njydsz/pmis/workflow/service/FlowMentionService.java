package com.njydsz.pmis.workflow.service;

import java.util.List;

/**
 * 审批 @提及服务（P2-3）
 *
 * <p>对标钉钉/飞书审批中的 @提及 功能，允许审批人在评论中 @其他用户，
 * 被@的用户会收到通知并可查看相关审批详情。
 *
 * <p>功能说明：
 * <ul>
 *   <li>解析评论文本中的 @userId 标记，提取被提及用户列表</li>
 *   <li>为每个被提及用户创建一条提及记录</li>
 *   <li>推送通知给被提及用户</li>
 *   <li>支持查询用户被提及的列表</li>
 *   <li>支持标记已读</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.9.0
 */
public interface FlowMentionService {

    /**
     * 处理评论中的 @提及，解析被提及用户并发送通知。
     *
     * @param instanceId 流程实例 ID
     * @param taskId     任务 ID（可空）
     * @param comment    评论内容（含 @userId 标记）
     * @param mentiodBy  提及人 ID
     * @param tenantId   租户 ID
     * @return 被提及的用户 ID 列表
     */
    List<String> processMentions(String instanceId, String taskId, String comment,
                                  String mentiodBy, String tenantId);

    /**
     * 查询用户被提及的列表。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @param onlyUnread 是否只查未读
     * @return 提及记录列表
     */
    List<java.util.Map<String, Object>> listMentions(String userId, String tenantId, boolean onlyUnread);

    /**
     * 标记提及为已读。
     *
     * @param mentionId 提及记录 ID
     * @param userId    用户 ID（安全校验：只能标记自己的提及）
     */
    void markRead(String mentionId, String userId);

    /**
     * 统计用户未读提及数。
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @return 未读数
     */
    long countUnread(String userId, String tenantId);
}
