package com.njydsz.pmis.message.service;

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
}
