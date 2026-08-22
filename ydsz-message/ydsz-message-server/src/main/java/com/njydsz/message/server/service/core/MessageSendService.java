package com.njydsz.message.server.service.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.safe.sensitive.SensitiveUtil;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.domain.repository.MsgLogRepository;
import com.njydsz.message.domain.vo.MsgLogVO;
import com.njydsz.message.domain.vo.MsgRouteRuleVO;
import com.njydsz.message.server.channel.ChannelRouter;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.config.RetryStrategyResolver;
import com.njydsz.message.server.metric.MessageMetrics;

/**
 * 消息发送与通道分发服务。
 *
 * <p>负责消息的通道投递、降级链、重试等发送侧核心逻辑。 从 {@link MessageServiceImpl}（原 God Class）中提取，与预处理 / 渲染 / 查询职责解耦。
 *
 * <p><b>职责边界：</b>
 *
 * <ul>
 *   <li>通道分发（{@link #dispatch}）—— 状态驱动（SENDING → SUCCESS / FAILED / RETRY）
 *   <li>多级降级链（{@link #tryFallbackChain}）—— 按路由规则逐个尝试
 *   <li>重试决策（{@link #handleFailure}）—— 指数退避 + 最大重试次数
 *   <li>成本计算、频率记录、配额扣减等发送后处理
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageSendService {

  private final ChannelRouter channelRouter;
  private final MsgLogRepository msgLogRepository;
  private final GuardService guardService;
  private final RetryStrategyResolver retryStrategyResolver;
  private final MessageMetrics messageMetrics;
  private final MessageTraceService messageTraceService;
  private final MessageProperties messageProperties;

  /**
   * 执行通道分发，包含通道降级与重试逻辑。
   *
   * <p>性能优化：移除 PENDING→SENDING 的冗余 DB 写入，仅在最终状态确定时执行一次 UPDATE，将单次发送的 DB 写入次数从 2~N
   * 次降低到 1 次。
   *
   * @param logVO 消息日志 VO（状态会被修改并落库）
   * @param matchedRule 命中的路由规则（用于解析降级通道）
   * @param receiver 收方标识（仅用于频率记录与日志，会脱敏打印）
   * @return 发送结果
   */
  public MessageResult dispatch(MsgLogVO logVO, MsgRouteRuleVO matchedRule, String receiver) {
    String channel = logVO.getChannel();
    long start = System.currentTimeMillis();
    try {
      messageTraceService.recordTrace(
          logVO.getMsgId(), "DISPATCH_START", "SUCCESS", channel, "通道分发开始");
      String providerTraceId = channelRouter.dispatch(logVO);
      long cost = System.currentTimeMillis() - start;
      logVO.setStatus(MessageStatusEnum.SUCCESS.name());
      logVO.setProviderTraceId(providerTraceId);
      logVO.setCostMs(cost);
      logVO.setCost(calculateCost(channel));
      msgLogRepository.update(logVO);
      if (StringUtils.hasText(receiver)) {
        guardService.recordFrequency(receiver, channel, logVO.getBizType());
      }
      messageMetrics.recordSend(channel, "SUCCESS", cost);
      messageMetrics.recordSendSuccess(channel, logVO.getTemplateCode(), logVO.getTenantId());
      messageTraceService.recordTrace(
          logVO.getMsgId(),
          "DISPATCH_SUCCESS",
          "SUCCESS",
          channel,
          "发送成功: cost=" + cost + "ms");
      log.info(
          "[Message] 发送成功: msgId={} channel={} receiver={} cost={}ms",
          logVO.getMsgId(),
          channel,
          SensitiveUtil.scanAndMask(receiver),
          cost);
      return MessageResult.ok(channel, providerTraceId);
    } catch (Exception e) {
      long cost = System.currentTimeMillis() - start;
      logVO.setCostMs(cost);
      logVO.setErrorMessage(e.getMessage());
      messageMetrics.recordSendFailure(
          channel, logVO.getTemplateCode(), logVO.getTenantId(), e.getClass().getSimpleName());
      messageMetrics.recordException(channel, e.getClass().getSimpleName());
      List<String> fallbackChannels = resolveFallbackChannels(matchedRule, channel);
      if (!fallbackChannels.isEmpty()) {
        MessageResult fallback = tryFallbackChain(logVO, fallbackChannels, cost);
        if (fallback != null) {
          return fallback;
        }
      }
      return handleFailure(logVO, e, cost);
    }
  }

  /** 解析降级通道列表。 */
  public List<String> resolveFallbackChannels(MsgRouteRuleVO matchedRule, String currentChannel) {
    if (matchedRule == null) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<>();
    String single = matchedRule.getFallbackChannel();
    if (StringUtils.hasText(single) && !single.equalsIgnoreCase(currentChannel)) {
      result.add(single.trim().toUpperCase());
    }
    return result;
  }

  /** 按降级链顺序逐个尝试，任一成功即返回。 */
  public MessageResult tryFallbackChain(
      MsgLogVO logVO, List<String> fallbackChannels, long prevCost) {
    String origChannel = logVO.getChannel();
    long accumulatedCost = prevCost;
    List<String> tried = new ArrayList<>(fallbackChannels.size() + 1);
    tried.add(origChannel);
    for (String fallbackChannel : fallbackChannels) {
      long start = System.currentTimeMillis();
      try {
        logVO.setChannel(fallbackChannel);
        String providerTraceId = channelRouter.dispatch(logVO);
        long cost = System.currentTimeMillis() - start;
        logVO.setStatus(MessageStatusEnum.SUCCESS.name());
        logVO.setProviderTraceId(providerTraceId);
        logVO.setCostMs(accumulatedCost + cost);
        logVO.setCost(calculateCost(fallbackChannel));
        msgLogRepository.update(logVO);
        messageMetrics.recordSend(fallbackChannel, "SUCCESS", cost);
        log.info(
            "[Message] 降级发送成功: msgId={} chain={} final={} cost={}ms",
            logVO.getMsgId(),
            tried,
            fallbackChannel,
            cost);
        return MessageResult.ok(fallbackChannel, providerTraceId);
      } catch (Exception fe) {
        long cost = System.currentTimeMillis() - start;
        accumulatedCost += cost;
        tried.add(fallbackChannel);
        log.warn(
            "[Message] 降级发送失败: msgId={} fallback={} err={} 继续尝试下一通道",
            logVO.getMsgId(),
            fallbackChannel,
            fe.getMessage());
      }
    }
    logVO.setChannel(origChannel);
    logVO.setErrorMessage(String.join("→", tried) + " 均失败");
    return null;
  }

  /** 失败处理：retryCount < MAX → RETRY + nextRetryAt（指数退避），否则 FAILED。 */
  public MessageResult handleFailure(MsgLogVO logVO, Exception e, long cost) {
    int retryCount = logVO.getRetryCount() == null ? 0 : logVO.getRetryCount();
    String maskedReceiver = SensitiveUtil.scanAndMask(logVO.getReceiver());
    if (!retryStrategyResolver.isMaxRetriesReached(retryCount, logVO.getChannel())) {
      logVO.setStatus(MessageStatusEnum.RETRY.name());
      logVO.setNextRetryAt(retryStrategyResolver.calcNextRetryAt(retryCount, logVO.getChannel()));
      msgLogRepository.update(logVO);
      messageMetrics.recordRetry(logVO.getChannel());
      log.warn(
          "[Message] 发送失败转重试: msgId={} channel={} receiver={} retryCount={} nextRetryAt={} err={}",
          logVO.getMsgId(),
          logVO.getChannel(),
          maskedReceiver,
          retryCount,
          logVO.getNextRetryAt(),
          e.getMessage());
      return MessageResult.fail(logVO.getChannel(), "发送失败,已加入重试队列: " + e.getMessage());
    }
    logVO.setStatus(MessageStatusEnum.FAILED.name());
    msgLogRepository.update(logVO);
    messageMetrics.recordSend(logVO.getChannel(), "FAILED", cost);
    log.error(
        "[Message] 发送失败(重试耗尽): msgId={} channel={} receiver={} retryCount={} err={}",
        logVO.getMsgId(),
        logVO.getChannel(),
        maskedReceiver,
        retryCount,
        e.getMessage());
    return MessageResult.fail(logVO.getChannel(), e.getMessage());
  }

  /** 按通道计算单条消息成本。 */
  public BigDecimal calculateCost(String channel) {
    MessageProperties.CostConfig cfg = messageProperties.getCost();
    if (cfg == null || !cfg.isEnabled() || cfg.getUnitPrices() == null) {
      return BigDecimal.ZERO;
    }
    return cfg.getUnitPrices().getOrDefault(channel, BigDecimal.ZERO);
  }
}
