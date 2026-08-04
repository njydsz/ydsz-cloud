package com.remisoft.common.feign.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import lombok.Data;

/**
 * 实时推送 Feign DTO（WebSocket 消息体）
 *
 * <p>用于 NotificationClient.pushRealtime 的消息体参数，
 * 替代原来的 Object 类型，提供类型安全。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class RealtimePushDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 消息数据（键值对形式） */
    private Map<String, Object> data;

    public RealtimePushDTO() {
    }

    public RealtimePushDTO(Map<String, Object> data) {
        this.data = data;
    }
}
