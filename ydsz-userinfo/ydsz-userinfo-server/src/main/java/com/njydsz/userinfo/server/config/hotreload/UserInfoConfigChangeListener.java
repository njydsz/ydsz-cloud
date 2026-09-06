package com.njydsz.userinfo.server.config.hotreload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.config.hotreload.ConfigChangeListener;

/**
 * 用户信息中心配置变更监听器（P0-1：接入统一 ConfigChangeBridge）
 *
 * <p>监听用户信息模块相关的配置中心变更（{@code ydsz.userinfo.*}），将 Spring Cloud 配置变更事件桥接到运行时状态更新。
 *
 * <p><b>适用范围：</b>通过 Nacos / Apollo 动态调整用户中心行为参数（如 Token TTL、风险评分权重、会话限制、告警阈值等），无需重启服务即可生效。
 *
 * <p><b>设计说明：</b>本监听器实现 {@link ConfigChangeListener} 接口，由 {@code ydsz-common-config} 的 {@code
 * ConfigChangeBridge} 自动分发配置变更事件。大部分配置属性（如 {@code tokenTtlSeconds}、{@code maxSessionsPerUser}
 * 等）通过 Spring Cloud 原生 {@code @ConfigurationProperties} 自动热加载，本监听器负责发布<b>配置变更通知</b>到业务组件，
 * 实现感知式响应（如日志告警、指标上报、发布领域事件等）。
 *
 * <h3>当前支持的变更响应</h3>
 *
 * <ul>
 *   <li>风险评分权重变更（{@code risk-ip-weight} / {@code risk-device-weight} 等） → 提示风险评分服务使用新权重
 *   <li>安全告警阈值变更（{@code alert-brute-force-threshold} 等） → 提示告警服务使用新阈值
 *   <li>Token 自动续签阈值变更 → 记录日志供运维确认
 *   <li>会话限制变更 → 记录新限制值
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.06
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserInfoConfigChangeListener implements ConfigChangeListener {

  /** 用户信息模块配置属性前缀 */
  private static final String USERINFO_CONFIG_PREFIX = "ydsz.userinfo.";

  /** 风险评分权重配置前缀 */
  private static final String RISK_WEIGHT_PATTERN = "risk-";

  /** 安全告警配置前缀 */
  private static final String ALERT_PATTERN = "alert-";

  /** Token 续签配置键 */
  private static final String TOKEN_AUTO_RENEWAL_THRESHOLD_KEY =
      "ydsz.userinfo.token-auto-renewal-threshold-percent";

  /** 会话限制配置键 */
  private static final String MAX_SESSIONS_PER_USER_KEY = "ydsz.userinfo.max-sessions-per-user";

  /**
   * 接收配置变更回调
   *
   * <p>仅处理 {@code ydsz.userinfo.} 前缀的配置项，其他配置变更忽略。对于不同类别的配置变更，按需执行运行时状态更新。
   *
   * @param key 变更的配置键（如 ydsz.userinfo.risk-ip-weight）
   * @param oldValue 变更前的值
   * @param newValue 变更后的值
   */
  @Override
  public void onChange(String key, String oldValue, String newValue) {
    if (key == null || !key.startsWith(USERINFO_CONFIG_PREFIX)) {
      return;
    }

    log.info("[UserInfo] 配置变更通知: key={}, {} -> {}", key, oldValue, newValue);

    // 风险评分权重变更
    if (key.contains(RISK_WEIGHT_PATTERN)) {
      handleRiskWeightChange(key, oldValue, newValue);
      return;
    }

    // 安全告警阈值变更
    if (key.contains(ALERT_PATTERN)) {
      handleAlertThresholdChange(key, oldValue, newValue);
      return;
    }

    // Token 续签阈值变更
    if (TOKEN_AUTO_RENEWAL_THRESHOLD_KEY.equals(key)) {
      log.info("[UserInfo] Token 自动续签阈值变更: {} -> {}", oldValue, newValue);
      return;
    }

    // 会话限制变更
    if (MAX_SESSIONS_PER_USER_KEY.equals(key)) {
      log.info("[UserInfo] 单用户最大并发会话数变更: {} -> {}", oldValue, newValue);
    }
  }

  /**
   * 处理风险评分权重配置变更
   *
   * <p>提示 {@code RiskScoringService} 后续评分计算将使用新权重。权重评分公式：总分 = Σ(风险因子 × 权重)。
   * 权重变更可能影响 MFA 触发率，建议在低峰期调整。
   *
   * @param key 配置键
   * @param oldValue 旧值
   * @param newValue 新值
   */
  private void handleRiskWeightChange(String key, String oldValue, String newValue) {
    String weightName = key.substring(key.lastIndexOf('.') + 1);
    log.info("[UserInfo] 风险评分权重变更: {} = {} -> {}", weightName, oldValue, newValue);
  }

  /**
   * 处理安全告警阈值配置变更
   *
   * <p>提示安全告警服务后续检测将使用新阈值。阈值调整可能影响告警频率，建议在低峰期操作。
   *
   * @param key 配置键
   * @param oldValue 旧值
   * @param newValue 新值
   */
  private void handleAlertThresholdChange(String key, String oldValue, String newValue) {
    String thresholdName = key.substring(key.lastIndexOf('.') + 1);
    log.info("[UserInfo] 安全告警阈值变更: {} = {} -> {}", thresholdName, oldValue, newValue);
  }
}
