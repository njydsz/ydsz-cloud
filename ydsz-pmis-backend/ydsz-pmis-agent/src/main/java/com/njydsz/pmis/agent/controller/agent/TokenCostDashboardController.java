package com.njydsz.pmis.agent.controller.agent;

import com.njydsz.pmis.agent.engine.llm.LlmProviderRouter;
import com.njydsz.pmis.agent.engine.llm.ModelLoadBalancer;
import com.njydsz.pmis.agent.engine.llm.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token 成本看板 API（P2-7 落地）。
 *
 * <p>对标 Coze 用量统计 / Dify Token Usage / OpenAI Usage Dashboard：
 * 提供可视化的 Token 消耗和成本统计 API。
 *
 * <p>功能：
 * <ul>
 *   <li><b>总览</b> - 总 Token 数、总成本、请求次数</li>
 *   <li><b>按模型统计</b> - 每个 Provider/Model 的 Token 消耗和成本</li>
 *   <li><b>按任务类型统计</b> - 按任务类型（QA/推理/代码等）的 Token 分布</li>
 *   <li><b>按时间趋势</b> - 按日/小时统计 Token 消耗趋势</li>
 *   <li><b>缓存命中率</b> - 响应缓存命中率统计</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0 (P2-7)
 */
@Slf4j
@RestController
@RequestMapping("/agent/token-dashboard")
@RequiredArgsConstructor
public class TokenCostDashboardController {

    private final LlmProviderRouter llmProviderRouter;
    private final ObjectProvider<ModelLoadBalancer> loadBalancerProvider;

    // ==================== 内存统计存储 ====================

    /** 全局 Token 统计 */
    private static final AtomicLong totalPromptTokens = new AtomicLong(0);
    private static final AtomicLong totalCompletionTokens = new AtomicLong(0);
    private static final AtomicLong totalRequests = new AtomicLong(0);

    /** 按模型统计 */
    private static final Map<String, ModelStats> modelStatsMap = new ConcurrentHashMap<>();

    /** 按任务类型统计 */
    private static final Map<String, TaskStats> taskStatsMap = new ConcurrentHashMap<>();

    /** 按日期统计（yyyy-MM-dd → stats） */
    private static final Map<String, DailyStats> dailyStatsMap = new ConcurrentHashMap<>();

