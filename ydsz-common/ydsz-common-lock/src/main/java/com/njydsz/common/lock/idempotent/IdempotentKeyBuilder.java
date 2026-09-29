package com.njydsz.common.lock.idempotent;

import com.njydsz.common.lock.constant.LockConstants;

/**
 * 幂等键构造工具（P1-B）
 *
 * <p>统一网关与服务端的幂等键格式，确保跨模块的幂等标记使用一致的 key 规范，避免 key 冲突和重复。
 *
 * <h3>幂等键格式</h3>
 *
 * <ul>
 *   <li>通用幂等：{@code ydzsz:idem:{module}:{identifier}}
 *   <li>用户维度幂等：{@code ydzsz:idem:{module}:user:{userId}:{identifier}}
 *   <li>Outbox 消费幂等：{@code ydzsz:idem:outbox:{eventId}}
 * </ul>
 *
 * <p>所有需要构造或消费幂等键的组件都应通过本工具类构造，禁止在业务代码中直接拼接前缀。
 *
 * @author ydsz-team
 * @since 26.09.30
 */
public final class IdempotentKeyBuilder {

  private IdempotentKeyBuilder() {
    throw new AssertionError("Constants class cannot be instantiated");
  }

  /** Outbox 消费幂等 key 前缀 */
  private static final String OUTBOX_PREFIX = "outbox:";

  /** 用户维度幂等 key 前缀段 */
  private static final String USER_SEGMENT = "user:";

  /**
   * 构造通用幂等键
   *
   * @param module 模块名（如 "gateway"、"approval"、"order"）
   * @param identifier 唯一标识（如 orderId、eventId）
   * @return 幂等键，如 {@code ydzsz:idem:gateway:req_abc123}
   */
  public static String build(String module, String identifier) {
    return LockConstants.IDEMPOTENT_KEY_PREFIX + module + ":" + identifier;
  }

  /**
   * 构造用户维度的幂等键
   *
   * @param module 模块名
   * @param userId 用户 ID
   * @param identifier 唯一标识
   * @return 幂等键，如 {@code ydzsz:idem:gateway:user:u123:req_abc}
   */
  public static String buildForUser(String module, String userId, String identifier) {
    return LockConstants.IDEMPOTENT_KEY_PREFIX + module + ":" + USER_SEGMENT + userId + ":"
        + identifier;
  }

  /**
   * 构造 Outbox 消费幂等键
   *
   * @param eventId 事件 ID
   * @return 幂等键，如 {@code ydzsz:idem:outbox:evt_12345}
   */
  public static String buildForOutbox(String eventId) {
    return LockConstants.IDEMPOTENT_KEY_PREFIX + OUTBOX_PREFIX + eventId;
  }

  /**
   * 获取幂等键的 Redis 前缀（用于 SCAN 操作时的 pattern 匹配）
   *
   * @return 幂等键前缀，如 {@code ydzsz:idem:}
   */
  public static String prefix() {
    return LockConstants.IDEMPOTENT_KEY_PREFIX;
  }

  /**
   * 获取 Outbox 消费幂等键的前缀
   *
   * @return Outbox 幂等键前缀，如 {@code ydzsz:idem:outbox:}
   */
  public static String outboxPrefix() {
    return LockConstants.IDEMPOTENT_KEY_PREFIX + OUTBOX_PREFIX;
  }
}
