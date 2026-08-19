package com.njydsz.message.server.channel;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.sentry.resilience.CircuitBreaker;
import com.njydsz.message.domain.entity.core.MsgLog;
import com.njydsz.message.server.channel.ChannelScoreCalculator.ChannelScore;
import com.njydsz.message.server.channel.ChannelScoreCalculator.ScoreConfig;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.metric.MessageMetrics;

/**
 * 消息通道路由器。
 *
 * <p>启动时通过 {@link ApplicationContext#getBeansOfType(Class)} 收集所有 {@link MessageChannel} Bean，按
 * {@link MessageChannel#channelType()} 大写形式 注册到内部缓存，供 {@link #route(String)} 与 {@link
 * #dispatch(MessageRequest)} 使用。
 *
 * <p>通道开关由 {@code ydsz.message.channel-enabled.*} 配置控制， 通过 {@link
 * MessageProperties#getChannelEnabled()} 读取。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelRouter {

  /** Spring 上下文，用于收集通道 Bean */
  private final ApplicationContext applicationContext;

  /** 消息配置，用于读取通道开关 */
  private final MessageProperties messageProperties;

  /** P2-4: 通道级错误指标采集 */
  private final MessageMetrics messageMetrics;

  /** 通道综合评分计算器，用于 dispatchWithScore 评分选优 */
  private final ChannelScoreCalculator channelScoreCalculator;

  /** P0-4: 通道缓存改为 ConcurrentHashMap，保证多线程并发读写的可见性与安全性 */
  private final Map<String, MessageChannel> channelCache = new ConcurrentHashMap<>();

  /** P0-4: 熔断器缓存改为 ConcurrentHashMap */
  private final Map<String, CircuitBreaker> breakerCache = new ConcurrentHashMap<>();

  /**
   * D-5: 从 MessageProperties.CircuitBreakerConfig 构建熔断配置，消除硬编码。
   *
   * <p>在 {@link #initChannels()} 中构建，确保配置已注入完成。
   */
  private CircuitBreakerConfig buildCircuitBreakerConfig() {
    MessageProperties.CircuitBreakerConfig cb = messageProperties.getCircuitBreaker();
    return CircuitBreakerConfig.custom()
        .failureRateThreshold(cb.getFailureRateThreshold())
        .slowCallRateThreshold(cb.getSlowCallRateThreshold())
        .slowCallDurationThreshold(Duration.ofSeconds(cb.getSlowCallDurationSeconds()))
        .waitDurationInOpenState(Duration.ofSeconds(cb.getWaitDurationInOpenStateSeconds()))
        .permittedNumberOfCallsInHalfOpenState(cb.getPermittedNumberOfCallsInHalfOpenState())
        .slidingWindowSize(cb.getSlidingWindowSize())
        .minimumNumberOfCalls(cb.getMinimumNumberOfCalls())
        .build();
  }

  /**
   * 收集所有 MessageChannel Bean 并按通道类型注册,同时为每个通道创建独立熔断器。
   *
   * <p>使用 {@link com.njydsz.common.sentry.resilience.CircuitBreaker} 封装 Resilience4j，
   * 符合《云顶编码规范》：业务模块不得直接使用 Resilience4j。
   */
  @PostConstruct
  public void initChannels() {
    Map<String, MessageChannel> beans = applicationContext.getBeansOfType(MessageChannel.class);
    io.github.resilience4j.circuitbreaker.CircuitBreakerConfig r4jConfig =
        buildCircuitBreakerConfig();
    CircuitBreakerRegistry r4jRegistry = CircuitBreakerRegistry.of(r4jConfig);
    for (MessageChannel channel : beans.values()) {
      String type = channel.channelType() == null ? "" : channel.channelType().trim().toUpperCase();
      if (type.isEmpty()) {
        log.warn("[ChannelRouter] 跳过 channelType 为空的通道: {}", channel.getClass().getName());
        continue;
      }
      channelCache.put(type, channel);
      // 使用 common-sentry CircuitBreaker 封装 Resilience4j，符合编码规范
      breakerCache.put(type, new CircuitBreaker("ch-" + type, r4jConfig, r4jRegistry));
    }
    log.info("[ChannelRouter] 已注册 {} 个消息通道(含熔断器): {}", channelCache.size(), channelCache.keySet());
  }

  /**
   * 路由到指定通道，缺失时抛 {@link SysException}。
   *
   * @param channel 通道类型字符串（大小写无关）
   * @return 对应通道实例
   * @throws SysException 通道为空或不存在
   */
  public MessageChannel route(String channel) {
    if (channel == null || channel.isBlank()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("消息通道不能为空")
          .build();
    }
    MessageChannel target = channelCache.get(channel.trim().toUpperCase());
    if (target == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("不支持的消息通道: " + channel)
          .build();
    }
    return target;
  }

  /**
   * 路由并发送消息：记录开始时间，发送后输出耗时日志，异常捕获返回 fail。
   *
   * @param request 消息请求
   * @return 发送结果
   */
  public MessageResult dispatch(MessageRequest request) {
    String channel = request.getChannel();
    MessageChannel target = route(channel);
    CircuitBreaker breaker = breakerCache.get(channel.trim().toUpperCase());
    // 熔断开启时快速失败,不调用真实通道
    if (breaker != null && !breaker.canExecute()) {
      log.warn("[ChannelRouter] 通道熔断中,快速失败: channel={} state={}", channel, breaker.getState());
      messageMetrics.recordChannelError(channel, "CIRCUIT_BREAKER");
      return MessageResult.fail(channel, "通道熔断中,请稍后重试");
    }
    long start = System.currentTimeMillis();
    try {
      MessageResult result = target.send(request);
      long cost = System.currentTimeMillis() - start;
      log.info(
          "[ChannelRouter] channel={} status={} costMs={} cbState={}",
          channel,
          result.isSuccess() ? "SUCCESS" : "FAILED",
          cost,
          breaker == null ? "N/A" : breaker.getState());
      // 业务失败(非异常)也计入熔断失败率
      if (breaker != null) {
        if (result.isSuccess()) {
          breaker.recordSuccess(cost, TimeUnit.MILLISECONDS);
        } else {
          breaker.recordFailure(
              cost, TimeUnit.MILLISECONDS, new RuntimeException(result.getErrorMessage()));
          // P2-4: 记录通道级业务错误指标
          messageMetrics.recordChannelError(channel, "BUSINESS_ERROR");
        }
      } else if (!result.isSuccess()) {
        messageMetrics.recordChannelError(channel, "BUSINESS_ERROR");
      }
      return result;
    } catch (Exception e) {
      long cost = System.currentTimeMillis() - start;
      if (breaker != null) {
        breaker.recordFailure(cost, TimeUnit.MILLISECONDS, e);
      }
      // P2-4: 记录通道级异常指标
      messageMetrics.recordChannelError(channel, "EXCEPTION");
      log.error(
          "[ChannelRouter] channel={} 发送异常 costMs={} cbState={}",
          channel,
          cost,
          breaker == null ? "N/A" : breaker.getState(),
          e);
      // P3-2: 透传 root cause 链，避免包装异常掩盖真实错误原因
      return MessageResult.fail(channel, buildErrorMessageWithCause(e));
    }
  }

  /**
   * 基于评分选优的智能分发：对所有启用通道按综合评分降序排序，依次尝试 dispatch，首次成功即返回。
   *
   * <p>评分模型由 {@link ChannelScoreCalculator} 提供，基于「通道成功率 + 成本 + 用户打开率」三因子加权计算。 全部通道失败时返回最后一个失败结果。
   *
   * @param request 消息请求
   * @return 发送结果（首个成功或最后一个失败）
   */
  public MessageResult dispatchWithScore(MessageRequest request) {
    return dispatchWithScore(request, null);
  }

  /**
   * 基于评分选优的智能分发（带权重配置重载）。
   *
   * <p>对所有启用通道按综合评分降序排序，依次尝试 dispatch：
   *
   * <ol>
   *   <li>从 channelCache 获取所有启用（isChannelEnabled）的通道</li>
   *   <li>调用 {@link ChannelScoreCalculator#rankChannels(List, String, ScoreConfig)} 获取排序后的评分列表</li>
   *   <li>依次调用 {@link #dispatch(MessageRequest)}，将 request.channel 替换为当前评分通道</li>
   *   <li>首次成功即返回，全部失败时返回最后一个失败结果</li>
   * </ol>
   *
   * @param request 消息请求
   * @param scoreConfig 评分权重配置，为 null 时使用默认权重
   * @return 发送结果（首个成功或最后一个失败）
   */
  public MessageResult dispatchWithScore(MessageRequest request, ScoreConfig scoreConfig) {
    if (request == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("消息请求不能为空")
          .build();
    }

    // 1. 获取所有启用的通道
    List<String> enabledChannels = new ArrayList<>();
    for (String channelKey : channelCache.keySet()) {
      if (isChannelEnabled(channelKey)) {
        enabledChannels.add(channelKey);
      }
    }

    if (enabledChannels.isEmpty()) {
      log.warn("[ChannelRouter] dispatchWithScore: 无可用启用通道");
      return MessageResult.fail(null, "无可用启用通道", "无可用启用通道", null);
    }

    // 2. 按评分降序排序
    String userId = request.getReceiver();
    List<ChannelScore> rankedScores =
        channelScoreCalculator.rankChannels(enabledChannels, userId, scoreConfig);

    log.info(
        "[ChannelRouter] dispatchWithScore: 通道评分排序={}",
        rankedScores.stream()
            .map(s -> s.channel() + ":" + s.totalRate())
            .reduce((a, b) -> a + ", " + b)
            .orElse(""));

    // 3. 依次尝试 dispatch，首次成功即返回
    MessageResult lastResult = null;
    for (ChannelScore channelScore : rankedScores) {
      // 构建针对当前评分通道的请求副本
      MessageRequest channelRequest = cloneRequestWithChannel(request, channelScore.channel());
      lastResult = dispatch(channelRequest);
      if (lastResult.isSuccess()) {
        log.info(
            "[ChannelRouter] dispatchWithScore: 通道发送成功 channel={} totalRate={}",
            channelScore.channel(),
            channelScore.totalRate());
        return lastResult;
      }
      log.warn(
          "[ChannelRouter] dispatchWithScore: 通道发送失败尝试下一个 channel={} err={}",
          channelScore.channel(),
          lastResult.getErrorMessage());
    }

    // 4. 全部失败，返回最后一个失败结果
    log.error("[ChannelRouter] dispatchWithScore: 所有通道均失败, lastError={}", lastResult != null ? lastResult.getErrorMessage() : "unknown");
    return lastResult;
  }

  /**
   * 克隆 MessageRequest 并替换为指定通道（避免修改原始请求的通道字段）。
   *
   * @param original 原始请求
   * @param channel 目标通道
   * @return 替换通道后的请求副本
   */
  private MessageRequest cloneRequestWithChannel(MessageRequest original, String channel) {
    MessageRequest copy = new MessageRequest();
    copy.setChannel(channel);
    copy.setReceiver(original.getReceiver());
    copy.setSubject(original.getSubject());
    copy.setContent(original.getContent());
    copy.setBizType(original.getBizType());
    copy.setBizId(original.getBizId());
    copy.setTemplateCode(original.getTemplateCode());
    copy.setMessageId(original.getMessageId());
    copy.setParams(original.getParams());
    copy.setChannelMeta(original.getChannelMeta());
    copy.setScheduledAt(original.getScheduledAt());
    copy.setPriority(original.getPriority());
    copy.setParentMsgId(original.getParentMsgId());
    copy.setCascadeTo(original.getCascadeTo());
    copy.setScenario(original.getScenario());
    return copy;
  }

  /**
   * P3-2: 构造包含 cause 链的错误消息。
   *
   * <p>遍历异常的 cause 链（最多 5 层防御性兜底），将每层异常的 {@code SimpleName: message} 拼接，避免 Spring/HTTP 客户端等包装异常
   * 掩盖真实的底层错误（如 SSLHandshakeException 被 ResourceAccessException 包装）。
   *
   * @param e 顶层异常
   * @return 包含 cause 链的错误消息
   */
  private String buildErrorMessageWithCause(Throwable e) {
    StringBuilder sb = new StringBuilder();
    sb.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
    Throwable cause = e.getCause();
    int depth = 0;
    while (cause != null && depth++ < 5) {
      sb.append(" | caused by: ")
          .append(cause.getClass().getSimpleName())
          .append(": ")
          .append(cause.getMessage());
      cause = cause.getCause();
    }
    return sb.toString();
  }

  /**
   * 基于 {@link MsgLog} 的分发重载：将日志实体转换为 {@link MessageRequest} 后委托 {@link #dispatch(MessageRequest)}
   * 执行，便于上层 service 直接传入日志实体。
   *
   * <p>返回供应商侧追踪 ID（{@code providerTraceId}）；发送失败时抛 {@link SysException}， 由调用方 catch 处理。
   *
   * @param logDO 消息日志实体
   * @return 供应商侧追踪 ID
   * @throws SysException 发送失败
   */
  public String dispatch(MsgLog logDO) {
    if (logDO == null) {
      throw SysException.builder().resultCode(YdszResultCode.BAD_REQUEST).message("消息日志为空").build();
    }
    MessageRequest request = new MessageRequest();
    request.setChannel(logDO.getChannel());
    request.setReceiver(logDO.getReceiver());
    request.setContent(logDO.getContent());
    request.setBizType(logDO.getBizType());
    request.setBizId(logDO.getBizId());
    request.setTemplateCode(logDO.getTemplateCode());
    request.setMessageId(logDO.getMsgId());
    String templateParams = logDO.getTemplateParams();
    if (templateParams != null && !templateParams.isBlank()) {
      try {
        request.setParams(YdszJson.fromJsonToMap(templateParams, String.class, Object.class));
      } catch (Exception e) {
        log.warn(
            "[ChannelRouter] templateParams 解析失败,忽略: msgId={}, err={}",
            logDO.getMsgId(),
            e.getMessage());
      }
    }
    MessageResult result = dispatch(request);
    if (!result.isSuccess()) {
      throw SysException.builder().message(result.getErrorMessage()).build();
    }
    return result.getTraceId();
  }

  /**
   * 判断通道是否启用，结合 {@code ydsz.message.channel-enabled.*} 配置。 配置未显式指定时默认启用。
   *
   * @param channel 通道类型字符串（大小写无关）
   * @return true 表示启用
   */
  public boolean isChannelEnabled(String channel) {
    if (channel == null || channel.isBlank()) {
      return false;
    }
    String key = channel.trim().toUpperCase();
    Map<String, Boolean> enabled = messageProperties.getChannelEnabled();
    if (enabled == null) {
      return true;
    }
    Boolean val = enabled.get(key);
    return val == null || val;
  }

  /**
   * 获取已注册通道的只读视图（供诊断 / 测试使用）。
   *
   * @return 通道缓存只读 Map
   */
  public Map<String, MessageChannel> getChannelCache() {
    return Collections.unmodifiableMap(channelCache);
  }

  /**
   * 获取熔断器缓存的只读视图（供健康检查使用）。
   *
   * @return 熔断器缓存只读 Map
   */
  public Map<String, CircuitBreaker> getBreakerCache() {
    return Collections.unmodifiableMap(breakerCache);
  }
}
