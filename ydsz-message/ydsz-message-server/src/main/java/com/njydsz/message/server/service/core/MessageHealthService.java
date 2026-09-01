package com.njydsz.message.server.service.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.sentry.resilience.CircuitBreaker;
import com.njydsz.message.domain.vo.ChannelHealthVO;
import com.njydsz.message.domain.vo.SystemHealthVO;
import com.njydsz.message.server.channel.ChannelRouter;
import com.njydsz.message.server.config.MessageProperties;

/**
 * 消息模块健康检查服务。
 *
 * <p>汇总各通道的熔断器状态、启用状态、滑动窗口失败计数等运维关键指标，生成 {@link SystemHealthVO} 供管理后台和监控系统消费。
 *
 * <p><b>健康判定规则：</b>
 *
 * <ul>
 *   <li>任一通道熔断器状态为 OPEN → 整体状态 DEGRADED
 *   <li>所有通道熔断器状态为 CLOSED → 整体状态 UP
 *   <li>无可用通道 → 整体状态 DOWN
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageHealthService {
  /** 计算精度 */
  private static final int SCALE = 4;


  /** 通道路由器（获取通道和熔断器状态） */
  private final ChannelRouter channelRouter;

  /** 消息模块配置属性（获取通道启用状态） */
  private final MessageProperties messageProperties;

  /**
   * 获取系统整体健康状态。
   *
   * @return 系统健康状态视图对象
   */
  public SystemHealthVO getSystemHealth() {
    List<ChannelHealthVO> channelHealths = getChannelHealths();

    int totalChannels = channelHealths.size();
    int enabledChannels = (int) channelHealths.stream().filter(ChannelHealthVO::isEnabled).count();
    int openBreakers = (int) channelHealths.stream()
        .filter(ch -> "OPEN".equals(ch.getCircuitBreakerState()))
        .count();

    String status;
    if (totalChannels == 0) {
      status = "DOWN";
    } else if (openBreakers > 0) {
      status = "DEGRADED";
    } else {
      status = "UP";
    }

    return SystemHealthVO.builder()
        .status(status)
        .totalChannels(totalChannels)
        .enabledChannels(enabledChannels)
        .openBreakers(openBreakers)
        .channels(channelHealths)
        .build();
  }

  /**
   * 获取各通道详细健康状态列表。
   *
   * @return 通道健康状态列表
   */
  public List<ChannelHealthVO> getChannelHealths() {
    List<ChannelHealthVO> result = new ArrayList<>();
    Map<String, CircuitBreaker> breakerCache = channelRouter.getBreakerCache();
    Map<String, Boolean> channelEnabled = messageProperties.getChannelEnabled();

    for (Map.Entry<String, CircuitBreaker> entry : breakerCache.entrySet()) {
      String channel = entry.getKey();
      CircuitBreaker breaker = entry.getValue();

      boolean enabled = channelEnabled == null || channelEnabled.getOrDefault(channel, true);
      int failureCount = breaker.getFailureCount();
      int totalCount = breaker.getTotalCount();
      double failureRate = totalCount > 0
          ? BigDecimal.valueOf((double) failureCount / totalCount)
              .setScale(SCALE, RoundingMode.HALF_UP)
              .doubleValue()
          : 0.0;

      result.add(ChannelHealthVO.builder()
          .channel(channel)
          .enabled(enabled)
          .circuitBreakerState(breaker.getState().name())
          .failureCount(failureCount)
          .totalCount(totalCount)
          .failureRate(failureRate)
          .build());
    }

    return result;
  }
}
