package com.njydsz.pmis.cronjob.core.dag;

import com.njydsz.pmis.cronjob.entity.JobRelationDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DagParser} 单元测试（P4-2 DAG 工作流）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DagParser DAG 解析器测试")
class DagParserTest {

    private final DagParser parser = new DagParser();

    // ==================== buildAdjacencyList ====================

    @Test
    @DisplayName("buildAdjacencyList: 空列表返回空 Map")
    void buildAdjacencyList_empty_returnsEmptyMap() {
        Map<String, List<String>> adj = parser.buildAdjacencyList(Collections.emptyList());
        assertTrue(adj.isEmpty());
    }

    @Test
    @DisplayName("buildAdjacencyList: 正常构建邻接表")
    void buildAdjacencyList_normal() {
        List<JobRelationDO> edges = Arrays.asList(
                buildRelation("A", "B"),
                buildRelation("A", "C"),
                buildRelation("B", "D"));
        Map<String, List<String>> adj = parser.buildAdjacencyList(edges);
        assertEquals(Arrays.asList("B", "C"), adj.get("A"));
        assertEquals(List.of("D"), adj.get("B"));
        assertTrue(adj.containsKey("C"));
        assertTrue(adj.containsKey("D"));
    }

    // ==================== topologicalSort ====================

    @Test
    @DisplayName("topologicalSort: 无环图正常排序")
    void topologicalSort_noCycle_returnsOrdered() {
        List<JobRelationDO> edges = Arrays.asList(
                buildRelation("A", "B"),
                buildRelation("A", "C"),
                buildRelation("B", "D"),
                buildRelation("C", "D"));
        Map<String, List<String>> adj = parser.buildAdjacencyList(edges);
        List<String> sorted = parser.topologicalSort(adj);
        // A 必须在 B, C 之前；B, C 必须在 D 之前
        assertTrue(sorted.indexOf("A") < sorted.indexOf("B"));
        assertTrue(sorted.indexOf("A") < sorted.indexOf("C"));
        assertTrue(sorted.indexOf("B") < sorted.indexOf("D"));
        assertTrue(sorted.indexOf("C") < sorted.indexOf("D"));
    }

    @Test
    @DisplayName("topologicalSort: 有环图排序结果不含环节点")
    void topologicalSort_hasCycle_excludesCycleNodes() {
        List<JobRelationDO> edges = Arrays.asList(
                buildRelation("A", "B"),
                buildRelation("B", "C"),
                buildRelation("C", "A")); // A→B→C→A 环
        Map<String, List<String>> adj = parser.buildAdjacencyList(edges);
        List<String> sorted = parser.topologicalSort(adj);
        // 环中的节点不会被输出（入度永远 > 0）
        assertTrue(sorted.size() < 3);
    }

    @Test
    @DisplayName("topologicalSort: 空图返回空列表")
    void topologicalSort_empty_returnsEmptyList() {
        List<String> sorted = parser.topologicalSort(Collections.emptyMap());
        assertTrue(sorted.isEmpty());
    }

    // ==================== hasCycle ====================

    @Test
    @DisplayName("hasCycle: 无环图返回 false")
    void hasCycle_noCycle_returnsFalse() {
        List<JobRelationDO> edges = Arrays.asList(
                buildRelation("A", "B"),
                buildRelation("B", "C"));
        Map<String, List<String>> adj = parser.buildAdjacencyList(edges);
        assertFalse(parser.hasCycle(adj));
    }

    @Test
    @DisplayName("hasCycle: 有环图返回 true")
    void hasCycle_cycle_returnsTrue() {
        List<JobRelationDO> edges = Arrays.asList(
                buildRelation("A", "B"),
                buildRelation("B", "C"),
                buildRelation("C", "A"));
        Map<String, List<String>> adj = parser.buildAdjacencyList(edges);
        assertTrue(parser.hasCycle(adj));
    }

