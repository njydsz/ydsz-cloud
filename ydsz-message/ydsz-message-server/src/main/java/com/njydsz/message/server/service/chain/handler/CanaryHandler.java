package com.njydsz.message.server.service.chain.handler;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.message.domain.entity.canary.MsgCanary;
import com.njydsz.message.server.service.canary.CanaryService;
import com.njydsz.message.server.service.chain.SendContext;
import com.njydsz.message.server.service.chain.SendHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 灰度命中差异化处理 Handler。
 *
 * <p>命中灰度实验时，可切换模板编码和/或通道。 灰度命中后设置 canaryFlag=1，供落库时记录。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
@Component
@Order(300)
@RequiredArgsConstructor
public class CanaryHandler implements SendHandler {

  private final CanaryService canaryService;

  @Override
  public boolean handle(MessageRequest request, SendContext ctx) {
    String templateCode = ctx.getTemplateCode();
    String receiver = ctx.getReceiver();
    if (!StringUtils.hasText(templateCode) || !StringUtils.hasText(receiver)) {
      return true;
    }
    MsgCanary canary = canaryService.matchConfig(templateCode, receiver);
    if (canary == null) {
      return true;
    }
    ctx.setCanaryFlag(1);
    ctx.setCanaryKeyForLog(templateCode);
    if (StringUtils.hasText(canary.getExperimentTemplateCode())) {
      log.info(
          "[Message] 灰度命中切换模板: orig={} exp={}", templateCode, canary.getExperimentTemplateCode());
      request.setTemplateCode(canary.getExperimentTemplateCode());
      ctx.setTemplateCode(canary.getExperimentTemplateCode());
    }
    if (StringUtils.hasText(canary.getExperimentChannel())) {
      log.info(
          "[Message] 灰度命中切换通道: orig={} exp={}", ctx.getChannel(), canary.getExperimentChannel());
      ctx.setChannel(canary.getExperimentChannel());
      request.setChannel(ctx.getChannel());
    }
    return true;
  }

  @Override
  public int order() {
    return 300;
  }
}
