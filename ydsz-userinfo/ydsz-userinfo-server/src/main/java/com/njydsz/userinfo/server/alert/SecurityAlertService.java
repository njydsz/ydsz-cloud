package com.njydsz.userinfo.server.alert;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.alert.SecurityAlert;
import com.njydsz.userinfo.domain.alert.SecurityAlertRepository;
import com.njydsz.userinfo.server.config.UserInfoProperties;

/**
 * 安全告警服务。
 *
 * <p>基于风险评分评估异常行为并触发告警通知，实现告警去重和频率控制。
 *
 * <p><b>告警去重策略：</b>
 *
 * <ul>
 *   <li>同一用户同一类型告警：5 分钟内不重复触发</li>
 *   <li>同一 IP 同一类型告警：3 分钟内不重复触发</li>
 *   <li>高风险和严重告警不受去重限制</li>
 * </ul>
 *
 * <p><b>告警频率控制：</b>
 *
 * <ul>
 *   <li>Redis 缓存已发送告警标记，防止告警风暴</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 2.18.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAlertService {

  /** 告警去重标记 Redis Key 前缀 */
  private static final String ALERT_DEDUP_KEY_PREFIX = "userinfo:alert:dedup:";

  private final SecurityAlertRepository alertRepository;
  private final List<AlertNotificationChannel> notificationChannels;
  private final RedisStringOps redisStringOps;
  private final UserInfoProperties properties;

  /**
   * 触发账号锁定告警。
   *
   * @param userId 用户 ID
   * @param username 用户名
   * @param lockDuration 锁定时长（分钟）
   * @param reason 锁定原因
   */
  public void triggerAccountLockedAlert(
      String userId, String username, long lockDuration, String reason) {
    SecurityAlert.RiskLevel riskLevel = lockDuration > 60 || lockDuration == -1
        ? SecurityAlert.RiskLevel.HIGH
        : SecurityAlert.RiskLevel.MEDIUM;

    String title = String.format("账号锁定告警：用户 %s 已被锁定", username);
    String content = String.format(
        "用户 %s（ID: %s）因 %s 被锁定，锁定时长 %d 分钟",
        username, userId, reason, lockDuration);

    createAndSendAlert(
        SecurityAlert.AlertType.ACCOUNT_LOCKED,
        riskLevel,
        userId,
        username,
        null,
        title,
        content);
  }

  /**
   * 触发账号封禁告警。
   *
   * @param userId 用户 ID
   * @param username 用户名
   * @param banType 封禁类型
   * @param reason 封禁原因
   * @param bannedBy 操作者
   */
  public void triggerAccountBannedAlert(
      String userId, String username, String banType, String reason, String bannedBy) {
    boolean isPermanent = "PERMANENT".equals(banType);
    SecurityAlert.RiskLevel riskLevel = isPermanent
        ? SecurityAlert.RiskLevel.CRITICAL
        : SecurityAlert.RiskLevel.HIGH;

    String title = String.format("账号封禁告警：用户 %s 已被%s封禁",
        username, isPermanent ? "永久" : "临时");
    String content = String.format(
        "用户 %s（ID: %s）因 %s 被 %s %s封禁",
        username, userId, reason, bannedBy, isPermanent ? "永久" : "临时");

    createAndSendAlert(
        SecurityAlert.AlertType.ACCOUNT_BANNED,
        riskLevel,
        userId,
        username,
        null,
        title,
        content);
  }

  /**
   * 触发 MFA 验证失败告警。
   *
   * @param userId 用户 ID
   * @param username 用户名
   * @param mfaType MFA 类型
   * @param reason 失败原因
   * @param sourceIp 来源 IP
   */
  public void triggerMfaFailedAlert(
      String userId, String username, String mfaType, String reason, String sourceIp) {
    // 检查是否需要触发告警：短时间内多次 MFA 失败才告警
    if (!shouldAlertMfaFailed(userId, sourceIp)) {
      return;
    }

    SecurityAlert.RiskLevel riskLevel = SecurityAlert.RiskLevel.MEDIUM;

    String title = String.format("MFA 验证失败告警：用户 %s 多次验证失败", username);
    String content = String.format(
        "用户 %s（ID: %s）使用 %s 进行 MFA 验证失败，原因：%s，来源 IP：%s",
        username, userId, mfaType, reason, sourceIp);

    createAndSendAlert(
        SecurityAlert.AlertType.MFA_FAILED,
        riskLevel,
        userId,
        username,
        sourceIp,
        title,
        content);
  }

  /**
   * 触发暴力破解告警（同一 IP 多次登录失败）。
   *
   * @param sourceIp 攻击来源 IP
   * @param failCount 失败次数
   * @param targetedUsernames 被攻击的用户名列表
   */
  public void triggerBruteForceAlert(String sourceIp, int failCount, List<String> targetedUsernames) {
    SecurityAlert.RiskLevel riskLevel = failCount >= 20
        ? SecurityAlert.RiskLevel.CRITICAL
        : SecurityAlert.RiskLevel.HIGH;

    String title = String.format("暴力破解告警：IP %s 多次登录失败", sourceIp);
    String content = String.format(
        "来源 IP %s 短时间内登录失败 %d 次，涉及用户：%s",
        sourceIp, failCount, String.join(", ", targetedUsernames));

    createAndSendAlert(
        SecurityAlert.AlertType.BRUTE_FORCE,
        riskLevel,
        null,
        null,
        sourceIp,
        title,
        content);
  }

  /**
   * 触发密码喷洒告警（多用户同一 IP 失败）。
   *
   * @param sourceIp 攻击来源 IP
   * @param targetedUsernames 被尝试的用户名列表
   */
  public void triggerPasswordSprayAlert(String sourceIp, List<String> targetedUsernames) {
    SecurityAlert.RiskLevel riskLevel = SecurityAlert.RiskLevel.CRITICAL;

    String title = String.format("密码喷洒告警：IP %s 尝试多个账号", sourceIp);
    String content = String.format(
        "来源 IP %s 短时间内尝试登录 %d 个不同账号：%s",
        sourceIp, targetedUsernames.size(), String.join(", ", targetedUsernames));

    createAndSendAlert(
        SecurityAlert.AlertType.PASSWORD_SPRAY,
        riskLevel,
        null,
        null,
        sourceIp,
        title,
        content);
  }

  /**
   * 触发异常登录告警（新设备 + 异常时段）。
   *
   * @param userId 用户 ID
   * @param username 用户名
   * @param sourceIp 来源 IP
   * @param userAgent User-Agent
   */
  public void triggerAnomalousLoginAlert(
      String userId, String username, String sourceIp, String userAgent) {
    String title = String.format("异常登录告警：用户 %s 从新设备/异常时段登录", username);
    String content = String.format(
        "用户 %s（ID: %s）从来源 IP %s 使用新设备或异常时段登录，User-Agent：%s",
        username, userId, sourceIp, userAgent);

    createAndSendAlert(
        SecurityAlert.AlertType.ANOMALOUS_LOGIN,
        SecurityAlert.RiskLevel.MEDIUM,
        userId,
        username,
        sourceIp,
        title,
        content);
  }

  // ==================== 内部方法 ====================

  /**
   * * 创建告警并发送通知。
   *
   * @param alertType 告警类型
   * @param riskLevel 风险等级
   * @param userId 用户 ID
   * @param username 用户名
   * @param sourceIp 来源 IP
   * @param title 标题
   * @param content 内容
   */
  private void createAndSendAlert(
      SecurityAlert.AlertType alertType,
      SecurityAlert.RiskLevel riskLevel,
      String userId,
      String username,
      String sourceIp,
      String title,
      String content) {
    // 高风险和严重告警不受去重限制
    if (riskLevel != SecurityAlert.RiskLevel.CRITICAL && riskLevel != SecurityAlert.RiskLevel.HIGH) {
      if (isDuplicate(alertType, userId, sourceIp)) {
        log.debug("告警去重跳过: type={}, userId={}, sourceIp={}", alertType, userId, sourceIp);
        return;
      }
    }

    // 保存告警到数据库
    SecurityAlert alert = new SecurityAlert(
        UUID.randomUUID().toString(),
        alertType,
        riskLevel,
        userId,
        username,
        sourceIp,
        title,
        content,
        SecurityAlert.AlertStatus.PENDING,
        LocalDateTime.now(),
        null,
        null);

    SecurityAlert savedAlert;
    try {
      savedAlert = alertRepository.save(alert);
    } catch (Exception e) {
      log.error("保存告警失败: type={}, userId={}, error={}", alertType, userId, e.getMessage(), e);
      return;
    }

    // 设置去重标记
    setDedupMarker(alertType, userId, sourceIp);

    // 发送通知到各渠道
    sendNotification(savedAlert);
  }

  /**
   * * 判断是否为重复告警。
   *
   * @param alertType 告警类型
   * @param userId 用户 ID
   * @param sourceIp 来源 IP
   * @return true 表示是重复告警
   */
  private boolean isDuplicate(SecurityAlert.AlertType alertType, String userId, String sourceIp) {
    try {
      // 用户维度去重
      if (userId != null) {
        String userKey = ALERT_DEDUP_KEY_PREFIX + "user:" + alertType.name() + ":" + userId;
        if (redisStringOps.hasKey(userKey)) {
          return true;
        }
      }
      // IP 维度去重
      if (sourceIp != null) {
        String ipKey = ALERT_DEDUP_KEY_PREFIX + "ip:" + alertType.name() + ":" + sourceIp;
        if (redisStringOps.hasKey(ipKey)) {
          return true;
        }
      }
    } catch (Exception e) {
      log.warn("检查告警去重标记异常: {}", e.getMessage());
    }
    return false;
  }

  /**
   * 设置去重标记。
   *
   * @param alertType 告警类型
   * @param userId 用户 ID
   * @param sourceIp 来源 IP
   */
  private void setDedupMarker(SecurityAlert.AlertType alertType, String userId, String sourceIp) {
    try {
      if (userId != null) {
        String userKey = ALERT_DEDUP_KEY_PREFIX + "user:" + alertType.name() + ":" + userId;
        redisStringOps.set(userKey, "1", properties.getAlertDedupTtlSeconds());
      }
      if (sourceIp != null) {
        String ipKey = ALERT_DEDUP_KEY_PREFIX + "ip:" + alertType.name() + ":" + sourceIp;
        redisStringOps.set(ipKey, "1", properties.getAlertIpDedupTtlSeconds());
      }
    } catch (Exception e) {
      log.warn("设置告警去重标记异常: {}", e.getMessage());
    }
  }

  /**
   * 检查 MFA 失败是否需要触发告警。
   *
   * <p>同一用户 5 分钟内 MFA 失败 3 次以上，或同一 IP 3 分钟内 MFA 失败 5 次以上时触发告警。
   *
   * @param userId 用户 ID
   * @param sourceIp 来源 IP
   * @return true 表示需要告警
   */
  private boolean shouldAlertMfaFailed(String userId, String sourceIp) {
    try {
      LocalDateTime fiveMinAgo = LocalDateTime.now().minusMinutes(5);
      // 用户维度统计
      if (userId != null) {
        long userFailCount = alertRepository.countRecentAlerts(
            SecurityAlert.AlertType.MFA_FAILED, userId, null, fiveMinAgo);
        if (userFailCount >= 2) {
          // 已有 2 条（含本次将第 3 条），触发告警
          return true;
        }
      }
      // IP 维度统计
      if (sourceIp != null) {
        LocalDateTime threeMinAgo = LocalDateTime.now().minusMinutes(3);
        long ipFailCount = alertRepository.countRecentAlerts(
            SecurityAlert.AlertType.MFA_FAILED, null, sourceIp, threeMinAgo);
        if (ipFailCount >= 4) {
          return true;
        }
      }
    } catch (Exception e) {
      log.warn("检查 MFA 告警条件异常: {}", e.getMessage());
    }
    // 默认为首次或第 2 次失败，继续记录但不告警
    return false;
  }

  /**
   * * 发送通知到所有可用渠道。
   *
   * @param alert 安全告警
   */
  private void sendNotification(SecurityAlert alert) {
    for (AlertNotificationChannel channel : notificationChannels) {
      if (!channel.isAvailable()) {
        continue;
      }
      try {
        channel.sendAlert(alert);
      } catch (Exception e) {
        log.error("发送告警通知失败: channel={}, alertId={}, error={}",
            channel.getChannelName(), alert.id(), e.getMessage(), e);
      }
    }
  }
}
