package com.njydsz.pmis.message.channel.impl;

import com.njydsz.pmis.message.channel.MessageChannel;
import com.njydsz.pmis.message.channel.MessageRequest;
import com.njydsz.pmis.message.channel.MessageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 短信通道 - Mock 实现
 *
 * <p>仅在控制台输出，实际生产应替换为阿里云/腾讯云等 SDK 实现。
 */
@Slf4j
@Component
public class MockSmsChannel implements MessageChannel {

    @Override
    public String channelType() {
        return "SMS";
    }

    @Override
    public MessageResult send(MessageRequest request) {
        if (request.getReceiver() == null || request.getReceiver().isBlank()) {
            return MessageResult.fail("SMS", "接收人手机号不能为空");
        }
        String traceId = "MOCK-SMS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("[SMS-MOCK] 发送短信 receiver={} template={} content={}",
                request.getReceiver(), request.getTemplateCode(), request.getContent());
        return MessageResult.ok("SMS", traceId);
    }
}
