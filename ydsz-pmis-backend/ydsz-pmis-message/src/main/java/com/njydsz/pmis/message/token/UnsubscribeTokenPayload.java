package com.njydsz.pmis.message.token;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 退订 token 载荷（P1-5）。
 *
 * <p>封装 token 解析后的关键字段，用于退订确认页渲染与执行退订。
 * 字段经 HMAC-SHA256 签名，token 不可篡改。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnsubscribeTokenPayload {

    /** 用户 ID */
    private String userId;

    /** 主题编码 */
    private String topicCode;

    /** 通道 */
    private String channel;

    /** 过期时间（epoch 秒） */
    private long expiresAt;
}
