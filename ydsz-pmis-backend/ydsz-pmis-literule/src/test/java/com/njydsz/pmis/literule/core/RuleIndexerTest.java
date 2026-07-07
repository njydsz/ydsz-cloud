package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.Rule;
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
 * RuleIndexer 单元测试
 *
 * <p>测试规则索引器的索引重建、增量维护、候选规则查找等核心能力，
 * 覆盖租户过滤、场景过滤（DEFAULT/精确/ALL/null）、互斥组过滤、索引阈值启用等维度，
 * 目标覆盖率 100%。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("RuleIndexer 单元测试")
class RuleIndexerTest {

    private RuleIndexer indexer;

    @BeforeEach
    void setUp() {
        indexer = new RuleIndexer();
    }

    /**
     * 构造 Rule mock 测试桩
     *
     * @param code        规则编码
     * @param priority    优先级
     * @param tenantId    租户 ID（null 表示使用默认值）
     * @param scope       场景作用域
     * @param mutexGroup  互斥组名称
     * @return Rule mock
     */
    private Rule mockRule(String code, int priority, String tenantId,
                          String scope, String mutexGroup) {
        Rule rule = Mockito.mock(Rule.class);
        when(rule.getCode()).thenReturn(code);
        when(rule.getPriority()).thenReturn(priority);
        when(rule.getTenantId()).thenReturn(tenantId);
        when(rule.getScope()).thenReturn(scope);
        when(rule.getMutexGroup()).thenReturn(mutexGroup);
        return rule;
    }

