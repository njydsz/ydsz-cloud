package com.njydsz.message.server.channel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.metric.MessageMetrics;

/**
 * 通道综合评分计算器。
 *
 * <p>基于「通道成功率 + 成本 + 用户打开率」三因子加权评分选优，为 {@link
 * ChannelRouter#dispatchWithScore(com.njydsz.common.feign.MessageRequest)} 提供排序依据。
 *
 * <p>评分模型（加权求和，总分 0-100）：
 *
 * <ul>
 *   <li>成功率权重 50%：successRate（0-1）× 100 × 0.5 → 贡献 0-50 分
 *   <li>成本权重 30%：costScore（0-100）× 0.3 → 贡献 0-30 分
 *   <li>用户打开率权重 20%：openRate（0-1）× 100 × 0.2 → 贡献 0-20 分
 * </ul>
 *
 * <p>成功率默认 0.95（无可用指标时兜底），用户打开率默认 0.5（预留扩展口，待用户行为数据接入后替换）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ChannelScoreCalculator {

  /** 成功率默认值（无可用指标时兜底） */
  private static final double DEFAULT_SUCCESS_RATE = 0.95;

  /** 用户打开率默认值（预留扩展口，待用户行为数据接入后替换） */
  private static final double DEFAULT_OPEN_RATE = 0.5;

  private final MessageMetrics messageMetrics;

  private final MessageProperties messageProperties;

  /**
   * 构造器注入。
   *
   * @param messageMetrics 消息指标（用于获取通道实时成功率）
   * @param messageProperties 消息配置（用于获取成本配置）
   */
  public ChannelScoreCalculator(MessageMetrics messageMetrics, MessageProperties messageProperties) {
    this.messageMetrics = messageMetrics;
    this.messageProperties = messageProperties;
  }

  /**
   * 计算单个通道的综合评分。
   *
   * @param channel 通道名称（大写）
   * @param userId 用户 ID（用于查询用户打开率，当前未使用，预留扩展）
   * @param config 评分权重配置，为 null 时使用默认权重
   * @return 通道评分记录
   */
  public ChannelScore score(String channel, String userId, ScoreConfig config) {
    ScoreConfig cfg = config == null ? ScoreConfig.builder().build() : config;

    double successRate = getSuccessRate(channel);
    double costScore = getCostScore(channel);
    double openRate = getUserOpenRate(channel, userId);

    double totalRate =
        successRate * 100 * cfg.getSuccessWeight()
            + costScore * cfg.getCostWeight()
            + openRate * 100 * cfg.getOpenWeight();

    // 保留两位小数
    totalRate = BigDecimal.valueOf(totalRate).setScale(2, RoundingMode.HALF_UP).doubleValue();

    log.debug(
        "[ChannelScoreCalculator] channel={} successRate={} costScore={} openRate={} totalRate={}",
        channel,
        successRate,
        costScore,
        openRate,
        totalRate);

    return new ChannelScore(channel, totalRate, successRate, costScore, openRate);
  }

  /**
   * 对通道列表按综合评分降序排序。
   *
   * @param channels 通道名称列表
   * @param userId 用户 ID
   * @param config 评分权重配置
   * @return 按 totalRate 降序排列的评分列表
   */
  public List<ChannelScore> rankChannels(
      List<String> channels, String userId, ScoreConfig config) {
    List<ChannelScore> scores = new ArrayList<>(channels.size());
    for (String channel : channels) {
      scores.add(score(channel, userId, config));
    }
    scores.sort(Comparator.comparingDouble(ChannelScore::totalRate).reversed());
    return scores;
  }

  /**
   * 获取通道最近成功率。
   *
   * <p>当前 Micrometer 指标仅支持写入，不支持实时查询。预留扩展口：后续可注入 MeterRegistry
   * 按通道聚合最近 5 分钟的 success/total 比率。无可用指标时返回默认值 {@link #DEFAULT_SUCCESS_RATE}。
   *
   * @param channel 通道名称
   * @return 成功率（0-1）
   */
  double getSuccessRate(String channel) {
    // TODO: 后续可注入 MeterRegistry，按 channel 标签聚合最近 5 分钟的 send.total{success} / send.total{*}
    // 当前无可用数据源，返回默认值
    return DEFAULT_SUCCESS_RATE;
  }

  /**
   * 获取通道成本评分。
   *
   * <p>normalizedCost = unitPrice / maxUnitPrice，costScore = (1 - normalizedCost) × 100。
   * 成本越高评分越低，免费通道得满分 100。
   *
   * @param channel 通道名称
   * @return 成本评分（0-100）
   */
  double getCostScore(String channel) {
    MessageProperties.CostConfig costConfig = messageProperties.getCost();
    if (costConfig == null) {
      return 100.0;
    }
    Map<String, BigDecimal> unitPrices = costConfig.getUnitPrices();
    if (unitPrices == null || unitPrices.isEmpty()) {
      return 100.0;
    }

    BigDecimal unitPrice =
        unitPrices.getOrDefault(channel.toUpperCase(), BigDecimal.ZERO);
    if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) == 0) {
      return 100.0;
    }

    BigDecimal maxUnitPrice =
        unitPrices.values().stream()
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ONE);

    if (maxUnitPrice.compareTo(BigDecimal.ZERO) == 0) {
      return 100.0;
    }

    double normalizedCost =
        unitPrice.divide(maxUnitPrice, 4, RoundingMode.HALF_UP).doubleValue();
    double costScore = (1.0 - normalizedCost) * 100;

    return BigDecimal.valueOf(costScore)
        .setScale(2, RoundingMode.HALF_UP)
        .doubleValue();
  }

  /**
   * 获取用户在该通道的历史打开率。
   *
   * <p>预留扩展口：后续可接入用户行为日志表，按 userId + channel 统计历史打开率。
   * 当前返回默认值 {@link #DEFAULT_OPEN_RATE}。
   *
   * @param channel 通道名称
   * @param userId 用户 ID
   * @return 用户打开率（0-1）
   */
  double getUserOpenRate(String channel, String userId) {
    // TODO: 接入用户行为数据后替换为实际查询逻辑
    return DEFAULT_OPEN_RATE;
  }

  /**
   * 通道综合评分记录。
   *
   * @param channel 通道名称
   * @param totalRate 综合评分总分（0-100）
   * @param successRate 成功率得分（0-1）
   * @param costScore 成本评分（0-100）
   * @param openRate 用户打开率（0-1）
   */
  public record ChannelScore(
      String channel,
      double totalRate,
      double successRate,
      double costScore,
      double openRate) {}

  /**
   * 评分权重配置。
   *
   * <p>默认权重：成功率 50%、成本 30%、用户打开率 20%。权重之和应为 1.0。
   */
  @Data
  @Builder
  public static class ScoreConfig {
    /** 成功率权重（默认 0.5） */
    private double successWeight = 0.5;

    /** 成本权重（默认 0.3） */
    private double costWeight = 0.3;

    /** 用户打开率权重（默认 0.2） */
    private double openWeight = 0.2;
  }
}
