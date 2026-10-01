package com.njydsz.agent.infra.trace;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.trace.TraceMeta;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.core.trace.TraceIdGenerator;
import com.njydsz.common.json.YdszJson;

/**
 * 内存执行链路记录器
 *
 * <p>使用 {@link ConcurrentHashMap} 在内存中存储执行链路，适用于开发调试。 生产环境可替换为数据库或链路追踪系统实现。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
public class InMemoryTraceRecorder implements TraceRecorder {
  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  /** 最大链路存储数 */
  private static final int MAX_TRACES = 1000;

  /** 链路 TTL（小时） */
  private static final long TTL_HOURS = 24L;

  /**
   * 链路缓存（traceId → TraceFullRecord 完整记录含 steps/status/meta）。
   *
   * <p>使用 YdszCache 替代三张 ConcurrentHashMap（traces / traceStatus / traceMetas），
   * 统一由框架管理 TTL（24h）和容量上限（1000），删除手写 evictExpiredTraces()。
   */
  private final Cache<String, TraceFullRecord> traceCache =
      YdszCache.<String, TraceFullRecord>newBuilder()
          .maximumSize(MAX_TRACES)
          .expireAfterAccess(TTL_HOURS, TimeUnit.HOURS)
          .build();

  /**
   * 启动一条新的执行链路。
   *
   * @param conversationId 对话 ID
   * @param agentId Agent 标识
   * @return 生成的链路追踪 ID
   */
  @Override
  public String startTrace(String conversationId, String agentId) {
    String traceId = TraceIdGenerator.generateSortableTraceId();
    TraceFullRecord record = new TraceFullRecord(traceId, conversationId, agentId, LocalDateTime.now());
    traceCache.put(traceId, record);
    log.info("[Trace] 开始链路: traceId={}, convId={}, agentId={}", traceId, conversationId, agentId);
    return traceId;
  }

  /**
   * 记录一个执行步骤（无成本信息）。
   *
   * @param traceId 链路追踪 ID
   * @param stepType 步骤类型（如 LLM_CALL、TOOL_CALL）
   * @param content 步骤内容摘要
   * @param input 步骤输入对象
   * @param output 步骤输出对象
   * @param durationMs 步骤耗时（毫秒）
   */
  @Override
  public void recordStep(
      String traceId,
      String stepType,
      String content,
      Object input,
      Object output,
      long durationMs) {
    recordStep(traceId, stepType, content, input, output, durationMs, BigDecimal.ZERO);
  }

  /**
   * 记录一个执行步骤（含成本信息）。
   *
   * @param traceId 链路追踪 ID
   * @param stepType 步骤类型（如 LLM_CALL、TOOL_CALL）
   * @param content 步骤内容摘要
   * @param input 步骤输入对象
   * @param output 步骤输出对象
   * @param durationMs 步骤耗时（毫秒）
   * @param cost 步骤成本（美元）
   */
  @Override
  public void recordStep(
      String traceId,
      String stepType,
      String content,
      Object input,
      Object output,
      long durationMs,
      BigDecimal cost) {
    TraceFullRecord record = traceCache.getIfPresent(traceId);
    if (record == null) {
      log.warn("[Trace] 链路不存在，忽略步骤记录: traceId={}", traceId);
      return;
    }
    List<TraceStep> steps = record.getSteps();
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

  /**
   * 结束一条执行链路（标记最终状态，计算总耗时）。
   *
   * @param traceId 链路追踪 ID
   * @param status 最终状态（如 SUCCESS / FAILED）
   */
  @Override
  public void endTrace(String traceId, String status) {
    TraceFullRecord record = traceCache.getIfPresent(traceId);
    if (record != null) {
      record.setStatus(status);
      List<TraceStep> steps = record.getSteps();
      long totalMs = steps.stream().mapToLong(TraceStep::getDurationMs).sum();
      record.setTotalDurationMs(totalMs);
      log.info("[Trace] 结束链路: traceId={}, status={}, steps={}", traceId, status, steps.size());
    }
  }

  /**
   * 获取指定链路的所有步骤。
   *
   * @param traceId 链路追踪 ID
   * @return 步骤列表（按序号升序）；链路不存在时返回空列表
   */
  @Override
  public List<TraceStep> getSteps(String traceId) {
    TraceFullRecord record = traceCache.getIfPresent(traceId);
    return record != null ? record.getSteps() : List.of();
  }

  /**
   * 获取指定链路的最终状态。
   *
   * @param traceId 链路追踪 ID
   * @return 状态字符串（如 SUCCESS/FAILED）；链路不存在时返回 {@code "UNKNOWN"}
   */
  public String getStatus(String traceId) {
    TraceFullRecord record = traceCache.getIfPresent(traceId);
    return record != null ? record.getStatus() : "UNKNOWN";
  }

  /**
   * 获取已记录的链路总数。
   *
   * @return 内存中保留的 traceId 数量
   */
  public int getTraceCount() {
    return (int) traceCache.estimatedSize();
  }

  /**
   * 清空全部链路数据。
   *
   * <p>主要用于测试用例之间隔离数据，或调试面板手动释放内存； 生产环境慎用——链路是纯内存存储，清空后历史不可恢复。
   */
  public void clear() {
    traceCache.invalidateAll();
  }

  /**
   * 列出最近的链路 ID（按开始时间降序）。
   *
   * @param limit 返回条数上限（≤0 时取默认值 10）
   * @return 链路 ID 列表
   */
  @Override
  public List<String> listRecentTraces(int limit) {
    int safeLimit = limit > 0 ? limit : 10;
    return traceCache.asMap().values().stream()
        .sorted(Comparator.comparing(TraceFullRecord::getStartedAt).reversed())
        .limit(safeLimit)
        .map(TraceFullRecord::getTraceId)
        .toList();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<TraceMeta> listRecentTraceMetas(int limit) {
    int safeLimit = limit > 0 ? limit : 10;
    return traceCache.asMap().values().stream()
        .sorted(Comparator.comparing(TraceFullRecord::getStartedAt).reversed())
        .limit(safeLimit)
        .map(TraceFullRecord::toTraceMeta)
        .toList();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public TraceMeta getTraceMeta(String traceId) {
    TraceFullRecord record = traceCache.getIfPresent(traceId);
    return record != null ? record.toTraceMeta() : null;
  }

  /**
   * 链路完整记录（步骤 + 状态 + 元数据）。
   *
   * <p>封装为不可变字段 + 可变状态，由 YdszCache 统一管理 TTL 和容量。
   */
  public static class TraceFullRecord {
    /** 链路 ID */
    private final String traceId;

    /** 对话 ID */
    private final String conversationId;

    /** Agent ID */
    private final String agentId;

    /** 开始时间 */
    private final LocalDateTime startedAt;

    /** 执行步骤 */
    private final List<TraceStep> steps = new ArrayList<>(COLLECTION_CAPACITY);

    /** 执行状态 */
    private volatile String status = "RUNNING";

    /** 总耗时（毫秒） */
    private volatile long totalDurationMs;

    public TraceFullRecord(
        String traceId, String conversationId, String agentId, LocalDateTime startedAt) {
      this.traceId = traceId;
      this.conversationId = conversationId;
      this.agentId = agentId;
      this.startedAt = startedAt;
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

    public List<TraceStep> getSteps() {
      return steps;
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

    public TraceMeta toTraceMeta() {
      return new TraceMeta(traceId, conversationId, agentId, startedAt, status, totalDurationMs, steps.size());
    }
  }
}