    @Test
    @DisplayName("hasCycle: 自环返回 true")
    void hasCycle_selfLoop_returnsTrue() {
        List<JobRelationDO> edges = List.of(buildRelation("A", "A"));
        Map<String, List<String>> adj = parser.buildAdjacencyList(edges);
        assertTrue(parser.hasCycle(adj));
    }

    @Test
    @DisplayName("hasCycle: 空图返回 false")
    void hasCycle_empty_returnsFalse() {
        assertFalse(parser.hasCycle(Collections.emptyMap()));
    }

    // ==================== wouldCreateCycle ====================

    @Test
    @DisplayName("wouldCreateCycle: 自依赖返回 true")
    void wouldCreateCycle_selfRef_returnsTrue() {
        assertTrue(parser.wouldCreateCycle("A", "A", Collections.emptyList()));
    }

    @Test
    @DisplayName("wouldCreateCycle: 正常添加不形成环返回 false")
    void wouldCreateCycle_normal_returnsFalse() {
        List<JobRelationDO> edges = Arrays.asList(
                buildRelation("A", "B"),
                buildRelation("B", "C"));
        // A→C 不形成环（A 已是 C 的祖先）
        assertFalse(parser.wouldCreateCycle("A", "C", edges));
    }

    @Test
    @DisplayName("wouldCreateCycle: 形成环返回 true")
    void wouldCreateCycle_createsCycle_returnsTrue() {
        List<JobRelationDO> edges = Arrays.asList(
                buildRelation("A", "B"),
                buildRelation("B", "C"));
        // C→A 形成环 A→B→C→A
        assertTrue(parser.wouldCreateCycle("C", "A", edges));
    }

    @Test
    @DisplayName("wouldCreateCycle: 空图添加任何边返回 false")
    void wouldCreateCycle_emptyGraph_returnsFalse() {
        assertFalse(parser.wouldCreateCycle("A", "B", Collections.emptyList()));
    }

    // ==================== getDescendants ====================

    @Test
    @DisplayName("getDescendants: 正常获取后代")
    void getDescendants_normal() {
        Map<String, List<String>> adj = Map.of(
                "A", Arrays.asList("B", "C"),
                "B", List.of("D"),
                "C", Collections.emptyList(),
                "D", Collections.emptyList());
        Set<String> descendants = parser.getDescendants("A", adj);
        assertEquals(3, descendants.size());
        assertTrue(descendants.contains("B"));
        assertTrue(descendants.contains("C"));
        assertTrue(descendants.contains("D"));
    }

    @Test
    @DisplayName("getDescendants: 无后代返回空集合")
    void getDescendants_noChildren_returnsEmpty() {
        Map<String, List<String>> adj = Map.of("A", Collections.emptyList());
        Set<String> descendants = parser.getDescendants("A", adj);
        assertTrue(descendants.isEmpty());
    }

    // ==================== getAncestors ====================

    @Test
    @DisplayName("getAncestors: 正常获取祖先")
    void getAncestors_normal() {
        Map<String, List<String>> adj = new java.util.HashMap<>();
        adj.put("A", Arrays.asList("B", "C"));
        adj.put("B", List.of("D"));
        adj.put("C", Collections.emptyList());
        adj.put("D", Collections.emptyList());
        Set<String> ancestors = parser.getAncestors("D", adj);
        // D 的祖先: B, A（因为 A→B→D）
        assertEquals(2, ancestors.size());
        assertTrue(ancestors.contains("A"));
        assertTrue(ancestors.contains("B"));
    }

    @Test
    @DisplayName("getAncestors: 无祖先返回空集合")
    void getAncestors_noParents_returnsEmpty() {
        Map<String, List<String>> adj = Map.of("A", Collections.emptyList());
        Set<String> ancestors = parser.getAncestors("A", adj);
        assertTrue(ancestors.isEmpty());
    }

    // ==================== 辅助方法 ====================

    private JobRelationDO buildRelation(String parent, String child) {
        JobRelationDO r = new JobRelationDO();
        r.setParentJobId(parent);
        r.setChildJobId(child);
        r.setFailStrategy("FAIL_FAST");
        return r;
    }
}
