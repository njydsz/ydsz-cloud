package com.njydsz.pmis.message.server.service.core;

import java.time.LocalDateTime;

/**
 * P1-1: 智能推送时间优化器。
 *
 * <p>基于用户历史活跃数据（消息已读/点击行为），学习每个用户的最佳推送时间窗口。
 * 对于非紧急消息，推荐在用户最活跃的时段推送，提升消息打开率和用户体验。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
public interface DeliveryTimeOptimizer {

    /**
     * 记录用户活跃行为。
     *
     * <p>当用户查看/点击消息时调用，更新用户活跃度画像。
     * Redis Bitmap 按 hour-of-week 存储，每周一轮转。
     *
     * @param userId 用户 ID
     * @param channel 通道
     */
    void recordActivity(String userId, String channel);

    /**
     * 获取用户最佳推送时间。
     *
     * <p>基于用户历史活跃数据，推荐未来最近的最佳推送时间窗口。
     * 如果用户无历史活跃数据，返回 null（由调用方使用默认时间）。
     *
     * @param userId  用户 ID
     * @param channel 通道
     * @return 推荐的推送时间；null 表示无足够数据，使用当前时间
     */
    LocalDateTime getOptimalDeliveryTime(String userId, String channel);

    /**
     * 获取用户活跃度评分（0-100）。
     *
     * <p>基于最近 7 天的活跃频次计算，用于评估用户活跃程度。
     *
     * @param userId 用户 ID
     * @return 活跃度评分（0-100），0 表示无活跃
     */
    int getActivityScore(String userId);
}
