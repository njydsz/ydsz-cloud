package com.njydsz.common.feign.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 广播推送请求 DTO。
 *
 * <p>封装广播推送的全部参数，替代原 {@code broadcast(String topic, RealtimePushDTO)} 的分离参数设计，
 * 使接口符合 RESTful POST 语义（请求体自描述），并支持幂等去重。
 *
 * <p><b>P0-3-fix</b>：将 topic 并入请求体，返回 {@link com.njydsz.common.socket.push.PushResult} 使调用方可感知结果。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 广播主题（如 "ALERT"、"TASK"）。
     *
     * <p>用于前端订阅过滤，仅推送给订阅了该主题的连接。
     */
    private String topic;

    /**
     * 推送数据载荷。
     *
     * <p>Map 结构，将被序列化为 JSON 发送到客户端。
     */
    private Map<String, Object> data;

    /**
     * 业务级消息唯一 ID（可选，用于幂等去重）。
     *
     * <p>非空时，消息中心基于此 ID 去重，避免重复广播。
     */
    private String messageId;
}
