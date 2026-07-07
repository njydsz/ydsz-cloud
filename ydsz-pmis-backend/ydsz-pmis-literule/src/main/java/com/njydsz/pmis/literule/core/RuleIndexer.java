package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.Rule;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则索引器（P0-1 轻量 RETE 优化）
 *
 * <p>解决大规则量（>500）场景下的线性扫描性能瓶颈。通过多维度索引将候选规则集缩小：
 * <ul>
 *   <li><b>租户索引</b>：tenantId -> rules，避免遍历其他租户的规则</li>
 *   <li><b>场景索引</b>：tenantId + scope -> rules，跳过不匹配的 scope</li>
 *   <li><b>互斥组索引</b>：tenantId + mutexGroup -> rules（按优先级排序），O(1) 查找</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>索引构建与 {@link DefaultRuleEngine} 的 CopyOnWriteArrayList 并行维护，保持一致</li>
 *   <li>索引数据结构使用 {@link ConcurrentHashMap}，线程安全</li>
 *   <li>索引为可选优化：当规则数 < 阈值（默认 200）时，直接返回全量规则，不使用索引</li>
 *   <li>索引命中后的候选规则集仍保持按优先级排序</li>
 * </ul>
 *
 * <p>性能预期：规则数 1000+ 时，单次评估候选规则数降至 10-100 条，性能提升 10-20x。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@Slf4j
public class RuleIndexer {

    /** 索引启用的最小规则数阈值（低于此值不启用索引） */
    private static final int INDEX_THRESHOLD = 200;

    /** 租户索引：tenantId -> 规则列表（按优先级排序） */
    private final Map<String, List<Rule>> tenantIndex = new ConcurrentHashMap<>();

    /** 场景索引：tenantId + "|" + scope -> 规则列表 */
    private final Map<String, List<Rule>> scopeIndex = new ConcurrentHashMap<>();

    /** 互斥组索引：tenantId + "|" + mutexGroup -> 规则列表（按优先级排序） */
    private final Map<String, List<Rule>> mutexGroupIndex = new ConcurrentHashMap<>();

    /** 全局规则列表（兼容无场景过滤的场景） */
    private volatile List<Rule> allRules = Collections.emptyList();

    /** 是否启用索引 */
    private volatile boolean indexEnabled = false;

    /**
     * 重建索引
     *
     * <p>在规则批量注册/注销后调用，重建全部索引。
     * 单条注册时使用 {@link #addToIndex(Rule)} 增量更新。
     *
     * @param rules 当前全部规则列表（已按优先级排序）
     */
    public synchronized void rebuildIndex(List<Rule> rules) {
        // 清空旧索引
        tenantIndex.clear();
        scopeIndex.clear();
        mutexGroupIndex.clear();

        allRules = new ArrayList<>(rules);
        indexEnabled = rules.size() >= INDEX_THRESHOLD;

        if (!indexEnabled) {
            log.debug("[LiteRule-Indexer] 规则数 {} < 阈值 {}，索引未启用", rules.size(), INDEX_THRESHOLD);
            return;
        }

        // 构建索引
        for (Rule rule : rules) {
            addToIndexInternal(rule);
        }

        log.info("[LiteRule-Indexer] 索引重建完成: totalRules={}, tenants={}, scopes={}, mutexGroups={}",
                rules.size(), tenantIndex.size(), scopeIndex.size(), mutexGroupIndex.size());
    }

    /**
     * 增量添加规则到索引
     *
     * @param rule 新注册的规则
     */
    public synchronized void addToIndex(Rule rule) {
        if (!indexEnabled) return;
        addToIndexInternal(rule);
    }

    /**
     * 从索引中移除规则
     *
     * @param ruleCode 规则编码
     */
    public synchronized void removeFromIndex(String ruleCode) {
        if (!indexEnabled) return;
        // 由于索引按引用存储，需要遍历移除
        tenantIndex.values().forEach(list -> list.removeIf(r -> ruleCode.equals(r.getCode())));
        scopeIndex.values().forEach(list -> list.removeIf(r -> ruleCode.equals(r.getCode())));
        mutexGroupIndex.values().forEach(list -> list.removeIf(r -> ruleCode.equals(r.getCode())));
        allRules.removeIf(r -> ruleCode.equals(r.getCode()));
    }

