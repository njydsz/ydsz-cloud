package com.njydsz.pmis.literule.orchestrator;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 规则链画布图验证器（P0-1）
 *
 * <p>对可视化编排画布进行结构合法性检查，校验项：
 * <ul>
 *   <li>自环：禁止边的 source 与 target 指向同一节点</li>
 *   <li>重复边：禁止 source-target-edgeType 三个字段都相同的边重复出现</li>
 *   <li>未连接节点：禁止除根节点外的孤立节点（无边相连）</li>
 *   <li>悬空引用：边的 source/target 必须指向已存在的节点</li>
 *   <li>根节点：必须有且仅有一个根节点（CHAIN 类型 + parentNodeId 为空）</li>
 *   <li>SINGLE 节点必须设置 ruleCode</li>
 * </ul>
 *
 * <p>典型用法：
 * <pre>
 *   List&lt;GraphValidationIssue&gt; issues = RuleGraphValidator.validate(graph);
 *   if (!issues.isEmpty()) {
 *       // 提示用户修复后再保存
 *   }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public final class RuleGraphValidator {

    private RuleGraphValidator() {}

    /**
     * 验证画布图，返回所有问题
     *
     * @param graph 画布图
     * @return 问题列表；为空表示无问题
     */
    public static List<GraphValidationIssue> validate(RuleChainGraph graph) {
        List<GraphValidationIssue> issues = new ArrayList<>();
        if (graph == null) {
            issues.add(GraphValidationIssue.error("GRAPH_NULL", "画布为空"));
            return issues;
        }
        List<ChainNodeDTO> nodes = graph.getNodes() == null ? List.of() : graph.getNodes();
        List<ChainEdgeDTO> edges = graph.getEdges() == null ? List.of() : graph.getEdges();

        // 1. 根节点校验
        int rootCount = 0;
        for (ChainNodeDTO n : nodes) {
            if ("CHAIN".equals(n.getNodeType()) && n.getParentNodeId() == null) {
                rootCount++;
            }
        }
        if (rootCount == 0) {
            issues.add(GraphValidationIssue.error("MISSING_ROOT",
                    "画布缺少根节点（CHAIN 类型且 parentNodeId 为空）"));
        } else if (rootCount > 1) {
            issues.add(GraphValidationIssue.error("MULTIPLE_ROOTS",
                    "画布存在 " + rootCount + " 个根节点，应仅有 1 个"));
        }

        // 2. SINGLE 节点必须设置 ruleCode
        for (ChainNodeDTO n : nodes) {
            if ("SINGLE".equals(n.getNodeType())
                    && (n.getRuleCode() == null || n.getRuleCode().isBlank())) {
                issues.add(GraphValidationIssue.error("MISSING_RULE_CODE",
                        "节点 " + n.getNodeId() + " 缺少 ruleCode"));
            }
        }

        // 3. 节点 ID 唯一性
        Set<String> nodeIds = new HashSet<>();
        for (ChainNodeDTO n : nodes) {
            if (n.getNodeId() == null || n.getNodeId().isBlank()) {
                issues.add(GraphValidationIssue.error("MISSING_NODE_ID", "存在未设置 nodeId 的节点"));
                continue;
            }
            if (!nodeIds.add(n.getNodeId())) {
                issues.add(GraphValidationIssue.error("DUPLICATE_NODE_ID",
                        "节点 ID 重复: " + n.getNodeId()));
            }
        }

        // 4. 边校验：自环 / 悬空 / 重复
        Set<String> edgeFingerprints = new HashSet<>();
        Set<String> referencedNodes = new HashSet<>();
        for (ChainEdgeDTO e : edges) {
            if (e.getSourceNodeId() == null || e.getTargetNodeId() == null) {
                issues.add(GraphValidationIssue.error("EDGE_NULL_ENDPOINT",
                        "边的 source/target 不能为空"));
                continue;
            }
            // 自环
            if (e.getSourceNodeId().equals(e.getTargetNodeId())) {
                issues.add(GraphValidationIssue.error("SELF_LOOP",
                        "节点 " + e.getSourceNodeId() + " 存在自环边"));
            }
            // 悬空
            if (!nodeIds.contains(e.getSourceNodeId())) {
                issues.add(GraphValidationIssue.error("DANGLING_SOURCE",
                        "边 " + e.getEdgeId() + " 的 source 节点不存在: " + e.getSourceNodeId()));
            }
            if (!nodeIds.contains(e.getTargetNodeId())) {
                issues.add(GraphValidationIssue.error("DANGLING_TARGET",
                        "边 " + e.getEdgeId() + " 的 target 节点不存在: " + e.getTargetNodeId()));
            }
            // 重复边：source + target + edgeType 三者相同
            String fp = e.getSourceNodeId() + "->" + e.getTargetNodeId()
                    + ":" + (e.getEdgeType() == null ? "" : e.getEdgeType());
            if (!edgeFingerprints.add(fp)) {
                issues.add(GraphValidationIssue.warn("DUPLICATE_EDGE",
                        "重复边: " + fp));
            }
            referencedNodes.add(e.getSourceNodeId());
            referencedNodes.add(e.getTargetNodeId());
        }

        // 5. 未连接节点校验：除根节点外，孤立节点告警
        for (ChainNodeDTO n : nodes) {
            if (n.getNodeId() == null) continue;
            if (n.getParentNodeId() == null) continue; // 根节点跳过
            if (!referencedNodes.contains(n.getNodeId())) {
                issues.add(GraphValidationIssue.warn("ORPHAN_NODE",
                        "孤立节点 " + n.getNodeId() + " 未被任何边连接"));
            }
        }
        return issues;
    }

    /**
     * 画布问题严重度
     */
    public enum Level { ERROR, WARN }

    /**
     * 画布验证问题
     */
    public static final class GraphValidationIssue implements Serializable {
        private static final long serialVersionUID = 1L;

        private final Level level;
        private final String code;
        private final String message;

        private GraphValidationIssue(Level level, String code, String message) {
            this.level = level;
            this.code = code;
            this.message = message;
        }

        public static GraphValidationIssue error(String code, String message) {
            return new GraphValidationIssue(Level.ERROR, code, message);
        }

        public static GraphValidationIssue warn(String code, String message) {
            return new GraphValidationIssue(Level.WARN, code, message);
        }

        public Level getLevel() { return level; }
        public String getCode() { return code; }
        public String getMessage() { return message; }

        @Override
        public String toString() {
            return level + " [" + code + "] " + message;
        }
    }

    /**
     * 是否通过（无 ERROR 级别问题）
     *
     * @param issues 问题列表
     * @return true=通过
     */
    public static boolean isValid(List<GraphValidationIssue> issues) {
        if (issues == null) return true;
        for (GraphValidationIssue i : issues) {
            if (i.getLevel() == Level.ERROR) {
                return false;
            }
        }
        return true;
    }

    /**
     * 按 level 汇总（用于前端展示）
     *
     * @param issues 问题列表
     * @return 不可修改的分类集合
     */
    public static Set<GraphValidationIssue> filterByLevel(List<GraphValidationIssue> issues, Level level) {
        Set<GraphValidationIssue> result = new LinkedHashSet<>();
        if (issues == null) return result;
        for (GraphValidationIssue i : issues) {
            if (i.getLevel() == level) {
                result.add(i);
            }
        }
        return result;
    }
}
