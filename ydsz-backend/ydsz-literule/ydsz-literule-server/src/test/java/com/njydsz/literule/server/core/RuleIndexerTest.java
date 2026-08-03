package com.njydsz.literule.server.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.literule.api.Rule;
import com.njydsz.literule.api.RuleDefinition;
import com.njydsz.literule.api.RuleEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RuleIndexer} 单元测试：覆盖索引启用/重建、增量添加/移除、
 * 按租户+环境+场景+互斥组过滤、倒排索引字段过滤等核心逻辑。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@DisplayName("规则索引器 RuleIndexer 测试")
class RuleIndexerTest {

    private RuleIndexer indexer;

    @BeforeEach
    void setUp() {
        indexer = new RuleIndexer();
    }

    /**
     * 构造一个 Mockito mock 的 Rule，预设基本元数据。
     * tenant 默认 "1"，environment 默认 default，scope 默认 null。
     */
    private Rule mockRule(String code, String name, int priority) {
        Rule rule = mock(Rule.class);
        when(rule.getCode()).thenReturn(code);
        when(rule.getName()).thenReturn(name);
        when(rule.getCategory()).thenReturn("TEST");
        when(rule.getPriority()).thenReturn(priority);
        when(rule.getTenantId()).thenReturn("1");
        when(rule.getEnvironment()).thenReturn(RuleEnvironment.DEFAULT);
        when(rule.getScope()).thenReturn(null);
        when(rule.getMutexGroup()).thenReturn(null);
        when(rule.getRuleDefinition()).thenReturn(null);
        return rule;
    }

    /**
     * 构造一个带条件表达式的 Rule，用于测试倒排索引字段提取。
     */
    private Rule mockRuleWithExpr(String code, String name, int priority, String conditionExpression) {
        Rule rule = mockRule(code, name, priority);
        RuleDefinition def = RuleDefinition.builder()
                .code(code)
                .name(name)
                .conditionExpression(conditionExpression)
                .build();
        when(rule.getRuleDefinition()).thenReturn(def);
        return rule;
    }

    /**
     * 批量生成 N 条 mock 规则，code 形如 R0001/R0002...
     */
    private List<Rule> mockRules(int count) {
        List<Rule> rules = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rules.add(mockRule(String.format("R%04d", i), "rule-" + i, 100 + i));
        }
        return rules;
    }

    @Nested
    @DisplayName("索引启用与重建")
    /**
     * 测试分组：索引启用与重建
     */
    /**
     * 测试分组：「初始状态：索引未启用」等
     */
    class IndexEnableCases {

        @Test
        @DisplayName("初始状态：索引未启用")
        void shouldBeDisabledInitially() {
            assertThat(indexer.isIndexEnabled()).isFalse();
        }

        @Test
        @DisplayName("rebuildIndex 后规则数 < 200 时索引仍未启用")
        void shouldRemainDisabledWhenRulesLessThanThreshold() {
            List<Rule> rules = mockRules(199);

            indexer.rebuildIndex(rules);

            assertThat(indexer.isIndexEnabled()).isFalse();
        }

        @Test
        @DisplayName("rebuildIndex 后规则数 >= 200 时启用索引")
        void shouldBeEnabledWhenRulesReachThreshold() {
            List<Rule> rules = mockRules(200);

            indexer.rebuildIndex(rules);

            assertThat(indexer.isIndexEnabled()).isTrue();
        }

        @Test
        @DisplayName("rebuildIndex 后 allRules 持有全部规则（无论是否启用索引）")
        void shouldHoldAllRulesAfterRebuild() {
            List<Rule> rules = mockRules(10);

            indexer.rebuildIndex(rules);

            // 未启用索引时，findCandidates 返回 allRules
            Set<String> triggeredGroups = new HashSet<>();
            List<Rule> candidates = indexer.findCandidates("1", RuleEnvironment.DEFAULT, "DEFAULT", triggeredGroups);
            assertThat(candidates).hasSize(10);
        }
    }

    @Nested
    @Display    /**
     * 测试分组：增量更新用例
     */
