package com.njydsz.pmis.message.channel.impl;

import com.njydsz.pmis.message.channel.MessageChannel;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * App 推送通道 - Mock 实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class MockPushChannel implements MessageChannel {

    @Override
    public String channelType() {
        return "PUSH";
    }

    @Override
    public MessageResult send(MessageRequest request) {
        if (request.getReceiver() == null || request.getReceiver().isBlank()) {
            return MessageResult.fail("PUSH", "推送目标不能为空");
        }
        String traceId = "MOCK-PUSH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("[PUSH-MOCK] 推送 receiver={} content={}", request.getReceiver(), request.getContent());
        return MessageResult.ok("PUSH", traceId);
    }
}
