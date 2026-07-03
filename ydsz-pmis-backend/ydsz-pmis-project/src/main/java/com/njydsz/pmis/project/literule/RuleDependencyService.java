package com.njydsz.pmis.project.literule;

import com.njydsz.pmis.project.entity.RuleDependencyDO;
import com.njydsz.pmis.project.mapper.RuleDependencyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则依赖关系 Service（P1-8）
 *
 * <p>提供规则依赖的 CRUD、循环依赖检测、级联禁用影响范围计算等能力。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleDependencyService {

    private final RuleDependencyMapper ruleDependencyMapper;

    /** 循环依赖检测缓存：fromCode → Set(toCodes) */
    private final Map<String, Set<String>> cycleDetectCache = new ConcurrentHashMap<>();

    /**
     * 新增依赖
     *
     * <p>若已存在相同的 (ruleCode, dependsOnRuleCode, dependencyType) 三元组则直接返回已有记录。
     *
     * @return 保存后的依赖记录
     */
    @Transactional(rollbackFor = Exception.class)
    public RuleDependencyDO add(String ruleCode, String dependsOnRuleCode, String dependencyType,
                                boolean cascadeOnDisable, String description, String operator) {
        if (ruleCode == null || ruleCode.isBlank()) {
            throw new IllegalArgumentException("ruleCode 不能为空");
        }
        if (dependsOnRuleCode == null || dependsOnRuleCode.isBlank()) {
            throw new IllegalArgumentException("dependsOnRuleCode 不能为空");
        }
        if (ruleCode.equals(dependsOnRuleCode)) {
            throw new IllegalArgumentException("规则不能依赖自身: " + ruleCode);
        }
        String depType = (dependencyType == null || dependencyType.isBlank()) ? "EXECUTE" : dependencyType;

        // 重复检查
        List<RuleDependencyDO> existing = ruleDependencyMapper.selectByRuleCode(ruleCode);
        for (RuleDependencyDO d : existing) {
            if (dependsOnRuleCode.equals(d.getDependsOnRuleCode()) && depType.equals(d.getDependencyType())) {
                log.info("[RuleDependency] 依赖已存在，直接返回: {} -> {}", ruleCode, dependsOnRuleCode);
                return d;
            }
        }

        // 循环检测：先添加这条，再做 BFS 检测是否形成环
        RuleDependencyDO entity = new RuleDependencyDO();
        entity.setRuleCode(ruleCode);
        entity.setDependsOnRuleCode(dependsOnRuleCode);
        entity.setDependencyType(depType);
        entity.setCascadeOnDisable(cascadeOnDisable);
        entity.setDescription(description);
        entity.setTenantId(1L);
        entity.setCreatedBy(operator == null ? "SYSTEM" : operator);
        entity.setCreatedAt(LocalDateTime.now());
        ruleDependencyMapper.insert(entity);

        // 重新构建邻接表并检测环
        invalidateCache();
        List<String> cycle = detectCycle(ruleCode);
        if (!cycle.isEmpty()) {
            // 回滚此次新增
            ruleDependencyMapper.deleteById(entity.getId());
            throw new IllegalStateException("检测到循环依赖: " + String.join(" -> ", cycle));
        }

        log.info("[RuleDependency] 新增依赖: {} -> {}, type={}, cascade={}",
                ruleCode, dependsOnRuleCode, depType, cascadeOnDisable);
        return entity;
    }

    /**
     * 删除一条依赖
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(String ruleCode, String dependsOnRuleCode) {
        if (ruleCode == null || dependsOnRuleCode == null) return;
        List<RuleDependencyDO> deps = ruleDependencyMapper.selectByRuleCode(ruleCode);
        for (RuleDependencyDO d : deps) {
            if (dependsOnRuleCode.equals(d.getDependsOnRuleCode())) {
                ruleDependencyMapper.deleteById(d.getId());
                log.info("[RuleDependency] 删除依赖: {} -> {}", ruleCode, dependsOnRuleCode);
                invalidateCache();
                return;
            }
        }
    }

    /**
     * 查询规则的依赖（正向：依赖了哪些）
     */
    public List<RuleDependencyDO> listDependencies(String ruleCode) {
        if (ruleCode == null) return Collections.emptyList();
        return ruleDependencyMapper.selectByRuleCode(ruleCode);
    }

    /**
     * 查询被依赖（反向：被哪些规则依赖）
     */
    public List<RuleDependencyDO> listDependents(String ruleCode) {
        if (ruleCode == null) return Collections.emptyList();
        return ruleDependencyMapper.selectByDependsOn(ruleCode);
    }

    /**
     * 计算禁用某条规则时，需要级联禁用的规则列表
     *
     * <p>采用 BFS 沿着反向依赖图传播：X depends on ruleCode 且 cascadeOnDisable=true，则 X 需要级联禁用；
     * 然后继续以 X 为新的禁用点向下传播。
     */
    public List<String> cascadingDisable(String ruleCode) {
        if (ruleCode == null || ruleCode.isBlank()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new java.util.LinkedList<>();
        queue.offer(ruleCode);
        visited.add(ruleCode);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            List<RuleDependencyDO> cascading = ruleDependencyMapper.selectCascadingByDependsOn(current);
            for (RuleDependencyDO d : cascading) {
                String dependent = d.getRuleCode();
                if (visited.add(dependent)) {
                    result.add(dependent);
                    queue.offer(dependent);
                }
            }
        }
        return result;
    }

    /**
     * 检测从 ruleCode 出发是否存在循环依赖
     *
     * @return 若存在循环，返回循环路径；否则返回空列表
     */
    public List<String> detectCycle(String ruleCode) {
        Map<String, Set<String>> adj = buildAdjacencyMap();
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        List<String> path = new ArrayList<>();
        if (hasCycleFrom(ruleCode, adj, visiting, visited, path)) {
            return path;
        }
        return Collections.emptyList();
    }

    private boolean hasCycleFrom(String node, Map<String, Set<String>> adj,
                                 Set<String> visiting, Set<String> visited, List<String> path) {
        if (visiting.contains(node)) {
            int idx = path.indexOf(node);
            if (idx >= 0) {
                List<String> cycle = new ArrayList<>(path.subList(idx, path.size()));
                cycle.add(node);
                path.clear();
                path.addAll(cycle);
            }
            return true;
        }
        if (visited.contains(node)) return false;
        visiting.add(node);
        path.add(node);
        Set<String> neighbors = adj.getOrDefault(node, Collections.emptySet());
        for (String n : neighbors) {
            if (hasCycleFrom(n, adj, visiting, visited, path)) return true;
        }
        visiting.remove(node);
        visited.add(node);
        if (!path.isEmpty()) path.remove(path.size() - 1);
        return false;
    }

    private Map<String, Set<String>> buildAdjacencyMap() {
        List<RuleDependencyDO> all = ruleDependencyMapper.selectList(null);
        Map<String, Set<String>> adj = new HashMap<>();
        for (RuleDependencyDO d : all) {
            adj.computeIfAbsent(d.getRuleCode(), k -> new LinkedHashSet<>())
                    .add(d.getDependsOnRuleCode());
        }
        return adj;
    }

    private void invalidateCache() {
        cycleDetectCache.clear();
    }
}
