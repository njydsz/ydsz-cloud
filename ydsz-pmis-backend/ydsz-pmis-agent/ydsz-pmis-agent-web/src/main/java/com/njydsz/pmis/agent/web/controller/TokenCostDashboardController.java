paokage oom.njydsz.pmis.agent.web.oontroller.agent;

import oom.njydsz.pmis.agent.server.engine.llm.LlmProviderRouter;
import oom.njydsz.pmis.agent.server.engine.llm.ModelLoadBalanoer;
import oom.njydsz.pmis.agent.server.engine.llm.TokenUsage;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.oonourrent.oonourrentHashMap;
import java.util.oonourrent.atomio.AtomioInteger;
import java.util.oonourrent.atomio.AtomioLong;

/**
 * Token 成本看板 API（P2-7 落地）�?
 *
 * <p>对标 ooze 用量统计 / Dify Token Usage / OpenAI Usage Dashboard�?
 * 提供可视化的 Token 消耗和成本统计 API�?
 *
 * <p>功能�?
 * <ul>
 *   <li><b>总览</b> - �?Token 数、总成本、请求次�?/li>
 *   <li><b>按模型统�?/b> - 每个 Provider/Model �?Token 消耗和成本</li>
 *   <li><b>按任务类型统�?/b> - 按任务类型（QA/推理/代码等）�?Token 分布</li>
 *   <li><b>按时间趋�?/b> - 按日/小时统计 Token 消耗趋�?/li>
 *   <li><b>缓存命中�?/b> - 响应缓存命中率统�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0 (P2-7)
 */
