package com.njydsz.agent.infra.trace;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.common.core.trace.TraceIdGenerator;
import com.njydsz.common.json.YdszJson;
import lombok.extern.slf4j.Slf4j;

/**
 * 内存执行链路记录器
 *
 * <p>使用 {@link ConcurrentHashMap} 在内存中存储执行链路，适用于开发调试。 生产环境可替换为数据库或链路追踪系统实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class InMemoryTraceRecorder implements TraceRecorder {

  /** 最大链路存储数 */
  private static final int MAX_TRACES = 1000;

  /** 链路 TTL（小时） */
  private static final long TTL_HOURS = 24L;

  /** 链路步骤存储（traceId → steps） */
  private final Map<String, List<TraceStep>> traces = new ConcurrentHashMap<>();

  /** 链路状态存储 */
  private final Map<String, String> traceStatus = new ConcurrentHashMap<>();

  /** 超容量后一次性多淘汰的链路数（避免频繁触发淘汰，同时防止低流量时清空近半链路） */
  private static final int EVICT_MARGIN = 10;

  /** 链路元数据存储 */
  private final Map<String, TraceMeta> traceMetas = new ConcurrentHashMap<>();

  @Override
  public String startTrace(String conversationId, String agentId) {
    evictExpiredTraces();
    String traceId = TraceIdGenerator.generateSortableTraceId();
    traces.put(traceId, new ArrayList<>());
    traceStatus.put(traceId, "RUNNING");
    traceMetas.put(traceId, new TraceMeta(traceId, conversationId, agentId, LocalDateTime.now()));
    log.info("[Trace] 开始链路: traceId={}, convId={}, agentId={}", traceId, conversationId, agentId);
    return traceId;
  }

  @Override
  public void recordStep(
      String traceId,
      String stepType,
      String content,
      Object input,
      Object output,
      long durationMs) {
    recordStep(traceId, stepType, content, input, output, durationMs, 0.0);
  }

  @Override
  public void recordStep(
      String traceId,
      String stepType,
      String content,
      Object input,
      Object output,
      long durationMs,
      double cost) {
    List<TraceStep> steps = traces.get(traceId);
    if (steps == null) {
      steps = new ArrayList<>();
      traces.put(traceId, steps);
    }
    int index = steps.size();
    String inputJson = input != null ? YdszJson.toJson(input) : null;
    String outputJson = output != null ? YdszJson.toJson(output) : null;
    steps.add(
        new TraceStep(
            traceId,
            index,
            stepType,
            content,
            inputJson,
            outputJson,
            durationMs,
            cost,
            LocalDateTime.now()));
    log.debug(
        "[Trace] 记录步骤: traceId={}, step={}, type={}, {}ms, cost=${}",
        traceId,
        index,
        stepType,
        durationMs,
        cost);
  }

  @Override
  public void endTrace(String traceId, String status) {
    traceStatus.put(traceId, status);
    TraceMeta meta = traceMetas.get(traceId);
    if (meta != null) {
      meta.setStatus(status);
      List<TraceStep> steps = traces.getOrDefault(traceId, List.of());
      long totalMs = steps.stream().mapToLong(TraceStep::getDurationMs).sum();
      meta.setTotalDurationMs(totalMs);
    }
    int stepCount = traces.getOrDefault(traceId, List.of()).size();
    log.info("[Trace] 结束链路: traceId={}, status={}, steps={}", traceId, status, stepCount);
  }

  @Override
  public List<TraceStep> getSteps(String traceId) {
    return traces.getOrDefault(traceId, List.of());
  }

  /**
   * 获取指定链路的最终状态。
   *
   * @param traceId 链路追踪 ID
   * @return 状态字符串（如 SUCCESS/FAILED）；链路不存在时返回 {@code "UNKNOWN"}
   */
  public String getStatus(String traceId) {
    return traceStatus.getOrDefault(traceId, "UNKNOWN");
  }

  /**
   * 获取已记录的链路总数。
   *
   * @return 内存中保留的 traceId 数量
   */
  public int getTraceCount() {
    return traces.size();
  }

  /**
   * 清空全部链路数据（步骤、状态、元数据三张表）。
   *
   * <p>主要用于测试用例之间隔离数据，或调试面板手动释放内存； 生产环境慎用——链路是纯内存存储，清空后历史不可恢复。
   *
   * <p><b>并发</b>：三个 Map 逐个 clear，整体<b>非原子</b>。 若清理期间有链路正在写入，可能出现步骤已清空但状态残留的中间态， 因此不应在有活跃会话时调用。
   */
  public void clear() {
    traces.clear();
    traceStatus.clear();
    traceMetas.clear();
  }

  /** 清理过期和超容量的链路 */
  private void evictExpiredTraces() {
    // 清理 TTL 过期的链路
    LocalDateTime cutoff = LocalDateTime.now().minusHours(TTL_HOURS);
    traceMetas.values().stream()
        .filter(meta -> meta.getStartedAt().isBefore(cutoff))
        .map(TraceMeta::getTraceId)
        .forEach(
            tid -> {
              traces.remove(tid);
              traceStatus.remove(tid);
              traceMetas.remove(tid);
            });
    // 超容量时清理最旧的链路（P2 修复：原一次删 100 条，低流量时可能清空近半链路）
    if (traces.size() >= MAX_TRACES) {
      int toRemove = traces.size() - MAX_TRACES + EVICT_MARGIN;
      traceMetas.values().stream()
          .sorted(Comparator.comparing(TraceMeta::getStartedAt))
          .limit(toRemove)
          .map(TraceMeta::getTraceId)
          .forEach(
              tid -> {
                traces.remove(tid);
                traceStatus.remove(tid);
                traceMetas.remove(tid);
              });
      log.info("[Trace] 清理超容量链路: 清除 {} 条", toRemove);
    }
  }

  @Override
  public List<String> listRecentTraces(int limit) {
    int safeLimit = limit > 0 ? limit : 10;
    return traceMetas.values().stream()
        .sorted(Comparator.comparing(TraceMeta::getStartedAt).reversed())
        .limit(safeLimit)
        .map(TraceMeta::getTraceId)
        .toList();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<com.njydsz.agent.domain.trace.TraceMeta> listRecentTraceMetas(int limit) {
    int safeLimit = limit > 0 ? limit : 10;
    return traceMetas.values().stream()
        .sorted(Comparator.comparing(TraceMeta::getStartedAt).reversed())
        .limit(safeLimit)
        .map(
            meta ->
                new com.njydsz.agent.domain.trace.TraceMeta(
                    meta.getTraceId(),
                    meta.getConversationId(),
                    meta.getAgentId(),
                    meta.getStartedAt(),
                    meta.getStatus(),
                    meta.getTotalDurationMs(),
                    traces.getOrDefault(meta.getTraceId(), List.of()).size()))
        .toList();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public com.njydsz.agent.domain.trace.TraceMeta getTraceMeta(String traceId) {
    TraceMeta meta = traceMetas.get(traceId);
    if (meta == null) {
      return null;
    }
    return new com.njydsz.agent.domain.trace.TraceMeta(
        meta.getTraceId(),
        meta.getConversationId(),
        meta.getAgentId(),
        meta.getStartedAt(),
        meta.getStatus(),
        meta.getTotalDurationMs(),
        traces.getOrDefault(traceId, List.of()).size());
  }

  /** 链路元数据 */
  public static class TraceMeta {
    /** 链路 ID */
    private final String traceId;

    /** 对话 ID */
    private final String conversationId;

    /** Agent ID */
    private final String agentId;

    /** 开始时间 */
    private final LocalDateTime startedAt;

    /** 执行状态 */
    private volatile String status;

    /** 总耗时（毫秒） */
    private volatile long totalDurationMs;

    public TraceMeta(
        String traceId, String conversationId, String agentId, LocalDateTime startedAt) {
      this.traceId = traceId;
      this.conversationId = conversationId;
      this.agentId = agentId;
      this.startedAt = startedAt;
      this.status = "RUNNING";
      this.totalDurationMs = 0;
    }

    public String getTraceId() {
      return traceId;
    }

    public String getConversationId() {
      return conversationId;
    }

    public String getAgentId() {
      return agentId;
    }

    public LocalDateTime getStartedAt() {
      return startedAt;
    }

    public String getStatus() {
      return status;
    }

    public long getTotalDurationMs() {
      return totalDurationMs;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public void setTotalDurationMs(long totalDurationMs) {
      this.totalDurationMs = totalDurationMs;
    }
  }
}
