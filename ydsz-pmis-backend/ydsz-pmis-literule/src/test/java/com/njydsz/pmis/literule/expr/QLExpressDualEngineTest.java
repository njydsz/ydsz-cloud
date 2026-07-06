package com.njydsz.pmis.literule.expr;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.config.LiteRuleAutoConfiguration;
import com.njydsz.pmis.literule.config.LiteRuleProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QLExpress 双引擎切换测试
 *
 * <p>验证：
 * <ul>
 *   <li>LiteRuleProperties.evaluator 默认值为 aviator</li>
 *   <li>配置 evaluator=qlexpress 时，LiteRuleAutoConfiguration 创建 QLExpressExpressionEvaluator</li>
 *   <li>配置 evaluator=aviator 时，创建 AviatorExpressionEvaluator</li>
 *   <li>QLExpress 引擎能正确执行布尔表达式</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@DisplayName("QLExpress 双引擎切换测试")
class QLExpressDualEngineTest {

    @Test
    @DisplayName("LiteRuleProperties.evaluator 默认值为 aviator")
    void evaluatorDefaultShouldBeAviator() {
        LiteRuleProperties props = new LiteRuleProperties();
        assertThat(props.getEvaluator()).isEqualTo("aviator");
    }

    @Test
    @DisplayName("配置 evaluator=aviator 时创建 AviatorExpressionEvaluator")
    void configAviatorShouldCreateAviatorEvaluator() {
        LiteRuleProperties props = new LiteRuleProperties();
        props.setEvaluator("aviator");
        LiteRuleAutoConfiguration config = new LiteRuleAutoConfiguration();
        ExpressionEvaluator evaluator = config.expressionEvaluator(props);
        assertThat(evaluator).isInstanceOf(AviatorExpressionEvaluator.class);
    }

    @Test
    @DisplayName("配置 evaluator=qlexpress 时创建 QLExpressExpressionEvaluator")
    void configQlExpressShouldCreateQlExpressEvaluator() {
        LiteRuleProperties props = new LiteRuleProperties();
        props.setEvaluator("qlexpress");
        LiteRuleAutoConfiguration config = new LiteRuleAutoConfiguration();
        ExpressionEvaluator evaluator = config.expressionEvaluator(props);
        assertThat(evaluator).isInstanceOf(QLExpressExpressionEvaluator.class);
    }

    @Test
    @DisplayName("配置 evaluator=QLEXPRESS（大小写不敏感）创建 QLExpressExpressionEvaluator")
    void configQlExpressCaseInsensitiveShouldCreateQlExpressEvaluator() {
        LiteRuleProperties props = new LiteRuleProperties();
        props.setEvaluator("QLEXPRESS");
        LiteRuleAutoConfiguration config = new LiteRuleAutoConfiguration();
        ExpressionEvaluator evaluator = config.expressionEvaluator(props);
        assertThat(evaluator).isInstanceOf(QLExpressExpressionEvaluator.class);
    }

    @Test
    @DisplayName("配置未知值时回退到 Aviator")
    void configUnknownShouldFallbackToAviator() {
        LiteRuleProperties props = new LiteRuleProperties();
        props.setEvaluator("unknown_engine");
        LiteRuleAutoConfiguration config = new LiteRuleAutoConfiguration();
        ExpressionEvaluator evaluator = config.expressionEvaluator(props);
        assertThat(evaluator).isInstanceOf(AviatorExpressionEvaluator.class);
    }

    @Test
    @DisplayName("配置 null 时回退到 Aviator")
    void configNullShouldFallbackToAviator() {
        LiteRuleProperties props = new LiteRuleProperties();
        props.setEvaluator(null);
        LiteRuleAutoConfiguration config = new LiteRuleAutoConfiguration();
        ExpressionEvaluator evaluator = config.expressionEvaluator(props);
        assertThat(evaluator).isInstanceOf(AviatorExpressionEvaluator.class);
    }

    @Test
    @DisplayName("QLExpress 引擎能执行布尔表达式 - 比较运算")
    void qlExpressShouldEvalComparisonExpression() {
        QLExpressExpressionEvaluator evaluator = new QLExpressExpressionEvaluator();
        RuleContext context = RuleContext.of(Map.of("amount", 200, "threshold", 100));
        boolean result = evaluator.evalBoolean("amount > threshold", context);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("QLExpress 引擎能执行布尔表达式 - 逻辑运算")
    void qlExpressShouldEvalLogicalExpression() {
        QLExpressExpressionEvaluator evaluator = new QLExpressExpressionEvaluator();
        RuleContext context = RuleContext.of(Map.of("a", 5, "b", 10));
        boolean result = evaluator.evalBoolean("a > 3 && b < 20", context);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("QLExpress 引擎能执行算术表达式并返回数值")
    void qlExpressShouldEvalArithmeticExpression() {
        QLExpressExpressionEvaluator evaluator = new QLExpressExpressionEvaluator();
        RuleContext context = RuleContext.of(Map.of("amount", 200));
        Number result = evaluator.eval("amount * 0.1", context);
        assertThat(result.doubleValue()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("QLExpress 引擎能校验表达式语法")
    void qlExpressShouldValidateExpression() {
        QLExpressExpressionEvaluator evaluator = new QLExpressExpressionEvaluator();
        // 使用字面量表达式避免变量未定义导致的校验失败
        assertThat(evaluator.validate("1 > 0")).isTrue();
        assertThat(evaluator.validate("")).isFalse();
        assertThat(evaluator.validate(null)).isFalse();
    }
}
