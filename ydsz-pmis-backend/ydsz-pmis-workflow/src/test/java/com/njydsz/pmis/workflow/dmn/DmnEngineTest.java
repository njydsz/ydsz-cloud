package com.njydsz.pmis.workflow.dmn;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DmnEngine 单元测试
 *
 * <p>覆盖 DMN 决策表执行引擎的 5 种命中策略（UNIQUE/FIRST/PRIORITY/ANY/COLLECT）、
 * 条件匹配（比较运算符、函数式、任意匹配）、输出值类型转换及 COLLECT 聚合运算。
 *
 * <p>DmnEngine 为无状态纯算法类，直接 new 实例即可，无需 Mockito。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>UNIQUE：单条命中返回；多条命中抛 IllegalStateException</li>
 *   <li>FIRST：多条命中返回第一条</li>
 *   <li>PRIORITY：多条命中返回优先级最高者（按规则定义顺序）</li>
 *   <li>ANY：多条命中返回任一条</li>
 *   <li>COLLECT：LIST/COUNT/SUM/MIN/MAX 聚合输出</li>
 *   <li>条件匹配：{@code >= <= != == > <}、{@code in/between/contains}、无运算符等于、{@code -}/空 任意匹配</li>
 *   <li>无命中返回空结果</li>
 *   <li>输出值解析：引号串、Long、Double、Boolean</li>
 *   <li>compare：数值优先 → 布尔 → 字符串</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
class DmnEngineTest {

    private final DmnEngine engine = new DmnEngine();

    // ============================== 辅助构造 ==============================

    /**
     * 构造决策表
     */
    private DmnDecisionTable buildTable(DmnHitPolicy policy, String collectOp,
                                        List<DmnInput> inputs, List<DmnOutput> outputs,
                                        List<DmnRule> rules) {
        DmnDecisionTable table = new DmnDecisionTable();
        table.setTableKey("test-table");
        table.setTableName("测试决策表");
        table.setHitPolicy(policy);
        table.setCollectOperator(collectOp);
        table.setInputs(inputs);
        table.setOutputs(outputs);
        table.setRules(rules);
        return table;
    }

    /**
     * 构造输入列，expression 为空时取值使用 name
     */
    private DmnInput input(String name, String expression) {
        DmnInput input = new DmnInput();
        input.setName(name);
        input.setExpression(expression);
        return input;
    }

    /**
     * 构造输出列
     */
    private DmnOutput output(String name) {
        DmnOutput output = new DmnOutput();
        output.setName(name);
        return output;
    }

    /**
     * 构造规则行
     */
    private DmnRule rule(List<String> inputEntries, List<String> outputEntries) {
        DmnRule rule = new DmnRule();
        rule.setInputEntries(inputEntries);
        rule.setOutputEntries(outputEntries);
        return rule;
    }

    // ============================== UNIQUE 命中策略 ==============================

    @Test
    @DisplayName("UNIQUE：单条规则命中时返回该规则结果")
    void uniqueShouldReturnSingleMatchWhenOneRuleMatches() {
        DmnInput amount = input("amount", null);
        DmnOutput level = output("level");
        DmnRule r1 = rule(List.of(">=100"), List.of("'高'"));
        DmnRule r2 = rule(List.of("<100"), List.of("'低'"));
        DmnDecisionTable table = buildTable(DmnHitPolicy.UNIQUE, null,
                List.of(amount), List.of(level), List.of(r1, r2));

        List<Map<String, Object>> result = engine.execute(table, Map.of("amount", 150));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("level", "高");
    }

