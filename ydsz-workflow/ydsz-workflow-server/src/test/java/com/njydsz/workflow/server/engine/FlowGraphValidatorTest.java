package com.njydsz.workflow.server.engine;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.locales.util.I18nMessages;
import com.njydsz.workflow.domain.enums.FlowNodeType;
import com.njydsz.workflow.domain.exception.WorkflowException;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowSkipVO;

/**
 * {@link FlowGraphValidator} 单元测试。
 *
 * <p>覆盖流程图校验的核心场景：合法图结构校验通过，空节点抛异常，缺少起/止节点抛异常，
 * 环路检测仅记录日志不拒绝，不可达节点 / 孤立节点抛异常。
 *
 * <p>使用真实 VO 构造输入数据；Mock 仅用于注入 {@link I18nMessages} 依赖（校验器内部通过它
 * 解析 i18n 错误消息；合法路径不触发 i18nMessages，非法路径通过异常类型断言，不受消息内容影响）。
 *
 * @author ydsz-smoke-test
 * @since 26.10.12
 */
class FlowGraphValidatorTest {

  /** 国际化消息 mock（合法路径不触发；非法路径只关心异常类型） */
  private I18nMessages i18nMessages;

  /** 被测对象 */
  private FlowGraphValidator validator;

  @BeforeEach
  void setUp() {
    i18nMessages = mock(I18nMessages.class);
    validator = new FlowGraphValidator(i18nMessages);
  }

  // ============================== 合法图：校验通过 ==============================

  @Nested
  @DisplayName("validate — 合法图校验通过")
  class ValidateHappyPath {

