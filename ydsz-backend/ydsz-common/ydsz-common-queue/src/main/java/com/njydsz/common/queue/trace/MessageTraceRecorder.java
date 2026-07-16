package com.njydsz.common.queue.trace;

import java.util.List;

/**
 * 消息轨迹记录器接口
 *
 * <p>定义消息轨迹的存储和查询标准操作。
 * 支持基于内存和 Redis 两种后端实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MessageTraceRecorder {

    /**
     * 记录消息轨迹
     *
     * @param trace 消息轨迹实体
     */
    void record(MessageTrace trace);

    /**
     * 按消息ID查询轨迹
     *
     * @param messageId 消息ID
     * @return 匹配的轨迹列表
     */
    List<MessageTrace> queryByMessageId(String messageId);

    /**
     * 按链路追踪ID查询轨迹
     *
     * @param traceId 链路追踪ID
     * @return 匹配的轨迹列表
     */
    List<MessageTrace> queryByTraceId(String traceId);
}
