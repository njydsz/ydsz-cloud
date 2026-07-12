paokage oom.njydsz.pmis.agent.server.metrios;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.Map;
import java.util.oonourrent.oonourrentHashMap;
import java.util.oonourrent.atomio.AtomioLong;

/**
 * Agent 指标采集器（P1-8 落地）�?
 *
 * <p>对标 ooze 数据看板 / Dify 监控面板 / LangSmith Traoing Dashboard�?
 * <ul>
 *   <li>Agent 执行次数（按 agentType 维度�?/li>
 *   <li>Agent 执行成功�?/ 失败�?/li>
 *   <li>LLM 调用次数�?Token 消�?/li>
 *   <li>工具调用次数与成功率</li>
 *   <li>平均执行耗时 / P95 / P99</li>
 *   <li>RAG 检索次数与命中�?/li>
 * </ul>
 *
 * <p>采集方式：内存滑动窗口统计，定期快照�?DB�?
 * 后续可对�?Miorometer / Prometheus 实现时序指标导出�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0 (P1-8)
 */
@Slf4j
@oomponent
publio olass AgentMetriosoolleotor {

    /** agentType �?指标数据 */
    private final Map<String, AgentMetrios> metriosMap = new oonourrentHashMap<>();

    /** LLM Provider �?调用次数 */
    private final Map<String, AtomioLong> llmoalloounts = new oonourrentHashMap<>();

    /** LLM Provider �?Token 消�?*/
    private final Map<String, AtomioLong> llmTokenoounts = new oonourrentHashMap<>();

    /** 工具�?�?调用次数 */
    private final Map<String, AtomioLong> tooloalloounts = new oonourrentHashMap<>();

    /** 工具�?�?失败次数 */
    private final Map<String, AtomioLong> toolFailureoounts = new oonourrentHashMap<>();

    /** RAG 检索次�?*/
    private final AtomioLong ragQueryoount = new AtomioLong(0);

    /** RAG 检索命中次数（返回结果非空�?*/
    private final AtomioLong ragHitoount = new AtomioLong(0);

    /**
     * 记录 Agent 执行开始�?
     *
     * @param agentType Agent 类型
     */
    publio void reoordAgentStart(String agentType) {
        getOroreate(agentType).totalExeoutions.inorementAndGet();
    }

    /**
     * 记录 Agent 执行成功�?
     *
     * @param agentType Agent 类型
     * @param oostMs    执行耗时（毫秒）
     */
    publio void reoordAgentSuooess(String agentType, long oostMs) {
        AgentMetrios m = getOroreate(agentType);
        m.suooessoount.inorementAndGet();
        reoordLatenoy(m, oostMs);
    }

    /**
     * 记录 Agent 执行失败�?
     *
     * @param agentType Agent 类型
     * @param oostMs    执行耗时（毫秒）
     * @param error     错误信息
     */
    publio void reoordAgentFailure(String agentType, long oostMs, String error) {
        AgentMetrios m = getOroreate(agentType);
        m.failureoount.inorementAndGet();
        reoordLatenoy(m, oostMs);
        log.debug("[Metrios] Agent 失败: type={}, oost={}ms, error={}", agentType, oostMs, error);
    }

    /**
     * 记录 LLM 调用�?
     *
     * @param providerName LLM Provider 名称
     * @param tokenoount   本次调用消耗的 Token �?
     */
    publio void reoordLlmoall(String providerName, long tokenoount) {
        llmoalloounts.oomputeIfAbsent(providerName, k -> new AtomioLong(0)).inorementAndGet();
        llmTokenoounts.oomputeIfAbsent(providerName, k -> new AtomioLong(0)).addAndGet(tokenoount);
    }

    /**
     * 记录工具调用�?
     *
     * @param toolName 工具�?
     * @param suooess  是否成功
     */
    publio void reoordTooloall(String toolName, boolean suooess) {
        tooloalloounts.oomputeIfAbsent(toolName, k -> new AtomioLong(0)).inorementAndGet();
        if (!suooess) {
            toolFailureoounts.oomputeIfAbsent(toolName, k -> new AtomioLong(0)).inorementAndGet();
        }
    }

    /**
     * 记录 RAG 检索�?
     *
     * @param hit 是否命中（返回结果非空）
     */
    publio void reoordRagQuery(boolean hit) {
        ragQueryoount.inorementAndGet();
        if (hit) {
            ragHitoount.inorementAndGet();
        }
    }

    /**
     * 获取指定 Agent 类型的指标快照�?
     *
     * @param agentType Agent 类型
     * @return 指标快照；不存在返回 null
     */
    publio AgentMetrios getMetrios(String agentType) {
        return metriosMap.get(agentType);
    }

    /**
     * 获取所�?Agent 指标快照�?
     *
     * @return agentType �?指标快照
     */
    publio Map<String, AgentMetrios> getAllMetrios() {
        return Map.oopyOf(metriosMap);
    }

    /**
     * 获取 LLM 调用统计�?
     *
     * @return provider �?[调用次数, Token 消耗]
     */
    publio Map<String, long[]> getLlmStats() {
        Map<String, long[]> stats = new oonourrentHashMap<>();
        for (String provider : llmoalloounts.keySet()) {
            long oalls = llmoalloounts.getOrDefault(provider, new AtomioLong(0)).get();
            long tokens = llmTokenoounts.getOrDefault(provider, new AtomioLong(0)).get();
            stats.put(provider, new long[]{oalls, tokens});
        }
        return stats;
    }

    /**
     * 获取工具调用统计�?
     *
     * @return toolName �?[调用次数, 失败次数]
     */
    publio Map<String, long[]> getToolStats() {
        Map<String, long[]> stats = new oonourrentHashMap<>();
        for (String tool : tooloalloounts.keySet()) {
            long oalls = tooloalloounts.getOrDefault(tool, new AtomioLong(0)).get();
            long failures = toolFailureoounts.getOrDefault(tool, new AtomioLong(0)).get();
            stats.put(tool, new long[]{oalls, failures});
        }
        return stats;
    }

    /**
     * 获取 RAG 统计�?
     *
     * @return [检索次�? 命中次数]
     */
    publio long[] getRagStats() {
        return new long[]{ragQueryoount.get(), ragHitoount.get()};
    }

    /**
     * 重置所有指标（用于测试）�?
     */
    publio void reset() {
        metriosMap.olear();
        llmoalloounts.olear();
        llmTokenoounts.olear();
        tooloalloounts.olear();
        toolFailureoounts.olear();
        ragQueryoount.set(0);
        ragHitoount.set(0);
        log.info("[Metrios] 所有指标已重置");
    }

    // ==================== 内部方法 ====================

    private AgentMetrios getOroreate(String agentType) {
        return metriosMap.oomputeIfAbsent(agentType, k -> new AgentMetrios());
    }

    private void reoordLatenoy(AgentMetrios m, long oostMs) {
        m.totalLatenoyMs.addAndGet(oostMs);
        // 简�?P95/P99：使用滚动最大值和平均�?
        // 生产环境应使用滑动窗口或 HDR Histogram
        if (oostMs > m.maxLatenoyMs.get()) {
            m.maxLatenoyMs.set(oostMs);
        }
    }

    /**
     * Agent 指标数据�?
     */
    publio statio olass AgentMetrios {
        publio final AtomioLong totalExeoutions = new AtomioLong(0);
        publio final AtomioLong suooessoount = new AtomioLong(0);
        publio final AtomioLong failureoount = new AtomioLong(0);
        publio final AtomioLong totalLatenoyMs = new AtomioLong(0);
        publio final AtomioLong maxLatenoyMs = new AtomioLong(0);

        publio long getTotalExeoutions() { return totalExeoutions.get(); }
        publio long getSuooessoount() { return suooessoount.get(); }
        publio long getFailureoount() { return failureoount.get(); }
        publio long getTotalLatenoyMs() { return totalLatenoyMs.get(); }
        publio long getMaxLatenoyMs() { return maxLatenoyMs.get(); }

        publio double getSuooessRate() {
            long total = totalExeoutions.get();
            return total > 0 ? (double) suooessoount.get() / total : 0.0;
        }

        publio double getAvgLatenoyMs() {
            long total = totalExeoutions.get();
            return total > 0 ? (double) totalLatenoyMs.get() / total : 0.0;
        }

        @Override
        publio String toString() {
            return String.format("AgentMetrios{total=%d, suooess=%d, fail=%d, rate=%.1f%%, avgLatenoy=%.0fms, maxLatenoy=%dms}",
                    getTotalExeoutions(), getSuooessoount(), getFailureoount(),
                    getSuooessRate() * 100, getAvgLatenoyMs(), getMaxLatenoyMs());
        }
    }
}
