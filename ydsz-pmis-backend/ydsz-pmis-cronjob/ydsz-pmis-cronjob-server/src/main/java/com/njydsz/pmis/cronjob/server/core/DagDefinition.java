package com.njydsz.pmis.cronjob.server.core.dag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * DAG 定义模型（P2 DAG 增强）。
 *
 * <p>对应 {@code JobDagDO.dagDefinition} JSON 字段，包含节点列表和边列表。
 * 由 {@link DagDefinitionCodec} 负责序列化/反序列化。
 *
 * <p>JSON 格式示例：
 * <pre>{@code
 * {
 *   "nodes": [
 *     {"jobKey":"a","jobId":"1","label":"抽取","x":100,"y":200,"paramsJson":"{}"},
 *     {"jobKey":"b","jobId":"2","label":"清洗","x":300,"y":200},
 *     {"jobKey":"c","jobId":null,"label":"条件","nodeType":"CONDITION","conditionExpression":"${a.result=='success'}"},
 *     {"jobKey":"d","jobId":null,"label":"循环","nodeType":"LOOP","loopCount":3},
 *     {"jobKey":"e","jobId":null,"label":"并行","nodeType":"PARALLEL_GATEWAY","parallelBranches":2}
 *   ],
 *   "edges": [
 *     {"from":"a","to":"b","failStrategy":"FAIL_FAST","condition":null}
 *   ]
 * }
 * }</pre>
 *
 * @param nodes 节点列表（不可为空）
 * @param edges 边列表（可为空，表示单节点 DAG）
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public record DagDefinition(List<DagNode> nodes, List<DagEdge> edges) {

    /**
     * 紧凑构造器：防御性拷贝 + null 处理。
     */
    public DagDefinition {
        Objects.requireNonNull(nodes, "nodes 不能为空");
        nodes = new ArrayList<>(nodes);
        edges = edges == null ? Collections.emptyList() : new ArrayList<>(edges);
    }

    /**
     * 工厂方法：创建空 DAG 定义（仅用于反序列化）。
     */
    public static DagDefinition empty() {
        return new DagDefinition(Collections.emptyList(), Collections.emptyList());
    }

    /**
     * 工厂方法：创建 DAG 定义。
     */
    public static DagDefinition of(List<DagNode> nodes, List<DagEdge> edges) {
        return new DagDefinition(nodes, edges);
    }

    /**
     * 根据 jobKey 查找节点。
     */
    public DagNode findNode(String jobKey) {
        if (jobKey == null) {
            return null;
        }
        return nodes.stream()
                .filter(n -> jobKey.equals(n.jobKey()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取指定节点的所有出边（from = jobKey）。
     */
    public List<DagEdge> outgoingEdges(String jobKey) {
        if (jobKey == null) {
            return Collections.emptyList();
        }
        return edges.stream()
                .filter(e -> jobKey.equals(e.from()))
                .toList();
    }

    /**
     * 获取指定节点的所有入边（to = jobKey）。
     */
    public List<DagEdge> incomingEdges(String jobKey) {
        if (jobKey == null) {
            return Collections.emptyList();
        }
        return edges.stream()
                .filter(e -> jobKey.equals(e.to()))
                .toList();
    }

    /**
     * 获取所有起始节点（无入边的节点）。
     */
    public List<DagNode> rootNodes() {
        return nodes.stream()
                .filter(n -> incomingEdges(n.jobKey()).isEmpty())
                .toList();
    }

    /**
     * 节点数量。
     */
    public int nodeCount() {
        return nodes.size();
    }
}