    /**
     * 记录 Token 用量（供内部调用）。
     */
    public static void recordUsage(TokenUsage usage, String taskType) {
        if (usage == null) return;

        totalPromptTokens.addAndGet(usage.getPromptTokens());
        totalCompletionTokens.addAndGet(usage.getCompletionTokens());
        totalRequests.incrementAndGet();

        // 按模型统计
        String modelKey = usage.getModel() != null ? usage.getModel()
                : (usage.getProvider() != null ? usage.getProvider() : "unknown");
        modelStatsMap.computeIfAbsent(modelKey, k -> new ModelStats())
                .record(usage.getPromptTokens(), usage.getCompletionTokens(),
                        usage.estimatedCostUsd());

        // 按任务类型统计
        String taskKey = taskType != null ? taskType : "DEFAULT";
        taskStatsMap.computeIfAbsent(taskKey, k -> new TaskStats())
                .record(usage.getPromptTokens(), usage.getCompletionTokens());

        // 按日期统计
        String dateKey = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date());
        dailyStatsMap.computeIfAbsent(dateKey, k -> new DailyStats())
                .record(usage.getPromptTokens(), usage.getCompletionTokens(),
                        usage.estimatedCostUsd());
    }

    /**
     * 成本总览。
     */
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalPromptTokens", totalPromptTokens.get());
        response.put("totalCompletionTokens", totalCompletionTokens.get());
        response.put("totalTokens", totalPromptTokens.get() + totalCompletionTokens.get());
        response.put("totalRequests", totalRequests.get());

        double totalCost = modelStatsMap.values().stream()
                .mapToDouble(s -> s.totalCost.get())
                .sum();
        response.put("totalEstimatedCostUsd", String.format("%.4f", totalCost));

        // 平均 Token
        long avgTokens = totalRequests.get() > 0
                ? (totalPromptTokens.get() + totalCompletionTokens.get()) / totalRequests.get()
                : 0;
        response.put("avgTokensPerRequest", avgTokens);

        // 缓存命中率
        response.put("cacheHitRate", String.format("%.2f", llmProviderRouter.getCacheHitRate()));
        response.put("activeProvider", llmProviderRouter.getActiveProviderName());

        return ResponseEntity.ok(response);
    }

    /**
     * 按模型统计。
     */
    @GetMapping("/by-model")
    public ResponseEntity<Map<String, Object>> byModel() {
        Map<String, Object> response = new LinkedHashMap<>();
        List<Map<String, Object>> models = new ArrayList<>();

        for (Map.Entry<String, ModelStats> entry : modelStatsMap.entrySet()) {
            Map<String, Object> stat = new LinkedHashMap<>();
            ModelStats s = entry.getValue();
            stat.put("model", entry.getKey());
            stat.put("promptTokens", s.promptTokens.get());
            stat.put("completionTokens", s.completionTokens.get());
            stat.put("totalTokens", s.promptTokens.get() + s.completionTokens.get());
            stat.put("requests", s.requests.get());
            stat.put("estimatedCostUsd", String.format("%.4f", s.totalCost.get()));
            stat.put("avgTokensPerRequest",
                    s.requests.get() > 0
                            ? (s.promptTokens.get() + s.completionTokens.get()) / s.requests.get()
                            : 0);
            models.add(stat);
        }

        models.sort((a, b) -> Long.compare(
                (long) b.get("totalTokens"), (long) a.get("totalTokens")));

        response.put("models", models);
        response.put("total", models.size());
        return ResponseEntity.ok(response);
    }

    /**
     * 按任务类型统计。
     */
    @GetMapping("/by-task")
    public ResponseEntity<Map<String, Object>> byTask() {
        Map<String, Object> response = new LinkedHashMap<>();
        List<Map<String, Object>> tasks = new ArrayList<>();

        for (Map.Entry<String, TaskStats> entry : taskStatsMap.entrySet()) {
            Map<String, Object> stat = new LinkedHashMap<>();
            TaskStats s = entry.getValue();
            stat.put("taskType", entry.getKey());
            stat.put("promptTokens", s.promptTokens.get());
            stat.put("completionTokens", s.completionTokens.get());
            stat.put("totalTokens", s.promptTokens.get() + s.completionTokens.get());
            stat.put("requests", s.requests.get());
            tasks.add(stat);
        }

        tasks.sort((a, b) -> Long.compare(
                (long) b.get("totalTokens"), (long) a.get("totalTokens")));

        response.put("tasks", tasks);
        response.put("total", tasks.size());
        return ResponseEntity.ok(response);
    }

    /**
     * 按日期趋势。
     */
    @GetMapping("/trend")
    public ResponseEntity<Map<String, Object>> trend() {
        Map<String, Object> response = new LinkedHashMap<>();
        List<Map<String, Object>> days = new ArrayList<>();

        // 按日期排序
        List<String> sortedDates = new ArrayList<>(dailyStatsMap.keySet());
        Collections.sort(sortedDates);

        for (String date : sortedDates) {
            DailyStats s = dailyStatsMap.get(date);
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("date", date);
            stat.put("promptTokens", s.promptTokens.get());
            stat.put("completionTokens", s.completionTokens.get());
            stat.put("totalTokens", s.promptTokens.get() + s.completionTokens.get());
            stat.put("requests", s.requests.get());
            stat.put("estimatedCostUsd", String.format("%.4f", s.totalCost.get()));
            days.add(stat);
        }

        response.put("trend", days);
        response.put("total", days.size());
        return ResponseEntity.ok(response);
    }

    /**
     * 负载均衡统计。
     */
    @GetMapping("/load-balancer")
    public ResponseEntity<Map<String, Object>> loadBalancerStats() {
        Map<String, Object> response = new LinkedHashMap<>();
        ModelLoadBalancer balancer = loadBalancerProvider.getIfAvailable();
        if (balancer != null) {
            response.put("stats", balancer.getStats());
        } else {
            response.put("stats", Collections.emptyMap());
            response.put("note", "ModelLoadBalancer 未启用");
        }
        return ResponseEntity.ok(response);
    }

    // ==================== 统计数据结构 ====================

    private static class ModelStats {
        final AtomicLong promptTokens = new AtomicLong(0);
        final AtomicLong completionTokens = new AtomicLong(0);
        final AtomicInteger requests = new AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicReference<Double> totalCost =
                new java.util.concurrent.atomic.AtomicReference<>(0.0);

        void record(int prompt, int completion, double cost) {
            promptTokens.addAndGet(prompt);
            completionTokens.addAndGet(completion);
            requests.incrementAndGet();
            totalCost.updateAndGet(v -> v + cost);
        }
    }

    private static class TaskStats {
        final AtomicLong promptTokens = new AtomicLong(0);
        final AtomicLong completionTokens = new AtomicLong(0);
        final AtomicInteger requests = new AtomicInteger(0);

        void record(int prompt, int completion) {
            promptTokens.addAndGet(prompt);
            completionTokens.addAndGet(completion);
            requests.incrementAndGet();
        }
    }

    private static class DailyStats {
        final AtomicLong promptTokens = new AtomicLong(0);
        final AtomicLong completionTokens = new AtomicLong(0);
        final AtomicInteger requests = new AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicReference<Double> totalCost =
                new java.util.concurrent.atomic.AtomicReference<>(0.0);

        void record(int prompt, int completion, double cost) {
            promptTokens.addAndGet(prompt);
            completionTokens.addAndGet(completion);
            requests.incrementAndGet();
            totalCost.updateAndGet(v -> v + cost);
        }
    }
}
