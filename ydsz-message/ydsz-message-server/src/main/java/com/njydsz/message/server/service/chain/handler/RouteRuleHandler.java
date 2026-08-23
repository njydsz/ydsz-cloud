package com.njydsz.message.server.service.chain.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.message.domain.entity.config.MsgRouteRule;
import com.njydsz.message.server.service.chain.SendContext;
import com.njydsz.message.server.service.chain.SendHandler;
import com.njydsz.message.server.service.config.RouteRuleService;

/**
 * 路由规则匹配 Handler。
 *
 * <p>按优先级遍历启用规则，SpEL 求值命中后覆盖通道。 路由规则仅做通道切换，不做拦截（拦截由下游 Handler 负责）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@Order(200)
@RequiredArgsConstructor
public class RouteRuleHandler implements SendHandler {
  /** 路由规则处理器优先级 */
  private static final int ROUTE_RULE_PRIORITY = 200;


  private final RouteRuleService routeRuleService;

  @Override
  public boolean handle(MessageRequest request, SendContext ctx) {
    MsgRouteRule matchedRule = routeRuleService.match(request);
    if (matchedRule != null && StringUtils.hasText(matchedRule.getTargetChannel())) {
      String newChannel = matchedRule.getTargetChannel();
      log.info(
          "[Message] 路由命中切换通道: orig={} -> target={} ruleCode={}",
          ctx.getChannel(),
          newChannel,
          matchedRule.getRuleCode());
      ctx.setChannel(newChannel);
      request.setChannel(newChannel);
    }
    ctx.setMatchedRule(matchedRule);
    return true;
  }

  @Override
  public int order() {
    return ROUTE_RULE_PRIORITY;
  }
}
