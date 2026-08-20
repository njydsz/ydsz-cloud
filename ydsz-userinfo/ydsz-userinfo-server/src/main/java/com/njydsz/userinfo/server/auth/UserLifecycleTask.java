package com.njydsz.userinfo.server.auth;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.userinfo.domain.repository.UserAccountRepository;

/**
 * 用户生命周期自动化定时任务。
 *
 * <p>按配置的 cron 表达式定期执行以下自动化处理：
 *
 * <ul>
 *   <li><b>临时封禁到期自动解封：</b>扫描所有临时封禁用户，对已过期的执行解封并清理封禁字段</li>
 *   <li><b>锁定过期自动解锁：</b>扫描所有锁定用户，对已过期的执行解锁（清零失败计数、清除锁定时间）</li>
 * </ul>
 *
 * <p><b>启用条件：</b>{@code ydsz.userinfo.lifecycle.task.enabled=true}。
 *
 * <p><b>cron 表达式：</b>通过 {@code ydsz.userinfo.lifecycle.task.cron} 配置，默认每 10 分钟执行一次。
 *
 * <p><b>实现说明：</b>通过 UserAccountRepository 进行条件查询，
 * 直接执行批量更新，避免逐条查询再更新的 N+1 问题。
 *
 * @author ydsz-team
 * @since 2.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "ydsz.userinfo.lifecycle.task",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
public class UserLifecycleTask {

  /** 临时封禁到期 Redis 缓存清理标记前缀 */
  private static final String BAN_EXPIRED_CACHE_PREFIX = "userinfo:security:banned:count";

  /** 锁定过期 Redis 缓存清理标记前缀 */
  private static final String LOCK_EXPIRED_CACHE_PREFIX = "userinfo:security:locked:count";

  private final UserAccountRepository userAccountRepository;
  private final SessionManager sessionManager;
  private final RedisStringOps redisStringOps;

  /**
   * 定时处理用户生命周期状态流转。
   *
   * <p>每次执行处理两类场景：
   *
   * <ol>
   *   <li>临时封禁到期 → 自动解封（清除封禁字段）</li>
   *   <li>锁定过期 → 自动解锁（清零失败计数、清除锁定时间）</li>
   * </ol>
   */
  @Scheduled(cron = "${ydsz.userinfo.lifecycle.task.cron:0 */10 * * * ?}")
  public void processLifecycleTransitions() {
    log.debug("Scheduled user lifecycle task started");
    try {
      int unbannedCount = processExpiredBans();
      int unlockedCount = processExpiredLocks();

      if (unbannedCount > 0 || unlockedCount > 0) {
        log.info(
            "User lifecycle task completed: unbanned={}, unlocked={}",
            unbannedCount,
            unlockedCount);
      }
    } catch (Exception e) {
      log.error("User lifecycle task failed: {}", e.getMessage(), e);
    }
  }

  /**
   * 处理临时封禁到期的用户，自动解封。
   *
   * <p>扫描条件：ban_type = 'TEMPORARY' 且 ban_expire_at &lt;= 当前时间。
   * 对命中的用户执行解封操作（清除封禁字段）。
   *
   * @return 解封的用户数
   */
  @Transactional(rollbackFor = Exception.class)
  public int processExpiredBans() {
    try {
      List<String> expiredBanIds = userAccountRepository.findIdsByBanTypeAndExpireAtBefore(
          "TEMPORARY", LocalDateTime.now());
      if (expiredBanIds.isEmpty()) {
        return 0;
      }

      int count = 0;
      for (String userId : expiredBanIds) {
        int updated = userAccountRepository.updateBanFields(
            userId, null, null, null, "SYSTEM_CRON");
        if (updated > 0) {
          count++;
        }
      }

      // 清理封禁计数缓存（下次读取时重新计算）
      clearCachedCount(BAN_EXPIRED_CACHE_PREFIX);

      if (count > 0) {
        log.info("Auto-unbanned {} users with expired temporary ban", count);
      }
      return count;
    } catch (Exception e) {
      log.warn("Failed to process expired bans: {}", e.getMessage(), e);
      return 0;
    }
  }

  /**
   * 处理锁定到期的用户，自动解锁。
   *
   * <p>扫描条件：locked_until 非空且 locked_until &lt;= 当前时间。
   * 对命中的用户执行解锁操作（清除锁定时间、清零失败计数）。
   *
   * @return 解锁的用户数
   */
  @Transactional(rollbackFor = Exception.class)
  public int processExpiredLocks() {
    try {
      List<String> expiredLockIds = userAccountRepository.findIdsByLockedUntilBefore(
          LocalDateTime.now());
      if (expiredLockIds.isEmpty()) {
        return 0;
      }

      int count = 0;
      for (String userId : expiredLockIds) {
        int updated = userAccountRepository.unlockAccount(userId);
        if (updated > 0) {
          count++;
        }
      }

      // 清理锁定计数缓存（下次读取时重新计算）
      clearCachedCount(LOCK_EXPIRED_CACHE_PREFIX);

      if (count > 0) {
        log.info("Auto-unlocked {} users with expired lock", count);
      }
      return count;
    } catch (Exception e) {
      log.warn("Failed to process expired locks: {}", e.getMessage(), e);
      return 0;
    }
  }

  /**
   * 清理缓存中的计数。
   *
   * <p>当用户状态发生变更时，清理相关的 Redis 缓存，确保下次读取时重新从数据库获取最新值。
   *
   * @param cacheKeyPrefix 缓存 Key 前缀
   */
  private void clearCachedCount(String cacheKeyPrefix) {
    try {
      redisStringOps.del(cacheKeyPrefix);
    } catch (Exception e) {
      log.warn("Failed to clear cache: key={}, error={}", cacheKeyPrefix, e.getMessage());
    }
  }
}
