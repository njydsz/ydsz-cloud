package com.njydsz.message.server.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 发射器服务（批次进度推送）。
 *
 * <p>管理所有活跃的 SSE 连接，按 batchId 聚合，支持向特定批次的所有订阅者广播进度更新。
 *
 * <p>使用方式：
 *
 * <ol>
 *   <li>客户端调用 {@code POST /api/v1/message/batch/progress/{batchId}/sse} 获取 SseEmitter
 *   <li>后端在处理过程中调用 {@code broadcastProgress(batchId, progress)}
 * </ol>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
@Service
public class SseEmitterService {

  /** 默认 SSE 超时时间（5 分钟） */
  private static final long DEFAULT_TIMEOUT_MS = 5 * 60 * 1000L;

  /** 活跃的 SSE 订阅（batchId → emitter 列表） */
  private final Map<String, List<SseEmitterSubscription>> subscriptions = new ConcurrentHashMap<>();

  /**
   * 为指定批次创建新的 SSE 订阅。
   *
   * @param batchId 批次 ID
   * @return SseEmitter（已配置超时与完成回调）
   */
  public SseEmitter subscribe(String batchId) {
    return subscribe(batchId, DEFAULT_TIMEOUT_MS, null);
  }

  /**
   * 为指定批次创建新的 SSE 订阅（含初始快照）。
   *
   * <p>建立连接后立即发送一条 initial 事件，携带当前进度快照， 客户端无需等待下一次推送即可渲染初始状态。
   *
   * @param batchId 批次 ID
   * @param initialSnapshot 初始进度快照（可为 null）
   * @return SseEmitter
   */
  public SseEmitter subscribe(String batchId, Object initialSnapshot) {
    return subscribe(batchId, DEFAULT_TIMEOUT_MS, initialSnapshot);
  }

  /**
   * 为指定批次创建新的 SSE 订阅（自定义超时）。
   *
   * @param batchId 批次 ID
   * @param timeoutMs 超时毫秒数
   * @param initialSnapshot 初始进度快照（可为 null）
   * @return SseEmitter
   */
  public SseEmitter subscribe(String batchId, long timeoutMs, Object initialSnapshot) {
    SseEmitter emitter = new SseEmitter(timeoutMs);
    SseEmitterSubscription subscription = new SseEmitterSubscription(batchId, emitter);
    subscriptions.computeIfAbsent(batchId, k -> new CopyOnWriteArrayList<>()).add(subscription);

    // 发送初始快照（让客户端立即看到当前状态）
    if (initialSnapshot != null) {
      try {
        emitter.send(SseEmitter.event().name("initial").data(initialSnapshot));
      } catch (IllegalStateException | IOException e) {
        log.debug("[SSE] 初始快照发送失败: batchId={} err={}", batchId, e.getMessage());
        removeSubscription(subscription);
        return emitter;
      }
    }

    // 超时或连接关闭时清理
    emitter.onTimeout(
        () -> {
          log.debug("[SSE] 订阅超时: batchId={}", batchId);
          removeSubscription(subscription);
        });
    emitter.onCompletion(
        () -> {
          log.debug("[SSE] 订阅完成: batchId={}", batchId);
          removeSubscription(subscription);
        });
    emitter.onError(
        e -> {
          log.debug("[SSE] 订阅异常: batchId={} err={}", batchId, e.getMessage());
          removeSubscription(subscription);
        });

    return emitter;
  }

  /**
   * 向指定批次的所有订阅者广播进度更新。
   *
   * @param batchId 批次 ID
   * @param eventData 事件数据（任意可序列化对象）
   */
  public void broadcastProgress(String batchId, Object eventData) {
    List<SseEmitterSubscription> subs = subscriptions.get(batchId);
    if (subs == null || subs.isEmpty()) {
      return;
    }
    List<SseEmitterSubscription> deadSubs = new ArrayList<>();
    for (SseEmitterSubscription sub : subs) {
      try {
        sub.emitter().send(SseEmitter.event().name("progress").data(eventData));
      } catch (IllegalStateException | IOException e) {
        // 连接已关闭，标记为待清理
        deadSubs.add(sub);
      }
    }
    subs.removeAll(deadSubs);
  }

  /**
   * 向指定批次广播完成事件并关闭所有连接。
   *
   * @param batchId 批次 ID
   * @param resultData 结果数据
   */
  public void broadcastComplete(String batchId, Object resultData) {
    List<SseEmitterSubscription> subs = subscriptions.remove(batchId);
    if (subs == null || subs.isEmpty()) {
      return;
    }
    for (SseEmitterSubscription sub : subs) {
      try {
        sub.emitter().send(SseEmitter.event().name("complete").data(resultData));
        sub.emitter().complete();
      } catch (IllegalStateException | IOException e) {
        log.debug("[SSE] 完成事件发送失败: batchId={} err={}", batchId, e.getMessage());
      }
    }
  }

  /** 清理无效订阅。 */
  private void removeSubscription(SseEmitterSubscription sub) {
    List<SseEmitterSubscription> subs = subscriptions.get(sub.batchId());
    if (subs != null) {
      subs.remove(sub);
      if (subs.isEmpty()) {
        subscriptions.remove(sub.batchId());
      }
    }
  }

  /** SSE 订阅记录（batchId + emitter）。 */
  private record SseEmitterSubscription(String batchId, SseEmitter emitter) {}
}