    /**
     * 查找候选规则集
     *
     * <p>按租户、场景、互斥组过滤，返回按优先级排序的候选规则列表。
     * 调用方仍需做互斥组短路逻辑（因为短路依赖运行时命中状态）。
     *
     * @param tenantId 租户 ID
     * @param scenario 场景（null 或 "DEFAULT" 表示全部）
     * @param triggeredMutexGroups 已命中的互斥组集合（用于排除）
     * @return 候选规则列表（按优先级排序）
     */
    public List<Rule> findCandidates(String tenantId, String scenario, Set<String> triggeredMutexGroups) {
        if (!indexEnabled) {
            return allRules;
        }

        // 1. 租户过滤
        String tenantKey = tenantId != null ? tenantId : "1";
        List<Rule> tenantRules = tenantIndex.get(tenantKey);
        if (tenantRules == null || tenantRules.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 场景过滤
        List<Rule> scopedRules;
        if (scenario == null || "DEFAULT".equals(scenario)) {
            // DEFAULT 场景：评估全部规则
            scopedRules = tenantRules;
        } else {
            // 精确场景：取 scope 匹配 + scope=ALL/null 的规则
            String scopeKey = tenantKey + "|" + scenario;
            List<Rule> exactScope = scopeIndex.get(scopeKey);
            String allScopeKey = tenantKey + "|ALL";
            List<Rule> allScope = scopeIndex.get(allScopeKey);
            String nullScopeKey = tenantKey + "|null";
            List<Rule> nullScope = scopeIndex.get(nullScopeKey);

            scopedRules = new ArrayList<>();
            if (exactScope != null) scopedRules.addAll(exactScope);
            if (allScope != null) scopedRules.addAll(allScope);
            if (nullScope != null) scopedRules.addAll(nullScope);

            // 重新按优先级排序
            scopedRules.sort((r1, r2) -> Integer.compare(r1.getPriority(), r2.getPriority()));
        }

        // 3. 互斥组过滤（排除已命中的互斥组中的规则）
        if (triggeredMutexGroups != null && !triggeredMutexGroups.isEmpty()) {
            List<Rule> filtered = new ArrayList<>(scopedRules.size());
            for (Rule rule : scopedRules) {
                String mutexGroup = rule.getMutexGroup();
                if (mutexGroup == null || mutexGroup.isBlank() || !triggeredMutexGroups.contains(mutexGroup)) {
                    filtered.add(rule);
                }
            }
            return filtered;
        }

        return scopedRules;
    }

    /**
     * 是否启用索引
     *
     * @return true=索引已启用（规则数超过阈值）
     */
    public boolean isIndexEnabled() {
        return indexEnabled;
    }

    /**
     * 获取索引统计信息
     *
     * @return 索引统计字符串
     */
    public String getIndexStats() {
        return String.format("RuleIndexer{enabled=%s, totalRules=%d, tenants=%d, scopes=%d, mutexGroups=%d}",
                indexEnabled, allRules.size(), tenantIndex.size(), scopeIndex.size(), mutexGroupIndex.size());
    }

    /**
     * 内部方法：将规则添加到索引
     */
    private void addToIndexInternal(Rule rule) {
        String tenantId = rule.getTenantId() != null ? rule.getTenantId() : "1";

        // 租户索引
        tenantIndex.computeIfAbsent(tenantId, k -> new ArrayList<>()).add(rule);

        // 场景索引
        String scope = rule.getScope();
        String scopeKey = tenantId + "|" + (scope != null ? scope : "null");
        scopeIndex.computeIfAbsent(scopeKey, k -> new ArrayList<>()).add(rule);

        // 互斥组索引
        String mutexGroup = rule.getMutexGroup();
        if (mutexGroup != null && !mutexGroup.isBlank()) {
            String mutexKey = tenantId + "|" + mutexGroup;
            mutexGroupIndex.computeIfAbsent(mutexKey, k -> new ArrayList<>()).add(rule);
        }
    }
}
