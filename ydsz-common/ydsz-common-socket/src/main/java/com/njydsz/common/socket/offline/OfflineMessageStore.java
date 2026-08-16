package com.njydsz.common.socket.offline;

import java.util.List;

/**
 * 离线消息存储接口。
 *
 * <p>抽象离线消息的缓存与拉取逻辑，默认提供 {@link RedisOfflineMessageStore} 实现。
 * 业务方可覆写为 DB 持久化实现（如 message-server 的 DB 溢出存储）。
 *
 * @author ydsz-team
 * @since 1.0.0
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

    /**
     * 分页查询用户的离线消息（FIFO 顺序：最旧的消息在前）。
     *
     * <p>与 {@link #drainOffline(String)} 不同，本方法仅读取指定范围的离线消息，
     * 不会清空缓存。适用于管理后台预览或客户端增量拉取场景。
     *
     * @param userId 用户 ID
     * @param offset 起始偏移（0 起始，最旧的消息在 offset=0）
     * @param limit  最多返回条数
     * @return 离线消息 JSON 列表（最旧在前），无则返回空列表
     * @throws IllegalArgumentException 当 offset &lt; 0 或 limit &lt;= 0 时
     */
    List<String> pageOffline(String userId, int offset, int limit);
}
