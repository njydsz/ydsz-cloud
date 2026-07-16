package com.njydsz.message.server.channel.push;

import org.springframework.stereotype.Component;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.util.id.SnowflakeUtils;
import com.njydsz.message.domain.entity.template.MsgTemplateDO;

import lombok.extern.slf4j.Slf4j;

/**
 * Mock 推送服务商（降级实现）。
 *
 * <p>当 {@code ydsz.message.push.provider=mock} 或未配置个推凭证时使用，
 * 仅记录日志并返回成功结果，保证开发/测试环境可运行。
 *
 * @author ydsz-team
 * @since 1.0.0
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
        String traceId = "MOCK-PUSH-" + SnowflakeUtils.nextIdStr();
        log.info("[PUSH-MOCK] 推送 receiver={} content={}",
                request.getReceiver(), request.getContent());
        return MessageResult.ok("PUSH", traceId);
    }
}
