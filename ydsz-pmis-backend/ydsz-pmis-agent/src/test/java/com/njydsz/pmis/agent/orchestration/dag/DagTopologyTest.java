package com.njydsz.pmis.agent.orchestration.dag;

import com.njydsz.pmis.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DagTopology 单元测试（P3-2 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-2)
 */
@DisplayName("DagTopology 拓扑分析工具")
class DagTopologyTest {

    @Nested
    @DisplayName("validate 校验")
    class ValidateTest {

        @Test
        @DisplayName("null DAG 抛异常")
        void shouldThrowWhenNull() {
            assertThatThrownBy(() -> DagTopology.validate(null))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("空节点列表抛异常")
        void shouldThrowWhenEmptyNodes() {
            DagDefinition dag = DagDefinition.builder()
                    .name("empty").nodes(List.of()).build();
            assertThatThrownBy(() -> DagTopology.validate(dag))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("节点无名称抛异常")
        void shouldThrowWhenNodeNoName() {
            DagNode node = DagNode.builder().name(" ").build();
            DagDefinition dag = DagDefinition.builder()
                    .name("noName").nodes(List.of(node)).build();
            assertThatThrownBy(() -> DagTopology.validate(dag))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("重复节点名抛异常")
        void shouldThrowWhenDuplicateNodeName() {
            DagNode n1 = DagNode.builder().name("a").build();
            DagNode n2 = DagNode.builder().name("a").build();
            DagDefinition dag = DagDefinition.builder()
                    .name("dup").nodes(List.of(n1, n2)).build();
            assertThatThrownBy(() -> DagTopology.validate(dag))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("依赖不存在的节点抛异常")
        void shouldThrowWhenMissingDependency() {
            DagNode n = DagNode.builder().name("a").dependsOn(List.of("ghost")).build();
            DagDefinition dag = DagDefinition.builder()
                    .name("missing").nodes(List.of(n)).build();
            assertThatThrownBy(() -> DagTopology.validate(dag))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("自环依赖抛异常")
        void shouldThrowWhenSelfDependency() {
            DagNode n = DagNode.builder().name("a").dependsOn(List.of("a")).build();
            DagDefinition dag = DagDefinition.builder()
                    .name("self").nodes(List.of(n)).build();
            assertThatThrownBy(() -> DagTopology.validate(dag))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("合法 DAG 校验通过")
        void shouldPassValidDag() {
            DagNode a = DagNode.builder().name("a").build();
            DagNode b = DagNode.builder().name("b").dependsOn(List.of("a")).build();
            DagDefinition dag = DagDefinition.builder()
                    .name("valid").nodes(List.of(a, b)).build();
            DagTopology.validate(dag); // 不抛异常即通过
        }
    }

    @Nested
    @DisplayName("topologicalSort 拓扑排序")
    class TopologicalSortTest {

        @Test
        @DisplayName("线性链 a->b->c")
        void shouldSortLinearChain() {
            DagNode a = DagNode.builder().name("a").build();
            DagNode b = DagNode.builder().name("b").dependsOn(List.of("a")).build();
            DagNode c = DagNode.builder().name("c").dependsOn(List.of("b")).build();
            DagDefinition dag = DagDefinition.builder()
                    .name("linear").nodes(List.of(a, b, c)).build();

            List<String> sorted = DagTopology.topologicalSort(dag);
            assertThat(sorted).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("菱形 a->{b,c}->d")
        void shouldSortDiamond() {
            DagNode a = DagNode.builder().name("a").build();
            DagNode b = DagNode.builder().name("b").dependsOn(List.of("a")).build();
            DagNode c = DagNode.builder().name("c").dependsOn(List.of("a")).build();
            DagNode d = DagNode.builder().name("d").dependsOn(List.of("b", "c")).build();
            DagDefinition dag = DagDefinition.builder()
                    .name("diamond").nodes(List.of(a, b, c, d)).build();

            List<String> sorted = DagTopology.topologicalSort(dag);
            assertThat(sorted).startsWith("a");
            assertThat(sorted).endsWith("d");
            assertThat(sorted).contains("b", "c");
            int idxB = sorted.indexOf("b");
            int idxC = sorted.indexOf("c");
            int idxA = sorted.indexOf("a");
            int idxD = sorted.indexOf("d");
            assertThat(idxA).isLessThan(idxB);
            assertThat(idxA).isLessThan(idxC);
            assertThat(idxB).isLessThan(idxD);
            assertThat(idxC).isLessThan(idxD);
        }

        @Test
        @DisplayName("存在环抛异常")
        void shouldThrowWhenCycle() {
            DagNode a = DagNode.builder().name("a").dependsOn(List.of("c")).build();
            DagNode b = DagNode.builder().name("b").dependsOn(List.of("a")).build();
            DagNode c = DagNode.builder().name("c").dependsOn(List.of("b")).build();
            DagDefinition dag = DagDefinition.builder()
                    .name("cycle").nodes(List.of(a, b, c)).build();

            assertThatThrownBy(() -> DagTopology.topologicalSort(dag))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("单节点")
        void shouldSortSingleNode() {
            DagNode a = DagNode.builder().name("a").build();
            DagDefinition dag = DagDefinition.builder()
                    .name("single").nodes(List.of(a)).build();

            List<String> sorted = DagTopology.topologicalSort(dag);
            assertThat(sorted).containsExactly("a");
        }
    }

    @Nested
    @DisplayName("layeredSort 分层排序")
    class LayeredSortTest {

        @Test
        @DisplayName("线性链分 3 层")
        void shouldLayerLinearChain() {
            DagNode a = DagNode.builder().name("a").build();
            DagNode b = DagNode.builder().name("b").dependsOn(List.of("a")).build();
            DagNode c = DagNode.builder().name("c").dependsOn(List.of("b")).build();
            DagDefinition dag = DagDefinition.builder()
                    .name("linear").nodes(List.of(a, b, c)).build();

            List<List<String>> layers = DagTopology.layeredSort(dag);
            assertThat(layers).hasSize(3);
            assertThat(layers.get(0)).containsExactly("a");
            assertThat(layers.get(1)).containsExactly("b");
            assertThat(layers.get(2)).containsExactly("c");
        }

        @Test
        @DisplayName("菱形分 3 层，中间层并行")
        void shouldLayerDiamond() {
            DagNode a = DagNode.builder().name("a").build();
            DagNode b = DagNode.builder().name("b").dependsOn(List.of("a")).build();
            DagNode c = DagNode.builder().name("c").dependsOn(List.of("a")).build();
            DagNode d = DagNode.builder().name("d").dependsOn(List.of("b", "c")).build();
            DagDefinition dag = DagDefinition.builder()
                    .name("diamond").nodes(List.of(a, b, c, d)).build();

            List<List<String>> layers = DagTopology.layeredSort(dag);
            assertThat(layers).hasSize(3);
            assertThat(layers.get(0)).containsExactly("a");
            assertThat(layers.get(1)).containsExactlyInAnyOrder("b", "c");
            assertThat(layers.get(2)).containsExactly("d");
        }

        @Test
        @DisplayName("两独立节点分 1 层")
        void shouldLayerIndependentNodes() {
            DagNode a = DagNode.builder().name("a").build();
            DagNode b = DagNode.builder().name("b").build();
            DagDefinition dag = DagDefinition.builder()
                    .name("independent").nodes(List.of(a, b)).build();

            List<List<String>> layers = DagTopology.layeredSort(dag);
            assertThat(layers).hasSize(1);
            assertThat(layers.get(0)).containsExactlyInAnyOrder("a", "b");
        }

        @Test
        @DisplayName("存在环抛异常")
        void shouldThrowWhenCycle() {
            DagNode a = DagNode.builder().name("a").dependsOn(List.of("b")).build();
            DagNode b = DagNode.builder().name("b").dependsOn(List.of("a")).build();
            DagDefinition dag = DagDefinition.builder()
                    .name("cycle").nodes(List.of(a, b)).build();

            assertThatThrownBy(() -> DagTopology.layeredSort(dag))
                    .isInstanceOf(BizException.class);
        }
    }

    @Nested
    @DisplayName("downstreamClosure / upstreamClosure 闭包")
    class ClosureTest {

        @Test
        @DisplayName("下游闭包：a->b->c，a 的下游是 {b, c}")
        void shouldComputeDownstreamClosure() {
            DagNode a = DagNode.builder().name("a").build();
            DagNode b = DagNode.builder().name("b").dependsOn(List.of("a")).build();
            DagNode c = DagNode.builder().name("c").dependsOn(List.of("b")).build();
            DagDefinition dag = DagDefinition.builder()
                    .name("linear").nodes(List.of(a, b, c)).build();

            Set<String> downstream = DagTopology.downstreamClosure(dag, "a");
            assertThat(downstream).containsExactlyInAnyOrder("b", "c");
        }

        @Test
        @DisplayName("上游闭包：a->b->c，c 的上游是 {a, b}")
        void shouldComputeUpstreamClosure() {
            DagNode a = DagNode.builder().name("a").build();
            DagNode b = DagNode.builder().name("b").dependsOn(List.of("a")).build();
            DagNode c = DagNode.builder().name("c").dependsOn(List.of("b")).build();
            DagDefinition dag = DagDefinition.builder()
                    .name("linear").nodes(List.of(a, b, c)).build();

            Set<String> upstream = DagTopology.upstreamClosure(dag, "c");
            assertThat(upstream).containsExactlyInAnyOrder("a", "b");
        }

        @Test
        @DisplayName("叶子节点无下游")
        void shouldReturnEmptyDownstreamForLeaf() {
            DagNode a = DagNode.builder().name("a").build();
            DagDefinition dag = DagDefinition.builder()
                    .name("single").nodes(List.of(a)).build();

            Set<String> downstream = DagTopology.downstreamClosure(dag, "a");
            assertThat(downstream).isEmpty();
        }

        @Test
        @DisplayName("根节点无上游")
        void shouldReturnEmptyUpstreamForRoot() {
            DagNode a = DagNode.builder().name("a").build();
            DagDefinition dag = DagDefinition.builder()
                    .name("single").nodes(List.of(a)).build();

            Set<String> upstream = DagTopology.upstreamClosure(dag, "a");
            assertThat(upstream).isEmpty();
        }
    }
}
