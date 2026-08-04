package com.remisoft.message.server.service.core;

import java.util.List;
import java.util.Map;

import com.remisoft.message.domain.entity.config.MsgTrace;
import com.remisoft.message.domain.entity.config.MsgTrace.Node;

/**
 * 消息端到端追踪 Service
 *
 * <p>在消息生命周期的每个关键节点记录轨迹,通过 {@code msgId} 串联形成完整链路,
 * 供运维和业务方快速定位"这条消息去哪了"。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>记录轨迹</b>：{@link #recordTrace} — 在关键节点(发送/路由/通道投递/回执/撤回等)记录一行</li>
 *   <li><b>查询</b>：{@link #getTraceByMsgId} / {@link #getTraceByTraceId} / {@link #getTraceByBiz}</li>
 * </ul>
 *
 * <p><b>轨迹节点：</b>{@code RECEIVED / ROUTED / SUBSCRIPTION_CHECKED / DEDUP_CHECKED / SENT / DELIVERED / RECEIPT / RECALLED} 等。
 *
 * <p><b>链路串联：</b>支持三种查询维度
 * <ul>
 *   <li>{@code msgId} — 单条消息的完整轨迹</li>
 *   <li>{@code traceId} — 同一次业务操作触发的多条消息(跨消息)</li>
 *   <li>{@code (bizType, bizId)} — 按业务单据 ID 查询所有相关消息</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see com.remisoft.message.domain.entity.config.MsgTrace 轨迹实体
 * @see MessageService 消息发送主流程(各节点调用 recordTrace)
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
    List<MsgTrace> getTraceByMsgId(String msgId);

    /**
     * 按 traceId 查询关联的轨迹（跨消息）。
     *
     * @param traceId 链路追踪 ID
     * @return 轨迹列表
     */
    List<MsgTrace> getTraceByTraceId(String traceId);

    /**
     * 按 bizType + bizId 查询关联的轨迹。
     *
     * @param bizType 业务类型
     * @param bizId   业务单据 ID
     * @return 轨迹列表
     */
    List<MsgTrace> getTraceByBiz(String bizType, String bizId);
}
