package com.njydsz.userinfo.server.auth;

import java.time.Duration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.security.DigestUtils;

/**
 * 登录尝试计数器（P1-2/P1-5 收敛）。
 *
 * <p>将登录风险因子（IP 失败次数、设备是否已见）从 DB 查询收敛为 Redis 计数器/标记：
 * 消除登录主路径上的 DB 往返，同时为 IP 封禁、风险评分提供统一的数据源（单一决策点），
 * 避免原先「DB 计数 + 事件聚合器 + 评分引擎」三套独立介质的不一致。
 *
 * <p><b>Redis Key 设计：</b>
 *
 * <pre>
 *   userinfo:risk:ip-fail:{ip}         →  Long     IP 窗口内失败次数（INCR + EXPIRE）
 *   userinfo:risk:device:{userId}:{ua} →  "1"      设备已见标记（SETNX + EXPIRE，ua 为 SHA-256 摘要）
 * </pre>
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see RedisStringOps Redis 字符串操作
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptCounterService {

  /** IP 失败计数 Redis Key 前缀 */
  private static final String IP_FAIL_KEY_PREFIX = "userinfo:risk:ip-fail:";

  /** 设备已见标记 Redis Key 前缀 */
  private static final String DEVICE_KEY_PREFIX = "userinfo:risk:device:";

  /** 设备标记值 */
  private static final String SEEN_VALUE = "1";

  private final RedisStringOps redisStringOps;

  /**
   * 记录一次 IP 登录失败（INCR 自增，首次写入时设置窗口 TTL）。
   *
   * @param ip 来源 IP
   * @param windowSeconds 统计窗口（秒）
   */
  public void recordIpFail(String ip, long windowSeconds) {
    if (ip == null || ip.isBlank()) {
      return;
    }
    try {
      String key = buildIpFailKey(ip);
      redisStringOps.incr(key, 1);
      redisStringOps.expire(key, Duration.ofSeconds(windowSeconds));
    } catch (Exception e) {
      log.warn("Failed to record ip fail count: ip={}, error={}", ip, e.getMessage(), e);
    }
  }

  /**
   * 读取 IP 在窗口内的失败次数。
   *
   * @param ip 来源 IP
   * @return 失败次数，无记录或异常时返回 0
   */
  public int getIpFailCount(String ip) {
    if (ip == null || ip.isBlank()) {
      return 0;
    }
    try {
      Long count = redisStringOps.get(buildIpFailKey(ip), Long.class);
      return count != null ? Math.toIntExact(count) : 0;
    } catch (Exception e) {
      log.warn("Failed to read ip fail count: ip={}, error={}", ip, e.getMessage(), e);
      return 0;
    }
  }

  /**
   * 标记设备为已见过（SETNX + TTL，仅首次写入有效）。
   *
   * @param userId 用户 ID
   * @param userAgent 用户代理（SHA-256 摘要后作 key 段，避免超长 key）
   * @param windowSeconds 标记有效期（秒）
   */
  public void markDeviceSeen(String userId, String userAgent, long windowSeconds) {
    if (userId == null || userId.isBlank() || userAgent == null || userAgent.isBlank()) {
      return;
    }
    try {
      redisStringOps.setIfAbsent(buildDeviceKey(userId, userAgent), SEEN_VALUE, windowSeconds);
    } catch (Exception e) {
      log.warn("Failed to mark device seen: userId={}, error={}", userId, e.getMessage(), e);
    }
  }

  /**
   * 判断是否为未见过的新设备。
   *
   * @param userId 用户 ID
   * @param userAgent 用户代理
   * @return true 表示窗口内未见过的设备（可能为新设备）
   */
  public boolean isNewDevice(String userId, String userAgent) {
    if (userId == null || userId.isBlank() || userAgent == null || userAgent.isBlank()) {
      return false;
    }
    try {
      return !redisStringOps.hasKey(buildDeviceKey(userId, userAgent));
    } catch (Exception e) {
      log.warn("Failed to check device seen: userId={}, error={}", userId, e.getMessage(), e);
      return false;
    }
  }

  private String buildIpFailKey(String ip) {
    return IP_FAIL_KEY_PREFIX + ip;
  }

  private String buildDeviceKey(String userId, String userAgent) {
    return DEVICE_KEY_PREFIX + userId + ":" + DigestUtils.sha256Hex(userAgent);
  }
}
