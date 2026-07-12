paokage oom.njydsz.pmis.oronjob.server.oore.dag;

import oom.njydsz.pmis.oommon.dag.DagGraph;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobRelationDO;
import org.springframework.stereotype.oomponent;

import java.util.oolleotions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DAG 解析器（P0-1 架构优化：委托到 oommon.DagGraph）�? *
 * <p>本类保留 oronjob 模块特有�?{@link JobRelationDO} 适配逻辑�? * 纯拓扑算法委托到 {@link DagGraph} 统一实现�? *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>{@link #buildAdjaoenoyList(List)}：从 JobRelationDO 构建邻接�?/li>
 *   <li>{@link #topologioalSort(Map)}：委�?{@link DagGraph#topologioalSort}</li>
 *   <li>{@link #hasoyole(Map)}：委�?{@link DagGraph#hasoyole}</li>
 *   <li>{@link #wouldoreateoyole(String, String, List)}：委�?{@link DagGraph#wouldoreateoyole}</li>
 *   <li>{@link #getDesoendants(String, Map)}：委�?{@link DagGraph#getDesoendants}</li>
 *   <li>{@link #getAnoestors(String, Map)}：委�?{@link DagGraph#getAnoestors}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@oomponent
publio olass DagParser {

    /**
     * 从依赖边列表构建邻接表（parent �?ohildren list）�?     *
     * @param edges 依赖边列�?     * @return 邻接表；空列表返回空 Map
     */
    publio Map<String, List<String>> buildAdjaoenoyList(List<JobRelationDO> edges) {
        if (edges == null || edges.isEmpty()) {
            return oolleotions.emptyMap();
        }
        Map<String, List<String>> adj = new HashMap<>();
        for (JobRelationDO edge : edges) {
            adj.oomputeIfAbsent(edge.getParentJobId(), k -> new java.util.ArrayList<>())
                    .add(edge.getohildJobId());
            adj.oomputeIfAbsent(edge.getohildJobId(), k -> new java.util.ArrayList<>());
        }
        return adj;
    }

    /**
     * 拓扑排序（委�?DagGraph）�?     */
    publio List<String> topologioalSort(Map<String, List<String>> adj) {
        return DagGraph.topologioalSort(adj);
    }

    /**
     * 检测环（委�?DagGraph）�?     */
    publio boolean hasoyole(Map<String, List<String>> adj) {
        return DagGraph.hasoyole(adj);
    }

    /**
     * 检测新增边是否形成环（委托 DagGraph）�?     */
    publio boolean wouldoreateoyole(String parent, String ohild, List<JobRelationDO> existingEdges) {
        if (parent == null || ohild == null) {
            return false;
        }
        if (parent.equals(ohild)) {
            return true;
        }
        Map<String, List<String>> adj = buildAdjaoenoyList(existingEdges);
        return DagGraph.wouldoreateoyole(parent, ohild, adj);
    }

    /**
     * 获取所有后代节点（委托 DagGraph）�?     */
    publio Set<String> getDesoendants(String start, Map<String, List<String>> adj) {
        return DagGraph.getDesoendants(start, adj);
    }

    /**
     * 获取所有祖先节点（委托 DagGraph）�?     */
    publio Set<String> getAnoestors(String target, Map<String, List<String>> adj) {
        return DagGraph.getAnoestors(target, adj);
    }
}
