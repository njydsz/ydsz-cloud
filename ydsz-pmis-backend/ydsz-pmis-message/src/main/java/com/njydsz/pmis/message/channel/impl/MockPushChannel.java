package com.njydsz.pmis.message.channel.impl;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.njydsz.pmis.message.channel.MessageChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * App 推送通道 - Mock 实现。
 *
 * <p>仅在控制台输出日志，实际生产应替换为个推 / 极光 / FCM 等 SDK 实现。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class MockPushChannel implements MessageChannel {

    /** 通道类型 */
    private static final String CHANNEL_TYPE = "PUSH";

    /**
     * 通道类型。
     *
     * @return PUSH
     */
    @Override
    public String channelType() {
        return CHANNEL_TYPE;
    }

    /**
     * 模拟推送发送，仅记录日志并返回成功结果。
     *
     * @param request 消息请求
     * @return 发送结果（含模拟追踪 ID）
     */
    @Override
    public MessageResult send(MessageRequest request) {
        if (request.getReceiver() == null || request.getReceiver().isBlank()) {
            return MessageResult.fail(CHANNEL_TYPE, "推送目标不能为空");
        }
        String traceId = "MOCK-PUSH-" + SnowflakeIdGenerator.nextTraceId();
        log.info("[PUSH-MOCK] 推送 receiver={} content={}", request.getReceiver(), request.getContent());
        return MessageResult.ok(CHANNEL_TYPE, traceId);
    }
}
