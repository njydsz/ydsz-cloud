package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * RuleIndexer 倒排索引单元测试（P1-2）
 *
 * <p>测试倒排索引的构建、查询、字段提取、与现有索引协同过滤等核心能力，
 * 覆盖单字段/多字段规则、关键字过滤、非表达式规则、移除同步、中文/下划线字段名等维度。
 *
 * <p>测试风格参考 {@link RuleIndexerTest}，使用 Mockito.mock 手动创建 Rule 桩，
 * 通过 {@link RuleIndexer#getIndexStats()} 中的 {@code fieldIndexSize} 和
 * {@link RuleIndexer#findCandidatesByFacts(String, Set)} /
 * {@link RuleIndexer#filterByFacts(List, Set)} 的行为验证倒排索引效果。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@DisplayName("RuleIndexer 倒排索引单元测试（P1-2）")
class RuleIndexerInvertedIndexTest {

    private RuleIndexer indexer;

    @BeforeEach
    void setUp() {
        indexer = new RuleIndexer();
    }

    /**
     * 构造带条件表达式的 Rule mock 测试桩
     *
     * @param code        规则编码
     * @param priority    优先级
     * @param tenantId    租户 ID（null 表示使用默认值）
     * @param scope       场景作用域
     * @param mutexGroup  互斥组名称
     * @param condition   条件表达式（Aviator 语法）
     * @return Rule mock
     */
    private Rule mockExprRule(String code, int priority, String tenantId,
                              String scope, String mutexGroup, String condition) {
        Rule rule = Mockito.mock(Rule.class);
        when(rule.getCode()).thenReturn(code);
        when(rule.getPriority()).thenReturn(priority);
        when(rule.getTenantId()).thenReturn(tenantId);
        when(rule.getScope()).thenReturn(scope);
        when(rule.getMutexGroup()).thenReturn(mutexGroup);
        // 构造 RuleDefinition 并暴露 conditionExpression
        RuleDefinition def = RuleDefinition.builder()
                .code(code)
                .conditionExpression(condition)
                .build();
        when(rule.getRuleDefinition()).thenReturn(def);
        return rule;
    }

    /**
     * 构造无 RuleDefinition 的 Rule mock（模拟编码规则/DecisionTableRule 等非表达式规则）
     *
     * @param code       规则编码
     * @param priority   优先级
     * @param tenantId   租户 ID
     * @param scope      场景作用域
     * @param mutexGroup 互斥组名称
     * @return Rule mock（getRuleDefinition 返回 null）
     */
    private Rule mockNonExprRule(String code, int priority, String tenantId,
                                 String scope, String mutexGroup) {
        Rule rule = Mockito.mock(Rule.class);
        when(rule.getCode()).thenReturn(code);
        when(rule.getPriority()).thenReturn(priority);
        when(rule.getTenantId()).thenReturn(tenantId);
        when(rule.getScope()).thenReturn(scope);
        when(rule.getMutexGroup()).thenReturn(mutexGroup);
        when(rule.getRuleDefinition()).thenReturn(null);
        return rule;
    }

    /**
     * 构造空条件表达式的 Rule mock
     */
    private Rule mockEmptyExprRule(String code, int priority, String tenantId,
                                   String scope, String mutexGroup) {
        Rule rule = Mockito.mock(Rule.class);
        when(rule.getCode()).thenReturn(code);
        when(rule.getPriority()).thenReturn(priority);
        when(rule.getTenantId()).thenReturn(tenantId);
        when(rule.getScope()).thenReturn(scope);
        when(rule.getMutexGroup()).thenReturn(mutexGroup);
        RuleDefinition def = RuleDefinition.builder()
                .code(code)
                .conditionExpression("")  // 空表达式
                .build();
        when(rule.getRuleDefinition()).thenReturn(def);
        return rule;
    }

    /**
     * 构造 Rule mock 测试桩（无表达式，用于补足规则数到阈值）
     */
    private Rule mockRule(String code, int priority, String tenantId,
                          String scope, String mutexGroup) {
        return mockNonExprRule(code, priority, tenantId, scope, mutexGroup);
    }

    /**
     * 批量生成指定数量的填充规则（无表达式），用于达到索引阈值
     */
    private List<Rule> mockFillRules(int count, String tenantId, String scope) {
        List<Rule> rules = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rules.add(mockRule("FILL_" + i, 500 + i, tenantId, scope, null));
        }
        return rules;
    }

    // ==================== 1. 倒排索引构建 ====================

    @Nested
    @DisplayName("倒排索引构建")
    class InvertedIndexBuildTest {

        @Test
        @DisplayName("注册含 amount > 1000 表达式的规则 - fieldIndexSize 包含 amount 字段")
        void shouldBuildFieldIndexForExpressionRule() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockExprRule("R_AMOUNT", 100, "1", null, null, "amount > 1000"));
            rules.addAll(mockFillRules(199, "1", null));

            indexer.rebuildIndex(rules);

            assertThat(indexer.isIndexEnabled()).isTrue();
            // fieldIndexSize 至少为 1（amount 字段）
            assertThat(indexer.getIndexStats()).contains("fieldIndexSize=1");
        }

        @Test
        @DisplayName("多条规则引用同一字段 - fieldIndexSize 反映唯一字段数")
        void shouldCountUniqueFieldsAcrossRules() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockExprRule("R1", 100, "1", null, null, "amount > 1000"));
            rules.add(mockExprRule("R2", 200, "1", null, null, "amount > 2000 && score > 800"));
            rules.addAll(mockFillRules(198, "1", null));

            indexer.rebuildIndex(rules);

            // 唯一字段：amount, score → fieldIndexSize=2
            assertThat(indexer.getIndexStats()).contains("fieldIndexSize=2");
        }
    }

    // ==================== 2. 倒排索引查询 ====================

    @Nested
    @DisplayName("倒排索引查询")
    class InvertedIndexQueryTest {

        @Test
        @DisplayName("facts 含 amount 时返回候选规则")
        void shouldReturnCandidatesWhenFactsContainsField() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockExprRule("R_AMOUNT", 100, "1", null, null, "amount > 1000"));
            rules.addAll(mockFillRules(199, "1", null));

            indexer.rebuildIndex(rules);

            Set<String> factKeys = new HashSet<>();
            factKeys.add("amount");
            List<Rule> candidates = indexer.findCandidatesByFacts("1", factKeys);

            assertThat(candidates).extracting(Rule::getCode).contains("R_AMOUNT");
        }

        @Test
        @DisplayName("facts 不含 amount 时返回空候选（仅表达式规则）")
        void shouldReturnEmptyWhenFactsMissingField() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockExprRule("R_AMOUNT", 100, "1", null, null, "amount > 1000"));
            rules.addAll(mockFillRules(199, "1", null));

            indexer.rebuildIndex(rules);

            // facts 仅含不相关的字段
            Set<String> factKeys = new HashSet<>();
            factKeys.add("other_field");
            List<Rule> candidates = indexer.findCandidatesByFacts("1", factKeys);

            // R_AMOUNT 不应出现（amount 缺失），但无字段引用的填充规则会保留
            assertThat(candidates).extracting(Rule::getCode).doesNotContain("R_AMOUNT");
        }
    }

    // ==================== 3. 多字段规则 ====================

    @Nested
    @DisplayName("多字段规则")
    class MultiFieldRuleTest {

        @Test
        @DisplayName("amount > 1000 && score > 800 需要 facts 同时含 amount 和 score 才命中")
        void shouldRequireAllFieldsPresent() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockExprRule("R_MULTI", 100, "1", null, null, "amount > 1000 && score > 800"));
            rules.addAll(mockFillRules(199, "1", null));

            indexer.rebuildIndex(rules);

            // 仅含 amount，缺少 score → 不命中
            Set<String> partialFacts = new HashSet<>();
            partialFacts.add("amount");
            List<Rule> partialCandidates = indexer.findCandidatesByFacts("1", partialFacts);
            assertThat(partialCandidates).extracting(Rule::getCode).doesNotContain("R_MULTI");

            // 同时含 amount 和 score → 命中
            Set<String> fullFacts = new HashSet<>();
            fullFacts.add("amount");
            fullFacts.add("score");
            List<Rule> fullCandidates = indexer.findCandidatesByFacts("1", fullFacts);
            assertThat(fullCandidates).extracting(Rule::getCode).contains("R_MULTI");
        }

        @Test
        @DisplayName("filterByFacts 对多字段规则的过滤效果一致")
        void shouldFilterByFactsConsistently() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockExprRule("R_MULTI", 100, "1", null, null, "amount > 1000 && score > 800"));
            rules.add(mockExprRule("R_SINGLE", 200, "1", null, null, "amount > 100"));
            rules.addAll(mockFillRules(198, "1", null));

            indexer.rebuildIndex(rules);

            // 先用 findCandidates 获取候选列表
            List<Rule> candidates = indexer.findCandidates("1", null, null);
            // 仅含 amount → 仅 R_SINGLE 命中
            Set<String> partialFacts = new HashSet<>();
            partialFacts.add("amount");
            List<Rule> filtered = indexer.filterByFacts(candidates, partialFacts);
            assertThat(filtered).extracting(Rule::getCode)
                    .contains("R_SINGLE")
                    .doesNotContain("R_MULTI");
        }
    }

    // ==================== 4. 字段提取过滤关键字 ====================

    @Nested
    @DisplayName("字段提取过滤关键字")
    class KeywordFilterTest {

        @Test
        @DisplayName("if (x > 1) return true 不应提取 if 和 return")
        void shouldFilterAviatorKeywords() {
            List<Rule> rules = new ArrayList<>();
            // 表达式含 if/return 关键字，仅 x 应被提取
            rules.add(mockExprRule("R_KW", 100, "1", null, null, "if (x > 1) return true"));
            rules.addAll(mockFillRules(199, "1", null));

            indexer.rebuildIndex(rules);

            // 仅 x 一个字段（if/return/true 被过滤）
            // 注意：true 在 AVIATOR_KEYWORDS 中，if/return 也在
            // fieldIndexSize 应为 1（只有 x）
            assertThat(indexer.getIndexStats()).contains("fieldIndexSize=1");

            // facts 含 x 时命中
            Set<String> factKeys = new HashSet<>();
            factKeys.add("x");
            List<Rule> candidates = indexer.findCandidatesByFacts("1", factKeys);
            assertThat(candidates).extracting(Rule::getCode).contains("R_KW");

            // facts 不含 x 时不命中
            Set<String> otherFacts = new HashSet<>();
            otherFacts.add("if");
            otherFacts.add("return");
            List<Rule> otherCandidates = indexer.findCandidatesByFacts("1", otherFacts);
            assertThat(otherCandidates).extracting(Rule::getCode).doesNotContain("R_KW");
        }

        @Test
        @DisplayName("字段提取过滤 true/false/null 等字面量")
        void shouldFilterBooleanLiterals() {
            List<Rule> rules = new ArrayList<>();
            // flag == true → 仅 flag 被提取，true 被过滤
            rules.add(mockExprRule("R_BOOL", 100, "1", null, null, "flag == true"));
            rules.addAll(mockFillRules(199, "1", null));

            indexer.rebuildIndex(rules);

            // 仅 flag 一个字段
            assertThat(indexer.getIndexStats()).contains("fieldIndexSize=1");
        }
    }

    // ==================== 5. 非 ExpressionRule 不参与倒排索引 ====================

    @Nested
    @DisplayName("非表达式规则处理")
    class NonExpressionRuleTest {

        @Test
        @DisplayName("getRuleDefinition 返回 null 的规则不参与倒排索引过滤")
        void shouldNotIndexNonExpressionRules() {
            List<Rule> rules = new ArrayList<>();
            // 全部为非表达式规则
            rules.add(mockNonExprRule("R_NON1", 100, "1", null, null));
            rules.add(mockNonExprRule("R_NON2", 200, "1", null, null));
            rules.addAll(mockFillRules(198, "1", null));

            indexer.rebuildIndex(rules);

            // 无字段引用，fieldIndexSize=0
            assertThat(indexer.getIndexStats()).contains("fieldIndexSize=0");
            assertThat(indexer.hasFieldIndex()).isFalse();

            // 倒排索引为空时，filterByFacts 回退返回原候选列表
            List<Rule> candidates = indexer.findCandidates("1", null, null);
            Set<String> factKeys = new HashSet<>();
            factKeys.add("any_field");
            List<Rule> filtered = indexer.filterByFacts(candidates, factKeys);
            // 非表达式规则全部保留
            assertThat(filtered).extracting(Rule::getCode)
                    .contains("R_NON1", "R_NON2");
        }

        @Test
        @DisplayName("混合规则：表达式规则被字段过滤，非表达式规则保留")
        void shouldKeepNonExpressionRulesAsCandidates() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockExprRule("R_EXPR", 100, "1", null, null, "amount > 1000"));
            rules.add(mockNonExprRule("R_NON", 200, "1", null, null));
            rules.addAll(mockFillRules(198, "1", null));

            indexer.rebuildIndex(rules);

            // facts 不含 amount
            Set<String> factKeys = new HashSet<>();
            factKeys.add("other");
            List<Rule> candidates = indexer.findCandidatesByFacts("1", factKeys);

            // 表达式规则被过滤，非表达式规则保留
            assertThat(candidates).extracting(Rule::getCode)
                    .contains("R_NON")
                    .doesNotContain("R_EXPR");
        }
    }

    // ==================== 6. removeFromIndex 同步清除倒排索引 ====================

    @Nested
    @DisplayName("removeFromIndex 同步清除倒排索引")
    class RemoveSyncTest {

        @Test
        @DisplayName("移除规则后倒排索引同步清除对应字段映射")
        void shouldSyncRemoveInvertedIndex() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockExprRule("R_AMOUNT", 100, "1", null, null, "amount > 1000"));
            rules.add(mockExprRule("R_SCORE", 200, "1", null, null, "score > 800"));
            rules.addAll(mockFillRules(198, "1", null));

            indexer.rebuildIndex(rules);
            assertThat(indexer.getIndexStats()).contains("fieldIndexSize=2");

            // 移除 R_AMOUNT
            indexer.removeFromIndex("R_AMOUNT");

            // amount 字段应被清除（无其他规则引用），仅剩 score
            assertThat(indexer.getIndexStats()).contains("fieldIndexSize=1");

            // 验证：facts 含 amount 但不含 score 时，R_AMOUNT 不再被返回
            Set<String> factKeys = new HashSet<>();
            factKeys.add("amount");
            List<Rule> candidates = indexer.findCandidatesByFacts("1", factKeys);
            assertThat(candidates).extracting(Rule::getCode).doesNotContain("R_AMOUNT");
        }

        @Test
        @DisplayName("多规则引用同字段 - 移除一条后字段仍保留")
        void shouldKeepFieldWhenOtherRulesReference() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockExprRule("R1", 100, "1", null, null, "amount > 1000"));
            rules.add(mockExprRule("R2", 200, "1", null, null, "amount > 2000"));
            rules.addAll(mockFillRules(198, "1", null));

            indexer.rebuildIndex(rules);
            assertThat(indexer.getIndexStats()).contains("fieldIndexSize=1");

            // 移除 R1，amount 仍被 R2 引用
            indexer.removeFromIndex("R1");
            assertThat(indexer.getIndexStats()).contains("fieldIndexSize=1");

            // R2 仍可被 amount 字段命中
            Set<String> factKeys = new HashSet<>();
            factKeys.add("amount");
            List<Rule> candidates = indexer.findCandidatesByFacts("1", factKeys);
            assertThat(candidates).extracting(Rule::getCode).contains("R2");
        }
    }

    // ==================== 7. 倒排索引 + 现有索引协同过滤 ====================

    @Nested
    @DisplayName("倒排索引与现有索引协同过滤")
    class CoordinatedFilterTest {

        @Test
        @DisplayName("倒排索引与租户索引协同 - 跨租户规则不被返回")
        void shouldCoordinateWithTenantIndex() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockExprRule("R_T1", 100, "TENANT_1", null, null, "amount > 1000"));
            rules.add(mockExprRule("R_T2", 200, "TENANT_2", null, null, "amount > 2000"));
            // 补足 200 条到 TENANT_1
            rules.addAll(mockFillRules(198, "TENANT_1", null));

            indexer.rebuildIndex(rules);

            // 查询 TENANT_1 的候选，facts 含 amount
            Set<String> factKeys = new HashSet<>();
            factKeys.add("amount");
            List<Rule> candidates = indexer.findCandidatesByFacts("TENANT_1", factKeys);

            // 仅返回 TENANT_1 的规则
            assertThat(candidates).extracting(Rule::getCode)
                    .contains("R_T1")
                    .doesNotContain("R_T2");
        }

        @Test
        @DisplayName("倒排索引与场景索引协同 - findCandidates + filterByFacts 链式过滤")
        void shouldChainFindCandidatesAndFilterByFacts() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockExprRule("R_COCKPIT", 100, "1", "COCKPIT", null, "amount > 1000"));
            rules.add(mockExprRule("R_OTHER", 200, "1", "OTHER", null, "amount > 500"));
            rules.add(mockExprRule("R_ALL", 300, "1", "ALL", null, "score > 800"));
            rules.addAll(mockFillRules(197, "1", "OTHER"));

            indexer.rebuildIndex(rules);

            // 第一层：场景过滤（COCKPIT 场景应匹配 COCKPIT + ALL scope）
            List<Rule> scopedCandidates = indexer.findCandidates("1", "COCKPIT", null);
            assertThat(scopedCandidates).extracting(Rule::getCode)
                    .contains("R_COCKPIT", "R_ALL")
                    .doesNotContain("R_OTHER");

            // 第二层：字段过滤（facts 仅含 amount）
            Set<String> factKeys = new HashSet<>();
            factKeys.add("amount");
            List<Rule> finalCandidates = indexer.filterByFacts(scopedCandidates, factKeys);

            // R_COCKPIT 命中（amount），R_ALL 不命中（需要 score）
            assertThat(finalCandidates).extracting(Rule::getCode)
                    .contains("R_COCKPIT")
                    .doesNotContain("R_ALL", "R_OTHER");
        }

        @Test
        @DisplayName("倒排索引与互斥组过滤协同")
        void shouldCoordinateWithMutexGroupFilter() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockExprRule("R_G1", 100, "1", null, "GROUP_A", "amount > 1000"));
            rules.add(mockExprRule("R_G2", 200, "1", null, "GROUP_A", "amount > 2000"));
            rules.add(mockExprRule("R_G3", 300, "1", null, "GROUP_B", "score > 800"));
            rules.addAll(mockFillRules(197, "1", null));

            indexer.rebuildIndex(rules);

            // 互斥组 GROUP_A 已命中 → 排除 R_G1, R_G2
            Set<String> triggered = new HashSet<>();
            triggered.add("GROUP_A");
            List<Rule> candidates = indexer.findCandidates("1", null, triggered);

            // 字段过滤：facts 含 amount 和 score
            Set<String> factKeys = new HashSet<>();
            factKeys.add("amount");
            factKeys.add("score");
            List<Rule> filtered = indexer.filterByFacts(candidates, factKeys);

            // R_G3 命中（GROUP_B 未触发，score 字段存在）
            assertThat(filtered).extracting(Rule::getCode).contains("R_G3");
            // R_G1, R_G2 被互斥组排除
            assertThat(filtered).extracting(Rule::getCode)
                    .doesNotContain("R_G1", "R_G2");
        }
    }

    // ==================== 8. 大规则量启用倒排索引 ====================

    @Nested
    @DisplayName("大规则量倒排索引")
    class LargeScaleTest {

        @Test
        @DisplayName("规则数 > 200 启用倒排索引 - fieldIndexSize 正确统计")
        void shouldEnableInvertedIndexForLargeRuleSet() {
            List<Rule> rules = new ArrayList<>();
            // 100 条引用 amount 的规则
            for (int i = 0; i < 100; i++) {
                rules.add(mockExprRule("R_AMT_" + i, 100 + i, "1", null, null, "amount > " + (i * 100)));
            }
            // 100 条引用 score 的规则
            for (int i = 0; i < 100; i++) {
                rules.add(mockExprRule("R_SCR_" + i, 200 + i, "1", null, null, "score > " + (i * 10)));
            }
            // 50 条无表达式规则
            rules.addAll(mockFillRules(50, "1", null));

            indexer.rebuildIndex(rules);

            assertThat(indexer.isIndexEnabled()).isTrue();
            // 唯一字段：amount, score → fieldIndexSize=2
            assertThat(indexer.getIndexStats()).contains("fieldIndexSize=2");
            assertThat(indexer.hasFieldIndex()).isTrue();
        }

        @Test
        @DisplayName("大规则量下倒排索引显著缩小候选集")
        void shouldReduceCandidateSetForLargeRuleSet() {
            List<Rule> rules = new ArrayList<>();
            // 200 条引用 amount 的规则
            for (int i = 0; i < 200; i++) {
                rules.add(mockExprRule("R_AMT_" + i, 100 + i, "1", null, null, "amount > " + i));
            }
            // 50 条引用 score 的规则
            for (int i = 0; i < 50; i++) {
                rules.add(mockExprRule("R_SCR_" + i, 300 + i, "1", null, null, "score > " + i));
            }

            indexer.rebuildIndex(rules);

            // facts 仅含 amount → 仅 200 条 amount 规则命中，50 条 score 规则被过滤
            Set<String> factKeys = new HashSet<>();
            factKeys.add("amount");
            List<Rule> candidates = indexer.findCandidatesByFacts("1", factKeys);

            // 候选数应为 200（amount 规则），不含 score 规则
            assertThat(candidates).hasSize(200);
            assertThat(candidates).allMatch(r -> r.getCode().startsWith("R_AMT_"));
        }
    }

    // ==================== 9. 空表达式规则的处理 ====================

    @Nested
    @DisplayName("空表达式规则处理")
    class EmptyExpressionTest {

        @Test
        @DisplayName("空条件表达式不参与倒排索引 - 保留为候选")
        void shouldNotIndexEmptyExpression() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockEmptyExprRule("R_EMPTY", 100, "1", null, null));
            rules.add(mockExprRule("R_EXPR", 200, "1", null, null, "amount > 1000"));
            rules.addAll(mockFillRules(198, "1", null));

            indexer.rebuildIndex(rules);

            // 仅 amount 一个字段（R_EMPTY 的空表达式不贡献字段）
            assertThat(indexer.getIndexStats()).contains("fieldIndexSize=1");

            // facts 为任意集合时，R_EMPTY 保留为候选（无字段引用）
            Set<String> factKeys = new HashSet<>();
            factKeys.add("other_field");
            List<Rule> candidates = indexer.findCandidatesByFacts("1", factKeys);
            assertThat(candidates).extracting(Rule::getCode).contains("R_EMPTY");
        }

        @Test
        @DisplayName("null 条件表达式不参与倒排索引")
        void shouldNotIndexNullExpression() {
            List<Rule> rules = new ArrayList<>();
            // 构造 conditionExpression 为 null 的规则
            Rule rule = Mockito.mock(Rule.class);
            when(rule.getCode()).thenReturn("R_NULL_EXPR");
            when(rule.getPriority()).thenReturn(100);
            when(rule.getTenantId()).thenReturn("1");
            when(rule.getScope()).thenReturn(null);
            when(rule.getMutexGroup()).thenReturn(null);
            RuleDefinition def = RuleDefinition.builder()
                    .code("R_NULL_EXPR")
                    .conditionExpression(null)  // null 表达式
                    .build();
            when(rule.getRuleDefinition()).thenReturn(def);
            rules.add(rule);
            rules.addAll(mockFillRules(199, "1", null));

            indexer.rebuildIndex(rules);

            // 无字段引用
            assertThat(indexer.getIndexStats()).contains("fieldIndexSize=0");

            // R_NULL_EXPR 保留为候选
            Set<String> factKeys = new HashSet<>();
            factKeys.add("any");
            List<Rule> candidates = indexer.findCandidatesByFacts("1", factKeys);
            assertThat(candidates).extracting(Rule::getCode).contains("R_NULL_EXPR");
        }

        @Test
        @DisplayName("facts 为空时仅返回无字段引用的规则")
        void shouldReturnOnlyNoFieldRulesWhenFactsEmpty() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockExprRule("R_EXPR", 100, "1", null, null, "amount > 1000"));
            rules.add(mockNonExprRule("R_NON", 200, "1", null, null));
            rules.add(mockEmptyExprRule("R_EMPTY", 300, "1", null, null));
            rules.addAll(mockFillRules(197, "1", null));

            indexer.rebuildIndex(rules);

            // facts 为空
            List<Rule> candidates = indexer.findCandidatesByFacts("1", Collections.emptySet());
            // R_EXPR 不应出现（需要 amount），R_NON 和 R_EMPTY 应出现
            assertThat(candidates).extracting(Rule::getCode)
                    .contains("R_NON", "R_EMPTY")
                    .doesNotContain("R_EXPR");
        }
    }

    // ==================== 10. 字段名含中文/下划线的处理 ====================

    @Nested
    @DisplayName("中文/下划线字段名处理")
    class ChineseAndUnderscoreFieldTest {

        @Test
        @DisplayName("字段名含下划线 - 正确提取并索引")
        void shouldExtractUnderscoreFieldName() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockExprRule("R_UNDER", 100, "1", null, null, "user_name == 'admin' && user_age > 18"));
            rules.addAll(mockFillRules(199, "1", null));

            indexer.rebuildIndex(rules);

            // user_name, user_age 两个字段
            assertThat(indexer.getIndexStats()).contains("fieldIndexSize=2");

            // facts 含 user_name 和 user_age 时命中
            Set<String> factKeys = new HashSet<>();
            factKeys.add("user_name");
            factKeys.add("user_age");
            List<Rule> candidates = indexer.findCandidatesByFacts("1", factKeys);
            assertThat(candidates).extracting(Rule::getCode).contains("R_UNDER");

            // 仅含 user_name 时不命中
            Set<String> partialFacts = new HashSet<>();
            partialFacts.add("user_name");
            List<Rule> partialCandidates = indexer.findCandidatesByFacts("1", partialFacts);
            assertThat(partialCandidates).extracting(Rule::getCode).doesNotContain("R_UNDER");
        }

        @Test
        @DisplayName("字段名含中文 - 正确提取并索引")
        void shouldExtractChineseFieldName() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockExprRule("R_CN", 100, "1", null, null, "金额 > 1000"));
            rules.addAll(mockFillRules(199, "1", null));

            indexer.rebuildIndex(rules);

            // 中文字段"金额"被提取
            assertThat(indexer.getIndexStats()).contains("fieldIndexSize=1");

            // facts 含"金额"时命中
            Set<String> factKeys = new HashSet<>();
            factKeys.add("金额");
            List<Rule> candidates = indexer.findCandidatesByFacts("1", factKeys);
            assertThat(candidates).extracting(Rule::getCode).contains("R_CN");

            // facts 不含"金额"时不命中
            Set<String> otherFacts = new HashSet<>();
            otherFacts.add("other");
            List<Rule> otherCandidates = indexer.findCandidatesByFacts("1", otherFacts);
            assertThat(otherCandidates).extracting(Rule::getCode).doesNotContain("R_CN");
        }

        @Test
        @DisplayName("中英文混合字段名 - 全部正确提取")
        void shouldExtractMixedFieldNames() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockExprRule("R_MIXED", 100, "1", null, null, "amount > 1000 && 金额 > 500 && user_score > 10"));
            rules.addAll(mockFillRules(199, "1", null));

            indexer.rebuildIndex(rules);

            // 3 个字段：amount, 金额, user_score
            assertThat(indexer.getIndexStats()).contains("fieldIndexSize=3");

            // 需要全部字段都存在才命中
            Set<String> fullFacts = new HashSet<>();
            fullFacts.add("amount");
            fullFacts.add("金额");
            fullFacts.add("user_score");
            List<Rule> fullCandidates = indexer.findCandidatesByFacts("1", fullFacts);
            assertThat(fullCandidates).extracting(Rule::getCode).contains("R_MIXED");

            // 缺少任一字段不命中
            Set<String> partialFacts = new HashSet<>();
            partialFacts.add("amount");
            partialFacts.add("金额");
            List<Rule> partialCandidates = indexer.findCandidatesByFacts("1", partialFacts);
            assertThat(partialCandidates).extracting(Rule::getCode).doesNotContain("R_MIXED");
        }
    }

    // ==================== 增量添加倒排索引 ====================

    @Nested
    @DisplayName("增量添加倒排索引")
    class AddInvertedIndexTest {

        @Test
        @DisplayName("addToIndex 增量添加表达式规则到倒排索引")
        void shouldAddToInvertedIndexIncrementally() {
            List<Rule> rules = new ArrayList<>();
            rules.addAll(mockFillRules(200, "1", null));
            indexer.rebuildIndex(rules);

            // 初始无字段引用
            assertThat(indexer.getIndexStats()).contains("fieldIndexSize=0");

            // 增量添加表达式规则
            Rule newRule = mockExprRule("R_NEW", 50, "1", null, null, "amount > 1000");
            indexer.addToIndex(newRule);

            // 倒排索引已更新
            assertThat(indexer.getIndexStats()).contains("fieldIndexSize=1");
            assertThat(indexer.hasFieldIndex()).isTrue();

            // facts 含 amount 时命中新规则
            Set<String> factKeys = new HashSet<>();
            factKeys.add("amount");
            List<Rule> candidates = indexer.findCandidatesByFacts("1", factKeys);
            assertThat(candidates).extracting(Rule::getCode).contains("R_NEW");
        }
    }
}
