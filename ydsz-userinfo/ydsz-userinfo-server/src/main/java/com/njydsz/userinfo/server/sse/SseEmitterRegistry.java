package com.njydsz.userinfo.server.sse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

/**
 * SSE Emitter 注册表（P3-1）。
 *
 * <p>管理所有已连接的 SSE Emitter，按用户 ID 分组。支持单用户多设备（多 Tab/浏览器）同时订阅。
 *
 * <p><b>生命周期管理：</b>
 *
 * <ul>
 *   <li>连接建立：{@link #register(String, SseEmitter)} 注册 emitter</li>
 *   <li>事件推送：{@link #pushToUser(String, String, Object)} 推送给指定用户</li>
 *   <li>连接断开：{@link #remove(String, SseEmitter)} 移除失效 emitter</li>
 * </ul>
 *
 * <p><b>线程安全：</b>使用 {@link CopyOnWriteArrayList} 保证并发读写安全。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class SseEmitterRegistry {

  /** 用户 ID → Emitter 列表映射（支持多设备同时在线） */
  private final Map<String, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

  /**
   * 注册 SSE 连接。
   *
   * @param userId 用户 ID
   * @param emitter SSE Emitter 实例
   */
  public void register(String userId, SseEmitter emitter) {
    userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
    log.debug("SSE 连接注册: userId={}, 当前连接数={}", userId, userEmitters.get(userId).size());
  }

  /**
   * 移除失效的 SSE 连接。
   *
   * @param userId 用户 ID
   * @param emitter 待移除的 Emitter
   */
  public void remove(String userId, SseEmitter emitter) {
    List<SseEmitter> emitters = userEmitters.get(userId);
    if (emitters != null) {
      emitters.remove(emitter);
      if (emitters.isEmpty()) {
        userEmitters.remove(userId);
      }
    }
    log.debug("SSE 连接移除: userId={}", userId);
  }

  /**
   * 向指定用户推送事件。
   *
   * <p>自动清理已失效的 emitter（发送 IOException 时移除）。
   *
   * @param userId 目标用户 ID
   * @param eventName SSE 事件名称
   * @param data 事件数据
   */
  public void pushToUser(String userId, String eventName, Object data) {
    List<SseEmitter> emitters = userEmitters.get(userId);
    if (emitters == null || emitters.isEmpty()) {
      return;
    }

    for (SseEmitter emitter : emitters) {
      try {
        emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
      } catch (IOException e) {
        log.debug("SSE 推送失败[连接已断开]: userId={}, event={}", userId, eventName);
        emitters.remove(emitter);
      } catch (IllegalStateException e) {
        log.debug("SSE Emitter 已关闭: userId={}, event={}", userId, eventName);
        emitters.remove(emitter);
      }
    }
  }

  /**
   * 向所有在线用户广播事件（系统级通知使用）。
   *
   * @param eventName SSE 事件名称
   * @param data 事件数据
   */
  public void broadcast(String eventName, Object data) {
    userEmitters.forEach((userId, emitters) -> pushToUser(userId, eventName, data));
  }

  /**
   * 获取指定用户的活跃连接数。
   *
   * @param userId 用户 ID
   * @return 活跃连接数
   */
  public int getConnectionCount(String userId) {
    List<SseEmitter> emitters = userEmitters.get(userId);
    return emitters != null ? emitters.size() : 0;
  }

  /**
   * 获取全系统活跃连接总数。
   *
   * @return 活跃连接总数
   */
  public int getTotalConnectionCount() {
    return userEmitters.values().stream().mapToInt(List::size).sum();
  }
}
