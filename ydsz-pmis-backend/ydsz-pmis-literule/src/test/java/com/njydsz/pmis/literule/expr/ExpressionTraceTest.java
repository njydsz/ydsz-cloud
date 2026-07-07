package com.njydsz.pmis.literule.expr;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.config.RuleAdminService;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import com.njydsz.pmis.literule.spi.RuleVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 表达式追踪功能单元测试
 *
 * <p>测试目标：
 * <ul>
 *   <li>{@link AviatorExpressionEvaluator#evalBooleanWithTrace} - 带追踪的布尔表达式求值</li>
 *   <li>{@link ExpressionTraceNode} - 追踪树节点数据模型（静态工厂方法、builder）</li>
 *   <li>{@link RuleAdminService#traceExpression} - 规则管理服务的表达式追踪入口</li>
 * </ul>
 *
 * <p>覆盖场景：空表达式、简单比较、逻辑运算（AND/OR）、短路分析、复合嵌套、
 * 沙箱拦截、求值异常、变量不存在、TraceResult 结构、节点工厂方法。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("表达式追踪功能测试")
class ExpressionTraceTest {

    // ==================== AviatorExpressionEvaluator.evalBooleanWithTrace ====================

    @Nested
    @DisplayName("AviatorExpressionEvaluator.evalBooleanWithTrace")
    class EvalBooleanWithTraceTest {

        private AviatorExpressionEvaluator evaluator;

        @BeforeEach
        void setUp() {
            evaluator = new AviatorExpressionEvaluator();
        }

        @Test
        @DisplayName("空表达式返回 ROOT 节点，result=false，error='表达式为空'")
        void shouldReturnRootWithErrorForEmptyExpression() {
            ExpressionEvaluator.TraceResult result = evaluator.evalBooleanWithTrace(
                    "", RuleContext.of(new HashMap<>()));

            assertThat(result.result()).isFalse();
            ExpressionTraceNode root = result.traceTree();
            assertThat(root.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.ROOT);
            assertThat(root.getError()).isEqualTo("表达式为空");
            assertThat(root.getResult()).isEqualTo(false);
        }

        @Test
        @DisplayName("null 表达式返回 ROOT 节点，result=false，error='表达式为空'")
        void shouldReturnRootWithErrorForNullExpression() {
            ExpressionEvaluator.TraceResult result = evaluator.evalBooleanWithTrace(
                    null, RuleContext.of(new HashMap<>()));

            assertThat(result.result()).isFalse();
            ExpressionTraceNode root = result.traceTree();
            assertThat(root.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.ROOT);
            assertThat(root.getError()).isEqualTo("表达式为空");
        }

        @Test
        @DisplayName("简单比较表达式返回 COMPARISON 节点，含变量名、变量值、运算符、结果")
        void shouldReturnComparisonNodeForSimpleComparison() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 1500);
            RuleContext context = RuleContext.of(facts);

            ExpressionEvaluator.TraceResult result = evaluator.evalBooleanWithTrace(
                    "amount > 1000", context);

            assertThat(result.result()).isTrue();
            ExpressionTraceNode node = result.traceTree();
            assertThat(node.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.COMPARISON);
            assertThat(node.getOperator()).isEqualTo(">");
            assertThat(node.getExpression()).isEqualTo("amount > 1000");
            assertThat(node.getResult()).isEqualTo(true);
            // 子节点：左 VARIABLE，右 LITERAL
            assertThat(node.getChildren()).hasSize(2);
            ExpressionTraceNode leftChild = node.getChildren().get(0);
            assertThat(leftChild.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.VARIABLE);
            assertThat(leftChild.getVariableName()).isEqualTo("amount");
            assertThat(leftChild.getVariableValue()).isEqualTo(1500);
            ExpressionTraceNode rightChild = node.getChildren().get(1);
            assertThat(rightChild.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.LITERAL);
            assertThat(rightChild.getLiteralValue()).isEqualTo(1000);
        }

        @Test
        @DisplayName("AND 逻辑表达式返回 LOGICAL 节点，operator='&&'，有两个子节点")
        void shouldReturnLogicalNodeForAndExpression() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 1500);
            facts.put("score", 900);
            RuleContext context = RuleContext.of(facts);

            ExpressionEvaluator.TraceResult result = evaluator.evalBooleanWithTrace(
                    "amount > 1000 && score > 800", context);

            assertThat(result.result()).isTrue();
            ExpressionTraceNode node = result.traceTree();
            assertThat(node.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.LOGICAL);
            assertThat(node.getOperator()).isEqualTo("&&");
            assertThat(node.isShortCircuited()).isFalse();
            assertThat(node.getChildren()).hasSize(2);
            // 两个子节点都是 COMPARISON
            assertThat(node.getChildren().get(0).getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.COMPARISON);
            assertThat(node.getChildren().get(1).getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.COMPARISON);
        }

        @Test
        @DisplayName("AND 短路：左侧 false 时右侧不执行，shortCircuited=true")
        void shouldShortCircuitAndWhenLeftIsFalse() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 500);  // amount > 1000 = false
            facts.put("score", 900);
            RuleContext context = RuleContext.of(facts);

            ExpressionEvaluator.TraceResult result = evaluator.evalBooleanWithTrace(
                    "amount > 1000 && score > 800", context);

            assertThat(result.result()).isFalse();
            ExpressionTraceNode node = result.traceTree();
            assertThat(node.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.LOGICAL);
            assertThat(node.getOperator()).isEqualTo("&&");
            assertThat(node.isShortCircuited()).isTrue();
            // 左子节点已求值（COMPARISON）
            assertThat(node.getChildren().get(0).getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.COMPARISON);
            // 右子节点被短路跳过
            ExpressionTraceNode rightChild = node.getChildren().get(1);
            assertThat(rightChild.isShortCircuited()).isTrue();
            assertThat(rightChild.getError()).isEqualTo("短路跳过");
        }

        @Test
        @DisplayName("OR 逻辑表达式返回 LOGICAL 节点，operator='||'")
        void shouldReturnLogicalNodeForOrExpression() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 500);
            facts.put("score", 900);
            RuleContext context = RuleContext.of(facts);

            ExpressionEvaluator.TraceResult result = evaluator.evalBooleanWithTrace(
                    "amount > 1000 || score > 800", context);

            assertThat(result.result()).isTrue();
            ExpressionTraceNode node = result.traceTree();
            assertThat(node.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.LOGICAL);
            assertThat(node.getOperator()).isEqualTo("||");
            assertThat(node.getChildren()).hasSize(2);
            assertThat(node.isShortCircuited()).isFalse();
        }

        @Test
        @DisplayName("OR 短路：左侧 true 时右侧不执行，shortCircuited=true")
        void shouldShortCircuitOrWhenLeftIsTrue() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 1500);  // amount > 1000 = true
            facts.put("score", 700);
            RuleContext context = RuleContext.of(facts);

            ExpressionEvaluator.TraceResult result = evaluator.evalBooleanWithTrace(
                    "amount > 1000 || score > 800", context);

            assertThat(result.result()).isTrue();
            ExpressionTraceNode node = result.traceTree();
            assertThat(node.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.LOGICAL);
            assertThat(node.getOperator()).isEqualTo("||");
            assertThat(node.isShortCircuited()).isTrue();
            // 右子节点被短路跳过
            ExpressionTraceNode rightChild = node.getChildren().get(1);
            assertThat(rightChild.isShortCircuited()).isTrue();
            assertThat(rightChild.getError()).isEqualTo("短路跳过");
        }

        @Test
        @DisplayName("复合嵌套表达式 a > 1 && b > 2 || c > 3")
        void shouldHandleCompositeNestedExpression() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("a", 5);
            facts.put("b", 1);   // b > 2 = false
            facts.put("c", 10);  // c > 3 = true
            RuleContext context = RuleContext.of(facts);

            // (a > 1 && b > 2) || c > 3 = (true && false) || true = true
            ExpressionEvaluator.TraceResult result = evaluator.evalBooleanWithTrace(
                    "a > 1 && b > 2 || c > 3", context);

            assertThat(result.result()).isTrue();
            ExpressionTraceNode root = result.traceTree();
            // 顶层是 OR（|| 优先级最低，先拆分）
            assertThat(root.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.LOGICAL);
            assertThat(root.getOperator()).isEqualTo("||");
            assertThat(root.getChildren()).hasSize(2);
            // 左子节点是 AND 表达式
            ExpressionTraceNode leftChild = root.getChildren().get(0);
            assertThat(leftChild.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.LOGICAL);
            assertThat(leftChild.getOperator()).isEqualTo("&&");
            // 右子节点是 COMPARISON 表达式
            ExpressionTraceNode rightChild = root.getChildren().get(1);
            assertThat(rightChild.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.COMPARISON);
        }

        @Test
        @DisplayName("沙箱拦截：危险表达式返回 ROOT 节点，error 包含'沙箱拦截'")
        void shouldReturnRootWithSandboxErrorForDangerousExpression() {
            RuleContext context = RuleContext.of(new HashMap<>());

            // System.exit(0) 含危险类 System 和危险方法 exit
            ExpressionEvaluator.TraceResult result = evaluator.evalBooleanWithTrace(
                    "System.exit(0)", context);

            assertThat(result.result()).isFalse();
            ExpressionTraceNode root = result.traceTree();
            assertThat(root.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.ROOT);
            assertThat(root.getError()).contains("沙箱拦截");
        }

        @Test
        @DisplayName("求值异常：返回 ROOT 节点，error 包含'求值异常'")
        void shouldReturnRootWithErrorForEvaluationException() {
            // 使用 null context 触发 buildTraceTree 中的 NullPointerException
            // evalBoolean 内部捕获 NPE 返回 false，但 buildTraceTree 直接调用 context.getFacts() 抛出 NPE
            // 该 NPE 被 evalBooleanWithTrace 的 catch(Exception) 捕获，产生"求值异常"错误
            ExpressionEvaluator.TraceResult result = evaluator.evalBooleanWithTrace(
                    "amount > 1000", null);

            assertThat(result.result()).isFalse();
            ExpressionTraceNode root = result.traceTree();
            assertThat(root.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.ROOT);
            assertThat(root.getError()).contains("求值异常");
        }

        @Test
        @DisplayName("变量不存在时仍返回 COMPARISON 节点，子 VARIABLE 节点的 variableValue=null")
        void shouldHandleNonExistentVariable() {
            RuleContext context = RuleContext.of(new HashMap<>());

            ExpressionEvaluator.TraceResult result = evaluator.evalBooleanWithTrace(
                    "nonexistent > 1000", context);

            assertThat(result.result()).isFalse();
            ExpressionTraceNode node = result.traceTree();
            assertThat(node.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.COMPARISON);
            assertThat(node.getOperator()).isEqualTo(">");
            // 子 VARIABLE 节点的 variableValue 为 null
            ExpressionTraceNode leftChild = node.getChildren().get(0);
            assertThat(leftChild.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.VARIABLE);
            assertThat(leftChild.getVariableName()).isEqualTo("nonexistent");
            assertThat(leftChild.getVariableValue()).isNull();
        }

        @Test
        @DisplayName("TraceResult 的 result 和 traceTree 字段正确")
        void shouldHaveCorrectResultAndTraceTreeFields() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 1500);
            RuleContext context = RuleContext.of(facts);

            ExpressionEvaluator.TraceResult result = evaluator.evalBooleanWithTrace(
                    "amount > 1000", context);

            assertThat(result.result()).isTrue();
            assertThat(result.traceTree()).isNotNull();
            assertThat(result.traceTree().getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.COMPARISON);
        }
    }

    // ==================== ExpressionTraceNode 数据模型 ====================

    @Nested
    @DisplayName("ExpressionTraceNode 数据模型")
    class ExpressionTraceNodeModelTest {

        @Test
        @DisplayName("variable() 创建 VARIABLE 节点")
        void shouldCreateVariableNode() {
            ExpressionTraceNode node = ExpressionTraceNode.variable("amount", 1500);

            assertThat(node.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.VARIABLE);
            assertThat(node.getVariableName()).isEqualTo("amount");
            assertThat(node.getVariableValue()).isEqualTo(1500);
            assertThat(node.getExpression()).isEqualTo("amount");
            assertThat(node.getResult()).isEqualTo(1500);
        }

        @Test
        @DisplayName("literal() 创建 LITERAL 节点")
        void shouldCreateLiteralNode() {
            ExpressionTraceNode node = ExpressionTraceNode.literal(1000);

            assertThat(node.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.LITERAL);
            assertThat(node.getLiteralValue()).isEqualTo(1000);
            assertThat(node.getExpression()).isEqualTo("1000");
            assertThat(node.getResult()).isEqualTo(1000);
        }

        @Test
        @DisplayName("logical() 创建 LOGICAL 节点")
        void shouldCreateLogicalNode() {
            ExpressionTraceNode left = ExpressionTraceNode.variable("a", 1);
            ExpressionTraceNode right = ExpressionTraceNode.variable("b", 2);

            ExpressionTraceNode node = ExpressionTraceNode.logical("&&", true, left, right);

            assertThat(node.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.LOGICAL);
            assertThat(node.getOperator()).isEqualTo("&&");
            assertThat(node.getResult()).isEqualTo(true);
            assertThat(node.getChildren()).hasSize(2);
            assertThat(node.getExpression()).isEqualTo("a && b");
        }

        @Test
        @DisplayName("comparison() 创建 COMPARISON 节点")
        void shouldCreateComparisonNode() {
            ExpressionTraceNode node = ExpressionTraceNode.comparison(
                    ">", "amount", 1500, "1000", 1000, true);

            assertThat(node.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.COMPARISON);
            assertThat(node.getOperator()).isEqualTo(">");
            assertThat(node.getExpression()).isEqualTo("amount > 1000");
            assertThat(node.getResult()).isEqualTo(true);
            assertThat(node.getChildren()).hasSize(2);
            // 左子节点是 VARIABLE
            ExpressionTraceNode leftChild = node.getChildren().get(0);
            assertThat(leftChild.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.VARIABLE);
            assertThat(leftChild.getVariableName()).isEqualTo("amount");
            assertThat(leftChild.getVariableValue()).isEqualTo(1500);
            // 右子节点是 LITERAL
            ExpressionTraceNode rightChild = node.getChildren().get(1);
            assertThat(rightChild.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.LITERAL);
            assertThat(rightChild.getLiteralValue()).isEqualTo(1000);
        }

        @Test
        @DisplayName("builder 构建完整节点")
        void shouldBuildCompleteNodeWithBuilder() {
            ExpressionTraceNode node = ExpressionTraceNode.builder()
                    .nodeType(ExpressionTraceNode.NodeType.ROOT)
                    .expression("test expression")
                    .result(true)
                    .elapsedNanos(1000L)
                    .error("none")
                    .build();

            assertThat(node.getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.ROOT);
            assertThat(node.getExpression()).isEqualTo("test expression");
            assertThat(node.getResult()).isEqualTo(true);
            assertThat(node.getElapsedNanos()).isEqualTo(1000L);
            assertThat(node.getError()).isEqualTo("none");
        }

        @Test
        @DisplayName("children 列表默认空")
        void shouldHaveEmptyChildrenByDefault() {
            ExpressionTraceNode node = ExpressionTraceNode.builder()
                    .nodeType(ExpressionTraceNode.NodeType.ROOT)
                    .build();

            assertThat(node.getChildren()).isNotNull();
            assertThat(node.getChildren()).isEmpty();
        }
    }

    // ==================== RuleAdminService.traceExpression ====================

    @Nested
    @DisplayName("RuleAdminService.traceExpression")
    class TraceExpressionServiceTest {

        private RuleAdminService adminService;
        private ExpressionEvaluator mockEvaluator;

        @BeforeEach
        void setUp() {
            mockEvaluator = Mockito.mock(ExpressionEvaluator.class);
            RuleEngine mockRuleEngine = Mockito.mock(RuleEngine.class);
            RuleConfigProvider mockConfigProvider = Mockito.mock(RuleConfigProvider.class);
            RuleVersionRepository mockVersionRepo = Mockito.mock(RuleVersionRepository.class);
            ApplicationEventPublisher mockPublisher = Mockito.mock(ApplicationEventPublisher.class);
            adminService = new RuleAdminService(mockRuleEngine, mockEvaluator, mockConfigProvider,
                    mockVersionRepo, mockPublisher);
        }

        @Test
        @DisplayName("空表达式返回 ROOT 节点，result=false，不委托给 evaluator")
        void shouldReturnRootForEmptyExpression() {
            ExpressionEvaluator.TraceResult result = adminService.traceExpression("", null);

            assertThat(result.result()).isFalse();
            assertThat(result.traceTree().getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.ROOT);
            assertThat(result.traceTree().getError()).isEqualTo("表达式为空");
            verify(mockEvaluator, never()).evalBooleanWithTrace(any(), any());
        }

        @Test
        @DisplayName("正常表达式委托给 evaluator.evalBooleanWithTrace")
        void shouldDelegateToEvaluatorForValidExpression() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 1500);

            ExpressionTraceNode mockTraceTree = ExpressionTraceNode.builder()
                    .nodeType(ExpressionTraceNode.NodeType.COMPARISON)
                    .build();
            ExpressionEvaluator.TraceResult mockResult = new ExpressionEvaluator.TraceResult(true, mockTraceTree);
            when(mockEvaluator.evalBooleanWithTrace(eq("amount > 1000"), any())).thenReturn(mockResult);

            ExpressionEvaluator.TraceResult result = adminService.traceExpression("amount > 1000", facts);

            assertThat(result).isSameAs(mockResult);
            ArgumentCaptor<RuleContext> captor = ArgumentCaptor.forClass(RuleContext.class);
            verify(mockEvaluator).evalBooleanWithTrace(eq("amount > 1000"), captor.capture());
            assertThat(captor.getValue().getFacts()).containsEntry("amount", 1500);
        }

        @Test
        @DisplayName("facts 为 null 时使用空 Map 构建上下文")
        void shouldUseEmptyMapWhenFactsIsNull() {
            ExpressionTraceNode mockTraceTree = ExpressionTraceNode.builder()
                    .nodeType(ExpressionTraceNode.NodeType.ROOT)
                    .build();
            ExpressionEvaluator.TraceResult mockResult = new ExpressionEvaluator.TraceResult(false, mockTraceTree);
            when(mockEvaluator.evalBooleanWithTrace(any(), any())).thenReturn(mockResult);

            adminService.traceExpression("amount > 1000", null);

            ArgumentCaptor<RuleContext> captor = ArgumentCaptor.forClass(RuleContext.class);
            verify(mockEvaluator).evalBooleanWithTrace(eq("amount > 1000"), captor.capture());
            RuleContext context = captor.getValue();
            assertThat(context).isNotNull();
            assertThat(context.getFacts()).isEmpty();
        }

        @Test
        @DisplayName("表达式为 null 时返回错误节点，不委托给 evaluator")
        void shouldReturnErrorNodeForNullExpression() {
            ExpressionEvaluator.TraceResult result = adminService.traceExpression(null, null);

            assertThat(result.result()).isFalse();
            assertThat(result.traceTree().getNodeType()).isEqualTo(ExpressionTraceNode.NodeType.ROOT);
            assertThat(result.traceTree().getError()).isEqualTo("表达式为空");
            verify(mockEvaluator, never()).evalBooleanWithTrace(any(), any());
        }
    }
}