    @Test
    @DisplayName("UNIQUE：多条规则命中时抛出 IllegalStateException")
    void uniqueShouldThrowWhenMultipleRulesMatch() {
        DmnInput amount = input("amount", null);
        DmnOutput level = output("level");
        DmnRule r1 = rule(List.of(">=100"), List.of("'高'"));
        DmnRule r2 = rule(List.of(">50"), List.of("'中'"));
        DmnDecisionTable table = buildTable(DmnHitPolicy.UNIQUE, null,
                List.of(amount), List.of(level), List.of(r1, r2));

        assertThatThrownBy(() -> engine.execute(table, Map.of("amount", 150)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UNIQUE");
    }

    // ============================== FIRST 命中策略 ==============================

    @Test
    @DisplayName("FIRST：多条规则命中时返回第一条")
    void firstShouldReturnFirstMatchWhenMultipleMatch() {
        DmnInput amount = input("amount", null);
        DmnOutput level = output("level");
        DmnRule r1 = rule(List.of(">=100"), List.of("'高'"));
        DmnRule r2 = rule(List.of(">50"), List.of("'中'"));
        DmnDecisionTable table = buildTable(DmnHitPolicy.FIRST, null,
                List.of(amount), List.of(level), List.of(r1, r2));

        List<Map<String, Object>> result = engine.execute(table, Map.of("amount", 150));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("level", "高");
    }

    // ============================== PRIORITY 命中策略 ==============================

    @Test
    @DisplayName("PRIORITY：多条命中时返回优先级最高者（按规则定义顺序）")
    void priorityShouldReturnHighestPriorityMatch() {
        DmnInput amount = input("amount", null);
        DmnOutput level = output("level");
        // 规则定义顺序即优先级：r1 优先级高于 r2
        DmnRule r1 = rule(List.of(">=100"), List.of("'高'"));
        DmnRule r2 = rule(List.of(">50"), List.of("'中'"));
        DmnDecisionTable table = buildTable(DmnHitPolicy.PRIORITY, null,
                List.of(amount), List.of(level), List.of(r1, r2));

        List<Map<String, Object>> result = engine.execute(table, Map.of("amount", 150));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("level", "高");
    }

    // ============================== ANY 命中策略 ==============================

    @Test
    @DisplayName("ANY：多条命中时返回任一条（首条命中）")
    void anyShouldReturnOneMatchWhenMultipleMatch() {
        DmnInput amount = input("amount", null);
        DmnOutput level = output("level");
        DmnRule r1 = rule(List.of(">=100"), List.of("'高'"));
        DmnRule r2 = rule(List.of(">50"), List.of("'中'"));
        DmnDecisionTable table = buildTable(DmnHitPolicy.ANY, null,
                List.of(amount), List.of(level), List.of(r1, r2));

        List<Map<String, Object>> result = engine.execute(table, Map.of("amount", 150));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsKey("level");
    }

    // ============================== COLLECT 命中策略 ==============================

    @Test
    @DisplayName("COLLECT(LIST)：聚合所有命中规则，保留顺序")
    void collectListShouldReturnAllMatches() {
        DmnInput amount = input("amount", null);
        DmnOutput level = output("level");
        DmnRule r1 = rule(List.of(">=100"), List.of("'高'"));
        DmnRule r2 = rule(List.of(">50"), List.of("'中'"));
        DmnRule r3 = rule(List.of("<10"), List.of("'低'"));
        DmnDecisionTable table = buildTable(DmnHitPolicy.COLLECT, "LIST",
                List.of(amount), List.of(level), List.of(r1, r2, r3));

        List<Map<String, Object>> result = engine.execute(table, Map.of("amount", 150));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(m -> m.get("level"))
                .containsExactly("高", "中");
    }

    @Test
    @DisplayName("COLLECT(默认 LIST)：collectOperator 为空时等价于 LIST")
    void collectDefaultShouldBeList() {
        DmnInput amount = input("amount", null);
        DmnOutput level = output("level");
        DmnRule r1 = rule(List.of(">=100"), List.of("'高'"));
        DmnRule r2 = rule(List.of(">50"), List.of("'中'"));
        DmnDecisionTable table = buildTable(DmnHitPolicy.COLLECT, null,
                List.of(amount), List.of(level), List.of(r1, r2));

        List<Map<String, Object>> result = engine.execute(table, Map.of("amount", 150));

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("COLLECT(COUNT)：返回命中数量")
    void collectCountShouldReturnCount() {
        DmnInput amount = input("amount", null);
        DmnOutput level = output("level");
        DmnRule r1 = rule(List.of(">=100"), List.of("'高'"));
        DmnRule r2 = rule(List.of(">50"), List.of("'中'"));
        DmnDecisionTable table = buildTable(DmnHitPolicy.COLLECT, "COUNT",
                List.of(amount), List.of(level), List.of(r1, r2));

        List<Map<String, Object>> result = engine.execute(table, Map.of("amount", 150));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("count", 2);
    }

    @Test
    @DisplayName("COLLECT(SUM)：对所有命中行的数值列求和")
    void collectSumShouldAggregate() {
        DmnInput amount = input("amount", null);
        DmnOutput score = output("score");
        DmnRule r1 = rule(List.of(">=100"), List.of("10"));
        DmnRule r2 = rule(List.of(">50"), List.of("20"));
        DmnDecisionTable table = buildTable(DmnHitPolicy.COLLECT, "SUM",
                List.of(amount), List.of(score), List.of(r1, r2));

        List<Map<String, Object>> result = engine.execute(table, Map.of("amount", 150));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("score", 30.0);
    }

    @Test
    @DisplayName("COLLECT(MIN)：返回所有命中行数值列的最小值")
    void collectMinShouldAggregate() {
        DmnInput amount = input("amount", null);
        DmnOutput score = output("score");
        DmnRule r1 = rule(List.of(">=100"), List.of("10"));
        DmnRule r2 = rule(List.of(">50"), List.of("20"));
        DmnDecisionTable table = buildTable(DmnHitPolicy.COLLECT, "MIN",
                List.of(amount), List.of(score), List.of(r1, r2));

        List<Map<String, Object>> result = engine.execute(table, Map.of("amount", 150));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("score", 10.0);
    }

    @Test
    @DisplayName("COLLECT(MAX)：返回所有命中行数值列的最大值")
    void collectMaxShouldAggregate() {
        DmnInput amount = input("amount", null);
        DmnOutput score = output("score");
        DmnRule r1 = rule(List.of(">=100"), List.of("10"));
        DmnRule r2 = rule(List.of(">50"), List.of("20"));
        DmnDecisionTable table = buildTable(DmnHitPolicy.COLLECT, "MAX",
                List.of(amount), List.of(score), List.of(r1, r2));

        List<Map<String, Object>> result = engine.execute(table, Map.of("amount", 150));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("score", 20.0);
    }

    @Test
    @DisplayName("COLLECT：无命中时返回空列表")
    void collectShouldReturnEmptyWhenNoMatch() {
        DmnInput amount = input("amount", null);
        DmnOutput level = output("level");
        DmnRule r1 = rule(List.of(">=100"), List.of("'高'"));
        DmnDecisionTable table = buildTable(DmnHitPolicy.COLLECT, "SUM",
                List.of(amount), List.of(level), List.of(r1));

        List<Map<String, Object>> result = engine.execute(table, Map.of("amount", 5));

        assertThat(result).isEmpty();
    }

    // ============================== 条件匹配：比较运算符 ==============================

    @Test
    @DisplayName("条件匹配：支持 >= <= != == > < 比较运算符")
    void shouldMatchComparisonOperators() {
        DmnInput n = input("n", null);
        DmnOutput r = output("r");

        // n=100
        // >=100 命中
        assertThat(engine.execute(
                buildTable(DmnHitPolicy.UNIQUE, null, List.of(n), List.of(r),
                        List.of(rule(List.of(">=100"), List.of("'命中'")))),
                Map.of("n", 100))).hasSize(1);

        // <=100 命中
        assertThat(engine.execute(
                buildTable(DmnHitPolicy.UNIQUE, null, List.of(n), List.of(r),
                        List.of(rule(List.of("<=100"), List.of("'命中'")))),
                Map.of("n", 100))).hasSize(1);

        // !=100 不命中
        assertThat(engine.execute(
                buildTable(DmnHitPolicy.UNIQUE, null, List.of(n), List.of(r),
                        List.of(rule(List.of("!=100"), List.of("'命中'")))),
                Map.of("n", 100))).isEmpty();

        // ==100 命中
        assertThat(engine.execute(
                buildTable(DmnHitPolicy.UNIQUE, null, List.of(n), List.of(r),
                        List.of(rule(List.of("==100"), List.of("'命中'")))),
                Map.of("n", 100))).hasSize(1);

        // >100 不命中
        assertThat(engine.execute(
                buildTable(DmnHitPolicy.UNIQUE, null, List.of(n), List.of(r),
                        List.of(rule(List.of(">100"), List.of("'命中'")))),
                Map.of("n", 100))).isEmpty();

        // <100 不命中
        assertThat(engine.execute(
                buildTable(DmnHitPolicy.UNIQUE, null, List.of(n), List.of(r),
                        List.of(rule(List.of("<100"), List.of("'命中'")))),
                Map.of("n", 100))).isEmpty();
    }

    // ============================== 条件匹配：函数式 ==============================

    @Test
    @DisplayName("条件匹配：支持 in(...) between(...) contains(...) 函数式条件")
    void shouldMatchFunctionConditions() {
        DmnInput n = input("n", null);
        DmnInput name = input("name", null);
        DmnOutput r = output("r");

        // in(1,2,3)
        DmnDecisionTable inTable = buildTable(DmnHitPolicy.UNIQUE, null,
                List.of(n), List.of(r), List.of(rule(List.of("in(1,2,3)"), List.of("'命中'"))));
        assertThat(engine.execute(inTable, Map.of("n", 2))).hasSize(1);
        assertThat(engine.execute(inTable, Map.of("n", 5))).isEmpty();

        // between(1,100) 闭区间
        DmnDecisionTable betweenTable = buildTable(DmnHitPolicy.UNIQUE, null,
                List.of(n), List.of(r), List.of(rule(List.of("between(1,100)"), List.of("'命中'"))));
        assertThat(engine.execute(betweenTable, Map.of("n", 1))).hasSize(1);
        assertThat(engine.execute(betweenTable, Map.of("n", 100))).hasSize(1);
        assertThat(engine.execute(betweenTable, Map.of("n", 101))).isEmpty();

        // contains(紧) 子串匹配
        DmnDecisionTable containsTable = buildTable(DmnHitPolicy.UNIQUE, null,
                List.of(name), List.of(r), List.of(rule(List.of("contains(紧)"), List.of("'命中'"))));
        assertThat(engine.execute(containsTable, Map.of("name", "紧急"))).hasSize(1);
        assertThat(engine.execute(containsTable, Map.of("name", "普通"))).isEmpty();
    }

    // ============================== 条件匹配：无运算符等于 / 任意匹配 ==============================

    @Test
    @DisplayName("条件匹配：无运算符等于；'-' 或空为任意匹配")
    void shouldMatchNoOperatorAndAny() {
        DmnInput n = input("n", null);
        DmnOutput r = output("r");

        // 无运算符等于 100
        DmnDecisionTable eqTable = buildTable(DmnHitPolicy.UNIQUE, null,
                List.of(n), List.of(r), List.of(rule(List.of("100"), List.of("'命中'"))));
        assertThat(engine.execute(eqTable, Map.of("n", 100))).hasSize(1);
        assertThat(engine.execute(eqTable, Map.of("n", 99))).isEmpty();

        // "-" 任意匹配
        DmnDecisionTable dashTable = buildTable(DmnHitPolicy.UNIQUE, null,
                List.of(n), List.of(r), List.of(rule(List.of("-"), List.of("'命中'"))));
        assertThat(engine.execute(dashTable, Map.of("n", 999))).hasSize(1);

        // 空字符串任意匹配
        DmnDecisionTable emptyTable = buildTable(DmnHitPolicy.UNIQUE, null,
                List.of(n), List.of(r), List.of(rule(List.of(""), List.of("'命中'"))));
        assertThat(engine.execute(emptyTable, Map.of("n", 999))).hasSize(1);
    }

    // ============================== 无命中 ==============================

    @Test
    @DisplayName("无命中规则时返回空结果且不抛异常")
    void shouldReturnEmptyWhenNoRuleMatches() {
        DmnInput amount = input("amount", null);
        DmnOutput level = output("level");
        DmnRule r1 = rule(List.of(">1000"), List.of("'高'"));
        DmnDecisionTable table = buildTable(DmnHitPolicy.UNIQUE, null,
                List.of(amount), List.of(level), List.of(r1));

        List<Map<String, Object>> result = engine.execute(table, Map.of("amount", 50));

        assertThat(result).isEmpty();
    }

    // ============================== 输出值类型转换 ==============================

    @Test
    @DisplayName("输出值解析：支持引号串、Long、Double、Boolean")
    void shouldParseOutputValueTypes() {
        DmnInput n = input("n", null);
        DmnOutput str = output("str");
        DmnOutput lng = output("lng");
        DmnOutput dbl = output("dbl");
        DmnOutput bool = output("bool");
        DmnRule r = rule(List.of("-"), List.of("'通过'", "100", "3.14", "true"));
        DmnDecisionTable table = buildTable(DmnHitPolicy.UNIQUE, null,
                List.of(n), List.of(str, lng, dbl, bool), List.of(r));

        List<Map<String, Object>> result = engine.execute(table, Map.of("n", 1));

        assertThat(result).hasSize(1);
        Map<String, Object> out = result.get(0);
        assertThat(out.get("str")).isEqualTo("通过");
        assertThat(out.get("lng")).isInstanceOf(Long.class).isEqualTo(100L);
        assertThat(out.get("dbl")).isInstanceOf(Double.class).isEqualTo(3.14);
        assertThat(out.get("bool")).isInstanceOf(Boolean.class).isEqualTo(true);
    }

    @Test
    @DisplayName("输出值解析：false 解析为 Boolean.FALSE")
    void shouldParseFalseBoolean() {
        DmnInput n = input("n", null);
        DmnOutput flag = output("flag");
        DmnRule r = rule(List.of("-"), List.of("false"));
        DmnDecisionTable table = buildTable(DmnHitPolicy.UNIQUE, null,
                List.of(n), List.of(flag), List.of(r));

        List<Map<String, Object>> result = engine.execute(table, Map.of("n", 1));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("flag")).isInstanceOf(Boolean.class).isEqualTo(false);
    }

    @Test
    @DisplayName("输出值解析：'-' 或空解析为 null")
    void shouldParseDashAsNull() {
        DmnInput n = input("n", null);
        DmnOutput v = output("v");
        DmnRule r1 = rule(List.of("-"), List.of("-"));
        DmnRule r2 = rule(List.of("-"), List.of(""));
        DmnDecisionTable table = buildTable(DmnHitPolicy.COLLECT, "LIST",
                List.of(n), List.of(v), List.of(r1, r2));

        List<Map<String, Object>> result = engine.execute(table, Map.of("n", 1));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("v")).isNull();
        assertThat(result.get(1).get("v")).isNull();
    }

    // ============================== compare 比较（通过 matchInput 间接覆盖）==============================

    @Test
    @DisplayName("compare：双方均可解析为数值时按数值比较（actual 为字符串数字也参与数值比较）")
    void shouldCompareByNumberFirst() {
        DmnInput n = input("n", null);
        DmnOutput r = output("r");
        // actual="100"(String) 与条件 "100" 均可解析为数值，按数值比较相等
        DmnDecisionTable table = buildTable(DmnHitPolicy.UNIQUE, null,
                List.of(n), List.of(r), List.of(rule(List.of("100"), List.of("'命中'"))));

        assertThat(engine.execute(table, Map.of("n", "100"))).hasSize(1);
        assertThat(engine.execute(table, Map.of("n", "101"))).isEmpty();
    }

    @Test
    @DisplayName("compare：非数值时按布尔比较")
    void shouldCompareByBoolean() {
        DmnInput b = input("b", null);
        DmnOutput r = output("r");
        DmnDecisionTable table = buildTable(DmnHitPolicy.UNIQUE, null,
                List.of(b), List.of(r), List.of(rule(List.of("==true"), List.of("'命中'"))));

        assertThat(engine.execute(table, Map.of("b", true))).hasSize(1);
        assertThat(engine.execute(table, Map.of("b", false))).isEmpty();
    }

    @Test
    @DisplayName("compare：非数值非布尔时按字符串比较")
    void shouldCompareByString() {
        DmnInput s = input("s", null);
        DmnOutput r = output("r");
        DmnDecisionTable table = buildTable(DmnHitPolicy.UNIQUE, null,
                List.of(s), List.of(r), List.of(rule(List.of("'紧急'"), List.of("'命中'"))));

        assertThat(engine.execute(table, Map.of("s", "紧急"))).hasSize(1);
        assertThat(engine.execute(table, Map.of("s", "普通"))).isEmpty();
    }

    // ============================== 输入取值 ==============================

    @Test
    @DisplayName("输入取值优先使用 expression，为空则使用 name")
    void shouldUseExpressionAsContextKeyWhenPresent() {
        DmnInput amount = input("amount", "exprAmount");
        DmnOutput level = output("level");
        DmnRule r = rule(List.of(">=100"), List.of("'高'"));
        DmnDecisionTable table = buildTable(DmnHitPolicy.UNIQUE, null,
                List.of(amount), List.of(level), List.of(r));

        // 用 expression 作为 key 命中
        assertThat(engine.execute(table, Map.of("exprAmount", 150))).hasSize(1);
        // 仅用 name 作为 key 不命中（expression 非空时忽略 name）
        assertThat(engine.execute(table, Map.of("amount", 150))).isEmpty();
    }

    @Test
    @DisplayName("输入值为 null 时该条件不匹配")
    void shouldNotMatchWhenInputValueIsNull() {
        DmnInput amount = input("amount", null);
        DmnOutput level = output("level");
        DmnRule r = rule(List.of(">=100"), List.of("'高'"));
        DmnDecisionTable table = buildTable(DmnHitPolicy.UNIQUE, null,
                List.of(amount), List.of(level), List.of(r));

        // context 中无 amount 键，取值为 null
        assertThat(engine.execute(table, Map.of())).isEmpty();
    }

    // ============================== 边界场景 ==============================

    @Test
    @DisplayName("table 为 null 时返回空列表")
    void shouldReturnEmptyWhenTableIsNull() {
        assertThat(engine.execute(null, Map.of())).isEmpty();
    }

    @Test
    @DisplayName("context 为 null 时按空上下文处理")
    void shouldHandleNullContext() {
        DmnInput amount = input("amount", null);
        DmnOutput level = output("level");
        // "-" 任意匹配，context 为 null 也能命中
        DmnRule r = rule(List.of("-"), List.of("'高'"));
        DmnDecisionTable table = buildTable(DmnHitPolicy.UNIQUE, null,
                List.of(amount), List.of(level), List.of(r));

        assertThat(engine.execute(table, null)).hasSize(1);

        // 带条件的规则在 context 为 null 时不命中
        DmnRule r2 = rule(List.of(">=100"), List.of("'高'"));
        DmnDecisionTable table2 = buildTable(DmnHitPolicy.UNIQUE, null,
                List.of(amount), List.of(level), List.of(r2));
        assertThat(engine.execute(table2, null)).isEmpty();
    }

    @Test
    @DisplayName("rules 为 null 或空时返回空列表")
    void shouldReturnEmptyWhenRulesIsNullOrEmpty() {
        DmnInput amount = input("amount", null);
        DmnOutput level = output("level");
        DmnDecisionTable table = new DmnDecisionTable();
        table.setTableKey("empty-table");
        table.setHitPolicy(DmnHitPolicy.UNIQUE);
        table.setInputs(List.of(amount));
        table.setOutputs(List.of(level));

        table.setRules(null);
        assertThat(engine.execute(table, Map.of("amount", 1))).isEmpty();

        table.setRules(List.of());
        assertThat(engine.execute(table, Map.of("amount", 1))).isEmpty();
    }

    @Test
    @DisplayName("hitPolicy 为 null 时默认按 UNIQUE 处理")
    void shouldDefaultToUniqueWhenHitPolicyIsNull() {
        DmnInput amount = input("amount", null);
        DmnOutput level = output("level");
        DmnRule r1 = rule(List.of(">=100"), List.of("'高'"));
        DmnRule r2 = rule(List.of(">50"), List.of("'中'"));
        DmnDecisionTable table = buildTable(null, null,
                List.of(amount), List.of(level), List.of(r1, r2));

        // 默认 UNIQUE，多条命中应抛异常
        assertThatThrownBy(() -> engine.execute(table, Map.of("amount", 150)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UNIQUE");
    }

    @Test
    @DisplayName("决策表无输入列时所有规则均命中")
    void shouldMatchAllWhenInputsIsEmpty() {
        DmnOutput level = output("level");
        DmnRule r1 = rule(List.of(), List.of("'高'"));
        DmnRule r2 = rule(List.of(), List.of("'中'"));
        DmnDecisionTable table = buildTable(DmnHitPolicy.COLLECT, "LIST",
                List.of(), List.of(level), List.of(r1, r2));

        List<Map<String, Object>> result = engine.execute(table, Map.of());

        assertThat(result).hasSize(2);
    }
}
