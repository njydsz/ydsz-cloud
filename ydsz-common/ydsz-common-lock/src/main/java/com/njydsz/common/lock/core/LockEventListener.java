package com.njydsz.common.lock.core;

import com.njydsz.common.lock.annotation.LockType;

/**
 * 分布式锁事件监听器
 *
 * <p>提供锁生命周期事件的 SPI 扩展点，业务方可实现此接口感知锁状态变化， 无需修改框架代码。
 *
 * <p>支持的事件类型：
 *
 * <ul>
 *   <li>{@link #onLockAcquired} - 锁获取成功
 *   <li>{@link #onLockReleased} - 锁释放成功
 *   <li>{@link #onLockAcquireTimeout} - 锁获取超时
 *   <li>{@link #onLockRenewalFailed} - 锁续期失败
 * </ul>
 *
 * <p><b>注意：</b>监听器方法必须轻量快速，避免阻塞锁操作主流程。 如需执行耗时操作，请异步处理。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface LockEventListener {

  /**
   * 锁获取成功时回调
   *
   * @param lockKey 锁键
   * @param lockValue 锁值（客户端标识）
   * @param lockType 锁类型
   * @param waitTimeMs 等待耗时（毫秒）
   */
  default void onLockAcquired(
      String lockKey, String lockValue, LockType lockType, long waitTimeMs) {
    // 默认空实现
  }

  /**
   * 锁释放成功时回调
   *
   * @param lockKey 锁键
   * @param lockValue 锁值
   * @param lockType 锁类型
   * @param holdTimeMs 锁持有时间（毫秒）
   */
  default void onLockReleased(
      String lockKey, String lockValue, LockType lockType, long holdTimeMs) {
    // 默认空实现
  }

  /**
   * 锁获取超时时回调
   *
   * @param lockKey 锁键
   * @param lockType 锁类型
   * @param waitTimeMs 等待时间（毫秒）
   */
  default void onLockAcquireTimeout(String lockKey, LockType lockType, long waitTimeMs) {
    // 默认空实现
  }

  /**
   * 锁获取失败时回调（非超时，如被其他持有者占用）
   *
   * @param lockKey 锁键
   * @param lockType 锁类型
   */
  default void onLockAcquireFailed(String lockKey, LockType lockType) {
    // 默认空实现
  }

  /**
   * 锁续期失败时回调
   *
   * @param lockKey 锁键
   * @param lockType 锁类型
   * @param totalFailures 累计续期失败次数
   */
  default void onLockRenewalFailed(String lockKey, LockType lockType, int totalFailures) {
    // 默认空实现
  }

  /** 空实现（不做任何事） */
  LockEventListener NO_OP = new LockEventListener() {};
}
