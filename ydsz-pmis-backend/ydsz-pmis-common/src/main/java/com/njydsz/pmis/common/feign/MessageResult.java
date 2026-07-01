package com.njydsz.pmis.common.feign;

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

    public static MessageResult ok(String channel, String providerTraceId) {
        return new MessageResult(channel, "SUCCESS", providerTraceId, null);
    }

    public static MessageResult fail(String channel, String error) {
        return new MessageResult(channel, "FAILED", null, error);
    }

    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
}
