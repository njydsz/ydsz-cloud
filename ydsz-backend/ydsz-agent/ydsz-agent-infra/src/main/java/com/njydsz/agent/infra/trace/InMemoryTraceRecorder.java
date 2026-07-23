package com.njydsz.agent.infra.trace;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.common.json.YdszJson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.agent.domain.trace.TraceRecorder;

/**
 * 内存执行链路记录器
 *
 * <p>使用 {@link ConcurrentHashMap} 在内存中存储执行链路，适用于开发调试。
 * 生产环境可替换为数据库或链路追踪系统实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class InMemoryTraceRecorder implements TraceRecorder {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTraceRecorder.class);
    private final Map<String, List<TraceStep>> traces = new ConcurrentHashMap<>();
    private final Map<String, String> traceStatus = new ConcurrentHashMap<>();
    private final Map<String, TraceMeta> traceMetas = new ConcurrentHashMap<>();

    @Override
    public String startTrace(String conversationId, String agentId) {
        String traceId = UUID.randomUUID().toString();
        traces.put(traceId, new ArrayList<>());
        traceStatus.put(traceId, "RUNNING");
        traceMetas.put(traceId, new TraceMeta(traceId, conversationId, agentId, LocalDateTime.now()));
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
        String inputJson = input != null ? YdszJson.toJson(input) : null;
        String outputJson = output != null ? YdszJson.toJson(output) : null;
        steps.add(new TraceStep(traceId, index, stepType, content,
                inputJson, outputJson, durationMs, LocalDateTime.now()));
        log.debug("[Trace] 记录步骤: traceId={}, step={}, type={}, {}ms",
                traceId, index, stepType, durationMs);
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
        traceMetas.clear();
    }

    /**
     * 列出最近的链路 ID
     *
     * @param limit 最大数量
     * @return 链路 ID 列表（按开始时间倒序）
     */
    public List<String> listRecentTraces(int limit) {
        int safeLimit = limit > 0 ? limit : 10;
        return traceMetas.values().stream()
                .sorted(Comparator.comparing(TraceMeta::getStartedAt).reversed())
                .limit(safeLimit)
                .map(TraceMeta::getTraceId)
                .toList();
    }

    /**
     * 列出最近的链路元数据
     *
     * @param limit 最大数量
     * @return 链路元数据列表（按开始时间倒序）
     */
    public List<TraceMeta> listRecentTraceMetas(int limit) {
        int safeLimit = limit > 0 ? limit : 10;
        return traceMetas.values().stream()
                .sorted(Comparator.comparing(TraceMeta::getStartedAt).reversed())
                .limit(safeLimit)
                .toList();
    }

    /**
     * 获取链路元数据
     *
     * @param traceId 链路 ID
     * @return 元数据，不存在返回 null
     */
    public TraceMeta getTraceMeta(String traceId) {
        return traceMetas.get(traceId);
    }

    /**
     * 链路元数据
     */
    public static class TraceMeta {
        private final String traceId;
        private final String conversationId;
        private final String agentId;
        private final LocalDateTime startedAt;
        private volatile String status;
        private volatile long totalDurationMs;

        public TraceMeta(String traceId, String conversationId, String agentId, LocalDateTime startedAt) {
            this.traceId = traceId;
            this.conversationId = conversationId;
            this.agentId = agentId;
            this.startedAt = startedAt;
            this.status = "RUNNING";
            this.totalDurationMs = 0;
        }

        public String getTraceId() { return traceId; }
        public String getConversationId() { return conversationId; }
        public String getAgentId() { return agentId; }
        public LocalDateTime getStartedAt() { return startedAt; }
        public String getStatus() { return status; }
        public long getTotalDurationMs() { return totalDurationMs; }
        public void setStatus(String status) { this.status = status; }
        public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }
    }
}
