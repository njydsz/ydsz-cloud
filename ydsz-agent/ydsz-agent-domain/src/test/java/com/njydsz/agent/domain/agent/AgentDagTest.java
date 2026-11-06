package com.njydsz.agent.domain.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.agent.domain.agent.AgentDag.Node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AgentDag} 单元测试。
 *
 * <p>覆盖：构造不可变性、name 回落、根节点计算（入度 0）、依赖解析（缺失依赖静默跳过）、Node 构造与默认值。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("AgentDag Agent 编排 DAG")
class AgentDagTest {

  private static Node node(String id) {
    return new Node(id, "chat", "prompt-" + id, null, null);
  }

  private static Map<String, Node> nodesOf(Node... nodes) {
    Map<String, Node> map = new HashMap<>();
    for (Node n : nodes) {
      map.put(n.getId(), n);
    }
    return map;
  }

  @Nested
  @DisplayName("构造与不可变性")
  class ConstructionTest {

    @Test
    @DisplayName("id 为 null 应抛 NPE")
    void shouldThrowForNullId() {
      assertThatThrownBy(() -> new AgentDag(null, "name", Map.of(), Map.of()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("name 为 null 时应回落为 id")
    void shouldFallbackNameToId() {
      AgentDag dag = new AgentDag("dag-1", null, Map.of(), Map.of());
      assertThat(dag.getName()).isEqualTo("dag-1");
    }

    @Test
    @DisplayName("name 非 null 时应保留")
    void shouldKeepExplicitName() {
      AgentDag dag = new AgentDag("dag-1", "我的编排", Map.of(), Map.of());
      assertThat(dag.getName()).isEqualTo("我的编排");
    }

    @Test
    @DisplayName("nodes/edges 应不可修改")
    void shouldExposeUnmodifiableCollections() {
      AgentDag dag = new AgentDag("d", "n", nodesOf(node("a")), Map.of());
      assertThatThrownBy(() -> dag.getNodes().put("b", node("b")))
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> dag.getEdges().put("a", List.of()))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("外部修改源 Map 不应影响 DAG")
    void shouldDefensiveCopy() {
      Map<String, Node> nodes = nodesOf(node("a"));
      AgentDag dag = new AgentDag("d", "n", nodes, Map.of());
      nodes.put("b", node("b"));
      assertThat(dag.getNodes()).containsOnlyKeys("a");
    }
  }

  @Nested
  @DisplayName("根节点计算")
  class RootNodesTest {

    @Test
    @DisplayName("全部无依赖时应全部为根")
    void shouldReturnAllAsRootsWhenNoEdges() {
      AgentDag dag = new AgentDag("d", "n", nodesOf(node("a"), node("b")), Map.of());
      assertThat(dag.getRootNodes()).extracting(Node::getId).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    @DisplayName("链式依赖：edges 值为依赖列表，被依赖者入度 > 0，故根为不被任何边引用的节点")
    void shouldReturnOnlyZeroIndegree() {
      Map<String, Node> nodes = nodesOf(node("a"), node("b"), node("c"));
      Map<String, List<String>> edges = new HashMap<>();
      // c 依赖 b，b 依赖 a → a、b 均出现在依赖集合中，c 未出现 → c 是根
      edges.put("b", List.of("a"));
      edges.put("c", List.of("b"));

      AgentDag dag = new AgentDag("d", "n", nodes, edges);
      assertThat(dag.getRootNodes()).extracting(Node::getId).containsExactly("c");
    }

    @Test
    @DisplayName("菱形依赖应返回唯一根（不被任何边引用的节点）")
    void shouldReturnSingleRootForDiamond() {
      Map<String, Node> nodes = nodesOf(node("a"), node("b"), node("c"), node("d"));
      Map<String, List<String>> edges = new HashMap<>();
      // b、c 依赖 a；d 依赖 b、c → a/b/c 均出现，d 未出现 → d 是根
      edges.put("b", List.of("a"));
      edges.put("c", List.of("a"));
      edges.put("d", List.of("b", "c"));

      AgentDag dag = new AgentDag("d", "n", nodes, edges);
      assertThat(dag.getRootNodes()).extracting(Node::getId).containsExactly("d");
    }
  }

  @Nested
  @DisplayName("依赖解析")
  class DependenciesTest {

    @Test
    @DisplayName("应返回全部依赖节点")
    void shouldReturnDependencies() {
      Map<String, Node> nodes = nodesOf(node("a"), node("b"), node("c"));
      Map<String, List<String>> edges = new HashMap<>();
      edges.put("c", List.of("a", "b"));

      AgentDag dag = new AgentDag("d", "n", nodes, edges);
      assertThat(dag.getDependencies("c")).extracting(Node::getId)
          .containsExactlyInAnyOrder("a", "b");
    }

    @Test
    @DisplayName("无依赖节点应返回空列表")
    void shouldReturnEmptyForNoDeps() {
      AgentDag dag = new AgentDag("d", "n", nodesOf(node("a")), Map.of());
      assertThat(dag.getDependencies("a")).isEmpty();
    }

    @Test
    @DisplayName("依赖 ID 缺失时应静默跳过")
    void shouldSkipMissingDependency() {
      Map<String, Node> nodes = nodesOf(node("a"), node("b"));
      Map<String, List<String>> edges = new HashMap<>();
      edges.put("b", List.of("a", "missing"));

      AgentDag dag = new AgentDag("d", "n", nodes, edges);
      assertThat(dag.getDependencies("b")).extracting(Node::getId).containsExactly("a");
    }

    @Test
    @DisplayName("未知节点应返回空列表")
    void shouldReturnEmptyForUnknownNode() {
      AgentDag dag = new AgentDag("d", "n", nodesOf(node("a")), Map.of());
      assertThat(dag.getDependencies("nope")).isEmpty();
    }
  }

  @Nested
  @DisplayName("节点构造")
  class NodeTest {

    @Test
    @DisplayName("prompt/config 为 null 时应回落为空串/空 Map")
    void shouldFallbackNullFields() {
      Node n = new Node("a", "chat", null, null, null);
      assertThat(n.getPrompt()).isEmpty();
      assertThat(n.getConfig()).isEmpty();
    }

    @Test
    @DisplayName("config 非 null 时应保留且不可变")
    void shouldKeepConfigAndMakeUnmodifiable() {
      Map<String, Object> config = new HashMap<>();
      config.put("k", "v");
      Node n = new Node("a", "chat", "p", null, config);
      assertThat(n.getConfig()).containsEntry("k", "v");
      assertThatThrownBy(() -> n.getConfig().put("k2", "v2"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("id/agentType 为 null 应抛 NPE")
    void shouldThrowForNullRequiredFields() {
      assertThatThrownBy(() -> new Node(null, "chat", "p", null, null))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> new Node("a", null, "p", null, null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("toString 应包含 id 与 agentType")
    void shouldIncludeIdAndTypeInToString() {
      Node n = new Node("a", "chat", "p", null, null);
      assertThat(n.toString()).contains("a").contains("chat");
    }
  }
}
