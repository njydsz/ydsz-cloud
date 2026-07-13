package com.njydsz.pmis.common.feign;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 消息发送结果 DTO（兼容旧 com.njydsz.pmis.common.feign.MessageResult）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class MessageResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否发送成功 */
    private boolean success;

    /** 错误信息（失败时填充） */
    private String errorMessage;

    /** 消息追踪 ID */
    private String traceId;

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 构建成功结果。
     *
     * @param channel 通道（保留参数，当前不使用）
     * @param traceId 追踪 ID
     * @return 成功结果
     */
    public static MessageResult ok(String channel, String traceId) {
        MessageResult result = new MessageResult();
        result.success = true;
        result.traceId = traceId;
        return result;
    }

    /**
     * 构建失败结果。
     *
     * @param channel      通道（保留参数，当前不使用）
     * @param errorMessage 错误信息
     * @return 失败结果
     */
    public static MessageResult fail(String channel, String errorMessage) {
        MessageResult result = new MessageResult();
        result.success = false;
        result.errorMessage = errorMessage;
        return result;
    }
}
