package com.njydsz.pmis.message.server.service.core;

/**
 * 限流与频率控制服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface RateLimitService {

    /**
     * 尝试获取令牌(分布式令牌桶)
     *
     * @param key      限流 key
     * @param permits  请求数量
     * @return true 表示获取成功
     */
    boolean tryAcquire(String key, int permits);

    /**
     * 基于用户偏好检查频率是否超限(每日 / 每小时上限)
     *
     * @param userId  用户 ID
     * @param channel 通道
     * @param bizType 业务类型
     * @return true 表示未超限允许发送
     */
    boolean checkFrequency(String userId, String channel, String bizType);

    /**
     * 记录一次发送频率统计(每日 / 每小时计数 +1)
     *
     * @param userId  用户 ID
     * @param channel 通道
     * @param bizType 业务类型
     */
    void recordFrequency(String userId, String channel, String bizType);

    /**
     * P2-5: 多维度发送限流检查。
     *
     * <p>按 receiver / templateCode / tenant 三个维度分别做令牌桶限流，
     * 任一维度超限即返回 false。各维度开关与 permits 由 {@code MessageProperties.rateLimit} 配置。
     * 维度间为 AND 关系：所有启用的维度都通过才允许发送。
     *
     * <p>调用方应在限流失败时记录 {@code messageMetrics.recordSend(channel, "RATE_LIMITED", 0)}
     * 并抛出 {@code SysException(RATE_LIMIT)}。
     *
     * @param channel      通道（用于日志，不参与限流 key）
     * @param receiver     接收人（可为空，空则跳过 receiver 维度）
     * @param templateCode 模板编码（可为空，空则跳过 template 维度）
     * @param tenantId     租户 ID（可为空，空则跳过 tenant 维度）
     * @return true 表示所有启用的维度都未超限，允许发送
     */
    boolean checkSendLimit(String channel, String receiver, String templateCode, String tenantId);

    /**
     * P0-5: 优先级感知的多维度限流检查。
     *
     * <p>在 {@link #checkSendLimit} 基础上增加优先级感知：
     * <ul>
     *   <li>URGENT：跳过 template 和 tenant 维度限流，仅保留 receiver 维度</li>
     *   <li>HIGH：限流阈值提升 2 倍</li>
     *   <li>NORMAL：正常限流</li>
     *   <li>LOW：限流阈值减半</li>
     * </ul>
     *
     * @param channel      通道
     * @param receiver     接收人
     * @param templateCode 模板编码
     * @param tenantId     租户 ID
     * @param priority     优先级（LOW/NORMAL/HIGH/URGENT），为空时按 NORMAL
     * @return true 表示允许发送
     */
    default boolean checkSendLimit(String channel, String receiver, String templateCode,
                                   String tenantId, String priority) {
        return checkSendLimit(channel, receiver, templateCode, tenantId);
    }
}
