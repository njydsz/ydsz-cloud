package com.njydsz.agent.infra.trace;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.agent.domain.entity.AgentTraceDO;
import com.njydsz.agent.domain.entity.AgentTraceStepDO;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.infra.mapper.AgentTraceMapper;
import com.njydsz.agent.infra.mapper.AgentTraceStepMapper;
import com.njydsz.common.core.trace.TraceIdGenerator;
import com.njydsz.common.json.YdszJson;

/**
 * 数据库执行链路记录器
 *
 * <p>将 Agent 执行链路持久化到 {@code ydsz_agent_trace} 与 {@code ydsz_agent_trace_step} 表中，
 * 替代内存实现以支持跨重启数据保留、多实例数据共享与长期审计。
 *
 * <p><b>线程安全</b>：每次写入使用独立的 Entity 实例，通过 MyBatis-Plus 操作数据库。 步骤序号使用进程内 {@link AtomicInteger} 计数（同一
 * traceId 下唯一且并发安全）， 替代「每次查询最大序号 +1」的额外 SELECT，消除 DAG 并行节点下的序号冲突风险。
 *
 * <p><b>性能考量（P1 优化）</b>：
 *
 * <ul>
 *   <li>步骤序号内存化：{@code recordStep} 不再执行 SELECT MAX(stepIndex)
 *   <li>总耗时墙钟化：{@code endTrace} 以链路开始到结束的墙钟时间计总耗时， 替代各步骤耗时求和（DAG 并行节点求和会虚高）
 *   <li>如需进一步降低写放大，可在后续版本引入异步批量写入缓冲（AsyncWriter）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class PgTraceRecorder implements TraceRecorder {

  private static final Logger log = LoggerFactory.getLogger(PgTraceRecorder.class);

  /** 输入/输出 JSON 最大长度（防止超长内容撑爆字段） */
  private static final int MAX_JSON_LENGTH = 8000;

  private final AgentTraceMapper traceMapper;
  private final AgentTraceStepMapper traceStepMapper;

  /** 每链路步骤序号（traceId → 自增计数） */
  private final Map<String, AtomicInteger> stepIndexes = new ConcurrentHashMap<>();

  /** 每链路开始时间戳（traceId → 墙钟毫秒） */
  private final Map<String, Long> startTimes = new ConcurrentHashMap<>();

  public PgTraceRecorder(AgentTraceMapper traceMapper, AgentTraceStepMapper traceStepMapper) {
    this.traceMapper = traceMapper;
    this.traceStepMapper = traceStepMapper;
  }

  @Override
  public String startTrace(String conversationId, String agentId) {
    String traceId = TraceIdGenerator.generateSortableTraceId();
    AgentTraceDO trace =
        AgentTraceDO.builder()
            .traceId(traceId)
            .conversationId(conversationId)
            .agentId(agentId)
            .status("RUNNING")
            .totalDurationMs(0L)
            .build();
    traceMapper.insert(trace);
    stepIndexes.put(traceId, new AtomicInteger(0));
    startTimes.put(traceId, System.currentTimeMillis());
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
    int nextIndex =
        stepIndexes.computeIfAbsent(traceId, k -> new AtomicInteger(0)).getAndIncrement();
    String inputJson = truncateJson(toJsonString(input));
    String outputJson = truncateJson(toJsonString(output));

    AgentTraceStepDO step =
        AgentTraceStepDO.builder()
            .traceId(traceId)
            .stepIndex(nextIndex)
            .stepType(stepType)
            .content(content)
            .inputJson(inputJson)
            .outputJson(outputJson)
            .durationMs(durationMs)
            .build();
    traceStepMapper.insert(step);
    log.debug(
        "[Trace] 记录步骤: traceId={}, step={}, type={}, {}ms",
        traceId,
        nextIndex,
        stepType,
        durationMs);
  }

  @Override
  public void endTrace(String traceId, String status) {
    AgentTraceDO trace = traceMapper.selectById(traceId);
    if (trace == null) {
      log.warn("[Trace] 链路不存在，无法结束: traceId={}", traceId);
      cleanup(traceId);
      return;
    }
    // 总耗时取墙钟时间（DAG 并行节点下各步耗时求和会虚高）
    Long start = startTimes.remove(traceId);
    long totalMs = start != null ? System.currentTimeMillis() - start : 0L;

    trace.setStatus(status);
    trace.setTotalDurationMs(totalMs);
    traceMapper.updateById(trace);
    cleanup(traceId);
    log.info("[Trace] 结束链路: traceId={}, status={}, totalMs={}", traceId, status, totalMs);
  }

  @Override
  public List<TraceStep> getSteps(String traceId) {
    List<AgentTraceStepDO> steps =
        traceStepMapper.selectList(
            new LambdaQueryWrapper<AgentTraceStepDO>()
                .eq(AgentTraceStepDO::getTraceId, traceId)
                .orderByAsc(AgentTraceStepDO::getStepIndex));
    return steps.stream().map(this::toTraceStep).toList();
  }

  /**
   * 清理该链路的内存计数与时间戳（避免长尾链路累积内存）。
   *
   * @param traceId 链路 ID
   */
  private void cleanup(String traceId) {
    stepIndexes.remove(traceId);
    startTimes.remove(traceId);
  }

  /**
   * 将对象序列化为 JSON 字符串。
   *
   * @param obj 目标对象
   * @return JSON 字符串；{@code null} 时返回 {@code null}
   */
  private String toJsonString(Object obj) {
    if (obj == null) {
      return null;
    }
    try {
      return YdszJson.toJson(obj);
    } catch (Exception e) {
      log.warn("[Trace] 序列化失败: {}", e.getMessage());
      return obj.toString();
    }
  }

  /**
   * 截断超长 JSON 字符串。
   *
   * @param json 原始 JSON 字符串
   * @return 截断后的字符串；{@code null} 时返回 {@code null}
   */
  private String truncateJson(String json) {
    if (json == null) {
      return null;
    }
    return json.length() > MAX_JSON_LENGTH ? json.substring(0, MAX_JSON_LENGTH) : json;
  }

  /**
   * 将 DO 转换为不可变的 TraceStep 视图。
   *
   * @param step 步骤 DO
   * @return 不可变 TraceStep
   */
  private TraceStep toTraceStep(AgentTraceStepDO step) {
    return new TraceStep(
        step.getTraceId(),
        step.getStepIndex() != null ? step.getStepIndex() : 0,
        step.getStepType(),
        step.getContent(),
        step.getInputJson(),
        step.getOutputJson(),
        step.getDurationMs() != null ? step.getDurationMs() : 0L,
        LocalDateTime.now());
  }
}
