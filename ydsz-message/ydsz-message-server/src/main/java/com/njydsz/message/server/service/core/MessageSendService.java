package com.njydsz.message.server.service.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.feign.MessageResult;
import com.njydsz.message.domain.entity.config.MsgRouteRule;
import com.njydsz.message.domain.entity.config.MsgTrace;
import com.njydsz.message.domain.entity.core.MsgLog;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.infra.mapper.core.MsgLogMapper;
import com.njydsz.message.server.channel.ChannelRouter;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.config.RetryStrategyResolver;
import com.njydsz.message.server.metric.MessageMetrics;
import com.njydsz.message.server.service.core.MessageTraceService;
import com.njydsz.message.server.service.core.RateLimitService;
import com.njydsz.message.server.util.PiiMasker;

/**
 * 消息发送与通道分发服务。
 *
 * <p>负责消息的通道投递、降级链、重试等发送侧核心逻辑。
 * 从 {@link MessageServiceImpl}（原 God Class）中提取，与预处理 / 渲染 / 查询职责解耦。
 *
 * <p><b>职责边界：</b>
 * <ul>
 *   <li>通道分发（{@link #dispatch}）—— 状态驱动（SENDING → SUCCESS / FAILED / RETRY）</li>
 *   <li>多级降级链（{@link #tryFallbackChain}）—— 按路由规则逐个尝试</li>
 *   <li>重试决策（{@link #handleFailure}）—— 指数退避 + 最大重试次数</li>
 *   <li>成本计算、频率记录、配额扣减等发送后处理</li>
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
    private final MsgLogMapper msgLogMapper;
    private final RateLimitService rateLimitService;
    private final RetryStrategyResolver retryStrategyResolver;
    private final MessageMetrics messageMetrics;
    private final MessageTraceService messageTraceService;
    private final MessageProperties messageProperties;

    /**
     * 执行通道分发，包含通道降级与重试逻辑。
     *
     * @param logDO        消息日志（状态会被修改并落库）
     * @param matchedRule  命中的路由规则（用于解析降级通道）
     * @param receiver     收方标识（仅用于频率记录与日志，会脱敏打印）
     * @return 发送结果
     */
    public MessageResult dispatch(MsgLog logDO, MsgRouteRule matchedRule, String receiver) {
        String channel = logDO.getChannel();
        long start = System.currentTimeMillis();
        try {
            logDO.setStatus(MessageStatusEnum.SENDING.name());
            msgLogMapper.updateById(logDO);
            messageTraceService.recordTrace(logDO.getMsgId(),
                    MsgTrace.Node.DISPATCH_START,
                    "SUCCESS", channel, "通道分发开始");
            String providerTraceId = channelRouter.dispatch(logDO);
            long cost = System.currentTimeMillis() - start;
            logDO.setStatus(MessageStatusEnum.SUCCESS.name());
            logDO.setProviderTraceId(providerTraceId);
            logDO.setCostMs(cost);
            logDO.setCost(calculateCost(channel));
            msgLogMapper.updateById(logDO);
            if (StringUtils.hasText(receiver)) {
                rateLimitService.recordFrequency(receiver, channel, logDO.getBizType());
            }
            messageMetrics.recordSend(channel, "SUCCESS", cost);
            messageMetrics.recordSendSuccess(channel, logDO.getTemplateCode(), logDO.getTenantId());
            messageTraceService.recordTrace(logDO.getMsgId(),
                    MsgTrace.Node.DISPATCH_SUCCESS,
                    "SUCCESS", channel, "发送成功: cost=" + cost + "ms");
            log.info("[Message] 发送成功: msgId={} channel={} receiver={} cost={}ms",
                    logDO.getMsgId(), channel, PiiMasker.maskReceiver(receiver), cost);
            return MessageResult.ok(channel, providerTraceId);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            logDO.setCostMs(cost);
            logDO.setErrorMessage(e.getMessage());
            messageMetrics.recordSendFailure(channel, logDO.getTemplateCode(),
                    logDO.getTenantId(), e.getClass().getSimpleName());
            messageMetrics.recordException(channel, e.getClass().getSimpleName());
            List<String> fallbackChannels = resolveFallbackChannels(matchedRule, channel);
            if (!fallbackChannels.isEmpty()) {
                MessageResult fallback = tryFallbackChain(logDO, fallbackChannels, cost);
                if (fallback != null) {
                    return fallback;
                }
            }
            return handleFailure(logDO, e, cost);
        }
    }

    /**
     * 解析降级通道列表。
     */
    public List<String> resolveFallbackChannels(MsgRouteRule matchedRule, String currentChannel) {
        if (matchedRule == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        String single = matchedRule.getFallbackChannel();
        if (StringUtils.hasText(single)
                && !single.equalsIgnoreCase(currentChannel)) {
            result.add(single.trim().toUpperCase());
        }
        return result;
    }

    /**
     * 按降级链顺序逐个尝试，任一成功即返回。
     */
    public MessageResult tryFallbackChain(MsgLog logDO, List<String> fallbackChannels, long prevCost) {
        String origChannel = logDO.getChannel();
        long accumulatedCost = prevCost;
        List<String> tried = new ArrayList<>();
        tried.add(origChannel);
        for (String fallbackChannel : fallbackChannels) {
            long start = System.currentTimeMillis();
            try {
                logDO.setStatus(MessageStatusEnum.SENDING.name());
                logDO.setChannel(fallbackChannel);
                msgLogMapper.updateById(logDO);
                String providerTraceId = channelRouter.dispatch(logDO);
                long cost = System.currentTimeMillis() - start;
                logDO.setStatus(MessageStatusEnum.SUCCESS.name());
                logDO.setProviderTraceId(providerTraceId);
                logDO.setCostMs(accumulatedCost + cost);
                logDO.setCost(calculateCost(fallbackChannel));
                msgLogMapper.updateById(logDO);
                messageMetrics.recordSend(fallbackChannel, "SUCCESS", cost);
                log.info("[Message] 降级发送成功: msgId={} chain={} final={} cost={}ms",
                        logDO.getMsgId(), tried, fallbackChannel, cost);
                return MessageResult.ok(fallbackChannel, providerTraceId);
            } catch (Exception fe) {
                long cost = System.currentTimeMillis() - start;
                accumulatedCost += cost;
                tried.add(fallbackChannel);
                log.warn("[Message] 降级发送失败: msgId={} fallback={} err={} 继续尝试下一通道",
                        logDO.getMsgId(), fallbackChannel, fe.getMessage());
            }
        }
        logDO.setChannel(origChannel);
        logDO.setErrorMessage(String.join("→", tried) + " 均失败");
        return null;
    }

    /**
     * 失败处理：retryCount < MAX → RETRY + nextRetryAt（指数退避），否则 FAILED。
     */
    public MessageResult handleFailure(MsgLog logDO, Exception e, long cost) {
        int retryCount = logDO.getRetryCount() == null ? 0 : logDO.getRetryCount();
        String maskedReceiver = PiiMasker.maskReceiver(logDO.getReceiver());
        if (!retryStrategyResolver.isMaxRetriesReached(retryCount, logDO.getChannel())) {
            logDO.setStatus(MessageStatusEnum.RETRY.name());
            logDO.setNextRetryAt(retryStrategyResolver.calcNextRetryAt(retryCount, logDO.getChannel()));
            msgLogMapper.updateById(logDO);
            messageMetrics.recordRetry(logDO.getChannel());
            log.warn("[Message] 发送失败转重试: msgId={} channel={} receiver={} retryCount={} nextRetryAt={} err={}",
                    logDO.getMsgId(), logDO.getChannel(), maskedReceiver, retryCount,
                    logDO.getNextRetryAt(), e.getMessage());
            return MessageResult.fail(logDO.getChannel(), "发送失败,已加入重试队列: " + e.getMessage());
        }
        logDO.setStatus(MessageStatusEnum.FAILED.name());
        msgLogMapper.updateById(logDO);
        messageMetrics.recordSend(logDO.getChannel(), "FAILED", cost);
        log.error("[Message] 发送失败(重试耗尽): msgId={} channel={} receiver={} retryCount={} err={}",
                logDO.getMsgId(), logDO.getChannel(), maskedReceiver, retryCount, e.getMessage());
        return MessageResult.fail(logDO.getChannel(), e.getMessage());
    }

    /**
     * 按通道计算单条消息成本。
     */
    public BigDecimal calculateCost(String channel) {
        MessageProperties.CostConfig cfg = messageProperties.getCost();
        if (cfg == null || !cfg.isEnabled() || cfg.getUnitPrices() == null) {
            return BigDecimal.ZERO;
        }
        return cfg.getUnitPrices().getOrDefault(channel, BigDecimal.ZERO);
    }
}
