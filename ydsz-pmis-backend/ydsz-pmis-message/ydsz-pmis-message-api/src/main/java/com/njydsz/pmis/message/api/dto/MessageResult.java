package com.njydsz.pmis.message.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息发送结果（跨模块共享 DTO）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageResult {

    /** 通道 */
    private String channel;

    /** 状态: SUCCESS/FAILED */
    private String status;

    /** 业务追踪 ID（供应商侧 ID） */
    private String providerTraceId;

    /** 错误信息 */
    private String errorMessage;

    /**
     * 构造发送成功的结果
     *
     * @param channel        通道
     * @param providerTraceId 供应商侧追踪 ID
     * @return 成功结果
     */
    public static MessageResult ok(String channel, String providerTraceId) {
        return new MessageResult(channel, "SUCCESS", providerTraceId, null);
    }

    /**
     * 构造发送失败的结果
     *
     * @param channel 通道
     * @param error   错误信息
     * @return 失败结果
     */
    public static MessageResult fail(String channel, String error) {
        return new MessageResult(channel, "FAILED", null, error);
    }

    /**
     * 判断是否发送成功
     *
     * @return true 表示发送成功
     */
    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
}
