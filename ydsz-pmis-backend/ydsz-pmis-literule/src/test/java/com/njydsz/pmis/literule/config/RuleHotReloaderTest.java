package com.njydsz.pmis.literule.config;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.core.DefaultRuleEngine;
import com.njydsz.pmis.literule.event.RuleConfigRefreshEvent;
import com.njydsz.pmis.literule.expr.AviatorExpressionEvaluator;
import com.njydsz.pmis.literule.impl.ExpressionRule;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * RuleHotReloader 热加载测试
 *
 * <p>覆盖 P1：热加载全量重载、单条刷新、事件分派、禁用跳过。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@DisplayName("RuleHotReloader 热加载测试")
class RuleHotReloaderTest {

    private DefaultRuleEngine engine;
    private AviatorExpressionEvaluator evaluator;
    private RuleConfigProvider configProvider;
    private LiteRuleProperties properties;
    private RuleHotReloader hotReloader;

    @BeforeEach
    void setUp() {
        engine = new DefaultRuleEngine();
        evaluator = new AviatorExpressionEvaluator();
        configProvider = mock(RuleConfigProvider.class);
        properties = new LiteRuleProperties();
        properties.setHotReloadEnabled(true);

        hotReloader = new RuleHotReloader(engine, evaluator, configProvider, properties);
    }

    private RuleDefinition createDef(String code, String condition, boolean enabled) {
        return RuleDefinition.builder()
                .code(code)
                .name("规则_" + code)
                .category("TEST")
                .conditionExpression(condition)
                .defaultSeverity(RuleSeverity.YELLOW)
                .titleTemplate("${code} 触发")
                .enabled(enabled)
                .version(1)
                .build();
    }

    @Nested
    @DisplayName("全量热加载")
    class FullReloadTest {

        @Test
        @DisplayName("fullReload 加载全部启用规则到引擎")
        void shouldLoadAllEnabledRules() {
            List<RuleDefinition> defs = List.of(
                    createDef("R1", "amount > 100", true),
                    createDef("R2", "amount > 200", true),
                    createDef("R3", "amount > 300", false) // 禁用
            );
            when(configProvider.loadEnabledRules()).thenReturn(defs);

            hotReloader.fullReload("TEST");

            assertEquals(2, engine.getRules().size());
            // R3 禁用不应注册
            assertTrue(engine.getRules().stream().noneMatch(r -> "R3".equals(r.getCode())));
        }

        @Test
        @DisplayName("fullReload 先注销旧 ExpressionRule 再注册新规则")
        void shouldUnregisterOldRulesBeforeReload() {
            // 先注册一条旧规则
            when(configProvider.loadEnabledRules()).thenReturn(List.of(
                    createDef("OLD", "amount > 100", true)));
            hotReloader.fullReload("INIT");
            assertEquals(1, engine.getRules().size());

            // 重新加载，旧规则被新规则替代
            when(configProvider.loadEnabledRules()).thenReturn(List.of(
                    createDef("NEW", "amount > 200", true)));
            hotReloader.fullReload("RELOAD");
            assertEquals(1, engine.getRules().size());
            assertTrue(engine.getRules().stream().anyMatch(r -> "NEW".equals(r.getCode())));
            assertFalse(engine.getRules().stream().anyMatch(r -> "OLD".equals(r.getCode())));
        }

        @Test
        @DisplayName("fullReload 保留 StaticRule 不被注销")
        void shouldPreserveStaticRules() {
            // 手动注册一条 StaticRule
            com.njydsz.pmis.literule.impl.StaticRule staticRule =
                    new com.njydsz.pmis.literule.impl.StaticRule("STATIC", "静态规则", "TEST", ctx ->
                            RuleResult.notTriggered("STATIC"));
            engine.register(staticRule);

            when(configProvider.loadEnabledRules()).thenReturn(List.of(
                    createDef("EXPR", "amount > 100", true)));
            hotReloader.fullReload("TEST");

            // StaticRule 应保留，ExpressionRule 新注册
            assertEquals(2, engine.getRules().size());
            assertTrue(engine.getRules().stream().anyMatch(r -> "STATIC".equals(r.getCode())));
            assertTrue(engine.getRules().stream().anyMatch(r -> "EXPR".equals(r.getCode())));
        }

