package com.njydsz.common.lock.core;

/**
 * 锁降级回调接口
 *
 * <p>当分布式锁因 Redis 不可用而降级为本地 {@link java.util.concurrent.locks.ReentrantLock} 时， 触发此回调通知业务方。降级后仅保证单
 * JVM 内的互斥，分布式一致性无法保证。
 *
 * <p>业务方可实现此接口执行自定义逻辑，如：
 *
 * <ul>
 *   <li>发送告警通知（短信/邮件/钉钉）
 *   <li>切换到只读模式
 *   <li>触发业务熔断
 *   <li>记录降级审计日志
 * </ul>
 *
 * <p><b>注意：</b>回调方法必须轻量快速，避免阻塞锁操作主流程。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface LockDegradationCallback {

  /**
   * 锁降级时回调
   *
   * <p>当检测到 Redis 不可用，切换到本地锁模式时触发此方法。
   *
   * @param lockKey 发生降级的锁键
   * @param consecutiveFailures 连续失败次数
   * @param lastErrorMessage 最后一次错误信息（可能为 null）
   */
  void onDegraded(String lockKey, int consecutiveFailures, String lastErrorMessage);

  /**
   * 锁恢复时回调
   *
   * <p>当 Redis 恢复可用，切换回分布式锁模式时触发此方法。
   *
   * @param lockKey 恢复的锁键
   */
  void onRecovered(String lockKey);

  /** 默认空实现（不做任何事） */
  LockDegradationCallback NO_OP =
      new LockDegradationCallback() {
        @Override
        public void onDegraded(String lockKey, int consecutiveFailures, String lastErrorMessage) {
          // 空实现
        }

        @Override
        public void onRecovered(String lockKey) {
          // 空实现
        }
      };
}
