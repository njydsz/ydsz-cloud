package com.njydsz.pmis.workflow.dmn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DmnEngine} 单元测试。
 *
 * <p>P2-10: 重点覆盖修复后的 PRIORITY 命中策略与新增的 RULE_ORDER / OUTPUT_ORDER 策略。
 *
 * <p>测试场景基于"风险等级决策表"：
 * <ul>
 *   <li>输入：amount（金额）</li>
 *   <li>输出：level（风险等级），allowedValues = ["高", "中", "低"]</li>
 *   <li>规则：
 *     <ul>
 *       <li>Rule A: amount &gt;= 100 → level = "低"</li>
 *       <li>Rule B: amount &gt;= 1000 → level = "中"</li>
 *       <li>Rule C: amount &gt;= 10000 → level = "高"</li>
 *     </ul>
 *     当 amount = 20000 时，A/B/C 三条规则全部命中，用于区分不同命中策略的返回行为：
 *     <ul>
 *       <li>PRIORITY → 返回 "高"（allowedValues 中 "高" 排第一）</li>
 *       <li>OUTPUT_ORDER → 返回 ["高", "中", "低"]（按 allowedValues 排序）</li>
 *       <li>RULE_ORDER → 返回 ["低", "中", "高"]（按规则定义顺序）</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@DisplayName("P2-10: DmnEngine 命中策略")
class DmnEngineTest {