        @Test
        @DisplayName("fullReload configProvider 异常不影响引擎已有规则")
        void shouldIsolateConfigProviderFailure() {
            // 先正常加载一条规则
            when(configProvider.loadEnabledRules()).thenReturn(List.of(
                    createDef("GOOD", "amount > 100", true)));
            hotReloader.fullReload("INIT");
            assertEquals(1, engine.getRules().size());

            // 第二次加载时 configProvider 抛异常
            when(configProvider.loadEnabledRules()).thenThrow(new RuntimeException("DB 连接失败"));
            hotReloader.fullReload("RELOAD");

            // 原有规则不受影响
            assertEquals(1, engine.getRules().size());
            assertEquals("GOOD", engine.getRules().get(0).getCode());
        }
    }

    @Nested
    @DisplayName("事件驱动热刷新")
    class EventDrivenTest {

        @Test
        @DisplayName("CREATE 事件触发单条规则注册")
        void shouldHandleCreateEvent() {
            RuleDefinition def = createDef("NEW_RULE", "amount > 100", true);
            when(configProvider.findByCode("NEW_RULE")).thenReturn(def);

            RuleConfigRefreshEvent event = RuleConfigRefreshEvent.of(
                    "NEW_RULE", RuleConfigRefreshEvent.ChangeType.CREATE, "USER");
            hotReloader.onConfigRefresh(event);

            assertTrue(engine.getRules().stream().anyMatch(r -> "NEW_RULE".equals(r.getCode())));
        }

        @Test
        @DisplayName("UPDATE 事件触发单条规则重新注册（覆盖旧版本）")
        void shouldHandleUpdateEvent() {
            // 先注册 v1
            RuleDefinition v1 = createDef("R1", "amount > 100", true);
            v1.setVersion(1);
            when(configProvider.findByCode("R1")).thenReturn(v1);
            hotReloader.onConfigRefresh(RuleConfigRefreshEvent.of(
                    "R1", RuleConfigRefreshEvent.ChangeType.CREATE, "USER"));

            // 更新为 v2（条件变更）
            RuleDefinition v2 = createDef("R1", "amount > 200", true);
            v2.setVersion(2);
            when(configProvider.findByCode("R1")).thenReturn(v2);
            hotReloader.onConfigRefresh(RuleConfigRefreshEvent.of(
                    "R1", RuleConfigRefreshEvent.ChangeType.UPDATE, "USER"));

            // 应只有 1 条规则（覆盖注册）
            assertEquals(1, engine.getRules().size());
        }

        @Test
        @DisplayName("DELETE 事件触发规则注销")
        void shouldHandleDeleteEvent() {
            // 先注册
            RuleDefinition def = createDef("R1", "amount > 100", true);
            when(configProvider.findByCode("R1")).thenReturn(def);
            hotReloader.onConfigRefresh(RuleConfigRefreshEvent.of(
                    "R1", RuleConfigRefreshEvent.ChangeType.CREATE, "USER"));
            assertEquals(1, engine.getRules().size());

            // 删除
            hotReloader.onConfigRefresh(RuleConfigRefreshEvent.of(
                    "R1", RuleConfigRefreshEvent.ChangeType.DELETE, "USER"));
            assertEquals(0, engine.getRules().size());
        }

