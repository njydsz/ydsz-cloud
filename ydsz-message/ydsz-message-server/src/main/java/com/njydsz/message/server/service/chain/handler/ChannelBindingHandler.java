package com.njydsz.message.server.service.chain.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.safe.sensitive.SensitiveUtil;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.service.chain.SendContext;
import com.njydsz.message.server.service.chain.SendHandler;
import com.njydsz.message.server.service.config.UserChannelBindingService;

/**
 * 用户通道绑定解析 Handler。
 *
 * <p>当 receiver 是 userId 而非通道原生联系方式时，自动解析为通道联系方式。 例如：userId=123 → 手机号 138xxxx（SMS）或
 * user@example.com（EMAIL）。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
@Component
@Order(150)
@RequiredArgsConstructor
public class ChannelBindingHandler implements SendHandler {

  private final UserChannelBindingService userChannelBindingService;
  private final MessageProperties messageProperties;

  @Override
  public boolean handle(MessageRequest request, SendContext ctx) {
    String receiver = ctx.getReceiver();
    String channel = ctx.getChannel();
    if (!StringUtils.hasText(receiver) || !StringUtils.hasText(channel)) {
      return true;
    }
    String resolved = userChannelBindingService.resolveChannelUserId(receiver, channel);
    if (resolved != null && !resolved.equals(receiver)) {
      log.debug(
          "[Message] 通道绑定解析: userId={} channel={} -> channelUserId={}",
          SensitiveUtil.scanAndMask(receiver),
          channel,
          resolved);
      request.setReceiver(resolved);
      ctx.setReceiver(resolved);
    }
    return true;
  }

  @Override
  public int order() {
    return 150;
  }
}
