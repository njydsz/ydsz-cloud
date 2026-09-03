package com.njydsz.message.server.service;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    SseEmitterSubscription subscription = new SseEmitterSubscription(batchId, emitter);
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
                SseEmitter.event().id(entry.eventId).name(entry.eventName).data(entry.eventData));
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