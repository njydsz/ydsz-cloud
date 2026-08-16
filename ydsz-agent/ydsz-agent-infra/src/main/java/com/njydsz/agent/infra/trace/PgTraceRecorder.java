package com.njydsz.agent.infra.trace;

import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
 * <p><b>线程安全</b>：每次写入使用独立的 Entity 实例，通过 MyBatis-Plus 操作数据库，
 * 步骤序号通过查询当前最大序号 +1 确定（同一 traceId 下唯一）。
 *
 * <p><b>性能考量</b>：recordStep 每次执行一次 INSERT，适合写多读少的链路追踪场景。
 * 如需批量写入，可考虑引入 AsyncWriter 缓冲区。
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

    public PgTraceRecorder(AgentTraceMapper traceMapper, AgentTraceStepMapper traceStepMapper) {
        this.traceMapper = traceMapper;
        this.traceStepMapper = traceStepMapper;
    }

    @Override
    public String startTrace(String conversationId, String agentId) {
        String traceId = TraceIdGenerator.generateSortableTraceId();
        AgentTraceDO trace = AgentTraceDO.builder()
                .traceId(traceId)
                .conversationId(conversationId)
                .agentId(agentId)
                .status("RUNNING")
                .totalDurationMs(0L)
                .build();
        traceMapper.insert(trace);
        log.info("[Trace] 开始链路: traceId={}, convId={}, agentId={}",
                traceId, conversationId, agentId);
        return traceId;
    }

    @Override
    public void recordStep(String traceId, String stepType, String content,
                           Object input, Object output, long durationMs) {
        int nextIndex = getNextStepIndex(traceId);
        String inputJson = truncateJson(toJsonString(input));
        String outputJson = truncateJson(toJsonString(output));

        AgentTraceStepDO step = AgentTraceStepDO.builder()
                .traceId(traceId)
                .stepIndex(nextIndex)
                .stepType(stepType)
                .content(content)
                .inputJson(inputJson)
                .outputJson(outputJson)
                .durationMs(durationMs)
                .build();
        traceStepMapper.insert(step);
        log.debug("[Trace] 记录步骤: traceId={}, step={}, type={}, {}ms",
                traceId, nextIndex, stepType, durationMs);
    }

    @Override
    public void endTrace(String traceId, String status) {
        AgentTraceDO trace = traceMapper.selectById(traceId);
        if (trace == null) {
            log.warn("[Trace] 链路不存在，无法结束: traceId={}", traceId);
            return;
        }
        // 计算总耗时
        List<AgentTraceStepDO> steps = traceStepMapper.selectList(
                new LambdaQueryWrapper<AgentTraceStepDO>()
                        .eq(AgentTraceStepDO::getTraceId, traceId));
        long totalMs = steps.stream()
                .mapToLong(s -> s.getDurationMs() != null ? s.getDurationMs() : 0L)
                .sum();

        trace.setStatus(status);
        trace.setTotalDurationMs(totalMs);
        traceMapper.updateById(trace);
        log.info("[Trace] 结束链路: traceId={}, status={}, steps={}, totalMs={}",
                traceId, status, steps.size(), totalMs);
    }

    @Override
    public List<TraceStep> getSteps(String traceId) {
        List<AgentTraceStepDO> steps = traceStepMapper.selectList(
                new LambdaQueryWrapper<AgentTraceStepDO>()
                        .eq(AgentTraceStepDO::getTraceId, traceId)
                        .orderByAsc(AgentTraceStepDO::getStepIndex));
        return steps.stream()
                .map(this::toTraceStep)
                .toList();
    }

    /**
     * 获取指定链路当前最大步骤序号 +1。
     *
     * <p>查询该 traceId 下已有的最大 stepIndex，返回其 +1 作为下一步序号。
     * 无步骤时返回 0。
     *
     * @param traceId 链路 ID
     * @return 下一个步骤序号
     */
    private int getNextStepIndex(String traceId) {
        LambdaQueryWrapper<AgentTraceStepDO> wrapper = new LambdaQueryWrapper<AgentTraceStepDO>()
                .eq(AgentTraceStepDO::getTraceId, traceId)
                .orderByDesc(AgentTraceStepDO::getStepIndex)
                .last("LIMIT 1");
        AgentTraceStepDO lastStep = traceStepMapper.selectOne(wrapper);
        return lastStep != null && lastStep.getStepIndex() != null ? lastStep.getStepIndex() + 1 : 0;
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
        return json.length() > MAX_JSON_LENGTH
                ? json.substring(0, MAX_JSON_LENGTH)
                : json;
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
