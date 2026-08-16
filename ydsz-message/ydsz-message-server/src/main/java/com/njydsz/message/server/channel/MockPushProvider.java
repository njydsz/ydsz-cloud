package com.njydsz.message.server.channel.push;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.message.domain.entity.template.MsgTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock 推送服务商（降级实现）。
 *
 * <p>当 {@code ydsz.message.push.provider=mock} 或未配置个推凭证时使用， 仅记录日志并返回成功结果，保证开发/测试环境可运行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockPushProvider implements PushProvider {

  private final SnowflakeIdGenerator snowflakeIdGenerator;

  @Override
  public String providerType() {
    return "mock";
  }

  @Override
  public MessageResult send(MessageRequest request, MsgTemplate template) {
    String traceId = "MOCK-PUSH-" + String.valueOf(snowflakeIdGenerator.nextId());
    log.info("[PUSH-MOCK] 推送 receiver={} content={}", request.getReceiver(), request.getContent());
    return MessageResult.ok("PUSH", traceId);
  }
}
