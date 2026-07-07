package com.njydsz.pmis.message.channel.impl;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.njydsz.pmis.message.channel.MessageChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 短信通道 - Mock 实现。
 *
 * <p>仅在控制台输出日志，实际生产应替换为阿里云 / 腾讯云等 SDK 实现。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class MockSmsChannel implements MessageChannel {

    /** 通道类型 */
    private static final String CHANNEL_TYPE = "SMS";

    /**
     * 通道类型。
     *
     * @return SMS
     */
    @Override
    public String channelType() {
        return CHANNEL_TYPE;
    }

    /**
     * 模拟短信发送，仅记录日志并返回成功结果。
     *
     * @param request 消息请求
     * @return 发送结果（含模拟追踪 ID）
     */
    @Override
    public MessageResult send(MessageRequest request) {
        if (request.getReceiver() == null || request.getReceiver().isBlank()) {
            return MessageResult.fail(CHANNEL_TYPE, "接收人手机号不能为空");
        }
        String traceId = "MOCK-SMS-" + SnowflakeIdGenerator.nextTraceId();
        log.info("[SMS-MOCK] 发送短信 receiver={} template={} content={}",
                request.getReceiver(), request.getTemplateCode(), request.getContent());
        return MessageResult.ok(CHANNEL_TYPE, traceId);
    }
}