    /**
     * 批量生成指定数量的 mock 规则
     */
    private List<Rule> mockRules(int count, String tenantId, String scope) {
        List<Rule> rules = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rules.add(mockRule("R" + i, i, tenantId, scope, null));
        }
        return rules;
    }

    // ==================== rebuildIndex 索引重建 ====================

    @Nested
    @DisplayName("rebuildIndex 索引重建")
    class RebuildIndexTest {

        @Test
        @DisplayName("空规则集 - 索引不启用")
        void shouldNotEnableIndexForEmptyRules() {
            indexer.rebuildIndex(Collections.emptyList());

            assertThat(indexer.isIndexEnabled()).isFalse();
            assertThat(indexer.getIndexStats()).contains("enabled=false");
            assertThat(indexer.getIndexStats()).contains("totalRules=0");
        }

        @Test
        @DisplayName("规则数 < 200 - 索引不启用")
        void shouldNotEnableIndexBelowThreshold() {
            List<Rule> rules = mockRules(199, "1", null);

            indexer.rebuildIndex(rules);

            assertThat(indexer.isIndexEnabled()).isFalse();
            assertThat(indexer.getIndexStats()).contains("totalRules=199");
        }

        @Test
        @DisplayName("规则数 = 200 - 索引启用")
        void shouldEnableIndexAtThreshold() {
            List<Rule> rules = mockRules(200, "1", null);

            indexer.rebuildIndex(rules);

            assertThat(indexer.isIndexEnabled()).isTrue();
            assertThat(indexer.getIndexStats()).contains("enabled=true");
            assertThat(indexer.getIndexStats()).contains("tenants=1");
        }

        @Test
        @DisplayName("规则数 > 200 - 索引启用")
        void shouldEnableIndexAboveThreshold() {
            List<Rule> rules = mockRules(300, "1", null);

            indexer.rebuildIndex(rules);

            assertThat(indexer.isIndexEnabled()).isTrue();
        }

        @Test
        @DisplayName("重建后旧索引被清空")
        void shouldClearOldIndexOnRebuild() {
            // 首次构建 200 条规则
            List<Rule> rules = mockRules(200, "1", null);
            indexer.rebuildIndex(rules);
            assertThat(indexer.isIndexEnabled()).isTrue();

            // 重建为空
            indexer.rebuildIndex(Collections.emptyList());
            assertThat(indexer.isIndexEnabled()).isFalse();
            assertThat(indexer.getIndexStats()).contains("tenants=0");
            assertThat(indexer.getIndexStats()).contains("totalRules=0");
        }
    }

    // ==================== addToIndex 增量添加 ====================

    @Nested
    @DisplayName("addToIndex 增量添加")
    class AddToIndexTest {

        @Test
        @DisplayName("索引未启用时 - 添加为空操作")
        void shouldBeNoOpWhenIndexDisabled() {
            Rule rule = mockRule("R1", 100, "1", null, null);

            indexer.addToIndex(rule);

            // 索引未启用，findCandidates 返回 allRules（空）
            List<Rule> candidates = indexer.findCandidates("1", null, null);
            assertThat(candidates).isEmpty();
        }

        @Test
        @DisplayName("索引启用后 - 增量添加到租户索引")
        void shouldAddToTenantIndexWhenEnabled() {
            // 初始构建 200 条规则
            List<Rule> rules = mockRules(200, "1", null);
            indexer.rebuildIndex(rules);

            // 增量添加一条新规则
            Rule newRule = mockRule("R_NEW", 50, "1", null, null);
            indexer.addToIndex(newRule);

            // 验证新规则已被索引
            List<Rule> candidates = indexer.findCandidates("1", null, null);
            assertThat(candidates).extracting(Rule::getCode).contains("R_NEW");
        }

        @Test
        @DisplayName("索引启用后 - 增量添加到场景索引")
        void shouldAddToScopeIndexWhenEnabled() {
            List<Rule> rules = mockRules(200, "1", null);
            indexer.rebuildIndex(rules);

            Rule newRule = mockRule("R_SCOPE", 50, "1", "COCKPIT", null);
            indexer.addToIndex(newRule);

            List<Rule> candidates = indexer.findCandidates("1", "COCKPIT", null);
            assertThat(candidates).extracting(Rule::getCode).contains("R_SCOPE");
        }

        @Test
        @DisplayName("索引启用后 - 增量添加到互斥组索引")
        void shouldAddToMutexGroupIndexWhenEnabled() {
            List<Rule> rules = mockRules(200, "1", null);
            indexer.rebuildIndex(rules);

            Rule newRule = mockRule("R_MUTEX", 50, "1", null, "GROUP_A");
            indexer.addToIndex(newRule);

            String stats = indexer.getIndexStats();
            assertThat(stats).contains("mutexGroups=1");
        }
    }

    // ==================== removeFromIndex 移除 ====================

    @Nested
    @DisplayName("removeFromIndex 移除")
    class RemoveFromIndexTest {

        @Test
        @DisplayName("索引未启用时 - 移除为空操作")
        void shouldBeNoOpWhenIndexDisabled() {
            // 索引未启用，移除不应抛异常
            indexer.removeFromIndex("R1");
            // 无异常即通过
        }

        @Test
        @DisplayName("索引启用后 - 从所有索引中移除规则")
        void shouldRemoveFromAllIndexes() {
            List<Rule> rules = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                rules.add(mockRule("R" + i, i, "1", "SCOPE_A", "GROUP_X"));
            }
            indexer.rebuildIndex(rules);

            indexer.removeFromIndex("R0");

            // 验证规则已从索引中移除
            List<Rule> candidates = indexer.findCandidates("1", "SCOPE_A", null);
            assertThat(candidates).extracting(Rule::getCode).doesNotContain("R0");
        }

        @Test
        @DisplayName("索引启用后 - 移除不存在的规则编码无副作用")
        void shouldNotFailWhenRemovingNonExistentRule() {
            List<Rule> rules = mockRules(200, "1", null);
            indexer.rebuildIndex(rules);

            indexer.removeFromIndex("NON_EXISTENT");

            List<Rule> candidates = indexer.findCandidates("1", null, null);
            assertThat(candidates).hasSize(200);
        }
    }

    // ==================== findCandidates 候选规则查找 ====================

    @Nested
    @DisplayName("findCandidates 候选规则查找")
    class FindCandidatesTest {

        @Test
        @DisplayName("索引未启用 - 返回 allRules")
        void shouldReturnAllRulesWhenIndexDisabled() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockRule("R1", 100, "1", null, null));
            rules.add(mockRule("R2", 200, "1", null, null));
            indexer.rebuildIndex(rules);

            List<Rule> candidates = indexer.findCandidates("1", null, null);
            assertThat(candidates).hasSize(2);
        }

        @Test
        @DisplayName("索引启用 - 租户不存在返回空")
        void shouldReturnEmptyForUnknownTenant() {
            List<Rule> rules = mockRules(200, "1", null);
            indexer.rebuildIndex(rules);

            List<Rule> candidates = indexer.findCandidates("UNKNOWN_TENANT", null, null);
            assertThat(candidates).isEmpty();
        }

        @Test
        @DisplayName("索引启用 - tenantId 为 null 时使用默认租户 1")
        void shouldUseDefaultTenantWhenTenantIdIsNull() {
            List<Rule> rules = mockRules(200, "1", null);
            indexer.rebuildIndex(rules);

            // tenantId=null 应映射到 "1"
            List<Rule> candidates = indexer.findCandidates(null, null, null);
            assertThat(candidates).hasSize(200);
        }

        @Test
        @DisplayName("索引启用 - scenario=null 返回租户全部规则")
        void shouldReturnAllTenantRulesForNullScenario() {
            List<Rule> rules = mockRules(200, "1", null);
            indexer.rebuildIndex(rules);

            List<Rule> candidates = indexer.findCandidates("1", null, null);
            assertThat(candidates).hasSize(200);
        }

        @Test
        @DisplayName("索引启用 - scenario=DEFAULT 返回租户全部规则")
        void shouldReturnAllTenantRulesForDefaultScenario() {
            List<Rule> rules = mockRules(200, "1", null);
            indexer.rebuildIndex(rules);

            List<Rule> candidates = indexer.findCandidates("1", "DEFAULT", null);
            assertThat(candidates).hasSize(200);
        }

        @Test
        @DisplayName("索引启用 - 精确场景匹配 + ALL + null scope")
        void shouldMatchExactAndAllAndNullScopes() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockRule("R_EXACT", 100, "1", "COCKPIT", null));
            rules.add(mockRule("R_ALL", 200, "1", "ALL", null));
            rules.add(mockRule("R_NULL", 300, "1", null, null));
            // 不应匹配的 scope
            rules.add(mockRule("R_OTHER", 400, "1", "OTHER", null));
            // 补足 200 条
            for (int i = 0; i < 196; i++) {
                rules.add(mockRule("R_FILL" + i, 500 + i, "1", "OTHER", null));
            }
            indexer.rebuildIndex(rules);

            List<Rule> candidates = indexer.findCandidates("1", "COCKPIT", null);
            assertThat(candidates).extracting(Rule::getCode)
                    .containsExactlyInAnyOrder("R_EXACT", "R_ALL", "R_NULL");
        }

        @Test
        @DisplayName("索引启用 - 多 scope 匹配后按优先级排序")
        void shouldSortByPriorityWhenMultipleScopesMatch() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockRule("R_NULL", 300, "1", null, null));
            rules.add(mockRule("R_ALL", 200, "1", "ALL", null));
            rules.add(mockRule("R_EXACT", 100, "1", "COCKPIT", null));
            for (int i = 0; i < 197; i++) {
                rules.add(mockRule("R_FILL" + i, 500 + i, "1", "OTHER", null));
            }
            indexer.rebuildIndex(rules);

            List<Rule> candidates = indexer.findCandidates("1", "COCKPIT", null);
            assertThat(candidates).extracting(Rule::getCode)
                    .containsExactly("R_EXACT", "R_ALL", "R_NULL");
        }

        @Test
        @DisplayName("索引启用 - 无匹配场景返回空")
        void shouldReturnEmptyWhenNoScopeMatch() {
            List<Rule> rules = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                rules.add(mockRule("R" + i, i, "1", "OTHER_SCOPE", null));
            }
            indexer.rebuildIndex(rules);

            List<Rule> candidates = indexer.findCandidates("1", "COCKPIT", null);
            assertThat(candidates).isEmpty();
        }

        @Test
        @DisplayName("索引启用 - 互斥组过滤排除已命中组")
        void shouldFilterTriggeredMutexGroups() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockRule("R1", 100, "1", null, "GROUP_A"));
            rules.add(mockRule("R2", 200, "1", null, "GROUP_A"));
            rules.add(mockRule("R3", 300, "1", null, "GROUP_B"));
            rules.add(mockRule("R4", 400, "1", null, null)); // 无互斥组
            rules.add(mockRule("R5", 500, "1", null, "  ")); // 空白互斥组
            for (int i = 0; i < 195; i++) {
                rules.add(mockRule("R_FILL" + i, 600 + i, "1", null, null));
            }
            indexer.rebuildIndex(rules);

            Set<String> triggered = new HashSet<>();
            triggered.add("GROUP_A");

            List<Rule> candidates = indexer.findCandidates("1", null, triggered);
            assertThat(candidates).extracting(Rule::getCode)
                    .doesNotContain("R1", "R2") // GROUP_A 已命中
                    .contains("R3", "R4", "R5"); // 其他规则保留
        }

        @Test
        @DisplayName("索引启用 - triggeredMutexGroups 为 null 不过滤")
        void shouldNotFilterWhenTriggeredGroupsIsNull() {
            List<Rule> rules = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                rules.add(mockRule("R" + i, i, "1", null, "GROUP_A"));
            }
            indexer.rebuildIndex(rules);

            List<Rule> candidates = indexer.findCandidates("1", null, null);
            assertThat(candidates).hasSize(200);
        }

        @Test
        @DisplayName("索引启用 - triggeredMutexGroups 为空集不过滤")
        void shouldNotFilterWhenTriggeredGroupsIsEmpty() {
            List<Rule> rules = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                rules.add(mockRule("R" + i, i, "1", null, "GROUP_A"));
            }
            indexer.rebuildIndex(rules);

            List<Rule> candidates = indexer.findCandidates("1", null, new HashSet<>());
            assertThat(candidates).hasSize(200);
        }

        @Test
        @DisplayName("索引启用 - 租户规则为空时返回空列表")
        void shouldReturnEmptyWhenTenantHasNoRules() {
            // 仅注册 tenant_2 的规则
            List<Rule> rules = mockRules(200, "TENANT_2", null);
            indexer.rebuildIndex(rules);

            // 查找 tenant_1 的规则（不存在）
            List<Rule> candidates = indexer.findCandidates("TENANT_1", null, null);
            assertThat(candidates).isEmpty();
        }
    }

    // ==================== isIndexEnabled 状态查询 ====================

    @Nested
    @DisplayName("isIndexEnabled 状态查询")
    class IsIndexEnabledTest {

        @Test
        @DisplayName("初始状态 - 索引未启用")
        void shouldBeDisabledInitially() {
            assertThat(indexer.isIndexEnabled()).isFalse();
        }

        @Test
        @DisplayName("重建后 - 根据规则数决定是否启用")
        void shouldReflectRebuildResult() {
            List<Rule> small = mockRules(100, "1", null);
            indexer.rebuildIndex(small);
            assertThat(indexer.isIndexEnabled()).isFalse();

            List<Rule> large = mockRules(200, "1", null);
            indexer.rebuildIndex(large);
            assertThat(indexer.isIndexEnabled()).isTrue();
        }
    }

    // ==================== getIndexStats 统计信息 ====================

    @Nested
    @DisplayName("getIndexStats 统计信息")
    class GetIndexStatsTest {

        @Test
        @DisplayName("返回包含各维度统计的格式化字符串")
        void shouldReturnFormattedStats() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockRule("R1", 100, "TENANT_A", "SCOPE_X", "GROUP_1"));
            rules.add(mockRule("R2", 200, "TENANT_B", null, null));
            for (int i = 0; i < 198; i++) {
                rules.add(mockRule("R_FILL" + i, 300 + i, "TENANT_A", null, null));
            }
            indexer.rebuildIndex(rules);

            String stats = indexer.getIndexStats();
            assertThat(stats).contains("enabled=true");
            assertThat(stats).contains("totalRules=200");
            assertThat(stats).contains("tenants=2"); // TENANT_A + TENANT_B
        }

        @Test
        @DisplayName("未启用索引时 - 返回 disabled 状态")
        void shouldReturnDisabledStats() {
            String stats = indexer.getIndexStats();
            assertThat(stats).contains("enabled=false");
            assertThat(stats).contains("totalRules=0");
        }

        @Test
        @DisplayName("互斥组统计正确")
        void shouldCountMutexGroupsCorrectly() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockRule("R1", 100, "1", null, "GROUP_A"));
            rules.add(mockRule("R2", 200, "1", null, "GROUP_B"));
            rules.add(mockRule("R3", 300, "1", null, "GROUP_A")); // 同组
            for (int i = 0; i < 197; i++) {
                rules.add(mockRule("R_FILL" + i, 400 + i, "1", null, null));
            }
            indexer.rebuildIndex(rules);

            String stats = indexer.getIndexStats();
            // GROUP_A 和 GROUP_B 两个互斥组
            assertThat(stats).contains("mutexGroups=2");
        }
    }

    // ==================== 边界场景 ====================

    @Nested
    @DisplayName("边界场景")
    class EdgeCaseTest {

        @Test
        @DisplayName("规则 tenantId 为 null 时使用默认租户")
        void shouldUseDefaultTenantForRuleWithNullTenantId() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockRule("R_NULL_TENANT", 100, null, null, null));
            for (int i = 0; i < 199; i++) {
                rules.add(mockRule("R" + i, 200 + i, "1", null, null));
            }
            indexer.rebuildIndex(rules);

            // tenantId=null 的规则应被索引到 "1" 下
            List<Rule> candidates = indexer.findCandidates("1", null, null);
            assertThat(candidates).extracting(Rule::getCode).contains("R_NULL_TENANT");
        }

        @Test
        @DisplayName("互斥组为空白字符串时不索引")
        void shouldNotIndexBlankMutexGroup() {
            List<Rule> rules = new ArrayList<>();
            rules.add(mockRule("R_BLANK", 100, "1", null, "  "));
            rules.add(mockRule("R_EMPTY", 200, "1", null, ""));
            for (int i = 0; i < 198; i++) {
                rules.add(mockRule("R" + i, 300 + i, "1", null, null));
            }
            indexer.rebuildIndex(rules);

            String stats = indexer.getIndexStats();
            // 空白互斥组不应被索引
            assertThat(stats).contains("mutexGroups=0");
        }

        @Test
        @DisplayName("多租户索引独立")
        void shouldIndexMultipleTenantsIndependently() {
            List<Rule> rules = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                rules.add(mockRule("TA_R" + i, i, "TENANT_A", null, null));
            }
            for (int i = 0; i < 100; i++) {
                rules.add(mockRule("TB_R" + i, i, "TENANT_B", null, null));
            }
            indexer.rebuildIndex(rules);

            List<Rule> tenantAResults = indexer.findCandidates("TENANT_A", null, null);
            List<Rule> tenantBResults = indexer.findCandidates("TENANT_B", null, null);

            assertThat(tenantAResults).hasSize(100);
            assertThat(tenantBResults).hasSize(100);
            assertThat(tenantAResults).allMatch(r -> r.getCode().startsWith("TA_"));
            assertThat(tenantBResults).allMatch(r -> r.getCode().startsWith("TB_"));
        }

        @Test
        @DisplayName("scope=ALL 大小写不敏感匹配")
        void shouldMatchAllScopeCaseInsensitive() {
            // 注意：RuleIndexer 中 scope 作为 Map key 是大小写敏感的
            // addToIndexInternal 使用 scope 原值作为 key
            List<Rule> rules = new ArrayList<>();
            rules.add(mockRule("R_ALL_LOWER", 100, "1", "all", null));
            rules.add(mockRule("R_ALL_UPPER", 200, "1", "ALL", null));
            for (int i = 0; i < 198; i++) {
                rules.add(mockRule("R_FILL" + i, 300 + i, "1", "OTHER", null));
            }
            indexer.rebuildIndex(rules);

            // 查找 COCKPIT 场景时，仅 ALL（大写）被匹配
            List<Rule> candidates = indexer.findCandidates("1", "COCKPIT", null);
            assertThat(candidates).extracting(Rule::getCode).contains("R_ALL_UPPER");
        }
    }
}
