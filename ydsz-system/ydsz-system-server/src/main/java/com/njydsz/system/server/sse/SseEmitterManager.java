package com.njydsz.system.server.sse;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 连接管理器
 *
 * <p>管理所有客户端的 SSE 连接，支持按租户/用户维度维护连接列表， 提供连接注册、移除、心跳检测和消息推送能力。
 *
 * <p><b>连接管理：</b>
 *
 * <ul>
 *   <li>每个租户可有多个 SSE 连接（多标签页 / 多设备）
 *   <li>连接建立后定期发送心跳包保持活跃
 *   <li>连接超时或异常时自动清理
 * </ul>
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>配置变更实时推送
 *   <li>系统通知广播
 *   <li>任务进度推送
 * </ul>
 *
 * @author ydsz-team
 * @since 1.9.0
 */
@Slf4j
@Component
public class SseEmitterManager {

  /** SSE 连接超时时间（30 分钟） */
  private static final long TIMEOUT_MS = 30 * 60 * 1000L;

  /** 心跳间隔（15 秒） */
  private static final long HEARTBEAT_INTERVAL_SECONDS = 15;

  /** 租户 ID → 连接列表 */
  private final Map<String, Map<String, SseEmitter>> tenantEmitters =
      new ConcurrentHashMap<>();

  /** 心跳调度器 */
  private final ScheduledExecutorService heartbeatScheduler =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
      });

  /**
   * 创建 SSE 连接并注册到管理器。
   *
   * @param tenantId 租户 ID
   * @param userId 用户 ID（用作连接标识）
   * @return SSE 发射器
   */
  public SseEmitter createEmitter(String tenantId, String userId) {
    // 清理同一用户的旧连接
    removeEmitter(tenantId, userId);

    SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
    tenantEmitters
        .computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
        .put(userId, emitter);

    // 超时回调
    emitter.onTimeout(() -> {
      log.debug("[SSE] 连接超时: tenant={}, user={}", tenantId, userId);
      removeEmitter(tenantId, userId);
    });

    // 异常回调
    emitter.onError(e -> {
      log.debug("[SSE] 连接异常: tenant={}, user={}, error={}", tenantId, userId, e.getMessage());
      removeEmitter(tenantId, userId);
    });

    // 完成回调
    emitter.onCompletion(() -> {
      log.debug("[SSE] 连接完成: tenant={}, user={}", tenantId, userId);
      removeEmitter(tenantId, userId);
    });

    // 发送初始连接成功事件
    try {
      emitter.send(SseEmitter.event()
          .name("connected")
          .data("{\"status\":\"connected\"}"));
    } catch (IOException e) {
      log.warn("[SSE] 发送连接确认失败: {}", e.getMessage());
      removeEmitter(tenantId, userId);
    }

    log.info("[SSE] 新连接注册: tenant={}, user={}, total={}",
        tenantId, userId, getTenantEmitterCount(tenantId));
    return emitter;
  }

  /**
   * 移除指定连接。
   *
   * @param tenantId 租户 ID
   * @param userId 用户 ID
   */
  public void removeEmitter(String tenantId, String userId) {
    Map<String, SseEmitter> emitters = tenantEmitters.get(tenantId);
    if (emitters != null) {
      SseEmitter emitter = emitters.remove(userId);
      if (emitter != null) {
        try {
          emitter.complete();
        } catch (Exception e) {
          // 忽略已完成连接的异常
        }
      }
      // 清理空租户
      if (emitters.isEmpty()) {
        tenantEmitters.remove(tenantId);
      }
    }
  }

  /**
   * 向指定租户的所有连接推送事件。
   *
   * @param tenantId 租户 ID
   * @param eventName 事件名称
   * @param data 事件数据（JSON 字符串）
   */
  public void pushToTenant(String tenantId, String eventName, Object data) {
    Map<String, SseEmitter> emitters = tenantEmitters.get(tenantId);
    if (emitters == null || emitters.isEmpty()) {
      return;
    }

    // 收集无效连接
    Map<String, SseEmitter> invalidEmitters = new ConcurrentHashMap<>();

    emitters.forEach((userId, emitter) -> {
      try {
        emitter.send(SseEmitter.event()
            .name(eventName)
            .data(data));
      } catch (Exception e) {
        log.warn("[SSE] 推送失败: tenant={}, user={}, error={}", tenantId, userId, e.getMessage());
        invalidEmitters.put(userId, emitter);
      }
    });

    // 清理无效连接
    invalidEmitters.forEach((userId, emitter) -> {
      removeEmitter(tenantId, userId);
    });
  }

  /**
   * 向所有租户广播事件。
   *
   * @param eventName 事件名称
   * @param data 事件数据
   */
  public void broadcast(String eventName, Object data) {
    tenantEmitters.keySet().forEach(tenantId ->
        pushToTenant(tenantId, eventName, data));
  }

  /**
   * 获取指定租户的连接数。
   *
   * @param tenantId 租户 ID
   * @return 连接数
   */
  public int getTenantEmitterCount(String tenantId) {
    Map<String, SseEmitter> emitters = tenantEmitters.get(tenantId);
    return emitters != null ? emitters.size() : 0;
  }

  /**
   * 获取全租户连接总数。
   *
   * @return 连接总数
   */
  public int getTotalEmitterCount() {
    return tenantEmitters.values().stream()
        .mapToInt(Map::size)
        .sum();
  }

  /**
   * 启动心跳任务（由 ApplicationRunner 触发）。
   */
  public void startHeartbeat() {
    heartbeatScheduler.scheduleAtFixedRate(() -> {
      try {
        broadcast("heartbeat", "{\"timestamp\":" + System.currentTimeMillis() + "}");
      } catch (Exception e) {
        log.warn("[SSE] 心跳推送失败: {}", e.getMessage());
      }
    }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    log.info("[SSE] 心跳任务已启动，间隔 {} 秒", HEARTBEAT_INTERVAL_SECONDS);
  }
}
