package com.remisoft.common.queue.trace;

import java.util.*;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * 基于内存的消息轨迹记录器（默认实现）
 *
 * <p>使用 ConcurrentHashMap 存储轨迹数据，LRU 策略控制最大容量（默认1000条）。
 * 内置 TTL 过期机制，默认30分钟自动清理过期轨迹。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public class DefaultMessageTraceRecorder implements MessageTraceRecorder {

    /**
     * 最大缓存条目数（LRU）
     */
    private final int maxCapacity;

    /**
     * TTL 过期时间（分钟）
     */
    private final long ttlMinutes;

    /**
     * 轨迹存储，key 为 messageId
     */
    private final LinkedHashMap<String, List<MessageTrace>> store;

    /**
     * 按 traceId 索引，key 为 traceId
     */
    private final LinkedHashMap<String, List<String>> traceIdIndex;

    public DefaultMessageTraceRecorder() {
        this(1000, 30);
    }

    public DefaultMessageTraceRecorder(int maxCapacity, long ttlMinutes) {
        this.maxCapacity = maxCapacity;
        this.ttlMinutes = ttlMinutes;

        this.store = new LinkedHashMap<>(maxCapacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<MessageTrace>> eldest) {
                if (size() > maxCapacity) {
                    traceIdIndex.values().forEach(list -> list.remove(eldest.getKey()));
                    traceIdIndex.entrySet().removeIf(e -> e.getValue().isEmpty());
                    log.debug("[MessageTrace] LRU 清理过期条目，messageId={}", eldest.getKey());
                    return true;
                }
                return false;
            }
        };

        this.traceIdIndex = new LinkedHashMap<>();
    }

    @Override
    public synchronized void record(MessageTrace trace) {
        if (trace == null || trace.getMessageId() == null) {
            log.warn("[MessageTrace] 轨迹记录被忽略，trace 或 messageId 为空");
            return;
        }

        expireOldEntries();

        String messageId = trace.getMessageId();
        List<MessageTrace> traces = store.computeIfAbsent(messageId, k -> new ArrayList<>());
        traces.add(trace);

        String traceId = trace.getTraceId();
        if (traceId != null) {
            traceIdIndex.computeIfAbsent(traceId, k -> new ArrayList<>()).add(messageId);
        }

        log.debug("[MessageTrace] 轨迹已记录，messageId={}, traceId={}, status={}",
                messageId, traceId, trace.getStatus());
    }

    @Override
    public synchronized List<MessageTrace> queryByMessageId(String messageId) {
        if (messageId == null) {
            return Collections.emptyList();
        }
        expireOldEntries();
        return Collections.unmodifiableList(store.getOrDefault(messageId, Collections.emptyList()));
    }

    @Override
    public synchronized List<MessageTrace> queryByTraceId(String traceId) {
        if (traceId == null) {
            return Collections.emptyList();
        }
        expireOldEntries();

        List<String> messageIds = traceIdIndex.getOrDefault(traceId, Collections.emptyList());
        return messageIds.stream()
                .map(store::get)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * 清理过期条目
     */
    private void expireOldEntries() {
        long cutoff = System.currentTimeMillis() - ttlMinutes * 60 * 1000;
        Iterator<Map.Entry<String, List<MessageTrace>>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, List<MessageTrace>> entry = it.next();
            List<MessageTrace> traces = entry.getValue();
            if (traces.isEmpty() || isExpired(traces.get(traces.size() - 1), cutoff)) {
                String messageId = entry.getKey();
                traceIdIndex.values().forEach(list -> list.remove(messageId));
                traceIdIndex.entrySet().removeIf(e -> e.getValue().isEmpty());
                it.remove();
            }
        }
    }

    private boolean isExpired(MessageTrace trace, long cutoff) {
        Long lastTimestamp = trace.getTimestamp("sent");
        if (lastTimestamp == null) {
            lastTimestamp = trace.getTimestamp("delivered");
        }
        if (lastTimestamp == null) {
            lastTimestamp = trace.getTimestamp("consumed");
        }
        if (lastTimestamp == null) {
            lastTimestamp = trace.getTimestamp("failed");
        }
        return lastTimestamp == null || lastTimestamp < cutoff;
    }
}
