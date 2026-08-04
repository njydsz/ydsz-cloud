package com.njydsz.workflow.server.engine;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.workflow.domain.entity.FlowNode;
import com.njydsz.workflow.domain.entity.FlowSkip;
import com.njydsz.workflow.domain.enums.FlowNodeType;

/**
 * FlowGraphValidator 流程图校验器单元测试
 *
 * <p>纯单元测试，不依赖 Spring 上下文，直接 new 出被测对象。
 *
 * <p>注意：被测类 {@link FlowGraphValidator} 对图结构违规统一抛出
 * {@link IllegalArgumentException}（非 SysException），故以下异常断言均针对
 * {@code IllegalArgumentException} 进行。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@DisplayName("FlowGraphValidator 流程图校验器测试")
class FlowGraphValidatorTest {

    private final FlowGraphValidator validator = new FlowGraphValidator();

    /**
     * 构建节点 DO
     *
     * @param code 节点编码
     * @param type 节点类型
     */
    private FlowNode node(String code, FlowNodeType type) {
        FlowNode n = new FlowNode();
        n.setNodeCode(code);
        n.setNodeType(type.getCode());
        n.setNodeName(code);
        return n;
    }

    /**
     * 构建跳转边 DO
     *
     * <p>sourceRef 存放在 ext JSON 中（与被测类 {@code extractSourceRef} 逻辑一致）。
     *
     * @param source 源节点编码
     * @param target 目标节点编码
     */
    private FlowSkip skip(String source, String target) {
        FlowSkip s = new FlowSkip();
        s.setExt("{\"sourceRef\":\"" + source + "\"}");
        s.setNextNodeCode(target);
        s.setSkipName(source + "->" + target);
        return s;
    }

    // ==================== 合法流程图 ====================

    @Nested
    @DisplayName("合法流程图校验通过")
    class ValidFlowTest {

        @Test
        @DisplayName("线性流程：START → APPROVAL → END 校验通过")
        void validLinearFlow() {
            List<FlowNode> nodes = List.of(
                    node("start", FlowNodeType.START),
                    node("approval", FlowNodeType.APPROVAL),
                    node("end", FlowNodeType.END));
            List<FlowSkip> skips = List.of(
                    skip("start", "approval"),
                    skip("approval", "end"));

            assertThatCode(() -> validator.validate(nodes, skips))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("分支流程：START → CONDITION → [APPROVAL1 → END, APPROVAL2 → END] 校验通过")
        void validBranchedFlow() {
            List<FlowNode> nodes = List.of(
                    node("start", FlowNodeType.START),
                    node("gateway", FlowNodeType.CONDITION),
                    node("approval1", FlowNodeType.APPROVAL),
                    node("approval2", FlowNodeType.APPROVAL),
                    node("end", FlowNodeType.END));
            List<FlowSkip> skips = List.of(
                    skip("start", "gateway"),
                    skip("gateway", "approval1"),
                    skip("gateway", "approval2"),
                    skip("approval1", "end"),
                    skip("approval2", "end"));

            assertThatCode(() -> validator.validate(nodes, skips))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("环路检测：含环路的流程图校验通过（BPMN 循环合法，仅记录日志不拒绝）")
        void detectCycle() {
            // START → A → B → A（构成环 A↔B），同时 A → END 保证可达终止
            List<FlowNode> nodes = List.of(
                    node("start", FlowNodeType.START),
                    node("a", FlowNodeType.APPROVAL),
                    node("b", FlowNodeType.APPROVAL),
                    node("end", FlowNodeType.END));
            List<FlowSkip> skips = List.of(
                    skip("start", "a"),
                    skip("a", "b"),
                    skip("b", "a"),
                    skip("a", "end"));

            // 环路仅记录日志，校验应通过
            assertThatCode(() -> validator.validate(nodes, skips))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("多 END 节点的分支流程校验通过")
        void validMultipleEndNodes() {
            List<FlowNode> nodes = List.of(
                    node("start", FlowNodeType.START),
                    node("gateway", FlowNodeType.CONDITION),
                    node("end1", FlowNodeType.END),
                    node("end2", FlowNodeType.END));
            List<FlowSkip> skips = List.of(
                    skip("start", "gateway"),
                    skip("gateway", "end1"),
                    skip("gateway", "end2"));

            assertThatCode(() -> validator.validate(nodes, skips))
                    .doesNotThrowAnyException();
        }
    }

    // ==================== 起始/结束节点校验 ====================

    @Nested
    @DisplayName("起始/结束节点校验")
    class StartEndNodeTest {

        @Test
        @DisplayName("多个开始节点 → 抛出 IllegalArgumentException")
        void detectMultipleStartNodes() {
            List<FlowNode> nodes = List.of(
                    node("start1", FlowNodeType.START),
                    node("start2", FlowNodeType.START),
                    node("end", FlowNodeType.END));
            List<FlowSkip> skips = List.of(
                    skip("start1", "end"),
                    skip("start2", "end"));

            assertThatThrownBy(() -> validator.validate(nodes, skips))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("多个开始节点");
        }

        @Test
        @DisplayName("缺少开始节点 → 抛出 IllegalArgumentException")
        void detectNoStartNode() {
            List<FlowNode> nodes = List.of(
                    node("approval", FlowNodeType.APPROVAL),
                    node("end", FlowNodeType.END));
            List<FlowSkip> skips = List.of(
                    skip("approval", "end"));

            assertThatThrownBy(() -> validator.validate(nodes, skips))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("缺少开始节点");
        }

        @Test
        @DisplayName("缺少结束节点 → 抛出 IllegalArgumentException")
        void detectNoEndNode() {
            List<FlowNode> nodes = List.of(
                    node("start", FlowNodeType.START),
                    node("approval", FlowNodeType.APPROVAL));
            List<FlowSkip> skips = List.of(
                    skip("start", "approval"));

            assertThatThrownBy(() -> validator.validate(nodes, skips))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("缺少结束节点");
        }
    }

    // ==================== 连通性与可达性校验 ====================

    @Nested
    @DisplayName("连通性与可达性校验")
    class ConnectivityTest {

        @Test
        @DisplayName("孤立节点（与图完全断开）→ 抛出 IllegalArgumentException")
        void detectOrphanNode() {
            // orphan 节点没有任何入边/出边，从 START 不可达
            List<FlowNode> nodes = List.of(
                    node("start", FlowNodeType.START),
                    node("end", FlowNodeType.END),
                    node("orphan", FlowNodeType.APPROVAL));
            List<FlowSkip> skips = List.of(
                    skip("start", "end"));

            assertThatThrownBy(() -> validator.validate(nodes, skips))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("orphan");
        }

        @Test
        @DisplayName("不可达节点（从 START 出发 BFS 无法到达）→ 抛出 IllegalArgumentException")
        void detectUnreachableNode() {
            // START → END 直连；另有 A → B → END 但 A 无入边，从 START 不可达
            List<FlowNode> nodes = List.of(
                    node("start", FlowNodeType.START),
                    node("end", FlowNodeType.END),
                    node("a", FlowNodeType.APPROVAL),
                    node("b", FlowNodeType.APPROVAL));
            List<FlowSkip> skips = List.of(
                    skip("start", "end"),
                    skip("a", "b"),
                    skip("b", "end"));

            assertThatThrownBy(() -> validator.validate(nodes, skips))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不可达");
        }

        @Test
        @DisplayName("死胡同节点（无法到达任何 END）→ 抛出 IllegalArgumentException")
        void detectDeadEndNode() {
            // START → A → END；START → B（B 无出边，无法到达 END）
            List<FlowNode> nodes = List.of(
                    node("start", FlowNodeType.START),
                    node("a", FlowNodeType.APPROVAL),
                    node("b", FlowNodeType.APPROVAL),
                    node("end", FlowNodeType.END));
            List<FlowSkip> skips = List.of(
                    skip("start", "a"),
                    skip("a", "end"),
                    skip("start", "b"));

            assertThatThrownBy(() -> validator.validate(nodes, skips))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("死胡同");
        }
    }

    // ==================== 悬空边校验 ====================

    @Nested
    @DisplayName("悬空边校验")
    class DanglingEdgeTest {

        @Test
        @DisplayName("跳转 sourceRef 指向不存在的节点 → 抛出 IllegalArgumentException")
        void detectDanglingEdge() {
            List<FlowNode> nodes = List.of(
                    node("start", FlowNodeType.START),
                    node("end", FlowNodeType.END));
            List<FlowSkip> skips = List.of(
                    skip("ghost", "end"),
                    skip("start", "end"));

            assertThatThrownBy(() -> validator.validate(nodes, skips))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sourceRef")
                    .hasMessageContaining("ghost");
        }

        @Test
        @DisplayName("跳转 nextNodeCode 指向不存在的节点 → 抛出 IllegalArgumentException")
        void detectDanglingTarget() {
            List<FlowNode> nodes = List.of(
                    node("start", FlowNodeType.START),
                    node("end", FlowNodeType.END));
            List<FlowSkip> skips = List.of(
                    skip("start", "ghost"),
                    skip("start", "end"));

            assertThatThrownBy(() -> validator.validate(nodes, skips))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nextNodeCode")
                    .hasMessageContaining("ghost");
        }
    }

    // ==================== 节点编码校验 ====================

    @Nested
    @DisplayName("节点编码校验")
    class NodeCodeTest {

        @Test
        @DisplayName("节点 nodeCode 为空 → 抛出 IllegalArgumentException")
        void detectEmptyNodeCode() {
            FlowNode emptyCodeNode = new FlowNode();
            emptyCodeNode.setNodeType(FlowNodeType.START.getCode());
            emptyCodeNode.setNodeName("empty-code");

            List<FlowNode> nodes = List.of(
                    emptyCodeNode,
                    node("end", FlowNodeType.END));
            List<FlowSkip> skips = List.of();

            assertThatThrownBy(() -> validator.validate(nodes, skips))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nodeCode 为空");
        }

        @Test
        @DisplayName("节点编码重复 → 抛出 IllegalArgumentException")
        void detectDuplicateNodeCode() {
            List<FlowNode> nodes = List.of(
                    node("dup", FlowNodeType.START),
                    node("dup", FlowNodeType.END));
            List<FlowSkip> skips = List.of();

            assertThatThrownBy(() -> validator.validate(nodes, skips))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("节点编码重复")
                    .hasMessageContaining("dup");
        }
    }

    // ==================== 空输入校验 ====================

    @Nested
    @DisplayName("空输入校验")
    class EmptyInputTest {

        @Test
        @DisplayName("nodes 为 null → 抛出 IllegalArgumentException")
        void detectNullNodes() {
            assertThatThrownBy(() -> validator.validate(null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("节点列表为空");
        }

        @Test
        @DisplayName("nodes 为空列表 → 抛出 IllegalArgumentException")
        void detectEmptyNodes() {
            assertThatThrownBy(() -> validator.validate(List.of(), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("节点列表为空");
        }
    }
}