Name("addToIndex / removeFromIndex 增量更新")
    class IncrementalUpdateCases {

        @Test
        @DisplayName("索引未启用时 addToIndex 不生效")
        void shouldNotAddWhenIndexDisabled() {
            indexer.rebuildIndex(mockRules(10));
            Rule newRule = mockRule("R_NEW", "新规则", 100);

            indexer.addToIndex(newRule);

            // 索引未启用，allRules 不变
            Set<String> triggeredGroups = new HashSet<>();
            List<Rule> candidates = indexer.findCandidates("1", RuleEnvironment.DEFAULT, "DEFAULT", triggeredGroups);
            assertThat(candidates).hasSize(10);
        }

        @Test
        @DisplayName("索引启用后 addToIndex 增量添加规则")
        void shouldAddRuleIncrementallyWhenEnabled() {
            List<Rule> rules = new ArrayList<>(mockRules(200));
            indexer.rebuildIndex(rules);
            assertThat(indexer.isIndexEnabled()).isTrue();

            Rule newRule = mockRule("R_NEW", "新规则", 50);
            indexer.addToIndex(newRule);

            Set<String> triggeredGroups = new HashSet<>();
            List<Rule> candidates = indexer.findCandidates("1", RuleEnvironment.DEFAULT, "DEFAULT", triggeredGroups);
            assertThat(candidates).hasSize(201);
            assertThat(candidates).extracting(Rule::getCode).contains("R_NEW");
            // 新规则 priority=50，应排在最前
            assertThat(candidates.get(0).getCode()).isEqualTo("R_NEW");
        }

        @Test
        @DisplayName("索引启用后 removeFromIndex 移除规则")
        void shouldRemoveRuleFromIndexWhenEnabled() {
            List<Rule> rules = mockRules(200);
            indexer.rebuildIndex(rules);
            assertThat(indexer.isIndexEnabled()).isTrue();

            indexer.removeFromIndex("R0000");

            Set<String> triggeredGroups = new HashSet<>();
            List<Rule> candidates = indexer.findCandidates("1", RuleEnvironment.DEFAULT, "DEFAULT", triggeredGroups);
            assertThat(candidates).hasSize(199);
            assertThat(candidates).extracting(Rule::getCode).doesNotContain("R0000");
        }

        @Test
        @DisplayName("索引未启用时 removeFromIndex 不抛异常")
        void shouldNotThrowWhenRemoveOnDisabledIndex() {
            indexer.rebuildIndex(mockRules(10));

            // 不应抛异常
            indexer.removeFromIndex("R0000");    /**
     * 测试分组：「findCandidates 按租户+环境+场景过滤」等
     */

        }
    }

    @Nested
    @DisplayName("findCandidates 按租户+环境+场景过滤")
    class FindCandidatesCases {

        @Test
        @DisplayName("按租户过滤：仅返回指定租户的规则")
        void shouldFilterByTenant() {
            List<Rule> rules = new ArrayList<>(mockRules(200));
            // 追加一条其他租户的规则
            Rule otherTenantRule = mockRule("R_OTHER_TENANT", "其他租户规则", 10);
            when(otherTenantRule.getTenantId()).thenReturn("2");
            rules.add(otherTenantRule);

            indexer.rebuildIndex(rules);

            Set<String> triggeredGroups = new HashSet<>();
            List<Rule> candidates = indexer.findCandidates("1", RuleEnvironment.DEFAULT, "DEFAULT", triggeredGroups);
            // 不应包含其他租户的规则
            assertThat(candidates).extracting(Rule::getCode).doesNotContain("R_OTHER_TENANT");
        }

        @Test
        @DisplayName("按环境过滤：default 环境规则匹配任何上下文环境")
        void defaultEnvironmentMatchesAnyContext() {
            List<Rule> rules = new ArrayList<>(mockRules(200));
            indexer.rebuildIndex(rules);

            Set<String> triggeredGroups = new HashSet<>();
            // 上下文为 dev，default 规则应全部匹配
            List<Rule> candidates = indexer.findCandidates("1", "dev", "DEFAULT", triggeredGroups);
            assertThat(candidates).hasSize(200);
        }

        @Test
        @DisplayName("按环境过滤：非 default 环境规则仅在上下文环境完全匹配时返回")
        void shouldFilterByExactEnvironmentMatch() {
            List<Rule> rules = new ArrayList<>(mockRules(199));
            // 追加一条 prod 环境规则
            Rule prodRule = mockRule("R_PROD", "生产规则", 10);
            when(prodRule.getEnvironment()).thenReturn("prod");
            rules.add(prodRule);

            indexer.rebuildIndex(rules);

            // 上下文为 dev：仅返回 default 规则（199 条）
            Set<String> triggeredGroups = new HashSet<>();
            List<Rule> devCandidates = indexer.findCandidates("1", "dev", "DEFAULT", triggeredGroups);
            assertThat(devCandidates).extracting(Rule::getCode).doesNotContain("R_PROD");

            // 上下文为 prod：返回 default 规则 + prod 规则
            List<Rule> prodCandidates = indexer.findCandidates("1", "prod", "DEFAULT", triggeredGroups);
            assertThat(prodCandidates).extracting(Rule::getCode).contains("R_PROD");
        }

        @Test
        @DisplayName("按场景过滤：DEFAULT 场景返回全部匹配租户/环境的规则")
        void shouldReturnAllForDefaultScenario() {
            List<Rule> rules = mockRules(200);
            indexer.rebuildIndex(rules);

            Set<String> triggeredGroups = new HashSet<>();
            List<Rule> candidates = indexer.findCandidates("1", RuleEnvironment.DEFAULT, "DEFAULT", triggeredGroups);
            assertThat(candidates).hasSize(200);
        }

        @Test
        @DisplayName("按场景过滤：精确场景下返回 scope=null/ALL/匹配场景的规则")
        void shouldFilterByExactScenario() {
            List<Rule> rules = new ArrayList<>(mockRules(198));
            // scope=CREDIT 的规则
            Rule creditRule = mockRule("R_CREDIT", "信用规则", 10);
            when(creditRule.getScope()).thenReturn("CREDIT");
            rules.add(creditRule);
            // scope=ALL 的规则
            Rule allScopeRule = mockRule("R_ALL", "全场景规则", 20);
            when(allScopeRule.getScope()).thenReturn("ALL");
            rules.add(allScopeRule);

            indexer.rebuildIndex(rules);

            // 场景 CREDIT：应包含 scope=null/ALL/CREDIT 的规则
            Set<String> triggeredGroups = new HashSet<>();
            List<Rule> creditCandidates = indexer.findCandidates("1", RuleEnvironment.DEFAULT, "CREDIT", triggeredGroups);
            assertThat(creditCandidates).extracting(Rule::getCode).contains("R_CREDIT", "R_ALL");

            // 场景 LOAN：不应包含 scope=CREDIT 的规则，但应包含 scope=null/ALL
            List<Rule> loanCandidates = indexer.findCandidates("1", RuleEnvironment.DEFAULT, "LOAN", triggeredGroups);
            assertThat(loanCandidates).extracting(Rule::getCode).doesNotContain("R_CREDIT");
            assertThat(loanCandidates).extracting(Rule::getCode).contains("R_ALL");
        }

        @Test
        @DisplayName("互斥组过滤：排除已命中的互斥组中的规则")
        void shouldExcludeTriggeredMutexGroups() {
            List<Rule> rules = new ArrayList<>(mockRules(199));
            Rule mutexRule = mockRule("R_MUTEX", "互斥组规则", 10);
            when(mutexRule.getMutexGroup()).thenReturn("MUTEX_A");
            rules.add(mutexRule);

            indexer.rebuildIndex(rules);

            // 已命中 MUTEX_A，应排除该组规则
            Set<String> triggeredGroups = new HashSet<>();
            triggeredGroups.add("MUTEX_A");
            List<Rule> candidates = indexer.findCandidates("1", RuleEnvironment.DEFAULT, "DEFAULT", triggeredGroups);
            assertThat(candidates).extracting(Rule::getCode).doesNotContain("R_MUTEX");
        }

        @Test
        @DisplayName("索引未启用时 findCandidates 返回 allRules")
        void shouldReturnAllRulesWhenIndexDisabled() {
            List<Rule> rules = mockRules(50);
            indexer.rebuildIndex(rules);
            assertThat(indexer.isIndexEnabled()).isFalse();

            // 即使上下文指定其他租户/环境，未启用索引时返回 allRules
            Set<String> triggeredGroups = new HashSet<>();
            List<Rule> candidates = indexer.findCandidates("other-tenant", "prod", "CREDIT", triggeredGroups);
            assertThat(candidates).hasSize(50);
        }

        @Test
        @DisplayName("环境+租户不匹配时返回空列表")
        void shouldReturnEmptyWhenNoMatch() {
            List<Rule> rules = mockRules(200);
            indexer.rebuildIndex(rules);

            // 指定不存在的租户
            Set<String> triggeredGroups = new HashSet<>();
            List<Rule> candidates = indexer.findCandidates("NON_EXIST_TENANT", RuleEnvironment.DEFAULT, "DEFAULT"    /**
     * 测试分组：「倒排索引与 filterByFacts」等
     */
, triggeredGroups);
            assertThat(candidates).isEmpty();
        }
    }

    @Nested
    @DisplayName("倒排索引与 filterByFacts")
    class InvertedIndexCases {

        @Test
        @DisplayName("hasFieldIndex：无字段引用时返回 false")
        void shouldReturnFalseWhenNoFieldIndex() {
            List<Rule> rules = mockRules(200);
            indexer.rebuildIndex(rules);

            // 全部规则均无 conditionExpression，倒排索引为空
            assertThat(indexer.hasFieldIndex()).isFalse();
        }

        @Test
        @DisplayName("hasFieldIndex：有字段引用时返回 true")
        void shouldReturnTrueWhenFieldIndexExists() {
            List<Rule> rules = new ArrayList<>(mockRules(199));
            rules.add(mockRuleWithExpr("R_EXPR", "表达式规则", 10, "amount > 1000 && score > 0.5"));
            indexer.rebuildIndex(rules);

            assertThat(indexer.hasFieldIndex()).isTrue();
        }

        @Test
        @DisplayName("filterByFacts：facts 包含全部引用字段时保留规则")
        void shouldKeepRuleWhenFactsContainAllFields() {
            List<Rule> rules = new ArrayList<>(mockRules(199));
            Rule exprRule = mockRuleWithExpr("R_EXPR", "表达式规则", 10, "amount > 1000 && score > 0.5");
            rules.add(exprRule);
            indexer.rebuildIndex(rules);

            // 候选列表（全部规则）
            Set<String> triggeredGroups = new HashSet<>();
            List<Rule> candidates = indexer.findCandidates("1", RuleEnvironment.DEFAULT, "DEFAULT", triggeredGroups);

            // facts 包含 amount 和 score，应保留 R_EXPR
            Set<String> factKeys = new HashSet<>();
            factKeys.add("amount");
            factKeys.add("score");
            List<Rule> filtered = indexer.filterByFacts(candidates, factKeys);
            assertThat(filtered).extracting(Rule::getCode).contains("R_EXPR");
        }

        @Test
        @DisplayName("filterByFacts：facts 缺少引用字段时过滤掉规则")
        void shouldFilterOutRuleWhenFactsMissingFields() {
            List<Rule> rules = new ArrayList<>(mockRules(199));
            Rule exprRule = mockRuleWithExpr("R_EXPR", "表达式规则", 10, "amount > 1000 && score > 0.5");
            rules.add(exprRule);
            indexer.rebuildIndex(rules);

            Set<String> triggeredGroups = new HashSet<>();
            List<Rule> candidates = indexer.findCandidates("1", RuleEnvironment.DEFAULT, "DEFAULT", triggeredGroups);

            // facts 仅包含 amount（缺少 score），应过滤掉 R_EXPR
            Set<String> factKeys = new HashSet<>();
            factKeys.add("amount");
            List<Rule> filtered = indexer.filterByFacts(candidates, factKeys);
            assertThat(filtered).extracting(Rule::getCode).doesNotContain("R_EXPR");
        }

        @Test
        @DisplayName("filterByFacts：无字段引用的规则始终保留")
        void shouldAlwaysKeepRulesWithoutFields() {
            List<Rule> rules = new ArrayList<>(mockRules(199));
            rules.add(mockRuleWithExpr("R_EXPR", "表达式规则", 10, "amount > 1000"));
            indexer.rebuildIndex(rules);

            Set<String> triggeredGroups = new HashSet<>();
            List<Rule> candidates = indexer.findCandidates("1", RuleEnvironment.DEFAULT, "DEFAULT", triggeredGroups);

            // facts 为空集合，仅保留无字段引用的规则（mockRules 的 199 条）
            Set<String> emptyFacts = new HashSet<>();
            List<Rule> filtered = indexer.filterByFacts(candidates, emptyFacts);
            assertThat(filtered).hasSize(199);
            assertThat(filtered).extracting(Rule::getCode).doesNotContain("R_EXPR");
        }

        @Test
        @DisplayName("filterByFacts：索引未启用时返回原候选列表")
        void shouldReturnOriginalCandidatesWhenIndexDisabled() {
            List<Rule> rules = mockRules(50);
            indexer.rebuildIndex(rules);
            assertThat(indexer.isIndexEnabled()).isFalse();

            Set<String> factKeys = new HashSet<>();
            factKeys.add("amount");
            List<Rule> filtered = indexer.filterByFacts(rules, factKeys);
            // 索引未启用时直接返回原列表
            assertThat(filtered).isSameAs(rules);
        }

        @Test
        @DisplayName("extractFields：从条件表达式中提取字段（过滤关键字与首字母大写标识符）")
        void shouldExtractFieldsFromExpression() {
            // 表达式包含变量、关键字、首字母大写标识符、数字
            Rule rule = mockRuleWithExpr("R001", "规则", 10,
                    "amount > 1000 && score >= 0.5 && true && Math.max(a, b) > 0");

            Set<String> fields = indexer.extractFields(rule);

            // 应提取 amount/score/a/b，过滤 true/Math/max（关键字或首字母大写）
            assertThat(fields).contains("amount", "score", "a", "b");
            assertThat(fields).doesNotContain("true", "Math", "max");
        }

        @Test
        @DisplayName("extractFields：无 RuleDefinition 时返回空集合")
        void shouldReturnEmptyWhenNoDefinition() {
            Rule rule = mockRule("R001", "规则", 10);

            Set<String> fields = indexer.extractFields(rule);

            assertThat(fields).isEmpty();
        }

        @Test
        @DisplayName("extractFields：conditionExpression 为空时返回空集合")
        void shouldReturnEmptyWhenExpressionBlank() {
            Rule rule = mockRuleWithEx    /**
     * 测试分组：「兼容方法」等
     */
pr("R001", "规则", 10, "");

            Set<String> fields = indexer.extractFields(rule);

            assertThat(fields).isEmpty();
        }
    }

    @Nested
    @DisplayName("兼容方法")
    class CompatibilityCases {

        @Test
        @DisplayName("findCandidates 三参数重载默认 environment=default")
        void shouldUseDefaultEnvironmentForThreeArgOverload() {
            List<Rule> rules = mockRules(200);
            indexer.rebuildIndex(rules);

            Set<String> triggeredGroups = new HashSet<>();
            // 三参数重载等价于 environment=default
            List<Rule> candidates = indexer.findCandidates("1", "DEFAULT", triggeredGroups);
            assertThat(candidates).hasSize(200);
        }

        @Test
        @DisplayName("getIndexStats 返回包含索引关键信息的字符串")
        void shouldReturnStatsString() {
            List<Rule> rules = mockRules(200);
            indexer.rebuildIndex(rules);

            String stats = indexer.getIndexStats();
            assertThat(stats).contains("enabled=true");
            assertThat(stats).contains("totalRules=200");
        }

        @Test
        @DisplayName("getEnvironmentIndexSize 返回环境索引大小")
        void shouldReturnEnvironmentIndexSize() {
            List<Rule> rules = new ArrayList<>(mockRules(199));
            Rule prodRule = mockRule("R_PROD", "生产规则", 10);
            when(prodRule.getEnvironment()).thenReturn("prod");
            rules.add(prodRule);
            indexer.rebuildIndex(rules);

            // 租户 "1" 有 default 和 prod 两个环境
            assertThat(indexer.getEnvironmentIndexSize()).isEqualTo(2);
        }
    }
}
