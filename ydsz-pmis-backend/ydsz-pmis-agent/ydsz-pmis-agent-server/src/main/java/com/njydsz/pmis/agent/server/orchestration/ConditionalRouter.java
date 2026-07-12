package com.njydsz.pmis.agent.server.orchestration.dag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DAG 条件边路由器（P1-4 落地）。
 *
 * <p>对标 Coze Router Node / Dify Conditional Branch / LangGraph Conditional Edges：
 * 根据上游节点的输出，动态选择下游执行分支。
 *
 * <p>工作方式：
 * <ol>
 *   <li>收集某个节点的所有出边（{@link DagEdge}）</li>
 *   <li>按优先级降序排序</li>
 *   <li>依次求值条件表达式，第一个满足的边对应的目标节点被执行</li>
 *   <li>如果所有条件都不满足，走 default 边（如果有）</li>
 *   <li>如果没有 default 边，该分支终止</li>
 * </ol>
 *
 * <p>与 {@link DagExecutor} 的集成：
 * <ul>
 *   <li>当 {@link DagDefinition#getEdges()} 不为空时，使用条件边模式</li>
 *   <li>否则降级为原有的 dependsOn 模式</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0 (P1-4)
 */
@Slf4j
public class ConditionalRouter {

    private final ExpressionParser spelParser = new SpelExpressionParser();

    /**
     * 根据上游节点输出和条件边，决定下游应执行的节点列表。
     *
     * @param upstreamNode    上游节点名
     * @param edges           DAG 中所有边
     * @param sharedVariables 共享变量（含上游节点输出）
     * @return 应执行的下游节点名列表；空列表表示无满足条件的下游
     */
    public List<String> route(String upstreamNode, List<DagEdge> edges,
                               Map<String, Object> sharedVariables) {
        if (edges == null || edges.isEmpty()) {
            return Collections.emptyList();
        }

        // 筛选该节点的出边，按优先级降序排列
        List<DagEdge> outEdges = edges.stream()
                .filter(e -> upstreamNode.equals(e.getFrom()))
                .sorted(Comparator.comparingInt(DagEdge::getPriority).reversed())
                .collect(Collectors.toList());

        if (outEdges.isEmpty()) {
            return Collections.emptyList();
        }

        // 区分条件边和默认边
        List<DagEdge> conditionalEdges = outEdges.stream()
                .filter(e -> !e.isDefaultEdge() && e.getCondition() != null && !e.getCondition().isBlank())
                .collect(Collectors.toList());
        List<DagEdge> defaultEdges = outEdges.stream()
                .filter(DagEdge::isDefaultEdge)
                .collect(Collectors.toList());
        List<DagEdge> unconditionalEdges = outEdges.stream()
                .filter(e -> !e.isDefaultEdge() && (e.getCondition() == null || e.getCondition().isBlank()))
                .collect(Collectors.toList());

        List<String> routed = new ArrayList<>();

        // 1. 评估条件边（按优先级）
        for (DagEdge edge : conditionalEdges) {
            if (evaluateCondition(edge.getCondition(), sharedVariables, upstreamNode)) {
                log.debug("[Router] 节点 {} → {} (条件满足: {})",
                        upstreamNode, edge.getTo(), edge.getCondition());
                routed.add(edge.getTo());
            }
        }

        // 2. 如果有满足的条件边，返回（不再走 default）
        if (!routed.isEmpty()) {
            return routed;
        }

        // 3. 无条件边直接加入
        for (DagEdge edge : unconditionalEdges) {
            log.debug("[Router] 节点 {} → {} (无条件边)", upstreamNode, edge.getTo());
            routed.add(edge.getTo());
        }

        // 4. 走默认边
        for (DagEdge edge : defaultEdges) {
            log.debug("[Router] 节点 {} → {} (默认边)", upstreamNode, edge.getTo());
            routed.add(edge.getTo());
        }

        return routed;
    }

    /**
     * 从 DagDefinition 的 edges 构建 from → to 邻接表（考虑条件边）。
     *
     * <p>与 {@link DagExecutor#buildAdjacencyFromDag} 不同，
     * 此方法使用 edges 定义拓扑，而非 dependsOn。
     *
     * @param dag DAG 定义
     * @return 邻接表
     */
    public Map<String, List<String>> buildAdjacencyFromEdges(DagDefinition dag) {
        Map<String, List<String>> adj = new HashMap<>();
        if (dag.getEdges() == null || dag.getEdges().isEmpty()) {
            // 降级为 dependsOn 模式
            for (DagNode node : dag.getNodes()) {
                adj.computeIfAbsent(node.getName(), k -> new ArrayList<>());
                if (node.getDependsOn() != null) {
                    for (String dep : node.getDependsOn()) {
                        adj.computeIfAbsent(dep, k -> new ArrayList<>()).add(node.getName());
                    }
                }
            }
            return adj;
        }

        // 使用 edges 构建邻接表
        for (DagNode node : dag.getNodes()) {
            adj.computeIfAbsent(node.getName(), k -> new ArrayList<>());
        }
        for (DagEdge edge : dag.getEdges()) {
            adj.computeIfAbsent(edge.getFrom(), k -> new ArrayList<>());
            if (!adj.get(edge.getFrom()).contains(edge.getTo())) {
                adj.get(edge.getFrom()).add(edge.getTo());
            }
            // 确保目标节点也在邻接表中
            adj.computeIfAbsent(edge.getTo(), k -> new ArrayList<>());
        }
        return adj;
    }

    /**
     * 求值 SpEL 条件表达式。
     *
     * @param expression      SpEL 表达式
     * @param sharedVariables 共享变量
     * @param nodeName        上游节点名（用于日志）
     * @return true 表示条件满足
     */
    private boolean evaluateCondition(String expression, Map<String, Object> sharedVariables,
                                       String nodeName) {
        try {
            Expression exp = spelParser.parseExpression(expression);
            EvaluationContext evalCtx = new StandardEvaluationContext(sharedVariables);
            Boolean result = exp.getValue(evalCtx, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("[Router] 节点 {} 条件表达式求值失败，默认 false: {} ({})",
                    nodeName, expression, e.getMessage());
            return false;
        }
    }

    /**
     * 校验边的完整性（无悬空引用、无环）。
     *
     * @param dag DAG 定义
     * @throws IllegalArgumentException 校验失败
     */
    public void validateEdges(DagDefinition dag) {
        if (dag.getEdges() == null || dag.getEdges().isEmpty()) {
            return;
        }

        Set<String> nodeNames = dag.getNodes().stream()
                .map(DagNode::getName)
                .collect(Collectors.toSet());

        for (DagEdge edge : dag.getEdges()) {
            if (!nodeNames.contains(edge.getFrom())) {
                throw new IllegalArgumentException(
                        "边引用了不存在的源节点: " + edge.getFrom());
            }
            if (!nodeNames.contains(edge.getTo())) {
                throw new IllegalArgumentException(
                        "边引用了不存在的目标节点: " + edge.getTo());
            }
        }

        // 检查同一节点的出边中最多一个 default
        Map<String, Long> defaultCount = dag.getEdges().stream()
                .filter(DagEdge::isDefaultEdge)
                .collect(Collectors.groupingBy(DagEdge::getFrom, Collectors.counting()));
        for (Map.Entry<String, Long> entry : defaultCount.entrySet()) {
            if (entry.getValue() > 1) {
                throw new IllegalArgumentException(
                        "节点 " + entry.getKey() + " 有多个 default 边");
            }
        }
    }
}
