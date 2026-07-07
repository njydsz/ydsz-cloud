package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEnvironment;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多环境隔离单元测试（P1-5）
 *
 * <p>测试 {@link RuleEnvironment} 常量校验、{@link RuleContext} 环境维度、
 * {@link RuleDefinition} 默认环境、{@link DefaultRuleEngine} 评估时环境过滤、
 * {@link RuleIndexer} 环境索引构建与查询、{@link RuleConfigProvider} 环境查询、
 * 以及多环境混合场景。
 *
 * <p>测试风格参考 {@link DefaultRuleEngineTest}，使用 Mockito.mock 手动创建 Rule 桩。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@DisplayName("多环境隔离单元测试（P1-5）")
class RuleEnvironmentTest {

    private DefaultRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DefaultRuleEngine();
    }

    @AfterEach
    void tearDown() {
        engine.destroy();
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造 Rule mock 测试桩（默认租户 1、指定环境、scope=null、无互斥组）
     *
     * @param code        规则编码
     * @param priority    优先级
     * @param environment 环境标识
     * @return Rule mock
     */
    private Rule mockRule(String code, int priority, String environment) {
        Rule rule = Mockito.mock(Rule.class);
        when(rule.getCode()).thenReturn(code);
        when(rule.getName()).thenReturn("规则-" + code);
        when(rule.getCategory()).thenReturn("TEST");
        when(rule.getPriority()).thenReturn(priority);
        when(rule.getTenantId()).thenReturn("1");
        when(rule.getEnvironment()).thenReturn(environment);
        RuleResult result = RuleResult.builder()
                .ruleCode(code)
                .ruleName("规则-" + code)
                .category("TEST")
                .triggered(true)
                .severity(RuleSeverity.RED)
                .title("标题-" + code)
                .description("描述-" + code)
                .threshold("amount > 100")
                .build();
        when(rule.evaluate(any())).thenReturn(result);
        return rule;
    }

    /**
     * 构造指定租户和环境的上下文
     */
    private RuleContext contextWithEnv(String tenantId, String environment) {
        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", 1000);
        return RuleContext.of(facts, "DEFAULT", "TEST", "trace-1", tenantId, environment);
    }

    /**
     * 构造指定环境的上下文（租户 1）
     */
    private RuleContext contextWithEnv(String environment) {
        return contextWithEnv("1", environment);
    }

    // ==================== 1. RuleEnvironment 常量校验 ====================

    @Nested
    @DisplayName("RuleEnvironment 常量校验")
    class RuleEnvironmentValidationTest {

        @Test
        @DisplayName("isValid - default/dev/staging/prod 均合法")
        void shouldValidateAllValidEnvironments() {
            assertThat(RuleEnvironment.isValid(RuleEnvironment.DEFAULT)).isTrue();
            assertThat(RuleEnvironment.isValid(RuleEnvironment.DEV)).isTrue();
            assertThat(RuleEnvironment.isValid(RuleEnvironment.STAGING)).isTrue();
            assertThat(RuleEnvironment.isValid(RuleEnvironment.PROD)).isTrue();
        }

        @Test
        @DisplayName("isValid - 非法环境标识返回 false")
        void shouldRejectInvalidEnvironments() {
            assertThat(RuleEnvironment.isValid("test")).isFalse();
            assertThat(RuleEnvironment.isValid("production")).isFalse();
            assertThat(RuleEnvironment.isValid("")).isFalse();
            assertThat(RuleEnvironment.isValid(null)).isFalse();
            assertThat(RuleEnvironment.isValid("DEV")).isFalse(); // 大小写敏感
        }

        @Test
        @DisplayName("常量值 - default/dev/staging/prod")
        void shouldHaveCorrectConstantValues() {
            assertThat(RuleEnvironment.DEFAULT).isEqualTo("default");
            assertThat(RuleEnvironment.DEV).isEqualTo("dev");
            assertThat(RuleEnvironment.STAGING).isEqualTo("staging");
            assertThat(RuleEnvironment.PROD).isEqualTo("prod");
        }
    }

    // ==================== 2. RuleContext 环境维度 ====================

    @Nested
    @DisplayName("RuleContext 环境维度")
    class RuleContextEnvironmentTest {

        @Test
        @DisplayName("of 带 environment 参数 - getEnvironment 返回指定值")
        void shouldReturnSpecifiedEnvironment() {
            Map<String, Object> facts = new HashMap<>();
            RuleContext ctx = RuleContext.of(facts, "DEFAULT", "TEST", "trace-1", "1", "dev");

            assertThat(ctx.getEnvironment()).isEqualTo("dev");
        }

        @Test
        @DisplayName("of 不带 environment 参数 - 默认 default")
        void shouldDefaultToDefaultEnvironment() {
            Map<String, Object> facts = new HashMap<>();
            RuleContext ctx = RuleContext.of(facts, "DEFAULT", "TEST", "trace-1", "1");

            assertThat(ctx.getEnvironment()).isEqualTo(RuleEnvironment.DEFAULT);
        }

        @Test
        @DisplayName("of 3 参数版本 - 默认 default")
        void shouldDefaultEnvironmentForThreeArgOf() {
            Map<String, Object> facts = new HashMap<>();
            RuleContext ctx = RuleContext.of(facts, "DEFAULT", "TEST");

            assertThat(ctx.getEnvironment()).isEqualTo(RuleEnvironment.DEFAULT);
            assertThat(ctx.getTenantId()).isEqualTo("1");
        }

        @Test
        @DisplayName("of 1 参数版本 - 默认 default")
        void shouldDefaultEnvironmentForSingleArgOf() {
            Map<String, Object> facts = new HashMap<>();
            RuleContext ctx = RuleContext.of(facts);

            assertThat(ctx.getEnvironment()).isEqualTo(RuleEnvironment.DEFAULT);
        }

        @Test
        @DisplayName("of 4 参数版本 - 默认 default")
        void shouldDefaultEnvironmentForFourArgOf() {
            Map<String, Object> facts = new HashMap<>();
            RuleContext ctx = RuleContext.of(facts, "DEFAULT", "TEST", "trace-1");

            assertThat(ctx.getEnvironment()).isEqualTo(RuleEnvironment.DEFAULT);
            assertThat(ctx.getTenantId()).isEqualTo("1");
        }

        @Test
        @DisplayName("of 带 null environment - 转为 default")
        void shouldConvertNullEnvironmentToDefault() {
            Map<String, Object> facts = new HashMap<>();
            RuleContext ctx = RuleContext.of(facts, "DEFAULT", "TEST", "trace-1", "1", null);

            assertThat(ctx.getEnvironment()).isEqualTo(RuleEnvironment.DEFAULT);
        }

        @Test
        @DisplayName("toString 包含 environment 字段")
        void shouldIncludeEnvironmentInToString() {
            Map<String, Object> facts = new HashMap<>();
            RuleContext ctx = RuleContext.of(facts, "DEFAULT", "TEST", "trace-1", "1", "prod");

            assertThat(ctx.toString()).contains("environment=prod");
        }
    }

    // ==================== 3. RuleDefinition 默认 environment ====================

    @Nested
    @DisplayName("RuleDefinition 默认 environment")
    class RuleDefinitionDefaultEnvTest {

        @Test
        @DisplayName("builder 不指定 environment - 默认 default")
        void shouldDefaultToDefaultEnvironment() {
            RuleDefinition def = RuleDefinition.builder()
                    .code("R1")
                    .name("测试规则")
                    .build();

            assertThat(def.getEnvironment()).isEqualTo(RuleEnvironment.DEFAULT);
        }

        @Test
        @DisplayName("builder 指定 environment - 返回指定值")
        void shouldReturnSpecifiedEnvironment() {
            RuleDefinition def = RuleDefinition.builder()
                    .code("R1")
                    .name("测试规则")
                    .environment("dev")
                    .build();

            assertThat(def.getEnvironment()).isEqualTo("dev");
        }

        @Test
        @DisplayName("NoArgsConstructor 创建 - environment 为 default")
        void shouldDefaultEnvironmentWithNoArgsConstructor() {
            RuleDefinition def = new RuleDefinition();

            assertThat(def.getEnvironment()).isEqualTo(RuleEnvironment.DEFAULT);
        }
    }

    // ==================== 4. DefaultRuleEngine 环境过滤 ====================

    @Nested
    @DisplayName("DefaultRuleEngine 环境过滤")
    class EngineEnvironmentFilterTest {

        @Test
        @DisplayName("规则 environment=default 匹配任何上下文环境（dev）")
        void shouldMatchDefaultRuleToAnyContextEnvironment() {
            Rule rule = mockRule("R1", 100, RuleEnvironment.DEFAULT);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(contextWithEnv("dev"));

            assertThat(results).hasSize(1);
            verify(rule).evaluate(any());
        }

        @Test
        @DisplayName("规则 environment=default 匹配上下文 environment=prod")
        void shouldMatchDefaultRuleToProdContext() {
            Rule rule = mockRule("R1", 100, RuleEnvironment.DEFAULT);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(contextWithEnv("prod"));

            assertThat(results).hasSize(1);
            verify(rule).evaluate(any());
        }

        @Test
        @DisplayName("规则 environment=dev 仅匹配 context.environment=dev")
        void shouldMatchDevRuleOnlyToDevContext() {
            Rule rule = mockRule("R1", 100, RuleEnvironment.DEV);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(contextWithEnv("dev"));

            assertThat(results).hasSize(1);
            verify(rule).evaluate(any());
        }

        @Test
        @DisplayName("规则 environment=dev 不匹配 context.environment=prod")
        void shouldNotMatchDevRuleToProdContext() {
            Rule rule = mockRule("R1", 100, RuleEnvironment.DEV);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(contextWithEnv("prod"));

            assertThat(results).isEmpty();
            verify(rule, never()).evaluate(any());
        }

        @Test
        @DisplayName("规则 environment=prod 不匹配 context.environment=dev")
        void shouldNotMatchProdRuleToDevContext() {
            Rule rule = mockRule("R1", 100, RuleEnvironment.PROD);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(contextWithEnv("dev"));

            assertThat(results).isEmpty();
            verify(rule, never()).evaluate(any());
        }

        @Test
        @DisplayName("规则 environment=staging 仅匹配 context.environment=staging")
        void shouldMatchStagingRuleOnlyToStagingContext() {
            Rule rule = mockRule("R1", 100, RuleEnvironment.STAGING);
            engine.register(rule);

            // 匹配 staging
            List<RuleResult> matchResults = engine.evaluate(contextWithEnv("staging"));
            assertThat(matchResults).hasSize(1);

            // 不匹配 dev
            engine.resetStats();
            List<RuleResult> mismatchResults = engine.evaluate(contextWithEnv("dev"));
            assertThat(mismatchResults).isEmpty();
        }

        @Test
        @DisplayName("规则 environment=null 视为 default，匹配任何上下文")
        void shouldTreatNullEnvironmentAsDefault() {
            Rule rule = mockRule("R1", 100, null);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(contextWithEnv("dev"));

            assertThat(results).hasSize(1);
            verify(rule).evaluate(any());
        }
    }

    // ==================== 5. DefaultRuleEngine dryRun 环境隔离 ====================

    @Nested
    @DisplayName("dryRun 环境隔离")
    class DryRunEnvironmentTest {

        @Test
        @DisplayName("dryRun 同样遵循环境隔离 - dev 规则在 prod 上下文被跳过")
        void shouldEnforceEnvironmentIsolationInDryRun() {
            Rule rule = mockRule("R1", 100, RuleEnvironment.DEV);
            engine.register(rule);

            List<RuleResult> results = engine.dryRun(contextWithEnv("prod"));

            assertThat(results).isEmpty();
            verify(rule, never()).evaluate(any());
        }

        @Test
        @DisplayName("dryRun - default 规则在任何环境均被评估")
        void shouldEvaluateDefaultRuleInAnyEnvInDryRun() {
            Rule rule = mockRule("R1", 100, RuleEnvironment.DEFAULT);
            engine.register(rule);

            List<RuleResult> results = engine.dryRun(contextWithEnv("prod"));

            assertThat(results).hasSize(1);
            verify(rule).evaluate(any());
        }
    }

    // ==================== 6. 多环境混合场景 ====================

    @Nested
    @DisplayName("多环境混合场景")
    class MixedEnvironmentTest {

        @Test
        @DisplayName("default + dev + prod 规则共存 - dev 上下文仅评估 default + dev")
        void shouldEvaluateDefaultAndDevRulesInDevContext() {
            Rule defaultRule = mockRule("R_DEFAULT", 10, RuleEnvironment.DEFAULT);
            Rule devRule = mockRule("R_DEV", 20, RuleEnvironment.DEV);
            Rule prodRule = mockRule("R_PROD", 30, RuleEnvironment.PROD);
            engine.register(defaultRule);
            engine.register(devRule);
            engine.register(prodRule);

            List<RuleResult> results = engine.evaluate(contextWithEnv("dev"));

            // default + dev 规则被评估，prod 规则被跳过
            assertThat(results).hasSize(2);
            assertThat(results).extracting(RuleResult::getRuleCode)
                    .containsExactlyInAnyOrder("R_DEFAULT", "R_DEV");
            verify(defaultRule).evaluate(any());
            verify(devRule).evaluate(any());
            verify(prodRule, never()).evaluate(any());
        }

        @Test
        @DisplayName("default + dev + prod 规则共存 - prod 上下文仅评估 default + prod")
        void shouldEvaluateDefaultAndProdRulesInProdContext() {
            Rule defaultRule = mockRule("R_DEFAULT", 10, RuleEnvironment.DEFAULT);
            Rule devRule = mockRule("R_DEV", 20, RuleEnvironment.DEV);
            Rule prodRule = mockRule("R_PROD", 30, RuleEnvironment.PROD);
            engine.register(defaultRule);
            engine.register(devRule);
            engine.register(prodRule);

            List<RuleResult> results = engine.evaluate(contextWithEnv("prod"));

            assertThat(results).hasSize(2);
            assertThat(results).extracting(RuleResult::getRuleCode)
                    .containsExactlyInAnyOrder("R_DEFAULT", "R_PROD");
            verify(devRule, never()).evaluate(any());
        }

        @Test
        @DisplayName("default 上下文仅评估 default 规则（非 default 规则不匹配）")
        void shouldEvaluateOnlyDefaultRulesInDefaultContext() {
            Rule defaultRule = mockRule("R_DEFAULT", 10, RuleEnvironment.DEFAULT);
            Rule devRule = mockRule("R_DEV", 20, RuleEnvironment.DEV);
            engine.register(defaultRule);
            engine.register(devRule);

            List<RuleResult> results = engine.evaluate(contextWithEnv(RuleEnvironment.DEFAULT));

            // default 上下文仅评估 default 规则
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R_DEFAULT");
            verify(devRule, never()).evaluate(any());
        }

        @Test
        @DisplayName("环境与租户正交 - 不同租户的同环境规则互不影响")
        void shouldOrthogonalWithTenantIsolation() {
            Rule tenant1Dev = mockRule("T1_DEV", 10, RuleEnvironment.DEV);
            when(tenant1Dev.getTenantId()).thenReturn("1");

            Rule tenant2Dev = mockRule("T2_DEV", 20, RuleEnvironment.DEV);
            when(tenant2Dev.getTenantId()).thenReturn("2");

            engine.register(tenant1Dev);
            engine.register(tenant2Dev);

            // 租户 1 + dev 上下文：仅评估 T1_DEV
            List<RuleResult> results = engine.evaluate(contextWithEnv("1", "dev"));

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("T1_DEV");
            verify(tenant2Dev, never()).evaluate(any());
        }
    }

    // ==================== 7. RuleIndexer 环境索引 ====================

    @Nested
    @DisplayName("RuleIndexer 环境索引")
    class RuleIndexerEnvironmentTest {

        @Test
        @DisplayName("环境索引构建 - 不同环境的规则分别索引")
        void shouldBuildEnvironmentIndex() {
            RuleIndexer indexer = new RuleIndexer();
            List<Rule> rules = new ArrayList<>();

            // 补足 200 条规则以启用索引
            for (int i = 0; i < 100; i++) {
                Rule rule = Mockito.mock(Rule.class);
                when(rule.getCode()).thenReturn("DEFAULT_" + i);
                when(rule.getPriority()).thenReturn(100 + i);
                when(rule.getTenantId()).thenReturn("1");
                when(rule.getEnvironment()).thenReturn(RuleEnvironment.DEFAULT);
                when(rule.getScope()).thenReturn(null);
                rules.add(rule);
            }
            for (int i = 0; i < 100; i++) {
                Rule rule = Mockito.mock(Rule.class);
                when(rule.getCode()).thenReturn("DEV_" + i);
                when(rule.getPriority()).thenReturn(200 + i);
                when(rule.getTenantId()).thenReturn("1");
                when(rule.getEnvironment()).thenReturn(RuleEnvironment.DEV);
                when(rule.getScope()).thenReturn(null);
                rules.add(rule);
            }

            indexer.rebuildIndex(rules);

            assertThat(indexer.isIndexEnabled()).isTrue();
            // 环境索引至少包含 "1|default" 和 "1|dev" 两个 key
            assertThat(indexer.getEnvironmentIndexSize()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("findCandidates - dev 上下文返回 default + dev 规则")
        void shouldReturnDefaultAndDevRulesForDevContext() {
            RuleIndexer indexer = new RuleIndexer();
            List<Rule> rules = new ArrayList<>();

            // 100 条 default 规则 + 100 条 dev 规则（达到索引阈值）
            for (int i = 0; i < 100; i++) {
                Rule rule = Mockito.mock(Rule.class);
                when(rule.getCode()).thenReturn("DEFAULT_" + i);
                when(rule.getPriority()).thenReturn(100 + i);
                when(rule.getTenantId()).thenReturn("1");
                when(rule.getEnvironment()).thenReturn(RuleEnvironment.DEFAULT);
                when(rule.getScope()).thenReturn(null);
                when(rule.getMutexGroup()).thenReturn(null);
                rules.add(rule);
            }
            for (int i = 0; i < 100; i++) {
                Rule rule = Mockito.mock(Rule.class);
                when(rule.getCode()).thenReturn("DEV_" + i);
                when(rule.getPriority()).thenReturn(200 + i);
                when(rule.getTenantId()).thenReturn("1");
                when(rule.getEnvironment()).thenReturn(RuleEnvironment.DEV);
                when(rule.getScope()).thenReturn(null);
                when(rule.getMutexGroup()).thenReturn(null);
                rules.add(rule);
            }

            indexer.rebuildIndex(rules);

            // dev 上下文应返回 default + dev = 200 条
            List<Rule> devCandidates = indexer.findCandidates("1", "dev", null, null);
            assertThat(devCandidates).hasSize(200);

            // prod 上下文应仅返回 default = 100 条
            List<Rule> prodCandidates = indexer.findCandidates("1", "prod", null, null);
            assertThat(prodCandidates).hasSize(100);
            assertThat(prodCandidates).allMatch(r -> r.getCode().startsWith("DEFAULT_"));
        }

        @Test
        @DisplayName("findCandidates - 旧签名兼容（environment 默认 default）")
        void shouldDefaultToDefaultEnvForOldSignature() {
            RuleIndexer indexer = new RuleIndexer();
            List<Rule> rules = new ArrayList<>();

            for (int i = 0; i < 200; i++) {
                Rule rule = Mockito.mock(Rule.class);
                when(rule.getCode()).thenReturn("R_" + i);
                when(rule.getPriority()).thenReturn(100 + i);
                when(rule.getTenantId()).thenReturn("1");
                when(rule.getEnvironment()).thenReturn(RuleEnvironment.DEFAULT);
                when(rule.getScope()).thenReturn(null);
                when(rule.getMutexGroup()).thenReturn(null);
                rules.add(rule);
            }

            indexer.rebuildIndex(rules);

            // 旧签名应等同于 environment=default
            List<Rule> candidates = indexer.findCandidates("1", null, null);
            assertThat(candidates).hasSize(200);
        }

        @Test
        @DisplayName("getIndexStats 包含 envs 统计")
        void shouldIncludeEnvCountInStats() {
            RuleIndexer indexer = new RuleIndexer();
            List<Rule> rules = new ArrayList<>();

            for (int i = 0; i < 100; i++) {
                Rule rule = Mockito.mock(Rule.class);
                when(rule.getCode()).thenReturn("R_D_" + i);
                when(rule.getPriority()).thenReturn(100 + i);
                when(rule.getTenantId()).thenReturn("1");
                when(rule.getEnvironment()).thenReturn(RuleEnvironment.DEFAULT);
                when(rule.getScope()).thenReturn(null);
                rules.add(rule);
            }
            for (int i = 0; i < 100; i++) {
                Rule rule = Mockito.mock(Rule.class);
                when(rule.getCode()).thenReturn("R_P_" + i);
                when(rule.getPriority()).thenReturn(200 + i);
                when(rule.getTenantId()).thenReturn("1");
                when(rule.getEnvironment()).thenReturn(RuleEnvironment.PROD);
                when(rule.getScope()).thenReturn(null);
                rules.add(rule);
            }

            indexer.rebuildIndex(rules);

            String stats = indexer.getIndexStats();
            assertThat(stats).contains("envs=2");
        }
    }

    // ==================== 8. RuleConfigProvider.loadEnabledRulesByEnv ====================

    @Nested
    @DisplayName("RuleConfigProvider.loadEnabledRulesByEnv 内存过滤")
    class RuleConfigProviderEnvTest {

        @Test
        @DisplayName("loadEnabledRulesByEnv - 返回 default + 匹配环境的规则")
        void shouldReturnDefaultAndMatchingEnvRules() {
            // 构造测试用 RuleConfigProvider
            List<RuleDefinition> allRules = List.of(
                    RuleDefinition.builder().code("R1").tenantId("1").environment("default").enabled(true).build(),
                    RuleDefinition.builder().code("R2").tenantId("1").environment("dev").enabled(true).build(),
                    RuleDefinition.builder().code("R3").tenantId("1").environment("prod").enabled(true).build()
            );

            RuleConfigProvider provider = new RuleConfigProvider() {
                @Override
                public List<RuleDefinition> loadEnabledRules() {
                    return allRules;
                }

                @Override
                public List<RuleDefinition> loadAllRules() {
                    return allRules;
                }

                @Override
                public RuleDefinition save(RuleDefinition definition, String operator) {
                    return null;
                }

                @Override
                public void toggleEnabled(String ruleCode, boolean enabled, String operator) {
                }

                @Override
                public RuleDefinition findByCode(String ruleCode) {
                    return null;
                }
            };

            // dev 环境：应返回 R1 (default) + R2 (dev)
            List<RuleDefinition> devRules = provider.loadEnabledRulesByEnv("1", "dev");
            assertThat(devRules).hasSize(2);
            assertThat(devRules).extracting(RuleDefinition::getCode)
                    .containsExactlyInAnyOrder("R1", "R2");

            // prod 环境：应返回 R1 (default) + R3 (prod)
            List<RuleDefinition> prodRules = provider.loadEnabledRulesByEnv("1", "prod");
            assertThat(prodRules).hasSize(2);
            assertThat(prodRules).extracting(RuleDefinition::getCode)
                    .containsExactlyInAnyOrder("R1", "R3");
        }

        @Test
        @DisplayName("loadEnabledRulesByEnv - default 环境返回全部规则")
        void shouldReturnAllRulesForDefaultEnvironment() {
            List<RuleDefinition> allRules = List.of(
                    RuleDefinition.builder().code("R1").tenantId("1").environment("default").enabled(true).build(),
                    RuleDefinition.builder().code("R2").tenantId("1").environment("dev").enabled(true).build()
            );

            RuleConfigProvider provider = new RuleConfigProvider() {
                @Override
                public List<RuleDefinition> loadEnabledRules() {
                    return allRules;
                }

                @Override
                public List<RuleDefinition> loadAllRules() {
                    return allRules;
                }

                @Override
                public RuleDefinition save(RuleDefinition definition, String operator) {
                    return null;
                }

                @Override
                public void toggleEnabled(String ruleCode, boolean enabled, String operator) {
                }

                @Override
                public RuleDefinition findByCode(String ruleCode) {
                    return null;
                }
            };

            // default 环境：应返回全部规则（不过滤环境）
            List<RuleDefinition> defaultRules = provider.loadEnabledRulesByEnv("1", "default");
            assertThat(defaultRules).hasSize(2);
        }
    }
}
