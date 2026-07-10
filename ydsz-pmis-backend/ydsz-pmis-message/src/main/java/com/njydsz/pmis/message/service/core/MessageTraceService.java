package com.njydsz.pmis.message.service;

import com.njydsz.pmis.message.entity.config.MsgTraceDO;
import com.njydsz.pmis.message.entity.config.MsgTraceDO.Node;

import java.util.List;
import java.util.Map;

/**
 * P0-2: 消息端到端追踪服务。
 *
 * <p>在消息生命周期的每个关键节点记录轨迹，通过 msgId 串联形成完整链路。
 * 支持按 msgId / bizType+bizId / traceId 查询完整轨迹。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
public interface MessageTraceService {

    /**
     * 记录一个轨迹节点。
     *
     * @param msgId   消息 ID
     * @param node    轨迹节点类型
     * @param status  节点状态: SUCCESS / FAILED / SKIPPED / PENDING
     * @param channel 通道（可为 null）
     * @param message 节点描述 / 错误信息
     * @param extra   扩展信息（会被序列化为 JSON）
     */
    void recordTrace(String msgId, Node node, String status, String channel,
                     String message, Map<String, Object> extra);

    /**
     * 记录一个轨迹节点（简化版，不含 extra）。
     *
     * @param msgId   消息 ID
     * @param node    轨迹节点类型
     * @param status  节点状态
     * @param channel 通道
     * @param message 节点描述
     */
    void recordTrace(String msgId, Node node, String status, String channel, String message);

    /**
     * 按 msgId 查询完整轨迹（按时间正序）。
     *
     * @param msgId 消息 ID
     * @return 轨迹列表（时间正序）
     */
    List<MsgTraceDO> getTraceByMsgId(String msgId);

    /**
     * 按 traceId 查询关联的轨迹（跨消息）。
     *
     * @param traceId 链路追踪 ID
     * @return 轨迹列表
     */
    List<MsgTraceDO> getTraceByTraceId(String traceId);

    /**
     * 按 bizType + bizId 查询关联的轨迹。
     *
     * @param bizType 业务类型
     * @param bizId   业务单据 ID
     * @return 轨迹列表
     */
    List<MsgTraceDO> getTraceByBiz(String bizType, String bizId);
}