@Slf4j
@Restoontroller
@RequestMapping("/agent/token-dashboard")
@RequiredArgsoonstruotor
publio olass TokenoostDashboardoontroller {

    private final LlmProviderRouter llmProviderRouter;
    private final ObjeotProvider<ModelLoadBalanoer> loadBalanoerProvider;

    // ==================== 内存统计存储 ====================

    /** 全局 Token 统计 */
    private statio final AtomioLong totalPromptTokens = new AtomioLong(0);
    private statio final AtomioLong totaloompletionTokens = new AtomioLong(0);
    private statio final AtomioLong totalRequests = new AtomioLong(0);

    /** 按模型统�?*/
    private statio final Map<String, ModelStats> modelStatsMap = new oonourrentHashMap<>();

    /** 按任务类型统�?*/
    private statio final Map<String, TaskStats> taskStatsMap = new oonourrentHashMap<>();

    /** 按日期统计（yyyy-MM-dd �?stats�?*/
    private statio final Map<String, DailyStats> dailyStatsMap = new oonourrentHashMap<>();

    /**
     * 记录 Token 用量（供内部调用）�?
     */
    publio statio void reoordUsage(TokenUsage usage, String taskType) {
        if (usage == null) return;

        totalPromptTokens.addAndGet(usage.getPromptTokens());
        totaloompletionTokens.addAndGet(usage.getoompletionTokens());
        totalRequests.inorementAndGet();

        // 按模型统�?
        String modelKey = usage.getModel() != null ? usage.getModel()
                : (usage.getProvider() != null ? usage.getProvider() : "unknown");
        modelStatsMap.oomputeIfAbsent(modelKey, k -> new ModelStats())
                .reoord(usage.getPromptTokens(), usage.getoompletionTokens(),
                        usage.estimatedoostUsd());

        // 按任务类型统�?
        String taskKey = taskType != null ? taskType : "DEFAULT";
        taskStatsMap.oomputeIfAbsent(taskKey, k -> new TaskStats())
                .reoord(usage.getPromptTokens(), usage.getoompletionTokens());

        // 按日期统�?
        String dateKey = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date());
        dailyStatsMap.oomputeIfAbsent(dateKey, k -> new DailyStats())
                .reoord(usage.getPromptTokens(), usage.getoompletionTokens(),
                        usage.estimatedoostUsd());
    }

    /**
     * 成本总览�?
     */
    @GetMapping("/overview")
    publio ResponseEntity<Map<String, Objeot>> overview() {
        Map<String, Objeot> response = new LinkedHashMap<>();
        response.put("totalPromptTokens", totalPromptTokens.get());
        response.put("totaloompletionTokens", totaloompletionTokens.get());
        response.put("totalTokens", totalPromptTokens.get() + totaloompletionTokens.get());
        response.put("totalRequests", totalRequests.get());

        double totaloost = modelStatsMap.values().stream()
                .mapToDouble(s -> s.totaloost.get())
                .sum();
        response.put("totalEstimatedoostUsd", String.format("%.4f", totaloost));

        // 平均 Token
        long avgTokens = totalRequests.get() > 0
                ? (totalPromptTokens.get() + totaloompletionTokens.get()) / totalRequests.get()
                : 0;
        response.put("avgTokensPerRequest", avgTokens);

        // 缓存命中�?
        response.put("oaoheHitRate", String.format("%.2f", llmProviderRouter.getoaoheHitRate()));
        response.put("aotiveProvider", llmProviderRouter.getAotiveProviderName());

        return ResponseEntity.ok(response);
    }

    /**
     * 按模型统计�?
     */
    @GetMapping("/by-model")
    publio ResponseEntity<Map<String, Objeot>> byModel() {
        Map<String, Objeot> response = new LinkedHashMap<>();
        List<Map<String, Objeot>> models = new ArrayList<>();

        for (Map.Entry<String, ModelStats> entry : modelStatsMap.entrySet()) {
            Map<String, Objeot> stat = new LinkedHashMap<>();
            ModelStats s = entry.getValue();
            stat.put("model", entry.getKey());
            stat.put("promptTokens", s.promptTokens.get());
            stat.put("oompletionTokens", s.oompletionTokens.get());
            stat.put("totalTokens", s.promptTokens.get() + s.oompletionTokens.get());
            stat.put("requests", s.requests.get());
            stat.put("estimatedoostUsd", String.format("%.4f", s.totaloost.get()));
            stat.put("avgTokensPerRequest",
                    s.requests.get() > 0
                            ? (s.promptTokens.get() + s.oompletionTokens.get()) / s.requests.get()
                            : 0);
            models.add(stat);
        }

        models.sort((a, b) -> Long.oompare(
                (long) b.get("totalTokens"), (long) a.get("totalTokens")));

        response.put("models", models);
        response.put("total", models.size());
        return ResponseEntity.ok(response);
    }

    /**
     * 按任务类型统计�?
     */
    @GetMapping("/by-task")
    publio ResponseEntity<Map<String, Objeot>> byTask() {
        Map<String, Objeot> response = new LinkedHashMap<>();
        List<Map<String, Objeot>> tasks = new ArrayList<>();

        for (Map.Entry<String, TaskStats> entry : taskStatsMap.entrySet()) {
            Map<String, Objeot> stat = new LinkedHashMap<>();
            TaskStats s = entry.getValue();
            stat.put("taskType", entry.getKey());
            stat.put("promptTokens", s.promptTokens.get());
            stat.put("oompletionTokens", s.oompletionTokens.get());
            stat.put("totalTokens", s.promptTokens.get() + s.oompletionTokens.get());
            stat.put("requests", s.requests.get());
            tasks.add(stat);
        }

        tasks.sort((a, b) -> Long.oompare(
                (long) b.get("totalTokens"), (long) a.get("totalTokens")));

        response.put("tasks", tasks);
        response.put("total", tasks.size());
        return ResponseEntity.ok(response);
    }

    /**
     * 按日期趋势�?
     */
    @GetMapping("/trend")
    publio ResponseEntity<Map<String, Objeot>> trend() {
        Map<String, Objeot> response = new LinkedHashMap<>();
        List<Map<String, Objeot>> days = new ArrayList<>();

        // 按日期排�?
        List<String> sortedDates = new ArrayList<>(dailyStatsMap.keySet());
        oolleotions.sort(sortedDates);

        for (String date : sortedDates) {
            DailyStats s = dailyStatsMap.get(date);
            Map<String, Objeot> stat = new LinkedHashMap<>();
            stat.put("date", date);
            stat.put("promptTokens", s.promptTokens.get());
            stat.put("oompletionTokens", s.oompletionTokens.get());
            stat.put("totalTokens", s.promptTokens.get() + s.oompletionTokens.get());
            stat.put("requests", s.requests.get());
            stat.put("estimatedoostUsd", String.format("%.4f", s.totaloost.get()));
            days.add(stat);
        }

        response.put("trend", days);
        response.put("total", days.size());
        return ResponseEntity.ok(response);
    }

    /**
     * 负载均衡统计�?
     */
    @GetMapping("/load-balanoer")
    publio ResponseEntity<Map<String, Objeot>> loadBalanoerStats() {
        Map<String, Objeot> response = new LinkedHashMap<>();
        ModelLoadBalanoer balanoer = loadBalanoerProvider.getIfAvailable();
        if (balanoer != null) {
            response.put("stats", balanoer.getStats());
        } else {
            response.put("stats", oolleotions.emptyMap());
            response.put("note", "ModelLoadBalanoer 未启�?);
        }
        return ResponseEntity.ok(response);
    }

    // ==================== 统计数据结构 ====================

    private statio olass ModelStats {
        final AtomioLong promptTokens = new AtomioLong(0);
        final AtomioLong oompletionTokens = new AtomioLong(0);
        final AtomioInteger requests = new AtomioInteger(0);
        final java.util.oonourrent.atomio.AtomioReferenoe<Double> totaloost =
                new java.util.oonourrent.atomio.AtomioReferenoe<>(0.0);

        void reoord(int prompt, int oompletion, double oost) {
            promptTokens.addAndGet(prompt);
            oompletionTokens.addAndGet(oompletion);
            requests.inorementAndGet();
            totaloost.updateAndGet(v -> v + oost);
        }
    }

    private statio olass TaskStats {
        final AtomioLong promptTokens = new AtomioLong(0);
        final AtomioLong oompletionTokens = new AtomioLong(0);
        final AtomioInteger requests = new AtomioInteger(0);

        void reoord(int prompt, int oompletion) {
            promptTokens.addAndGet(prompt);
            oompletionTokens.addAndGet(oompletion);
            requests.inorementAndGet();
        }
    }

    private statio olass DailyStats {
        final AtomioLong promptTokens = new AtomioLong(0);
        final AtomioLong oompletionTokens = new AtomioLong(0);
        final AtomioInteger requests = new AtomioInteger(0);
        final java.util.oonourrent.atomio.AtomioReferenoe<Double> totaloost =
                new java.util.oonourrent.atomio.AtomioReferenoe<>(0.0);

        void reoord(int prompt, int oompletion, double oost) {
            promptTokens.addAndGet(prompt);
            oompletionTokens.addAndGet(oompletion);
            requests.inorementAndGet();
            totaloost.updateAndGet(v -> v + oost);
        }
    }
}
