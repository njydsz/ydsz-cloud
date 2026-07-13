package com.njydsz.pmis.agent.infra.trace;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.agent.domain.trace.TraceRecorder;

/**
 * 内存执行链路记录器
 *
 * <p>使用 {@link ConcurrentHashMap} 在内存中存储执行链路，适用于开发调试。
 * 生产环境可替换为数据库或链路追踪系统实现。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public class InMemoryTraceRecorder implements TraceRecorder {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTraceRecorder.class);
    private final Map<String, List<TraceStep>> traces = new ConcurrentHashMap<>();
    private final Map<String, String> traceStatus = new ConcurrentHashMap<>();

    @Override
    public String startTrace(String conversationId, String agentId) {
        String traceId = UUID.randomUUID().toString();
        traces.put(traceId, new ArrayList<>());
        traceStatus.put(traceId, "RUNNING");
        log.info("[Trace] 开始链路: traceId={}, convId={}, agentId={}",
                traceId, conversationId, agentId);
        return traceId;
    }

    @Override
    public void recordStep(String traceId, String stepType, String content,
                           Object input, Object output, long durationMs) {
        List<TraceStep> steps = traces.get(traceId);
        if (steps == null) {
            steps = new ArrayList<>();
            traces.put(traceId, steps);
        }
        int index = steps.size();
        String inputJson = input != null ? JSON.toJSONString(input) : null;
        String outputJson = output != null ? JSON.toJSONString(output) : null;
        steps.add(new TraceStep(traceId, index, stepType, content,
                inputJson, outputJson, durationMs, LocalDateTime.now()));
        log.debug("[Trace] 记录步骤: traceId={}, step={}, type={}, {}ms",
                traceId, index, stepType, durationMs);
    }

    @Override
    public void endTrace(String traceId, String status) {
        traceStatus.put(traceId, status);
        int stepCount = traces.getOrDefault(traceId, List.of()).size();
        log.info("[Trace] 结束链路: traceId={}, status={}, steps={}",
                traceId, status, stepCount);
    }

    @Override
    public List<TraceStep> getSteps(String traceId) {
        return traces.getOrDefault(traceId, List.of());
    }

    public String getStatus(String traceId) {
        return traceStatus.getOrDefault(traceId, "UNKNOWN");
    }

    public int getTraceCount() {
        return traces.size();
    }

    public void clear() {
        traces.clear();
        traceStatus.clear();
    }

    /**
     * 列出最近的链路 ID
     *
     * @param limit 最大数量
     * @return 链路 ID 列表（按插入顺序倒序）
     */
    public List<String> listRecentTraces(int limit) {
        return traces.keySet().stream()
                .limit(limit > 0 ? limit : 10)
                .toList();
    }
}