    @Test
    @DisplayName("validate - 最简单的流程图（START -> APPROVAL -> END）应通过校验")
    void validate_simpleFlow_passes() {
      List<FlowNodeVO> nodes = List.of(
          buildNode("start", FlowNodeType.START),
          buildNode("approve", FlowNodeType.APPROVAL),
          buildNode("end", FlowNodeType.END));
      List<FlowSkipVO> skips = List.of(
          buildSkip("start", "approve"),
          buildSkip("approve", "end"));

      assertThatCode(() -> validator.validate(nodes, skips)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validate - 含并行分支的复杂图应通过校验")
    void validate_parallelGraph_passes() {
      List<FlowNodeVO> nodes = List.of(
          buildNode("start", FlowNodeType.START),
          buildNode("fork", FlowNodeType.PARALLEL),
          buildNode("branch1", FlowNodeType.APPROVAL),
          buildNode("branch2", FlowNodeType.SERVICE),
          buildNode("join", FlowNodeType.PARALLEL),
          buildNode("end", FlowNodeType.END));
      List<FlowSkipVO> skips = List.of(
          buildSkip("start", "fork"),
          buildSkip("fork", "branch1"),
          buildSkip("fork", "branch2"),
          buildSkip("branch1", "join"),
          buildSkip("branch2", "join"),
          buildSkip("join", "end"));

      assertThatCode(() -> validator.validate(nodes, skips)).doesNotThrowAnyException();
    }
  }

  // ============================== 空节点列表 ==============================

  @Nested
  @DisplayName("validate — 空节点列表")
  class ValidateEmptyNodes {

    @Test
    @DisplayName("validate - nodes 为 null 应抛出 WorkflowException")
    void validate_nodesNull_throwsException() {
      assertThatThrownBy(() -> validator.validate(null, List.of()))
          .isInstanceOf(WorkflowException.class);
    }

    @Test
    @DisplayName("validate - nodes 为空列表应抛出 WorkflowException")
    void validate_nodesEmpty_throwsException() {
      assertThatThrownBy(() -> validator.validate(new ArrayList<>(), List.of()))
          .isInstanceOf(WorkflowException.class);
    }
  }

  // ============================== 起止节点规则 ==============================

  @Nested
  @DisplayName("validate — 起止节点规则（startEndRequired）")
  class StartEndRequired {

    @Test
    @DisplayName("validate - 缺少 START 节点应抛出 WorkflowException")
    void validate_noStartNode_throwsException() {
      List<FlowNodeVO> nodes = List.of(
          buildNode("approve", FlowNodeType.APPROVAL),
          buildNode("end", FlowNodeType.END));
      List<FlowSkipVO> skips = List.of(buildSkip("approve", "end"));

      assertThatThrownBy(() -> validator.validate(nodes, skips))
          .isInstanceOf(WorkflowException.class);
    }

    @Test
    @DisplayName("validate - 缺少 END 节点应抛出 WorkflowException")
    void validate_noEndNode_throwsException() {
      List<FlowNodeVO> nodes = List.of(
          buildNode("start", FlowNodeType.START),
          buildNode("approve", FlowNodeType.APPROVAL));
      List<FlowSkipVO> skips = List.of(buildSkip("start", "approve"));

      assertThatThrownBy(() -> validator.validate(nodes, skips))
          .isInstanceOf(WorkflowException.class);
    }

    @Test
    @DisplayName("validate - 多个 START 节点应抛出 WorkflowException")
    void validate_duplicateStartNode_throwsException() {
      List<FlowNodeVO> nodes = List.of(
          buildNode("start1", FlowNodeType.START),
          buildNode("start2", FlowNodeType.START),
          buildNode("end", FlowNodeType.END));
      List<FlowSkipVO> skips = List.of(
          buildSkip("start1", "end"),
          buildSkip("start2", "end"));

      assertThatThrownBy(() -> validator.validate(nodes, skips))
          .isInstanceOf(WorkflowException.class);
    }
  }

  // ============================== 环路检测 ==============================

  @Nested
  @DisplayName("cycleDetection — 环路检测仅记录日志不拒绝")
  class CycleDetection {

    @Test
    @DisplayName("validate - 含合法环的图（A↔B 双向边）不应被拒绝")
    void validate_graphWithCycle_isNotRejected() {
      // START → A ↔ B → END（A→B, B→A 形成环，但整体可达终止）
      List<FlowNodeVO> nodes = List.of(
          buildNode("start", FlowNodeType.START),
          buildNode("a", FlowNodeType.APPROVAL),
          buildNode("b", FlowNodeType.APPROVAL),
          buildNode("end", FlowNodeType.END));
      List<FlowSkipVO> skips = List.of(
          buildSkip("start", "a"),
          buildSkip("a", "b"),
          buildSkip("a", "end"),
          buildSkip("b", "a"),
          buildSkip("b", "end"));

      // 环仅触发 warn 日志，校验仍然通过
      assertThatCode(() -> validator.validate(nodes, skips)).doesNotThrowAnyException();
    }
  }

  // ============================== 连通性与孤立节点 ==============================

  @Nested
  @DisplayName("validate — 连通性与孤立节点")
  class Reachability {

    @Test
    @DisplayName("validate - 孤立节点（无入边非 START）应抛出 WorkflowException")
    void validate_isolatedNodeNoInEdge_throwsException() {
      List<FlowNodeVO> nodes = List.of(
          buildNode("start", FlowNodeType.START),
          buildNode("approve", FlowNodeType.APPROVAL),
          buildNode("orphan", FlowNodeType.APPROVAL),
          buildNode("end", FlowNodeType.END));
      // orphan 不在任何 skip 中 → 入边为空且非 START → validateNoIsolatedNodes 抛出
      List<FlowSkipVO> skips = List.of(
          buildSkip("start", "approve"),
          buildSkip("approve", "end"));

      assertThatThrownBy(() -> validator.validate(nodes, skips))
          .isInstanceOf(WorkflowException.class);
    }

    @Test
    @DisplayName("validate - 无出边非 END 节点应抛出 WorkflowException")
    void validate_noOutEdgeNonEndNode_throwsException() {
      List<FlowNodeVO> nodes = List.of(
          buildNode("start", FlowNodeType.START),
          buildNode("deadEnd", FlowNodeType.APPROVAL),
          buildNode("end", FlowNodeType.END));
      // deadEnd 有入边但无出边（非 END）→ validateNoIsolatedNodes 抛出
      List<FlowSkipVO> skips = List.of(
          buildSkip("start", "deadEnd"),
          buildSkip("start", "end"));

      assertThatThrownBy(() -> validator.validate(nodes, skips))
          .isInstanceOf(WorkflowException.class);
    }
  }

  // ============================== 工具方法 ==============================

  /**
   * 构建 FlowNodeVO，仅设置关键字段（nodeCode + nodeType）。
   *
   * @param code 节点编码
   * @param type 节点类型枚举
   * @return 构建好的 FlowNodeVO
   */
  private FlowNodeVO buildNode(String code, FlowNodeType type) {
    FlowNodeVO node = new FlowNodeVO();
    node.setNodeCode(code);
    node.setNodeType(type.getCode());
    return node;
  }

  /**
   * 构建 FlowSkipVO，仅设置源节点编码和目标节点编码。
   *
   * @param sourceCode 源节点编码
   * @param targetCode 目标节点编码
   * @return 构建好的 FlowSkipVO
   */
  private FlowSkipVO buildSkip(String sourceCode, String targetCode) {
    FlowSkipVO skip = new FlowSkipVO();
    skip.setSourceNodeCode(sourceCode);
    skip.setNextNodeCode(targetCode);
    return skip;
  }
}
