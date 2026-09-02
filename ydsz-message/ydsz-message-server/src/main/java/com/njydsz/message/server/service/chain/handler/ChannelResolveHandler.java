package com.njydsz.message.server.service.chain.handler;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.safe.sensitive.SensitiveUtil;
import com.njydsz.message.domain.enums.MessageExceptionCode;
import com.njydsz.message.server.channel.ChannelRouter;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.service.chain.SendContext;
import com.njydsz.message.server.service.chain.SendHandler;
import com.njydsz.message.server.service.config.UserChannelBindingService;

/**
 * 通道解析 Handler（通道启用校验 + 用户绑定解析）。
 *
 * <p>合并原 ChannelValidationHandler 与 ChannelBindingHandler，执行两步操作：
 *
 * <ol>
 *   <li>校验消息通道是否已启用，未启用时短路管线</li>
 *   <li>当 receiver 是 userId 时，自动解析为通道原生联系方式</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class ChannelResolveHandler implements SendHandler {

  private final ChannelRouter channelRouter;
  private final MessageProperties messageProperties;
  private final UserChannelBindingService userChannelBindingService;

  @Override
  public boolean handle(MessageRequest request, SendContext ctx) {
    String channel = request.getChannel();
    if (!StringUtils.hasText(channel)) {
      ctx.setErrorResult(MessageResult.fail(null, null, "消息通道不能为空", "消息通道不能为空", null));
      return false;
    }
    if (!isChannelEnabled(channel)) {
      log.warn("[Message] 通道未启用: {}", channel);
      ctx.setErrorResult(MessageResult.fail(
          channel,
          MessageExceptionCode.CHANNEL_NOT_ENABLED.getCode(),
          "通道未启用: " + channel,
          "通道未启用: " + channel,
          null));
      return false;
    }
    // 解析 userId 到通道原生联系方式
    resolveChannelUser(request, ctx);
    // 填充上下文
    ctx.setChannel(channel);
    ctx.setReceiver(request.getReceiver());
    ctx.setBizType(request.getBizType());
    ctx.setTemplateCode(request.getTemplateCode());
    return true;
  }

  @Override
  public int order() {
    return 100;
  }

  /**
   * 判断通道是否启用：优先 ChannelRouter，回退 MessageProperties.channelEnabled。
   *
   * @param channel 通道标识（如 SMS、EMAIL、INAPP）
   * @return 通道已启用返回 true
   */
  private boolean isChannelEnabled(String channel) {
    try {
      if (!channelRouter.isChannelEnabled(channel)) {
        return false;
      }
    } catch (Exception e) {
      log.debug("[Message] ChannelRouter 判断异常,回退配置: {}", e.getMessage());
    }
    try {
      Map<String, Boolean> enabled = messageProperties.getChannelEnabled();
      if (enabled != null && enabled.containsKey(channel)) {
        return Boolean.TRUE.equals(enabled.get(channel));
      }
    } catch (Exception e) {
      log.debug("[Message] channelEnabled 配置读取异常: {}", e.getMessage());
    }
    return true;
  }

  /**
   * 解析 receiver 为通道原生联系方式。
   *
   * @param request 消息发送请求（receiver 可能是 userId，需解析为通道原生标识）
   * @param ctx 管线输出上下文（解析后更新 receiver）
   */
  private void resolveChannelUser(MessageRequest request, SendContext ctx) {
    String receiver = ctx.getReceiver();
    String channel = ctx.getChannel();
    if (!StringUtils.hasText(receiver) || !StringUtils.hasText(channel)) {
      return;
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
  }
}
