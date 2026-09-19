package com.njydsz.userinfo.server.aspect;

import java.time.Duration;

import com.njydsz.common.safe.annotation.SensitiveLevel;

/**
 * 二级认证 TTL 计算工具（原 AOP 切面已废弃）。
 *
 * <p>保留 {@link #resolveEffectiveTtl} 静态方法供 AuthController 调用，
 * 确保写入 Redis 的 TTL 计算逻辑统一。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class SecondaryAuthAspect {

  /** 关键操作 TTL 占配置值的比例（40%） */
  private static final double CRITICAL_TTL_RATIO = 0.4;

  /** 关键操作 TTL 下限（秒）：60 秒 */
  private static final long CRITICAL_TTL_MIN_SECONDS = 60;

  private SecondaryAuthAspect() {
    // 工具类，禁止实例化
  }

  /**
   * 计算实际生效的 TTL（CRITICAL 级别缩短为配置的 40%，最小 60 秒）。
   *
   * <p>供 AuthController 调用，确保写入 Redis 的 TTL 计算逻辑一致。
   *
   * @param scene 场景标识（预留，当前计算未使用）
   * @param ttlSeconds 配置的有效期（秒）
   * @param level 敏感操作等级
   * @return 实际生效的 TTL
   */
  public static Duration resolveEffectiveTtl(String scene, int ttlSeconds, SensitiveLevel level) {
    if (level == SensitiveLevel.CRITICAL) {
      long criticalTtl = Math.round(ttlSeconds * CRITICAL_TTL_RATIO);
      return Duration.ofSeconds(Math.max(criticalTtl, CRITICAL_TTL_MIN_SECONDS));
    }
    return Duration.ofSeconds(ttlSeconds);
  }
}
