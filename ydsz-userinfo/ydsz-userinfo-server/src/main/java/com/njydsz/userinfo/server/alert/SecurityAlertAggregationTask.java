package com.njydsz.userinfo.server.alert;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.repository.UserLoginHistoryRepository;
import com.njydsz.userinfo.domain.vo.UserLoginHistoryVO;
import com.njydsz.userinfo.server.config.UserInfoProperties;

/**
 * 安全告警聚合分析定时任务。
 *
 * <p>定期分析登录历史记录，检测暴力破解、密码喷洒等攻击模式，并触发告警。
 *
 * <p><b>检测规则：</b>
 *
 * <ul>
 *   <li><b>暴力破解检测：</b>同一 IP 在 5 分钟内登录失败达到配置阈值以上</li>
 *   <li><b>密码喷洒检测：</b>同一 IP 在 5 分钟内尝试登录达到配置阈值以上不同用户</li>
 * </ul>
 *
 * <p><b>启用条件：</b>{@code ydsz.userinfo.security.alert.aggregation.enabled=true}。
 *
 * <p><b>cron 表达式：</b>通过 {@code ydsz.userinfo.security.alert.aggregation.cron} 配置，默认每 5 分钟执行一次。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "ydsz.userinfo.security.alert.aggregation",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
public class SecurityAlertAggregationTask {

  /** 检测分析时间窗口（分钟） */
  private static final int ANALYSIS_WINDOW_MINUTES = 5;

  /** 单次聚合提取的被攻击用户名上限 */
  private static final int MAX_TARGETED_USERNAMES = 20;

  private final UserLoginHistoryRepository loginHistoryRepository;
  private final SecurityAlertService securityAlertService;
  private final UserInfoProperties properties;

  /**
   * 定时分析登录历史，检测异常登录模式。
   *
   * <p>每次执行分析最近 5 分钟的登录失败记录，按 IP 聚合统计，识别潜在的攻击行为。
   */
  @Scheduled(cron = "${ydsz.userinfo.security.alert.aggregation.cron:0 */5 * * * ?}")
  public void analyzeLoginPatterns() {
    log.debug("Security alert aggregation task started");
    try {
      detectBruteForce();
      detectPasswordSpray();
    } catch (Exception e) {
      log.error("Security alert aggregation task failed: {}", e.getMessage(), e);
    }
  }

  /**
   * 检测暴力破解攻击。
   *
   * <p>统计每个 IP 在时间窗口内的登录失败次数，超过阈值则触发告警。
   */
  private void detectBruteForce() {
    try {
      LocalDateTime since = LocalDateTime.now().minusMinutes(ANALYSIS_WINDOW_MINUTES);
      List<UserLoginHistoryVO> recentFails = loginHistoryRepository.findRecentFailedLogins(since, 1000);

      // 按 IP 分组统计
      Map<String, List<UserLoginHistoryVO>> ipGroups = recentFails.stream()
          .filter(h -> h.getLoginIp() != null && !h.getLoginIp().isBlank())
          .collect(Collectors.groupingBy(UserLoginHistoryVO::getLoginIp));

      int threshold = properties.getAlertBruteForceThreshold();
      for (Map.Entry<String, List<UserLoginHistoryVO>> entry : ipGroups.entrySet()) {
        String ip = entry.getKey();
        List<UserLoginHistoryVO> fails = entry.getValue();

        if (fails.size() >= threshold) {
          // 提取被攻击的用户名列表
          List<String> targetedUsernames = fails.stream()
              .map(UserLoginHistoryVO::getUsername)
              .distinct()
              .limit(MAX_TARGETED_USERNAMES)
              .toList();

          log.warn(
              "暴力破解检测: IP={}, 失败次数={}, 涉及用户数={}",
              ip, fails.size(), targetedUsernames.size());

          securityAlertService.triggerBruteForceAlert(ip, fails.size(), targetedUsernames);
        }
      }
    } catch (Exception e) {
      log.warn("暴力破解检测异常: {}", e.getMessage(), e);
    }
  }

  /**
   * 检测密码喷洒攻击。
   *
   * <p>统计每个 IP 在时间窗口内尝试登录的不同用户数，超过阈值则触发告警。
   */
  private void detectPasswordSpray() {
    try {
      LocalDateTime since = LocalDateTime.now().minusMinutes(ANALYSIS_WINDOW_MINUTES);
      List<UserLoginHistoryVO> recentFails = loginHistoryRepository.findRecentFailedLogins(since, 1000);

      // 按 IP 分组，统计不同用户数
      Map<String, List<UserLoginHistoryVO>> ipGroups = recentFails.stream()
          .filter(h -> h.getLoginIp() != null && !h.getLoginIp().isBlank())
          .collect(Collectors.groupingBy(UserLoginHistoryVO::getLoginIp));

      int threshold = properties.getAlertPasswordSprayThreshold();
      for (Map.Entry<String, List<UserLoginHistoryVO>> entry : ipGroups.entrySet()) {
        String ip = entry.getKey();
        List<UserLoginHistoryVO> fails = entry.getValue();

        // 统计该 IP 尝试的不同用户数
        List<String> uniqueUsernames = fails.stream()
            .map(UserLoginHistoryVO::getUsername)
            .distinct()
            .toList();

        if (uniqueUsernames.size() >= threshold) {
          log.warn(
              "密码喷洒检测: IP={}, 尝试用户数={}, 失败次数={}",
              ip, uniqueUsernames.size(), fails.size());

          securityAlertService.triggerPasswordSprayAlert(ip, uniqueUsernames);
        }
      }
    } catch (Exception e) {
      log.warn("密码喷洒检测异常: {}", e.getMessage(), e);
    }
  }
}
