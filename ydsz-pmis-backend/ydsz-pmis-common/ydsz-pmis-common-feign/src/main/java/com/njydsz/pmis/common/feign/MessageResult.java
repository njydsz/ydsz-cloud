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

    /** 服务商追踪 ID（回执查询用） */
    private String providerTraceId;

    /** 发送状态（SUCCESS / FAILED / UNKNOWN） */
    private String status;

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getProviderTraceId() {
        return providerTraceId;
    }

    public void setProviderTraceId(String providerTraceId) {
        this.providerTraceId = providerTraceId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
        result.status = "SUCCESS";
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
        result.status = "FAILED";
        return result;
    }

    /**
     * 全参数构造器（用于回执查询等场景）。
     *
     * @param channel         通道（保留参数，当前不使用）
     * @param status          发送状态
     * @param providerTraceId 服务商追踪 ID
     * @param errorMessage    错误信息
     */
    public MessageResult(String channel, String status, String providerTraceId, String errorMessage) {
        this.success = "SUCCESS".equals(status);
        this.status = status;
        this.providerTraceId = providerTraceId;
        this.errorMessage = errorMessage;
    }
}
