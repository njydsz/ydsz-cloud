package com.njydsz.message.server.channel.sms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.message.domain.entity.template.MsgTemplate;

/**
 * Mock 短信服务商（降级实现）。
 *
 * <p>当 {@code ydsz.message.sms.provider=mock} 或未配置阿里云凭证时使用，
 * 仅记录日志并返回成功结果，保证开发/测试环境可运行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockSmsProvider implements SmsProvider {

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    public String providerType() {
        return "mock";
    }

    @Override
    public MessageResult send(MessageRequest request, MsgTemplate template) {
        String traceId = "MOCK-SMS-" + String.valueOf(snowflakeIdGenerator.nextId());
        log.info("[SMS-MOCK] 发送短信 receiver={} template={} content={}",
                request.getReceiver(), request.getTemplateCode(), request.getContent());
        return MessageResult.ok("SMS", traceId);
    }
}
