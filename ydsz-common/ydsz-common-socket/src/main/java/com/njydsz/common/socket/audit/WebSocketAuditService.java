package com.njydsz.common.socket.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.json.YdszJson;

/**
 * WebSocket 审计日志服务。
 *
 * <p>记录连接建立/断开、消息推送的结构化审计日志， 通过专用 Logger {@code WS_AUDIT} 输出，便于日志采集和合规审计。
 *
 * <p>审计字段：
 *
 * <ul>
 *   <li>timestamp — 时间戳
 *   <li>traceId — 链路追踪 ID
 *   <li>event — 事件类型（CONNECT / DISCONNECT / PUSH）
 *   <li>userId — 用户 ID
 *   <li>sessionId — Session ID
 *   <li>pushType — 推送类型（PUSH 事件）
 *   <li>success — 是否成功
 *   <li>durationMs — 耗时（毫秒）
 *   <li>error — 错误信息（失败时）
 * </ul>
 *
 * <p><b>日志脱敏：</b>用户 ID 脱敏由日志采集层（Filebeat / Fluentd pipeline）统一处理， 应用层不重复脱敏，避免多业务模块脱敏逻辑不一致。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class WebSocketAuditService {

  /** 专用审计 Logger */
  private static final Logger AUDIT_LOG = LoggerFactory.getLogger("WS_AUDIT");

  /**
   * 审计连接建立事件。
   *
   * @param userId 用户 ID
   * @param sessionId Session ID
   * @param remoteIp 远程 IP
   */
  public void auditConnect(String userId, String sessionId, String remoteIp) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("timestamp", Instant.now().toString());
    entry.put("traceId", getTraceId());
    entry.put("event", "CONNECT");
    entry.put("userId", userId);
    entry.put("sessionId", sessionId);
    if (remoteIp != null) {
      entry.put("remoteIp", remoteIp);
    }
    AUDIT_LOG.info(YdszJson.toJson(entry));
  }

  /**
   * 审计连接断开事件。
   *
   * @param userId 用户 ID
   * @param sessionId Session ID
   * @param durationMs 连接时长（毫秒）
   */
  public void auditDisconnect(String userId, String sessionId, long durationMs) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("timestamp", Instant.now().toString());
    entry.put("traceId", getTraceId());
    entry.put("event", "DISCONNECT");
    entry.put("userId", userId);
    entry.put("sessionId", sessionId);
    entry.put("durationMs", durationMs);
    AUDIT_LOG.info(YdszJson.toJson(entry));
  }

  /**
   * 审计消息推送事件。
   *
   * @param pushType 推送类型
   * @param userId 目标用户 ID（广播时为 null）
   * @param topic 目标主题（主题推送时使用）
   * @param success 是否成功
   * @param durationMs 耗时（毫秒）
   * @param error 错误信息（失败时）
   */
  public void auditPush(
      String pushType,
      String userId,
      String topic,
      boolean success,
      long durationMs,
      String error) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("timestamp", Instant.now().toString());
    entry.put("traceId", getTraceId());
    entry.put("event", "PUSH");
    entry.put("pushType", pushType);
    if (userId != null) {
      entry.put("userId", userId);
    }
    if (topic != null) {
      entry.put("topic", topic);
    }
    entry.put("success", success);
    entry.put("durationMs", durationMs);
    if (error != null) {
      entry.put("error", truncate(error, 500));
    }
    AUDIT_LOG.info(YdszJson.toJson(entry));
  }

  /**
   * 获取当前 traceId。
   *
   * <p>优先从 {@link RequestContext} 读取，未命中返回 null。
   *
   * @return traceId，不存在时返回 null
   */
  private static String getTraceId() {
    return RequestContext.getTraceId();
  }

  /**
   * 截断字符串。
   *
   * @param value 原始值
   * @param max 最大长度
   * @return 截断后的字符串
   */
  private static String truncate(String value, int max) {
    return value.length() > max ? value.substring(0, max) + "..." : value;
  }
}
