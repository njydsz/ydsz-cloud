package com.njydsz.literule.domain.expression;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.njydsz.literule.domain.expression.ExpressionValidationResult.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link ExpressionValidationResult} 单元测试
 *
 * <p>验证静态工厂方法 {@link ExpressionValidationResult#ok(String, long, List)} 和
 * {@link ExpressionValidationResult#fail(String, ErrorType, String, long)} 的行为。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
@Tag("unit")
class ExpressionValidationResultTest {

  /** 测试用表达式 */
  private static final String TEST_EXPRESSION = "amount > 100";

  /** 测试用变量列表 */
  private static final List<String> TEST_VARIABLES = List.of("amount");

  @Test
  @DisplayName("ok 工厂方法应创建 isValid=true 的结果")
  void okFactoryShouldCreateValidResult() {
    ExpressionValidationResult result =
        ExpressionValidationResult.ok(TEST_EXPRESSION, 5L, TEST_VARIABLES);

    assertThat(result.isValid()).isTrue();
    assertThat(result.getErrorType()).isEqualTo(ErrorType.OK);
    assertThat(result.getExpression()).isEqualTo(TEST_EXPRESSION);
    assertThat(result.getParseTimeMs()).isEqualTo(5L);
    assertThat(result.getErrorLine()).isEqualTo(-1);
    assertThat(result.getErrorColumn()).isEqualTo(-1);
  }

  @Test
  @DisplayName("ok 工厂方法应将 expression 字段正确赋值")
  void okFactoryShouldSetExpression() {
    String expression = "price * quantity";
    ExpressionValidationResult result = ExpressionValidationResult.ok(expression, 1L, List.of());
    assertThat(result.getExpression()).isEqualTo(expression);
  }

  @Test
  @DisplayName("ok 工厂方法应保留 referencedVariables")
  void okFactoryShouldPreserveReferencedVariables() {
    List<String> variables = List.of("a", "b", "c");
    ExpressionValidationResult result =
        ExpressionValidationResult.ok("a + b + c", 1L, variables);
    assertThat(result.getReferencedVariables()).containsExactly("a", "b", "c");
  }

  @Test
  @DisplayName("ok 工厂方法传入 null 变量列表时应使用空列表")
  void okFactoryWithNullVariablesShouldUseEmptyList() {
    ExpressionValidationResult result =
        ExpressionValidationResult.ok(TEST_EXPRESSION, 1L, null);
    assertThat(result.getReferencedVariables()).isNotNull();
    assertThat(result.getReferencedVariables()).isEmpty();
  }

  @Test
  @DisplayName("ok 工厂方法的 errorMessage 应为 null")
  void okFactoryShouldHaveNullErrorMessage() {
    ExpressionValidationResult result =
        ExpressionValidationResult.ok(TEST_EXPRESSION, 1L, List.of());
    assertThat(result.getErrorMessage()).isNull();
  }

  @Test
  @DisplayName("fail 工厂方法应创建 isValid=false 的结果")
  void failFactoryShouldCreateInvalidResult() {
    ExpressionValidationResult result =
        ExpressionValidationResult.fail(
            TEST_EXPRESSION, ErrorType.SYNTAX_ERROR, "缺括号", 2L);

    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorType()).isEqualTo(ErrorType.SYNTAX_ERROR);
    assertThat(result.getErrorMessage()).isEqualTo("缺括号");
    assertThat(result.getExpression()).isEqualTo(TEST_EXPRESSION);
  }

  @Test
  @DisplayName("fail 工厂方法应将 errorLine 和 errorColumn 设为 -1")
  void failFactoryShouldSetErrorPositionToDefault() {
    ExpressionValidationResult result =
        ExpressionValidationResult.fail("err", ErrorType.UNKNOWN, "未知错误", 0L);
    assertThat(result.getErrorLine()).isEqualTo(-1);
    assertThat(result.getErrorColumn()).isEqualTo(-1);
  }

  @Test
  @DisplayName("ErrorType 枚举应包含全部 7 个值")
  void errorTypeShouldContainAllSevenValues() {
    assertThat(ErrorType.values())
        .containsExactlyInAnyOrder(
            ErrorType.OK,
            ErrorType.EMPTY,
            ErrorType.SYNTAX_ERROR,
            ErrorType.SANDBOX_VIOLATION,
            ErrorType.UNDEFINED_VARIABLE,
            ErrorType.TEMPLATE_FORMAT_ERROR,
            ErrorType.UNKNOWN);
  }

  @Test
  @DisplayName("Builder 模式应能构建完整对象")
  void builderShouldCreateCompleteObject() {
    ExpressionValidationResult result =
        ExpressionValidationResult.builder()
            .isValid(false)
            .errorType(ErrorType.EMPTY)
            .errorMessage("表达式为空")
            .expression("")
            .parseTimeMs(0L)
            .build();

    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorType()).isEqualTo(ErrorType.EMPTY);
    assertThat(result.getErrorMessage()).isEqualTo("表达式为空");
  }

  @Test
  @DisplayName("referencedVariables 字段默认值应为空列表")
  void referencedVariablesShouldDefaultToEmptyList() {
    ExpressionValidationResult result =
        ExpressionValidationResult.builder().build();
    assertThat(result.getReferencedVariables()).isNotNull();
    assertThat(result.getReferencedVariables()).isEmpty();
  }

  @Test
  @DisplayName("Setter 应能修改 isValid 状态")
  void setterShouldModifyIsValid() {
    ExpressionValidationResult result =
        ExpressionValidationResult.ok(TEST_EXPRESSION, 1L, List.of());

    result.setValid(false);
    assertThat(result.isValid()).isFalse();
  }
}
