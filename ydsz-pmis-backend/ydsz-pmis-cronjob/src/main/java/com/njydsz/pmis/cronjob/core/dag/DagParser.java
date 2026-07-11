package com.njydsz.pmis.cronjob.core.dag;

import com.njydsz.pmis.common.dag.DagGraph;
import com.njydsz.pmis.cronjob.entity.job.JobRelationDO;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DAG 解析器（P0-1 架构优化：委托到 common.DagGraph）。
 *
 * <p>本类保留 cronjob 模块特有的 {@link JobRelationDO} 适配逻辑，
 * 纯拓扑算法委托到 {@link DagGraph} 统一实现。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>{@link #buildAdjacencyList(List)}：从 JobRelationDO 构建邻接表</li>
 *   <li>{@link #topologicalSort(Map)}：委托 {@link DagGraph#topologicalSort}</li>
 *   <li>{@link #hasCycle(Map)}：委托 {@link DagGraph#hasCycle}</li>
 *   <li>{@link #wouldCreateCycle(String, String, List)}：委托 {@link DagGraph#wouldCreateCycle}</li>
 *   <li>{@link #getDescendants(String, Map)}：委托 {@link DagGraph#getDescendants}</li>
 *   <li>{@link #getAncestors(String, Map)}：委托 {@link DagGraph#getAncestors}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Component
public class DagParser {

    /**
     * 从依赖边列表构建邻接表（parent → children list）。
     *
     * @param edges 依赖边列表
     * @return 邻接表；空列表返回空 Map
     */
    public Map<String, List<String>> buildAdjacencyList(List<JobRelationDO> edges) {
        if (edges == null || edges.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> adj = new HashMap<>();
        for (JobRelationDO edge : edges) {
            adj.computeIfAbsent(edge.getParentJobId(), k -> new java.util.ArrayList<>())
                    .add(edge.getChildJobId());
            adj.computeIfAbsent(edge.getChildJobId(), k -> new java.util.ArrayList<>());
        }
        return adj;
    }

    /**
     * 拓扑排序（委托 DagGraph）。
     */
    public List<String> topologicalSort(Map<String, List<String>> adj) {
        return DagGraph.topologicalSort(adj);
    }

    /**
     * 检测环（委托 DagGraph）。
     */
    public boolean hasCycle(Map<String, List<String>> adj) {
        return DagGraph.hasCycle(adj);
    }

    /**
     * 检测新增边是否形成环（委托 DagGraph）。
     */
    public boolean wouldCreateCycle(String parent, String child, List<JobRelationDO> existingEdges) {
        if (parent == null || child == null) {
            return false;
        }
        if (parent.equals(child)) {
            return true;
        }
        Map<String, List<String>> adj = buildAdjacencyList(existingEdges);
        return DagGraph.wouldCreateCycle(parent, child, adj);
    }

    /**
     * 获取所有后代节点（委托 DagGraph）。
     */
    public Set<String> getDescendants(String start, Map<String, List<String>> adj) {
        return DagGraph.getDescendants(start, adj);
    }

    /**
     * 获取所有祖先节点（委托 DagGraph）。
     */
    public Set<String> getAncestors(String target, Map<String, List<String>> adj) {
        return DagGraph.getAncestors(target, adj);
    }
}
