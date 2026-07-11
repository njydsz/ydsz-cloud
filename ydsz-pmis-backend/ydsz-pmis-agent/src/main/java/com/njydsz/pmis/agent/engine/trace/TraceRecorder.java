package com.njydsz.pmis.agent.engine.trace;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Trace 事件记录器（P2-3 落地）。
 *
 * <p>收集和存储 Agent 执行过程中的 Trace 事件，支持按 traceId / sessionId 查询。
 * 对标 LangSmith Trace Store / Langfuse Storage。
 *
 * <p>当前使用内存存储（LRU 淘汰），生产环境可替换为持久化存储（Redis / DB）。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0 (P2-3)
 */
@Slf4j
@Component
public class TraceRecorder {

    /** 最大缓存的 Trace 数量 */
    private static final int MAX_TRACES = 1000;

    /** 每个 Trace 最大事件数 */
    private static final int MAX_EVENTS_PER_TRACE = 200;

    /** traceId → 事件列表 */
    private final Map<String, Queue<TraceEvent>> traceStore = new ConcurrentHashMap<>();

    /** sessionId → traceId 列表 */
    private final Map<String, List<String>> sessionIndex = new ConcurrentHashMap<>();

    /**
     * 记录 Trace 事件。
     *
     * @param traceId 追踪 ID
     * @param event   事件
     */
    public void record(String traceId, TraceEvent event) {
        if (traceId == null || event == null) return;
        Queue<TraceEvent> events = traceStore.computeIfAbsent(traceId,
                k -> new ConcurrentLinkedQueue<>());
        if (events.size() >= MAX_EVENTS_PER_TRACE) {
            events.poll(); // 淘汰最旧的事件
        }
        events.add(event);
    }

    /**
     * 批量记录事件。
     */
    public void recordAll(String traceId, List<TraceEvent> events) {
        if (traceId == null || events == null) return;
        for (TraceEvent event : events) {
            record(traceId, event);
        }
    }

    /**
     * 获取 Trace 的事件列表。
     *
     * @param traceId 追踪 ID
     * @return 事件列表；不存在返回 null
     */
    public List<TraceEvent> getEvents(String traceId) {
        Queue<TraceEvent> events = traceStore.get(traceId);
        if (events == null) return Collections.emptyList();
        return new ArrayList<>(events);
    }

    /**
     * 列出 Trace ID（按时间倒序）。
     *
     * @param sessionId 会话 ID（可选，为 null 返回所有）
     * @param limit     返回数量
     * @return Trace ID 列表
     */
    public List<String> listTraceIds(String sessionId, int limit) {
        if (sessionId != null && !sessionId.isBlank()) {
            List<String> ids = sessionIndex.get(sessionId);
            if (ids == null) return Collections.emptyList();
            return ids.stream().limit(limit).collect(Collectors.toList());
        }
        return traceStore.keySet().stream()
                .limit(limit > 0 ? limit : 20)
                .collect(Collectors.toList());
    }

    /**
     * 关联 Trace 到 Session。
     *
     * @param sessionId 会话 ID
     * @param traceId   追踪 ID
     */
    public void associateSession(String sessionId, String traceId) {
        if (sessionId == null || traceId == null) return;
        sessionIndex.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(traceId);
    }

    /**
     * 删除 Trace。
     *
     * @param traceId 追踪 ID
     */
    public void remove(String traceId) {
        traceStore.remove(traceId);
    }

    /**
     * 清空所有 Trace。
     */
    public void clear() {
        traceStore.clear();
        sessionIndex.clear();
    }

    /**
     * 获取当前缓存的 Trace 数量。
     */
    public int size() {
        return traceStore.size();
    }
}
