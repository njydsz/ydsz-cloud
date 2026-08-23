package com.njydsz.userinfo.server.auth;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.userinfo.server.config.GeoIpProperties;
import com.njydsz.userinfo.server.config.UserInfoProperties;

/**
 * 登录风险评分服务。
 *
 * <p>基于多维度因素评估登录请求的风险等级，用于动态调整认证策略（如触发 MFA、要求验证码、拒绝登录）。
 *
 * <p><b>P3-3 地理围栏新增维度：</b>
 *
 * <ul>
 *   <li><b>地理异常</b>：登录地与上次登录地距离超过阈值时，增加风险评分</li>
 * </ul>
 *
 * <p><b>风险评分维度（P1-1: 权重配置化）：</b>
 *
 * <ul>
 *   <li><b>IP 风险</b>：IP 在窗口内失败次数（权重默认 30%，可配置 {@code ydsz.userinfo.risk-ip-weight}）
 *   <li><b>时间异常</b>：登录时间是否在配置的异常时段内（权重默认 20%，时段可配置）
 *   <li><b>设备异常</b>：User-Agent 是否与历史记录匹配（权重默认 25%，可配置）
 *   <li><b>频率异常</b>：配置窗口内多次登录尝试（权重默认 25%，窗口/阈值可配置）
 *   <li><b>地理异常</b>：登录地点与上次登录地距离超过阈值（P3-3 地理围栏）
 * </ul>
 *
 * <p><b>风险等级：</b>
 *
 * <ul>
 *   <li>SAFE（0-30）：正常登录，无需额外验证
 *   <li>MEDIUM（31-60）：可疑登录，建议触发验证码
 *   <li>HIGH（61-80）：高风险登录，强制触发 MFA
 *   <li>CRITICAL（81-100）：极高风险，拒绝登录
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskScoringService {
  /** 风险因子集合初始容量 */
  private static final int FACTORS_INITIAL_CAPACITY = 4;


  /** 风险等级：安全阈值 */
  private static final int SAFE_THRESHOLD = 30;

  /** 风险等级：中等阈值 */
  private static final int MEDIUM_THRESHOLD = 60;

  /** 风险等级：高阈值 */
  private static final int HIGH_THRESHOLD = 80;

  /** P1-1: 风险权重配置 */
  private final UserInfoProperties properties;
  private final GeoIpProperties geoIpProperties;
  private final GeoIpService geoIpService;

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
    List<String> factors = new ArrayList<>(FACTORS_INITIAL_CAPACITY);

    // P1-1: 权重与阈值全部从配置读取（默认值与历史一致，保证行为不变）
    int ipWeight = properties.getRiskIpWeight();
    int timeWeight = properties.getRiskTimeWeight();
    int deviceWeight = properties.getRiskDeviceWeight();
    int frequencyWeight = properties.getRiskFrequencyWeight();

    // 1. IP 风险评分（基于失败次数）
    int ipRisk = calculateIpRisk(recentFailCount, ipWeight);
    if (ipRisk > 0) {
      factors.add("IP风险(" + ipRisk + ")");
    }

    // 2. 时间异常评分
    int timeRisk = calculateTimeRisk(timeWeight);
    if (timeRisk > 0) {
      factors.add("时间异常(" + timeRisk + ")");
    }

    // 3. 设备异常评分
    int deviceRisk = calculateDeviceRisk(isNewDevice, deviceWeight);
    if (deviceRisk > 0) {
      factors.add("设备异常(" + deviceRisk + ")");
    }

    // 4. 频率异常评分
    int frequencyRisk = calculateFrequencyRisk(recentFailCount, frequencyWeight);
    if (frequencyRisk > 0) {
      factors.add("频率异常(" + frequencyRisk + ")");
    }

    int totalScore = ipRisk + timeRisk + deviceRisk + frequencyRisk;
    RiskLevel level = determineRiskLevel(totalScore);

    return new RiskScore(totalScore, level, factors);
  }

  /**
   * 评估登录风险等级（含 P3-3 地理围栏）。
   *
   * <p>在基础风险评分上增加地理围栏维度：当本次登录地与上次登录地距离超过阈值时，
   * 增加 {@code geoIpProperties.riskScoreAnomaly} 分的风险评分。
   *
   * @param command 风险评估参数
   * @return 风险评分结果
   */
  public RiskScore evaluateRiskWithGeo(RiskEvaluateCommand command) {

    // 先执行基础评分
    RiskScore baseScore = evaluateRisk(
        command.username(), command.loginIp(), command.userAgent(),
        command.recentFailCount(), command.isNewDevice());

    // P3-3: 地理围栏检测
    if (!geoIpProperties.isEnabled()
        || command.lastLoginIp() == null || command.lastLoginIp().isBlank()) {
      return baseScore;
    }

    GeoIpService.GeoFenceResult geoResult =
        geoIpService.detectAnomaly(command.loginIp(), command.lastLoginIp());
    if (geoResult.isAnomaly()) {
      int geoAddition = geoResult.getRiskScoreAddition(geoIpProperties.getRiskScoreAnomaly());
      int newScore = Math.min(baseScore.score() + geoAddition, 100);
      RiskLevel newLevel = determineRiskLevel(newScore);

      List<String> newFactors = new ArrayList<>(baseScore.factors());
      newFactors.add("地理异常(" + geoAddition + "): " + geoResult.getDescription());

      log.warn("地理围栏告警: username={}, currentIp={}, lastIp={}, reason={}",
          command.username(), command.loginIp(), command.lastLoginIp(), geoResult.getDescription());

      return new RiskScore(newScore, newLevel, newFactors);
    }

    return baseScore;
  }

  /**
   * 计算 IP 风险分数。
   *
   * @param recentFailCount 最近失败次数
   * @param weight 配置权重
   * @return IP 风险分数（0-weight）
   */
  private int calculateIpRisk(int recentFailCount, int weight) {
    if (recentFailCount <= 0) {
      return 0;
    }
    if (recentFailCount >= 10) {
      return weight;
    }
    return (int) ((double) recentFailCount / 10 * weight);
  }

  /**
   * 计算时间异常分数。
   *
   * <p>登录时间在配置的异常时段（默认凌晨 0-6 点）内视为异常时间段。
   *
   * @param weight 配置权重
   * @return 时间异常分数（0-weight）
   */
  private int calculateTimeRisk(int weight) {
    int hour = LocalDateTime.now().getHour();
    int startHour = properties.getRiskAnomalyStartHour();
    int endHour = properties.getRiskAnomalyEndHour();
    if (isHourInRange(hour, startHour, endHour)) {
      return weight;
    }
    return 0;
  }

  /**
   * 判断小时是否落在异常时段内（支持跨午夜，如 22-6）。
   *
   * @param hour 当前小时（0-23）
   * @param startHour 起始小时
   * @param endHour 结束小时
   * @return true 表示在异常时段内
   */
  private boolean isHourInRange(int hour, int startHour, int endHour) {
    if (startHour == endHour) {
      return hour == startHour;
    }
    if (startHour < endHour) {
      return hour >= startHour && hour < endHour;
    }
    // 跨午夜：如 22-6 → 22 <= hour || hour < 6
    return hour >= startHour || hour < endHour;
  }

  /**
   * 计算设备异常分数。
   *
   * @param isNewDevice 是否新设备
   * @param weight 配置权重
   * @return 设备异常分数（0-weight）
   */
  private int calculateDeviceRisk(boolean isNewDevice, int weight) {
    return isNewDevice ? weight : 0;
  }

  /**
   * 计算频率异常分数。
   *
   * @param recentFailCount 最近失败次数
   * @param weight 配置权重
   * @return 频率异常分数（0-weight）
   */
  private int calculateFrequencyRisk(int recentFailCount, int weight) {
    if (recentFailCount >= properties.getRiskFrequencyThreshold()) {
      return weight;
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