    private DmnEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DmnEngine();
    }

    // ============== 辅助方法：构建风险等级决策表 ==============

    /**
     * 构建风险等级决策表。
     *
     * @param hitPolicy 命中策略
     * @param allowedValues 输出列 allowedValues（可空表示不定义优先级）
     */
    private DmnDecisionTable buildRiskTable(DmnHitPolicy hitPolicy, List<String> allowedValues) {
        DmnDecisionTable table = new DmnDecisionTable();
        table.setTableKey("risk_level");
        table.setTableName("风险等级决策表");
        table.setHitPolicy(hitPolicy);

        // 输入列：amount
        DmnInput input = new DmnInput();
        input.setName("amount");
        input.setLabel("金额");
        input.setType("NUMBER");
        table.setInputs(List.of(input));

        // 输出列：level（带 allowedValues 定义优先级）
        DmnOutput output = new DmnOutput();
        output.setName("level");
        output.setLabel("风险等级");
        output.setType("STRING");
        output.setAllowedValues(allowedValues);
        table.setOutputs(List.of(output));

        // 规则（按 A/B/C 定义顺序）
        DmnRule ruleA = new DmnRule();
        ruleA.setInputEntries(List.of(">= 100"));
        ruleA.setOutputEntries(List.of("'低'"));

        DmnRule ruleB = new DmnRule();
        ruleB.setInputEntries(List.of(">= 1000"));
        ruleB.setOutputEntries(List.of("'中'"));

        DmnRule ruleC = new DmnRule();
        ruleC.setInputEntries(List.of(">= 10000"));
        ruleC.setOutputEntries(List.of("'高'"));

        table.setRules(List.of(ruleA, ruleB, ruleC));
        return table;
    }

    private Map<String, Object> ctx(int amount) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("amount", amount);
        return map;
    }

    // ============== UNIQUE ==============

    @Nested
    @DisplayName("UNIQUE 命中策略")
    class UniqueHitPolicyTest {

        @Test
        @DisplayName("单条命中 → 返回 1 条")
        void singleMatchReturnsOne() {
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.UNIQUE, null);
            // amount=50 → 无规则命中（最低阈值 100）
            // amount=500 → 仅 Rule A 命中（>=100 但 <1000）
            List<Map<String, Object>> result = engine.execute(table, ctx(500));
            assertEquals(1, result.size());
            assertEquals("低", result.get(0).get("level"));
        }

        @Test
        @DisplayName("多条命中 → 抛 IllegalStateException")
        void multipleMatchesThrows() {
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.UNIQUE, null);
            // amount=20000 → A/B/C 三条全命中
            assertThrows(IllegalStateException.class,
                    () -> engine.execute(table, ctx(20000)));
        }

        @Test
        @DisplayName("无命中 → 返回空列表")
        void noMatchReturnsEmpty() {
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.UNIQUE, null);
            List<Map<String, Object>> result = engine.execute(table, ctx(50));
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // ============== FIRST ==============

    @Nested
    @DisplayName("FIRST 命中策略")
    class FirstHitPolicyTest {

        @Test
        @DisplayName("多条命中 → 返回第一条（按定义顺序，即 Rule A = 低）")
        void multipleMatchesReturnsFirst() {
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.FIRST, null);
            List<Map<String, Object>> result = engine.execute(table, ctx(20000));
            assertEquals(1, result.size());
            // FIRST 按定义顺序，Rule A（"低"）先命中
            assertEquals("低", result.get(0).get("level"));
        }

        @Test
        @DisplayName("无命中 → 返回空列表")
        void noMatchReturnsEmpty() {
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.FIRST, null);
            List<Map<String, Object>> result = engine.execute(table, ctx(50));
            assertTrue(result.isEmpty());
        }
    }

    // ============== PRIORITY (P2-10 修复重点) ==============

    @Nested
    @DisplayName("PRIORITY 命中策略（P2-10 修复）")
    class PriorityHitPolicyTest {

        @Test
        @DisplayName("多条命中 → 按 allowedValues 优先级返回第一条（高 > 中 > 低）")
        void multipleMatchesReturnsByPriority() {
            // allowedValues = ["高", "中", "低"] → "高" 优先级最高
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.PRIORITY,
                    List.of("高", "中", "低"));
            // amount=20000 → A/B/C 三条全命中，输出值分别为 低/中/高
            List<Map<String, Object>> result = engine.execute(table, ctx(20000));
            assertEquals(1, result.size());
            // PRIORITY 应返回 "高"（allowedValues 中排名第一），而非 FIRST 的 "低"
            assertEquals("高", result.get(0).get("level"),
                    "PRIORITY 应按 allowedValues 优先级返回，而非定义顺序");
        }

        @Test
        @DisplayName("单条命中 → 返回该条（无论优先级）")
        void singleMatchReturnsIt() {
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.PRIORITY,
                    List.of("高", "中", "低"));
            // amount=500 → 仅 Rule A 命中（"低"）
            List<Map<String, Object>> result = engine.execute(table, ctx(500));
            assertEquals(1, result.size());
            assertEquals("低", result.get(0).get("level"));
        }

        @Test
        @DisplayName("allowedValues 未定义 → 回退为命中顺序（第一条 = 低）")
        void noAllowedValuesFallsBackToFirst() {
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.PRIORITY, null);
            List<Map<String, Object>> result = engine.execute(table, ctx(20000));
            assertEquals(1, result.size());
            // 无 allowedValues 时回退为命中顺序，返回 Rule A（"低"）
            assertEquals("低", result.get(0).get("level"));
        }

        @Test
        @DisplayName("输出值不在 allowedValues 中 → 视为最低优先级")
        void valueNotInAllowedValuesTreatedAsLowest() {
            // allowedValues = ["高", "中"]，"低" 不在列表中 → 最低优先级
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.PRIORITY,
                    List.of("高", "中"));
            // amount=20000 → 命中 低/中/高
            // "中" 在 allowedValues 中索引 1，"高" 索引 0，"低" 不在列表（MAX_VALUE）
            // 排序后：高(0) < 中(1) < 低(MAX) → 返回 "高"
            List<Map<String, Object>> result = engine.execute(table, ctx(20000));
            assertEquals(1, result.size());
            assertEquals("高", result.get(0).get("level"));
        }

        @Test
        @DisplayName("无命中 → 返回空列表")
        void noMatchReturnsEmpty() {
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.PRIORITY,
                    List.of("高", "中", "低"));
            List<Map<String, Object>> result = engine.execute(table, ctx(50));
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("allowedValues 顺序反转（低>中>高）→ 返回 低")
        void reversedAllowedValues() {
            // allowedValues = ["低", "中", "高"] → "低" 优先级最高
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.PRIORITY,
                    List.of("低", "中", "高"));
            List<Map<String, Object>> result = engine.execute(table, ctx(20000));
            assertEquals(1, result.size());
            assertEquals("低", result.get(0).get("level"),
                    "allowedValues 顺序反转后，低 优先级最高");
        }
    }

    // ============== ANY ==============

    @Nested
    @DisplayName("ANY 命中策略")
    class AnyHitPolicyTest {

        @Test
        @DisplayName("多条命中 → 返回第一条（短路，与 FIRST 行为一致）")
        void multipleMatchesReturnsFirst() {
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.ANY, null);
            List<Map<String, Object>> result = engine.execute(table, ctx(20000));
            assertEquals(1, result.size());
            assertEquals("低", result.get(0).get("level"));
        }
    }

    // ============== COLLECT ==============

    @Nested
    @DisplayName("COLLECT 命中策略")
    class CollectHitPolicyTest {

        @Test
        @DisplayName("LIST 聚合 → 返回所有命中行（每行一个输出值，按定义顺序）")
        void collectList() {
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.COLLECT, null);
            table.setCollectOperator("LIST");
            List<Map<String, Object>> result = engine.execute(table, ctx(20000));
            // LIST 直接返回所有命中行，不做聚合
            assertEquals(3, result.size());
            // 按定义顺序：低/中/高
            assertEquals("低", result.get(0).get("level"));
            assertEquals("中", result.get(1).get("level"));
            assertEquals("高", result.get(2).get("level"));
        }

        @Test
        @DisplayName("无命中 → 返回空列表")
        void noMatchReturnsEmpty() {
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.COLLECT, null);
            table.setCollectOperator("LIST");
            List<Map<String, Object>> result = engine.execute(table, ctx(50));
            assertTrue(result.isEmpty());
        }
    }

    // ============== RULE_ORDER (P2-10 新增) ==============

    @Nested
    @DisplayName("RULE_ORDER 命中策略（P2-10 新增）")
    class RuleOrderHitPolicyTest {

        @Test
        @DisplayName("多条命中 → 返回所有命中行（按规则定义顺序：低/中/高）")
        void multipleMatchesReturnsAllInDefinitionOrder() {
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.RULE_ORDER, null);
            List<Map<String, Object>> result = engine.execute(table, ctx(20000));
            assertEquals(3, result.size());
            // 按定义顺序：Rule A(低) → Rule B(中) → Rule C(高)
            assertEquals("低", result.get(0).get("level"));
            assertEquals("中", result.get(1).get("level"));
            assertEquals("高", result.get(2).get("level"));
        }

        @Test
        @DisplayName("单条命中 → 返回 1 条")
        void singleMatchReturnsOne() {
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.RULE_ORDER, null);
            List<Map<String, Object>> result = engine.execute(table, ctx(500));
            assertEquals(1, result.size());
            assertEquals("低", result.get(0).get("level"));
        }

        @Test
        @DisplayName("无命中 → 返回空列表")
        void noMatchReturnsEmpty() {
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.RULE_ORDER, null);
            List<Map<String, Object>> result = engine.execute(table, ctx(50));
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("RULE_ORDER 不受 allowedValues 影响（仍按定义顺序）")
        void ruleOrderIgnoresAllowedValues() {
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.RULE_ORDER,
                    List.of("高", "中", "低"));
            List<Map<String, Object>> result = engine.execute(table, ctx(20000));
            assertEquals(3, result.size());
            // 即使定义了 allowedValues，RULE_ORDER 仍按定义顺序
            assertEquals("低", result.get(0).get("level"));
            assertEquals("高", result.get(2).get("level"));
        }
    }

    // ============== OUTPUT_ORDER (P2-10 新增) ==============

    @Nested
    @DisplayName("OUTPUT_ORDER 命中策略（P2-10 新增）")
    class OutputOrderHitPolicyTest {

        @Test
        @DisplayName("多条命中 → 按 allowedValues 排序返回所有（高/中/低）")
        void multipleMatchesReturnsAllSortedByPriority() {
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.OUTPUT_ORDER,
                    List.of("高", "中", "低"));
            List<Map<String, Object>> result = engine.execute(table, ctx(20000));
            assertEquals(3, result.size());
            // 按 allowedValues 排序：高(0) → 中(1) → 低(2)
            assertEquals("高", result.get(0).get("level"));
            assertEquals("中", result.get(1).get("level"));
            assertEquals("低", result.get(2).get("level"));
        }

        @Test
        @DisplayName("allowedValues 未定义 → 回退为命中顺序（低/中/高）")
        void noAllowedValuesFallsBackToDefinitionOrder() {
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.OUTPUT_ORDER, null);
            List<Map<String, Object>> result = engine.execute(table, ctx(20000));
            assertEquals(3, result.size());
            // 无 allowedValues 时回退为命中顺序
            assertEquals("低", result.get(0).get("level"));
            assertEquals("中", result.get(1).get("level"));
            assertEquals("高", result.get(2).get("level"));
        }

        @Test
        @DisplayName("allowedValues 顺序反转 → 返回 低/中/高")
        void reversedAllowedValues() {
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.OUTPUT_ORDER,
                    List.of("低", "中", "高"));
            List<Map<String, Object>> result = engine.execute(table, ctx(20000));
            assertEquals(3, result.size());
            assertEquals("低", result.get(0).get("level"));
            assertEquals("高", result.get(2).get("level"));
        }

        @Test
        @DisplayName("无命中 → 返回空列表")
        void noMatchReturnsEmpty() {
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.OUTPUT_ORDER,
                    List.of("高", "中", "低"));
            List<Map<String, Object>> result = engine.execute(table, ctx(50));
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("输出值不在 allowedValues 中 → 排在最后")
        void valueNotInAllowedValuesSortedLast() {
            // allowedValues = ["高", "中"]，"低" 不在列表中 → 排在最后
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.OUTPUT_ORDER,
                    List.of("高", "中"));
            List<Map<String, Object>> result = engine.execute(table, ctx(20000));
            assertEquals(3, result.size());
            // 排序：高(0) → 中(1) → 低(MAX_VALUE)
            assertEquals("高", result.get(0).get("level"));
            assertEquals("中", result.get(1).get("level"));
            assertEquals("低", result.get(2).get("level"));
        }
    }

    // ============== 边界场景 ==============

    @Nested
    @DisplayName("边界场景")
    class EdgeCaseTest {

        @Test
        @DisplayName("table 为 null → 返回空列表")
        void nullTableReturnsEmpty() {
            List<Map<String, Object>> result = engine.execute(null, Map.of("amount", 100));
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("rules 为空 → 返回空列表")
        void emptyRulesReturnsEmpty() {
            DmnDecisionTable table = new DmnDecisionTable();
            table.setTableKey("empty");
            table.setHitPolicy(DmnHitPolicy.FIRST);
            table.setRules(List.of());
            List<Map<String, Object>> result = engine.execute(table, Map.of("amount", 100));
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("rules 为 null → 返回空列表")
        void nullRulesReturnsEmpty() {
            DmnDecisionTable table = new DmnDecisionTable();
            table.setTableKey("null_rules");
            table.setHitPolicy(DmnHitPolicy.FIRST);
            table.setRules(null);
            List<Map<String, Object>> result = engine.execute(table, Map.of("amount", 100));
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("context 为 null → 无命中返回空列表（不抛 NPE）")
        void nullContextReturnsEmpty() {
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.FIRST, null);
            List<Map<String, Object>> result = engine.execute(table, null);
            // context 为 null 时，所有规则因 actual=null 不命中
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("hitPolicy 为 null → 默认 UNIQUE 语义")
        void nullHitPolicyDefaultsToUnique() {
            DmnDecisionTable table = buildRiskTable(null, null);
            // amount=500 → 单条命中，UNIQUE 语义不报错
            List<Map<String, Object>> result = engine.execute(table, ctx(500));
            assertEquals(1, result.size());
            assertEquals("低", result.get(0).get("level"));
        }

        @Test
        @DisplayName("PRIORITY 单条命中且无 allowedValues → 正常返回")
        void prioritySingleMatchNoAllowedValues() {
            DmnDecisionTable table = buildRiskTable(DmnHitPolicy.PRIORITY, null);
            List<Map<String, Object>> result = engine.execute(table, ctx(500));
            assertEquals(1, result.size());
            assertEquals("低", result.get(0).get("level"));
        }
    }
}
