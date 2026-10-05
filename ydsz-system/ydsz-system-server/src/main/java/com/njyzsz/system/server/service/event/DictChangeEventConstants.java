package com.njydsz.system.server.service.event;

/**
 * 字典变更事件常量 — 避免魔法值，确保 Publisher / Listener / Emitter 语义一致。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public final class DictChangeEventConstants {

  private DictChangeEventConstants() {
    throw new UnsupportedOperationException("Utility class");
  }

  /** Redis Pub/Sub 通道名称 — 字典变更事件广播通道 */
  public static final String REDIS_CHANNEL = "ydsz:dict:change";

  /** SSE 流端点路径（相对系统管理根前缀） */
  public static final String SSE_ENDPOINT = "/dict/sse";

  /** SSE 心跳文本（Thunder Client / curl 等不会断开） */
  public static final String SSE_KEEPALIVE_PAYLOAD = ":keepalive\n\n";

  /** SSE 心跳间隔（秒） */
  public static final long SSE_KEEPALIVE_INTERVAL_SECONDS = 30L;

  // ==================== 事件类型 ====================

  /** 创建 */
  public static final String EVENT_TYPE_CREATED = "CREATED";

  /** 更新 */
  public static final String EVENT_TYPE_UPDATED = "UPDATED";

  /** 删除 */
  public static final String EVENT_TYPE_DELETED = "DELETED";
}
