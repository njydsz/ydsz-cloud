package com.njydsz.pmis.message.domain.dto.batch;


import lombok.Data;

import java.util.Map;

/**
 * 批量发送请求 DTO。
 *
 * <p>支持两种接收人模式：
 * <ul>
 *   <li>直接传入 {@code requests} 列表（每条含 receiver/params）</li>
 *   <li>传入 {@code receiverList} 接收人列表 + 统一 {@code templateCode/params/channel}（引擎自动展开）</li>
 * </ul>
 *
 * <p>异步模式下立即返回 batchId，后台异步处理，前端通过 {@code /batch/{batchId}/progress} 查询进度。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Data
public class BatchSendRequestDTO {

    /** 批次 ID（业务侧生成；为空时引擎自动生成雪花 ID） */
    private String batchId;

    /** 批次名称 */
    private String batchName;

    /** 发送通道（receiverList 模式下必填） */
    private String channel;

    /** 模板编码（receiverList 模式下必填） */
    private String templateCode;

    /** 业务类型 */
    private String bizType;

    /** 统一模板参数（receiverList 模式下使用，所有接收人共用） */
    private Map<String, Object> params;

    /** 接收人列表（receiverList 模式） */
    private java.util.List<String> receiverList;

    /** 是否异步发送（默认 true；false 时同步返回结果） */
    private Boolean async = true;

    /** 触发发送的用户 ID */
    private String senderId;
}
