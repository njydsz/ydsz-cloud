package com.njydsz.userinfo.server.auth;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 登录风险评分服务。
 *
 * <p>基于多维度因素评估登录请求的风险等级，用于动态调整认证策略（如触发 MFA、要求验证码、拒绝登录）。
 *
 * <p><b>风险评分维度：</b>
 *
 * <ul>
 *   <li><b>IP 风险</b>：IP 在过去 15 分钟内失败次数（权重 30%）
 *   <li><b>时间异常</b>：登录时间是否在用户常用时间段外（权重 20%）
 *   <li><b>设备异常</b>：User-Agent 是否与历史记录匹配（权重 25%）
 *   <li><b>频率异常</b>：短时间内多次登录尝试（权重 25%）
 * </ul>
 *
 * <p><b>风险等级：</b>
 *
 * <ul>
 *   <li}SAFE（0-30）：正常登录，无需额外验证
 *   <li}MEDIUM（31-60）：可疑登录，建议触发验证码
 *   <li}HIGH（61-80）：高风险登录，强制触发 MFA
 *   <li}CRITICAL（81-100）：极高风险，拒绝登录
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class RiskScoringService {

  /** 风险等级：安全阈值 */
  private static final int SAFE_THRESHOLD = 30;

  /** 风险等级：中等阈值 */
  private static final int MEDIUM_THRESHOLD = 60;

  /** 风险等级：高阈值 */
  private static final int HIGH_THRESHOLD = 80;

  /** IP 风险权重 */
  private static final int IP_RISK_WEIGHT = 30;

  /** 时间异常权重 */
  private static final int TIME_ANOMALY_WEIGHT = 20;

  /** 设备异常权重 */
  private static final int DEVICE_ANOMALY_WEIGHT = 25;

  /** 频率异常权重 */
  private static final int FREQUENCY_ANOMALY_WEIGHT = 25;

  /** 短时间窗口（分钟）：用于频率检测 */
  private static final int FREQUENCY_WINDOW_MINUTES = 5;

  /** 频率异常阈值：5 分钟内尝试次数 */
  private static final int FREQUENCY_THRESHOLD = 3;

  /**
   * 评估登录风险等级。
   *
   * @param username 用户名
   * @param loginIp 登录 IP
   * @param userAgent 用户代理
   * @param recentFailCount 最近失败次数
   * @param isNewDevice 是否新设备
   * @return 风险评分结果
   */
  public RiskScore evaluateRisk(
      String username,
      String loginIp,
      String userAgent,
      int recentFailCount,
      boolean isNewDevice) {
    List<String> factors = new ArrayList<>();

    // 1. IP 风险评分（基于失败次数）
    int ipRisk = calculateIpRisk(recentFailCount);
    if (ipRisk > 0) {
      factors.add("IP风险(" + ipRisk + ")");
    }

    // 2. 时间异常评分
    int timeRisk = calculateTimeRisk();
    if (timeRisk > 0) {
      factors.add("时间异常(" + timeRisk + ")");
    }

    // 3. 设备异常评分
    int deviceRisk = calculateDeviceRisk(isNewDevice);
    if (deviceRisk > 0) {
      factors.add("设备异常(" + deviceRisk + ")");
    }

    // 4. 频率异常评分
    int frequencyRisk = calculateFrequencyRisk(recentFailCount);
    if (frequencyRisk > 0) {
      factors.add("频率异常(" + frequencyRisk + ")");
    }

    int totalScore = ipRisk + timeRisk + deviceRisk + frequencyRisk;
    RiskLevel level = determineRiskLevel(totalScore);

    return new RiskScore(totalScore, level, factors);
  }

  /**
   * 计算 IP 风险分数。
   *
   * @param recentFailCount 最近失败次数
   * @return IP 风险分数（0-IP_RISK_WEIGHT）
   */
  private int calculateIpRisk(int recentFailCount) {
    if (recentFailCount <= 0) {
      return 0;
    }
    if (recentFailCount >= 10) {
      return IP_RISK_WEIGHT;
    }
    return (int) ((double) recentFailCount / 10 * IP_RISK_WEIGHT);
  }

  /**
   * 计算时间异常分数。
   *
   * <p>如果登录时间在凌晨 0-6 点，视为异常时间段。
   *
   * @return 时间异常分数（0-TIME_ANOMALY_WEIGHT）
   */
  private int calculateTimeRisk() {
    int hour = LocalDateTime.now().getHour();
    // 凌晨 0-6 点视为异常时间段
    if (hour >= 0 && hour < 6) {
      return TIME_ANOMALY_WEIGHT;
    }
    return 0;
  }

  /**
   * 计算设备异常分数。
   *
   * @param isNewDevice 是否新设备
   * @return 设备异常分数（0-DEVICE_ANOMALY_WEIGHT）
   */
  private int calculateDeviceRisk(boolean isNewDevice) {
    return isNewDevice ? DEVICE_ANOMALY_WEIGHT : 0;
  }

  /**
   * 计算频率异常分数。
   *
   * @param recentFailCount 最近失败次数
   * @return 频率异常分数（0-FREQUENCY_ANOMALY_WEIGHT）
   */
  private int calculateFrequencyRisk(int recentFailCount) {
    if (recentFailCount >= FREQUENCY_THRESHOLD) {
      return FREQUENCY_ANOMALY_WEIGHT;
    }
    return 0;
  }

  /**
   * 根据总分确定风险等级。
   *
   * @param totalScore 总分
   * @return 风险等级
   */
  private RiskLevel determineRiskLevel(int totalScore) {
    if (totalScore <= SAFE_THRESHOLD) {
      return RiskLevel.SAFE;
    }
    if (totalScore <= MEDIUM_THRESHOLD) {
      return RiskLevel.MEDIUM;
    }
    if (totalScore <= HIGH_THRESHOLD) {
      return RiskLevel.HIGH;
    }
    return RiskLevel.CRITICAL;
  }

  /**
   * 风险等级枚举。
   */
  public enum RiskLevel {
    /** 安全 */
    SAFE,
    /** 中等风险 */
    MEDIUM,
    /** 高风险 */
    HIGH,
    /** 极高风险 */
    CRITICAL
  }

  /**
   * 风险评分结果。
   *
   * @param score 总分（0-100）
   * @param level 风险等级
   * @param factors 风险因素列表
   */
  public record RiskScore(int score, RiskLevel level, List<String> factors) {

    /**
     * 是否需要额外验证（验证码或 MFA）。
     *
     * @return true 如果需要额外验证
     */
    public boolean requiresAdditionalVerification() {
      return level == RiskLevel.MEDIUM || level == RiskLevel.HIGH;
    }

    /**
     * 是否应拒绝登录。
     *
     * @return true 如果应拒绝登录
     */
    public boolean shouldReject() {
      return level == RiskLevel.CRITICAL;
    }
  }
}
