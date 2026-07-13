package com.njydsz.pmis.common.socket.offline;

import java.util.List;

/**
 * 离线消息存储接口。
 *
 * <p>抽象离线消息的缓存与拉取逻辑，默认提供 {@link RedisOfflineMessageStore} 实现。
 * 业务方可覆写为 DB 持久化实现（如 message-server 的 DB 溢出存储）。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
public interface OfflineMessageStore {

    /**
     * 缓存一条离线消息。
     *
     * @param userId  用户 ID
     * @param type    消息类型标签（如 NOTIFICATION / ALERT）
     * @param payload 消息内容（任意可序列化对象）
     */
    void cacheOffline(String userId, String type, Object payload);

    /**
     * 拉取并清空用户的所有离线消息（FIFO 顺序：最旧的消息在前）。
     *
     * @param userId 用户 ID
     * @return 离线消息 JSON 列表（最旧在前），无则返回空列表
     */
    List<String> drainOffline(String userId);

    /**
     * 查询用户离线消息数量。
     *
     * @param userId 用户 ID
     * @return 离线消息数量
     */
    long countOffline(String userId);
}
