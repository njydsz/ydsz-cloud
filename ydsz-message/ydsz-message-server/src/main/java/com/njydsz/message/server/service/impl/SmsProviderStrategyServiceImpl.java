package com.njydsz.message.server.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.id.RandomUtils;
import com.njydsz.message.server.channel.sms.SmsProvider;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.service.core.SmsProviderStrategyService;

/**
 * 多短信服务商策略服务实现。
 *
 * <p>P2-15: 支持四种选择策略，默认轮询。
 *
 * <p>成本统计：使用 Redis 记录各 provider 的日发送量和失败量， key={@code
 * ydsz:sms:stats:{provider}:{yyyyMMdd}}，value=INCR 计数。
 *
 * <p>轮询使用 AtomicInteger 游标，权重使用配置文件中的权重比例， 成本优先按 provider 成本排序，可用性优先跳过连续失败超过阈值的 provider。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsProviderStrategyServiceImpl implements SmsProviderStrategyService {
  /** 默认分页大小 */
  private static final int DEFAULT_PAGE_SIZE = 50;


  /** Redis 模板（服务商日发送量 / 失败量统计） */
  private final RedisStringOps redisStringOps;

  /** OD-4: 成本配置 + P3-3.2: 策略 / 权重配置统一从 MessageProperties 读取 */
  private final MessageProperties messageProperties;

  /** 轮询游标 */
  private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

  /** 本地失败计数（用于可用性优先） */
  private final Map<String, AtomicInteger> failureCount = new ConcurrentHashMap<>();

  /** 连续失败阈值 */
  private static final int FAILURE_THRESHOLD = 5;

  /**
   * 按配置策略从可用服务商中选择一个发送短信。
   *
   * <p>支持轮询/权重/成本优先/可用性优先四种策略（配置非法时降级为轮询），单 provider 直接返回。 无可用 provider 抛 {@code
   * IllegalStateException}。
   *
   * @param availableProviders 当前可用的短信服务商列表
   * @return 选中的服务商
   * @throws IllegalStateException 当 availableProviders 为空时
   */
  @Override
  public SmsProvider selectProvider(List<SmsProvider> availableProviders) {
    if (availableProviders == null || availableProviders.isEmpty()) {
      throw new IllegalStateException("无可用 SMS provider");
    }
    if (availableProviders.size() == 1) {
      return availableProviders.get(0);
    }
    Strategy strategy;
    try {
      strategy = Strategy.valueOf(messageProperties.getSms().getStrategy().toUpperCase());
    } catch (IllegalArgumentException e) {
      strategy = Strategy.ROUND_ROBIN;
    }
    return switch (strategy) {
      case ROUND_ROBIN -> selectRoundRobin(availableProviders);
      case WEIGHTED -> selectWeighted(availableProviders);
      case COST_FIRST -> selectCostFirst(availableProviders);
      case AVAILABILITY_FIRST -> selectAvailabilityFirst(availableProviders);
    };
  }

  /**
   * 记录一次短信发送结果（成功/失败）到 Redis 日统计。
   *
   * <p>按日分片 key={@code ydsz:sms:stats:{provider}:{yyyyMMdd}:{total|failed}} 计数；
   * 失败时累加本地连续失败计数（供可用性优先策略熔断）。统计异常仅 debug 忽略，不影响主流程。
   *
   * @param providerType 服务商类型
   * @param success 本次发送是否成功
   */
  @Override
  public void recordSend(String providerType, boolean success) {
    try {
      String daySuffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
      String totalKey = "ydsz:sms:stats:" + providerType + ":" + daySuffix + ":total";
      redisStringOps.incr(totalKey, 1);
      if (!success) {
        String failKey = "ydsz:sms:stats:" + providerType + ":" + daySuffix + ":failed";
        redisStringOps.incr(failKey, 1);
        failureCount.computeIfAbsent(providerType, k -> new AtomicInteger(0)).incrementAndGet();
      } else {
        failureCount.computeIfAbsent(providerType, k -> new AtomicInteger(0)).set(0);
      }
    } catch (Exception e) {
      log.debug("[SmsStrategy] 统计记录失败(忽略): {}", e.getMessage());
    }
  }

  /**
   * 查询各短信服务商的当日发送统计。
   *
   * <p>返回 aliyun/tencent/mock 三家的 {@code [总量, 成功数, 失败数]}；查询异常时返回部分或空数据， 不抛异常，保证监控面板可用。
   *
   * @return providerType → long[3]{total, success, failed}
   */
  @Override
  public Map<String, long[]> getProviderStats() {
    Map<String, long[]> stats = new HashMap<>(16);
    try {
      String daySuffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
      for (String provider : new String[] {"aliyun", "tencent", "mock"}) {
        String totalKey = "ydsz:sms:stats:" + provider + ":" + daySuffix + ":total";
        String failKey = "ydsz:sms:stats:" + provider + ":" + daySuffix + ":failed";
        String totalStr = redisStringOps.get(totalKey, String.class);
        String failStr = redisStringOps.get(failKey, String.class);
        long total = totalStr != null ? Long.parseLong(totalStr) : 0;
        long failed = failStr != null ? Long.parseLong(failStr) : 0;
        stats.put(provider, new long[] {total, total - failed, failed});
      }
    } catch (Exception e) {
      log.warn("[SmsStrategy] 统计查询失败: {}", e.getMessage(), e);
    }
    return stats;
  }

  // ==================== 策略实现 ====================

  /**
   * 轮询选择。
   *
   * @param providers 可用服务商列表
   * @return 按轮询顺序选中的服务商
   */
  private SmsProvider selectRoundRobin(List<SmsProvider> providers) {
    int idx = Math.abs(roundRobinIndex.getAndIncrement()) % providers.size();
    return providers.get(idx);
  }

  /**
   * 权重选择。
   *
   * @param providers 可用服务商列表
   * @return 按权重随机选中的服务商
   */
  private SmsProvider selectWeighted(List<SmsProvider> providers) {
    Map<String, Integer> weights = parseWeights();
    int totalWeight =
        providers.stream().mapToInt(p -> weights.getOrDefault(p.providerType(), 1)).sum();
    int random = RandomUtils.randomInt(totalWeight);
    int cumulative = 0;
    for (SmsProvider p : providers) {
      cumulative += weights.getOrDefault(p.providerType(), 1);
      if (random < cumulative) {
        return p;
      }
    }
    return providers.get(0);
  }

  /**
   * 成本优先（aliyun < tencent < mock）。
   *
   * @param providers 可用服务商列表
   * @return 单位成本最低的服务商
   */
  private SmsProvider selectCostFirst(List<SmsProvider> providers) {
    return providers.stream()
        .min(
            (a, b) -> {
              int costA = getProviderCost(a.providerType());
              int costB = getProviderCost(b.providerType());
              return Integer.compare(costA, costB);
            })
        .orElse(providers.get(0));
  }

  /**
   * 可用性优先（跳过连续失败超过阈值的 provider）。
   *
   * @param providers 可用服务商列表
   * @return 连续失败未超阈值的服务商，全超阈值时降级返回第一个
   */
  private SmsProvider selectAvailabilityFirst(List<SmsProvider> providers) {
    for (SmsProvider p : providers) {
      AtomicInteger count = failureCount.get(p.providerType());
      if (count == null || count.get() < FAILURE_THRESHOLD) {
        return p;
      }
    }
    // 所有 provider 都超阈值，降级选择第一个
    log.warn("[SmsStrategy] 所有 provider 连续失败超阈值,降级选择第一个");
    return providers.get(0);
  }

  /** 解析权重配置（从 {@link MessageProperties.SmsConfig#getWeights()} 读取）。 */
  private Map<String, Integer> parseWeights() {
    Map<String, Integer> weights = new HashMap<>(16);
    String weightsConfig = messageProperties.getSms().getWeights();
    if (weightsConfig != null && !weightsConfig.isBlank()) {
      for (String pair : weightsConfig.split(",")) {
        String[] kv = pair.split(":");
        if (kv.length == 2) {
          try {
            weights.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
          } catch (NumberFormatException ignored) {
            log.debug("Caught exception (ignored): {}", ignored.getMessage());
          }
        }
      }
    }
    return weights;
  }

  /**
   * OD-4: 从 MessageProperties.CostConfig 读取成本，消除硬编码 switch。
   *
   * @param providerType 服务商类型（如 ALIYUN、TENCENT、MOCK）
   * @return 单位成本（以毫为单位），无配置时返回默认值
   */
  private int getProviderCost(String providerType) {
    // OD-4: 从配置读取成本，无配置时默认 50
    BigDecimal cost = messageProperties.getCost().getUnitPrices().get(providerType.toUpperCase());
    if (cost != null) {
      return cost.multiply(BigDecimal.valueOf(1000)).intValue();
    }
    return DEFAULT_PAGE_SIZE;
  }
}
