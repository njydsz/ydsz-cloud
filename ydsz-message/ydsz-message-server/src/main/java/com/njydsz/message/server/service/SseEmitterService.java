package com.njydsz.message.server.service;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 发射器服务（批次进度推送，支持 Last-Event-ID 断线重连）。
 *
 * <p>管理所有活跃的 SSE 连接，按 batchId 聚合，支持向特定批次的所有订阅者广播进度更新。
 *
 * <p><b>P3-1: Last-Event-ID 断线重连</b>
 *
 * <ul>
 *   <li>每个 SSE 事件携带递增的 {@code id} 字段（批次级单调递增）
 *   <li>每个批次保留最近 {@link #EVENT_LOG_SIZE} 条事件日志（供断线重连回放）
 *   <li>客户端重连时在 {@code Last-Event-ID} Header 携带上次收到的最后一个事件 ID
 *   <li>服务端检测缺失区间，自动重放 {@code initial}（快照）+ {@code progress}（增量）事件链
 * </ul>
 *
 * <p>使用方式：
 *
 * <ol>
 *   <li>客户端调用 {@code GET /api/v1/message/batch/progress/{batchId}/sse} 获取 SseEmitter
 *   <li>后端在处理过程中调用 {@code broadcastProgress(batchId, progress)}
 *   <li>客户端断线后重连时带上 {@code Last-Event-ID} Header 以恢复缺失的进度
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
public class SseEmitterService {

  /** 默认 SSE 超时时间（5 分钟） */
  private static final long DEFAULT_TIMEOUT_MS = 5 * 60 * 1000L;

  /** 每个批次保留的事件日志条数上限（环形缓冲区，超过时丢弃最旧条目） */
  private static final int EVENT_LOG_SIZE = 100;

  /** 活跃的 SSE 订阅（batchId → emitter 列表） */
  private final Map<String, List<SseEmitterSubscription>> subscriptions = new ConcurrentHashMap<>();

  /** 批次事件日志（batchId → 有序事件列表），供 Last-Event-ID 重连回放 */
  private final Map<String, List<SseEventEntry>> eventLogs = new ConcurrentHashMap<>();

  /** 批次事件 ID 生成器（batchId → 递增计数器） */
  private final Map<String, AtomicLong> eventIdGenerators = new ConcurrentHashMap<>();

  /**
   * 为指定批次创建新的 SSE 订阅。
   *
   * @param batchId 批次 ID
   * @return SseEmitter（已配置超时与完成回调）
   */
  public SseEmitter subscribe(String batchId) {
    return subscribe(batchId, DEFAULT_TIMEOUT_MS, null, null);
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
    return subscribe(batchId, DEFAULT_TIMEOUT_MS, initialSnapshot, null);
  }

  /**
   * 为指定批次创建新的 SSE 订阅（含 Last-Event-ID 断线重连）。
   *
   * <p>当客户端携带 {@code Last-Event-ID} Header 重连时，服务端子程序将重放该 ID 之后所有缺失的事件
   * （initial 快照 + 增量 progress）。 旧事件 ID 对应的事件已被环形缓冲区覆盖时，退化为发送当前快照等同首次订阅。
   *
   * @param batchId 批次 ID
   * @param initialSnapshot 初始进度快照（可为 null）
   * @param lastEventId 客户端上次收到的事件 ID（可为 null，表示首次订阅）
   * @return SseEmitter
   */
  public SseEmitter subscribe(String batchId, Object initialSnapshot, String lastEventId) {
    return subscribe(batchId, DEFAULT_TIMEOUT_MS, initialSnapshot, lastEventId);
  }

  /**
   * 为指定批次创建新的 SSE 订阅（自定义超时，支持 Last-Event-ID 重连）。
   *
   * @param batchId 批次 ID
   * @param timeoutMs 超时毫秒数
   * @param initialSnapshot 初始进度快照（可为 null）
   * @param lastEventId 客户端上次收到的事件 ID（可为 null）
   * @return SseEmitter
   */
  public SseEmitter subscribe(
      String batchId, long timeoutMs, Object initialSnapshot, String lastEventId) {
    SseEmitter emitter = new SseEmitter(timeoutMs);
    String subscriptionId = UUID.randomUUID().toString();
    SseEmitterSubscription subscription = new SseEmitterSubscription(subscriptionId, batchId, emitter, lastEventId);
    subscriptions.computeIfAbsent(batchId, k -> new CopyOnWriteArrayList<>()).add(subscription);

    // P3-1: Last-Event-ID 断线重连 —— 检测并播放缺失的事件
    if (lastEventId != null && !lastEventId.isEmpty()) {
      List<SseEventEntry> missedEvents = getMissedEvents(batchId, lastEventId);
      if (!missedEvents.isEmpty()) {
        log.info(
            "[SSE] Last-Event-ID 重连: batchId={} lastEventId={} replayCount={}",
            batchId,
            lastEventId,
            missedEvents.size());
        for (SseEventEntry entry : missedEvents) {
          try {
            emitter.send(
                SseEmitter.event()
                    .id(String.valueOf(entry.getEventId()))
                    .name(entry.getEventType())
                    .data(entry.getData()));
          } catch (IllegalStateException | IOException e) {
            log.debug("[SSE] 重连回放失败: batchId={} err={}", batchId, e.getMessage());
            break;
          }
        }
        // 注册清理回调后直接返回（不重复发 initial）
        registerCleanupHandlers(batchId, subscription);
        return emitter;
      }
      // lastEventId 太旧（已被环形缓冲区覆盖），退化发送当前快照
      log.debug(
          "[SSE] Last-Event-ID 已过期,退化快照重发: batchId={} lastEventId={}", batchId, lastEventId);
    }

    // 首次订阅或 lastEventId 过期 —— 发送当前快照
    if (initialSnapshot != null) {
      long eventId = nextEventId(batchId);
      String eventIdStr = String.valueOf(eventId);
      try {
        SseEmitter.SseEventBuilder event =
            SseEmitter.event().id(eventIdStr).name("initial").data(initialSnapshot);
        emitter.send(event);
        recordEvent(batchId, eventIdStr, "initial", initialSnapshot);
      } catch (IllegalStateException | IOException e) {
        log.debug("[SSE] 初始快照发送失败: batchId={} err={}", batchId, e.getMessage());
        removeSubscription(subscription);
        return emitter;
      }
    }

    // 超时或连接关闭时清理
    registerCleanupHandlers(batchId, subscription);
    return emitter;
  }

  /**
   * 向指定批次的所有订阅者广播进度更新。
   *
   * <p>事件自动分配递增 ID 并记录到事件日志，供后续 Last-Event-ID 重连回放。
   *
   * @param batchId 批次 ID
   * @param eventData 事件数据（任意可序列化对象）
   */
  public void broadcastProgress(String batchId, Object eventData) {
    List<SseEmitterSubscription> subs = subscriptions.get(batchId);
    if (subs == null || subs.isEmpty()) {
      return;
    }
    // 分配事件 ID 并记录到事件日志
    long eventId = nextEventId(batchId);
    String eventIdStr = String.valueOf(eventId);
    recordEvent(batchId, eventIdStr, "progress", eventData);

    List<SseEmitterSubscription> deadSubs = new ArrayList<>(16);
    for (SseEmitterSubscription sub : subs) {
      try {
        sub.getEmitter().send(
            SseEmitter.event().id(eventIdStr).name("progress").data(eventData));
      } catch (IllegalStateException | IOException e) {
        log.debug("[SSE] 发送失败,移除订阅: batchId={} subId={} err={}",
            batchId, sub.getSubscriptionId(), e.getMessage());
        deadSubs.add(sub);
      }
    }
    if (!deadSubs.isEmpty()) {
      subs.removeAll(deadSubs);
    }
  }

  /**
   * 移除指定订阅。
   *
   * @param subscription 待移除的订阅
   */
  private void removeSubscription(SseEmitterSubscription subscription) {
    List<SseEmitterSubscription> subs = subscriptions.get(subscription.getBatchId());
    if (subs != null) {
      subs.remove(subscription);
    }
  }

  /**
   * 注册清理回调（连接完成或超时时移除订阅）。
   *
   * @param batchId 批次 ID
   * @param subscription 订阅信息
   */
  private void registerCleanupHandlers(String batchId, SseEmitterSubscription subscription) {
    SseEmitter emitter = subscription.getEmitter();
    emitter.onCompletion(() -> {
      log.debug("[SSE] 连接完成: batchId={} subId={}", batchId, subscription.getSubscriptionId());
      removeSubscription(subscription);
    });
    emitter.onTimeout(() -> {
      log.debug("[SSE] 连接超时: batchId={} subId={}", batchId, subscription.getSubscriptionId());
      removeSubscription(subscription);
      emitter.complete();
    });
    emitter.onError(e -> {
      log.debug("[SSE] 连接错误: batchId={} subId={} err={}",
          batchId, subscription.getSubscriptionId(), e.getMessage());
      removeSubscription(subscription);
    });
  }

  /**
   * 获取批次下一个递增事件 ID。
   *
   * @param batchId 批次 ID
   * @return 事件 ID
   */
  private long nextEventId(String batchId) {
    return eventIdGenerators.computeIfAbsent(batchId, k -> new AtomicLong(0)).incrementAndGet();
  }

  /**
   * 记录事件到日志（环形缓冲区，超过上限时丢弃最旧条目）。
   *
   * @param batchId 批次 ID
   * @param eventIdStr 事件 ID 字符串
   * @param eventType 事件类型
   * @param eventData 事件数据
   */
  private void recordEvent(String batchId, String eventIdStr, String eventType, Object eventData) {
    long eventId = Long.parseLong(eventIdStr);
    SseEventEntry entry = new SseEventEntry(eventId, batchId, eventType, eventData);
    List<SseEventEntry> log = eventLogs.computeIfAbsent(batchId, k -> new CopyOnWriteArrayList<>());
    synchronized (log) {
      log.add(entry);
      if (log.size() > EVENT_LOG_SIZE) {
        log.remove(0);
      }
    }
  }

  /**
   * 获取 lastEventId 之后缺失的事件列表。
   *
   * @param batchId 批次 ID
   * @param lastEventId 客户端最后收到的事件 ID
   * @return 缺失的事件列表，若 lastEventId 已过期则返回空列表
   */
  private List<SseEventEntry> getMissedEvents(String batchId, String lastEventId) {
    List<SseEventEntry> log = eventLogs.get(batchId);
    if (log == null || log.isEmpty()) {
      return new ArrayList<>(0);
    }
    long lastId;
    try {
      lastId = Long.parseLong(lastEventId);
    } catch (NumberFormatException e) {
      return new ArrayList<>(0);
    }
    List<SseEventEntry> missed = new ArrayList<>();
    for (SseEventEntry entry : log) {
      if (entry.getEventId() > lastId) {
        missed.add(entry);
      }
    }
    // 如果找不到比 lastEventId 更新的事件（已被环形缓冲区覆盖），返回空列表
    return missed;
  }

  /**
   * 获取指定批次的当前订阅数量。
   *
   * @param batchId 批次 ID
   * @return 订阅数量
   */
  public int getSubscriptionCount(String batchId) {
    List<SseEmitterSubscription> subs = subscriptions.get(batchId);
    return subs == null ? 0 : subs.size();
  }
}
