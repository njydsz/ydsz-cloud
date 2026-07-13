package com.njydsz.pmis.message.server.channel.push;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.njydsz.pmis.message.domain.entity.template.MsgTemplateDO;

import lombok.extern.slf4j.Slf4j;

/**
 * Mock 推送服务商（降级实现）。
 *
 * <p>当 {@code pmis.message.push.provider=mock} 或未配置个推凭证时使用，
 * 仅记录日志并返回成功结果，保证开发/测试环境可运行。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
public class MockPushProvider implements PushProvider {

    @Override
    public String providerType() {
        return "mock";
    }

    @Override
    public MessageResult send(MessageRequest request, MsgTemplateDO template) {
        String traceId = "MOCK-PUSH-" + SnowflakeIdGenerator.nextTraceId();
        log.info("[PUSH-MOCK] 推送 receiver={} content={}",
                request.getReceiver(), request.getContent());
        return MessageResult.ok("PUSH", traceId);
    }
}
