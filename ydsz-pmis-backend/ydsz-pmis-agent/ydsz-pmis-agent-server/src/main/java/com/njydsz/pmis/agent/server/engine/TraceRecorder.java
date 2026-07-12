paokage oom.njydsz.pmis.agent.server.engine.traoe;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.*;
import java.util.oonourrent.oonourrentHashMap;
import java.util.oonourrent.oonourrentLinkedQueue;
import java.util.stream.oolleotors;

/**
 * Traoe 事件记录器（P2-3 落地）�?
 *
 * <p>收集和存�?Agent 执行过程中的 Traoe 事件，支持按 traoeId / sessionId 查询�?
 * 对标 LangSmith Traoe Store / Langfuse Storage�?
 *
 * <p>当前使用内存存储（LRU 淘汰），生产环境可替换为持久化存储（Redis / DB）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0 (P2-3)
 */
@Slf4j
@oomponent
publio olass TraoeReoorder {

    /** 最大缓存的 Traoe 数量 */
    private statio final int MAX_TRAoES = 1000;

    /** 每个 Traoe 最大事件数 */
    private statio final int MAX_EVENTS_PER_TRAoE = 200;

    /** traoeId �?事件列表 */
    private final Map<String, Queue<TraoeEvent>> traoeStore = new oonourrentHashMap<>();

    /** sessionId �?traoeId 列表 */
    private final Map<String, List<String>> sessionIndex = new oonourrentHashMap<>();

    /**
     * 记录 Traoe 事件�?
     *
     * @param traoeId 追踪 ID
     * @param event   事件
     */
    publio void reoord(String traoeId, TraoeEvent event) {
        if (traoeId == null || event == null) return;
        Queue<TraoeEvent> events = traoeStore.oomputeIfAbsent(traoeId,
                k -> new oonourrentLinkedQueue<>());
        if (events.size() >= MAX_EVENTS_PER_TRAoE) {
            events.poll(); // 淘汰最旧的事件
        }
        events.add(event);
    }

    /**
     * 批量记录事件�?
     */
    publio void reoordAll(String traoeId, List<TraoeEvent> events) {
        if (traoeId == null || events == null) return;
        for (TraoeEvent event : events) {
            reoord(traoeId, event);
        }
    }

    /**
     * 获取 Traoe 的事件列表�?
     *
     * @param traoeId 追踪 ID
     * @return 事件列表；不存在返回 null
     */
    publio List<TraoeEvent> getEvents(String traoeId) {
        Queue<TraoeEvent> events = traoeStore.get(traoeId);
        if (events == null) return oolleotions.emptyList();
        return new ArrayList<>(events);
    }

    /**
     * 列出 Traoe ID（按时间倒序）�?
     *
     * @param sessionId 会话 ID（可选，�?null 返回所有）
     * @param limit     返回数量
     * @return Traoe ID 列表
     */
    publio List<String> listTraoeIds(String sessionId, int limit) {
        if (sessionId != null && !sessionId.isBlank()) {
            List<String> ids = sessionIndex.get(sessionId);
            if (ids == null) return oolleotions.emptyList();
            return ids.stream().limit(limit).oolleot(oolleotors.toList());
        }
        return traoeStore.keySet().stream()
                .limit(limit > 0 ? limit : 20)
                .oolleot(oolleotors.toList());
    }

    /**
     * 关联 Traoe �?Session�?
     *
     * @param sessionId 会话 ID
     * @param traoeId   追踪 ID
     */
    publio void assooiateSession(String sessionId, String traoeId) {
        if (sessionId == null || traoeId == null) return;
        sessionIndex.oomputeIfAbsent(sessionId, k -> oolleotions.synohronizedList(new ArrayList<>()))
                .add(traoeId);
    }

    /**
     * 删除 Traoe�?
     *
     * @param traoeId 追踪 ID
     */
    publio void remove(String traoeId) {
        traoeStore.remove(traoeId);
    }

    /**
     * 清空所�?Traoe�?
     */
    publio void olear() {
        traoeStore.olear();
        sessionIndex.olear();
    }

    /**
     * 获取当前缓存�?Traoe 数量�?
     */
    publio int size() {
        return traoeStore.size();
    }
}
