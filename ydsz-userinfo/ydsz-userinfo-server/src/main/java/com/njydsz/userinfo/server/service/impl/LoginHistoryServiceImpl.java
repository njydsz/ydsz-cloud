package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.njydsz.userinfo.domain.dto.UserLoginHistoryDTO;
import com.njydsz.userinfo.domain.repository.UserLoginHistoryRepository;
import com.njydsz.userinfo.domain.vo.UserLoginHistoryVO;
import com.njydsz.userinfo.server.auth.LoginAttemptCounterService;
import com.njydsz.userinfo.server.config.UserInfoProperties;
import com.njydsz.userinfo.server.service.LoginAttemptContext;
import com.njydsz.userinfo.server.service.LoginHistoryService;

/**
 * 登录历史服务实现
 *
 * <p>提供登录历史记录和 IP 封禁检查功能。
 *
 * <p><b>IP 封禁策略（P1-2/P1-5 收敛）：</b>
 *
 * <ul>
 *   <li>时间窗口：15 分钟（窗口可配置，默认 15 分钟）
 *   <li>失败阈值：20 次
 *   <li>失败计数由 Redis 计数器（{@link LoginAttemptCounterService}）维护，替代原 DB count，
 *       与风险评分引擎共用同一数据源，消除登录主路径 DB 往返
 * </ul>
 *
 * <p><b>性能（P1-2）：</b>登录历史 {@link #recordLoginAttempt} 异步落库（{@code @Async}），
 * 不阻塞登录主流程；异步线程仅消费方法参数，无上下文传播依赖。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginHistoryServiceImpl implements LoginHistoryService {

  /** IP 封禁时间窗口（分钟）：15 分钟 */
  private static final int IP_BLOCK_WINDOW_MINUTES = 15;

  /** IP 封禁失败阈值：15 分钟内失败 20 次 */
  private static final int IP_BLOCK_THRESHOLD = 20;

  private final UserLoginHistoryRepository loginHistoryRepository;
  private final UserInfoProperties properties;
  private final LoginAttemptCounterService loginAttemptCounterService;

  @Override
  @Async
  public void recordLoginAttempt(
      LoginAttemptContext context, String result, String failReason, String userAgent) {
    // Redis 维度：记录 IP 失败计数（供 IP 封禁与风险评分统一读取）
    if ("FAILED".equals(result)
        && context.loginIp() != null
        && !context.loginIp().isBlank()) {
      loginAttemptCounterService.recordIpFail(context.loginIp(), properties.getRiskWindowSeconds());
    }

    // DB 维度：登录历史落库（审计留存），失败不影响主流程
    try {
      UserLoginHistoryDTO history = new UserLoginHistoryDTO();
      history.setUserId(context.userId());
      history.setUsername(context.username());
      history.setLoginIp(context.loginIp());
      history.setLoginResult(result);
      history.setFailReason(failReason);
      history.setUserAgent(userAgent);
      loginHistoryRepository.create(history);
    } catch (Exception e) {
      // 登录历史记录失败不应影响登录主流程
      log.warn(
          "Failed to record login history: username={}, ip={}, error={}",
          context.username(),
          context.loginIp(),
          e.getMessage());
    }
  }

  @Override
  public boolean isIpBlocked(String ip) {
    if (ip == null || ip.isBlank()) {
      return false;
    }
    try {
      int failCount = loginAttemptCounterService.getIpFailCount(ip);
      if (failCount >= IP_BLOCK_THRESHOLD) {
        log.warn("IP blocked due to too many failed attempts: ip={}, failCount={}", ip, failCount);
        return true;
      }
    } catch (Exception e) {
      log.warn("Failed to check IP block status: ip={}, error={}", ip, e.getMessage());
    }
    return false;
  }

  @Override
  public List<UserLoginHistoryVO> getRecentLogins(String userId, int limit) {
    if (userId == null || userId.isBlank()) {
      return List.of();
    }

    try {
      return loginHistoryRepository.findRecentByUserId(userId, Math.min(limit, 100));
    } catch (Exception e) {
      log.warn("Failed to query recent logins: userId={}, error={}", userId, e.getMessage());
      return List.of();
    }
  }
}
