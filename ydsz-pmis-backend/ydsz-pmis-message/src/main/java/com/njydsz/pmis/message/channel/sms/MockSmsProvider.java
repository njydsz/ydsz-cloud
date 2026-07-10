package com.njydsz.pmis.message.channel.sms;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.njydsz.pmis.message.entity.template.MsgTemplateDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock 短信服务商（降级实现）。
 *
 * <p>当 {@code pmis.message.sms.provider=mock} 或未配置阿里云凭证时使用，
 * 仅记录日志并返回成功结果，保证开发/测试环境可运行。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
public class MockSmsProvider implements SmsProvider {

    @Override
    public String providerType() {
        return "mock";
    }

    @Override
    public MessageResult send(MessageRequest request, MsgTemplateDO template) {
        String traceId = "MOCK-SMS-" + SnowflakeIdGenerator.nextTraceId();
        log.info("[SMS-MOCK] 发送短信 receiver={} template={} content={}",
                request.getReceiver(), request.getTemplateCode(), request.getContent());
        return MessageResult.ok("SMS", traceId);
    }
}