        @Test
        @DisplayName("TOGGLE 禁用规则触发注销")
        void shouldHandleToggleDisableEvent() {
            // 先注册
            RuleDefinition def = createDef("R1", "amount > 100", true);
            when(configProvider.findByCode("R1")).thenReturn(def);
            hotReloader.onConfigRefresh(RuleConfigRefreshEvent.of(
                    "R1", RuleConfigRefreshEvent.ChangeType.CREATE, "USER"));
            assertEquals(1, engine.getRules().size());

            // Toggle 禁用
            RuleDefinition disabled = createDef("R1", "amount > 100", false);
            when(configProvider.findByCode("R1")).thenReturn(disabled);
            hotReloader.onConfigRefresh(RuleConfigRefreshEvent.of(
                    "R1", RuleConfigRefreshEvent.ChangeType.TOGGLE, "USER"));
            assertEquals(0, engine.getRules().size());
        }

        @Test
        @DisplayName("FULL_RELOAD 事件触发全量重载")
        void shouldHandleFullReloadEvent() {
            when(configProvider.loadEnabledRules()).thenReturn(List.of(
                    createDef("R1", "amount > 100", true),
                    createDef("R2", "amount > 200", true)
            ));

            hotReloader.onConfigRefresh(RuleConfigRefreshEvent.fullReload("USER"));

            assertEquals(2, engine.getRules().size());
        }

        @Test
        @DisplayName("规则未找到时注销（已从 DB 删除）")
        void shouldUnregisterWhenRuleNotFound() {
            // 先注册
            RuleDefinition def = createDef("R1", "amount > 100", true);
            when(configProvider.findByCode("R1")).thenReturn(def);
            hotReloader.onConfigRefresh(RuleConfigRefreshEvent.of(
                    "R1", RuleConfigRefreshEvent.ChangeType.CREATE, "USER"));
            assertEquals(1, engine.getRules().size());

            // UPDATE 时 DB 中已删除
            when(configProvider.findByCode("R1")).thenReturn(null);
            hotReloader.onConfigRefresh(RuleConfigRefreshEvent.of(
                    "R1", RuleConfigRefreshEvent.ChangeType.UPDATE, "USER"));
            assertEquals(0, engine.getRules().size());
        }
    }

    @Nested
    @DisplayName("热加载禁用")
    class HotReloadDisabledTest {

        @Test
        @DisplayName("hotReloadEnabled=false 时 initLoad 跳过加载")
        void shouldSkipInitLoadWhenDisabled() {
            properties.setHotReloadEnabled(false);
            when(configProvider.loadEnabledRules()).thenReturn(List.of(
                    createDef("R1", "amount > 100", true)));

            hotReloader.initLoad();

            assertEquals(0, engine.getRules().size());
            verify(configProvider, never()).loadEnabledRules();
        }

        @Test
        @DisplayName("hotReloadEnabled=false 时事件被忽略")
        void shouldIgnoreEventWhenDisabled() {
            properties.setHotReloadEnabled(false);
            RuleDefinition def = createDef("R1", "amount > 100", true);
            when(configProvider.findByCode("R1")).thenReturn(def);

            hotReloader.onConfigRefresh(RuleConfigRefreshEvent.of(
                    "R1", RuleConfigRefreshEvent.ChangeType.CREATE, "USER"));

            assertEquals(0, engine.getRules().size());
        }
    }

    @Nested
    @DisplayName("热加载后规则可执行验证")
    class HotReloadedRuleExecutionTest {

        @Test
        @DisplayName("热加载的 ExpressionRule 可正常评估")
        void shouldExecuteHotReloadedRule() {
            RuleDefinition def = createDef("R1", "amount > 100", true);
            def.setTitleTemplate("金额预警: ${amount}");
            when(configProvider.loadEnabledRules()).thenReturn(List.of(def));

            hotReloader.fullReload("TEST");

            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 200);
            List<RuleResult> results = engine.evaluate(com.njydsz.pmis.literule.api.RuleContext.of(facts));

            assertEquals(1, results.size());
            assertTrue(results.get(0).isTriggered());
            assertEquals("R1", results.get(0).getRuleCode());
        }
    }
}
