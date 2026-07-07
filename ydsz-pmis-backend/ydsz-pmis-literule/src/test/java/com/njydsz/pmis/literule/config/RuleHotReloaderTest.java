package com.njydsz.pmis.literule.config;

import com.njydsz.pmis.literule.api.DecisionTableDefinition;
import com.njydsz.pmis.literule.api.DecisionTreeDefinition;
import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.api.ScorecardDefinition;
import com.njydsz.pmis.literule.api.ScriptDefinition;
import com.njydsz.pmis.literule.event.RuleConfigRefreshEvent;
import com.njydsz.pmis.literule.event.RuleConfigRefreshEvent.ChangeType;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.impl.DecisionTableRule;
import com.njydsz.pmis.literule.impl.DecisionTreeRule;
import com.njydsz.pmis.literule.impl.ExpressionRule;
import com.njydsz.pmis.literule.impl.ScorecardRule;
import com.njydsz.pmis.literule.impl.ScriptRule;
import com.njydsz.pmis.literule.spi.DecisionTableConfigProvider;
import com.njydsz.pmis.literule.spi.DecisionTreeConfigProvider;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import com.njydsz.pmis.literule.spi.ScorecardConfigProvider;
import com.njydsz.pmis.literule.spi.ScriptConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

/**
 * RuleHotReloader 单元测试
 *
 * <p>测试目标：验证规则热加载管理器在接收 {@link RuleConfigRefreshEvent} 事件后，
 * 正确从 SPI Provider 重新加载规则定义、构建对应 {@link Rule} 实例并注册到引擎，
 * 实现运行时规则热刷新。
 *
 * <p>覆盖范围：
 * <ul>
 *   <li>initLoad 启动加载（热加载禁用/自动注册禁用/正常加载三个分支）</li>
 *   <li>fullReload 全量刷新（5 种规则类型的加载/禁用跳过/注册异常/外层异常）</li>
 *   <li>onConfigRefresh 事件监听（FULL_RELOAD/DELETE/默认分支/热加载禁用）</li>
 *   <li>reloadSingle 单规则刷新（5 种类型的 tryReload 成功/禁用/未找到/全未找到/异常）</li>
 *   <li>isDynamicRule 动态规则识别（5 种动态类型 + 非动态类型）</li>
 *   <li>与 RuleEngine 的 register/unregister 联动验证</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 */
@DisplayName("RuleHotReloader 单元测试")
class RuleHotReloaderTest {

    private RuleEngine ruleEngine;
    private ExpressionEvaluator evaluator;
    private RuleConfigProvider configProvider;
    private LiteRuleProperties properties;
    private RuleHotReloader reloader;

    @BeforeEach
    void setUp() {
        ruleEngine = Mockito.mock(RuleEngine.class);
        evaluator = Mockito.mock(ExpressionEvaluator.class);
        configProvider = Mockito.mock(RuleConfigProvider.class);
        properties = new LiteRuleProperties();
        properties.setHotReloadEnabled(true);
        properties.setAutoRegisterBuiltinRules(true);
        reloader = new RuleHotReloader(ruleEngine, evaluator, configProvider, properties);
    }

    // ==================== 辅助方法 ====================

    /** 创建启用的表达式规则定义 */
    private RuleDefinition enabledExprDef(String code) {
        return RuleDefinition.builder()
                .code(code)
                .name(code)
                .conditionExpression("x > 0")
                .enabled(true)
                .build();
    }

    /** 创建禁用的表达式规则定义 */
    private RuleDefinition disabledExprDef(String code) {
        return RuleDefinition.builder()
                .code(code)
                .name(code)
                .enabled(false)
                .build();
    }

    /** 创建启用的决策表定义 */
    private DecisionTableDefinition enabledDtDef(String code) {
        return DecisionTableDefinition.builder()
                .tableCode(code)
                .tableName(code)
                .enabled(true)
                .build();
    }

    /** 创建禁用的决策表定义 */
    private DecisionTableDefinition disabledDtDef(String code) {
        return DecisionTableDefinition.builder()
                .tableCode(code)
                .tableName(code)
                .enabled(false)
                .build();
    }

    /** 创建启用的评分卡定义 */
    private ScorecardDefinition enabledScDef(String code) {
        return ScorecardDefinition.builder()
                .ruleCode(code)
                .ruleName(code)
                .enabled(true)
                .redThreshold(60)
                .yellowThreshold(80)
                .build();
    }

    /** 创建禁用的评分卡定义 */
    private ScorecardDefinition disabledScDef(String code) {
        return ScorecardDefinition.builder()
                .ruleCode(code)
                .ruleName(code)
                .enabled(false)
                .build();
    }

    /** 创建启用的决策树定义 */
    private DecisionTreeDefinition enabledTrDef(String code) {
        return DecisionTreeDefinition.builder()
                .ruleCode(code)
                .ruleName(code)
                .enabled(true)
                .build();
    }

    /** 创建禁用的决策树定义 */
    private DecisionTreeDefinition disabledTrDef(String code) {
        return DecisionTreeDefinition.builder()
                .ruleCode(code)
                .ruleName(code)
                .enabled(false)
                .build();
    }

    /** 创建启用的脚本规则定义 */
    private ScriptDefinition enabledScrDef(String code) {
        return ScriptDefinition.builder()
                .ruleCode(code)
                .ruleName(code)
                .script("return false")
                .sandboxEnabled(false)
                .enabled(true)
                .build();
    }

    /** 创建禁用的脚本规则定义 */
    private ScriptDefinition disabledScrDef(String code) {
        return ScriptDefinition.builder()
                .ruleCode(code)
                .ruleName(code)
                .enabled(false)
                .build();
    }

    // ==================== initLoad 测试 ====================

    @Nested
    @DisplayName("initLoad 启动加载")
    class InitLoadTests {

        @Test
        @DisplayName("热加载禁用时跳过初始加载")
        void initLoad_hotReloadDisabled_skip() {
            properties.setHotReloadEnabled(false);

            reloader.initLoad();

            verifyNoInteractions(configProvider);
            verify(ruleEngine, never()).getRules();
        }

        @Test
        @DisplayName("自动注册内置规则禁用时跳过初始加载")
        void initLoad_autoRegisterDisabled_skip() {
            properties.setAutoRegisterBuiltinRules(false);

            reloader.initLoad();

            verifyNoInteractions(configProvider);
            verify(ruleEngine, never()).getRules();
        }

        @Test
        @DisplayName("两者均启用时执行全量加载")
        void initLoad_bothEnabled_callsFullReload() {
            when(ruleEngine.getRules()).thenReturn(List.of());
            when(configProvider.loadEnabledRules()).thenReturn(List.of());

            reloader.initLoad();

            verify(ruleEngine).getRules();
            verify(configProvider).loadEnabledRules();
        }
    }

    // ==================== fullReload 测试 ====================

    @Nested
    @DisplayName("fullReload 全量刷新")
    class FullReloadTests {

        @Test
        @DisplayName("空规则源时正常完成，不调用 register")
        void fullReload_emptyAll() {
            when(ruleEngine.getRules()).thenReturn(List.of());
            when(configProvider.loadEnabledRules()).thenReturn(List.of());

            assertThatCode(() -> reloader.fullReload("tester")).doesNotThrowAnyException();

            verify(ruleEngine, never()).register(any(Rule.class));
        }

        @Test
        @DisplayName("加载表达式规则（含启用和禁用），仅注册启用的")
        void fullReload_expressionRules_mixed() {
            RuleDefinition enabled = enabledExprDef("R001");
            RuleDefinition disabled = disabledExprDef("R002");

            when(ruleEngine.getRules()).thenReturn(List.of());
            when(configProvider.loadEnabledRules()).thenReturn(List.of(enabled, disabled));

            reloader.fullReload("tester");

            verify(ruleEngine, times(1)).register(isA(ExpressionRule.class));
        }

        @Test
        @DisplayName("表达式规则注册异常时被捕获，不影响后续加载")
        void fullReload_expressionRuleRegisterException() {
            RuleDefinition enabled = enabledExprDef("R001");

            when(ruleEngine.getRules()).thenReturn(List.of());
            when(configProvider.loadEnabledRules()).thenReturn(List.of(enabled));
            doThrow(new RuntimeException("register error"))
                    .when(ruleEngine).register(isA(ExpressionRule.class));

            assertThatCode(() -> reloader.fullReload("tester")).doesNotThrowAnyException();

            verify(ruleEngine).register(isA(ExpressionRule.class));
        }

        @Test
        @DisplayName("加载决策表规则（含启用和禁用），仅注册启用的")
        void fullReload_decisionTables_mixed() {
            DecisionTableDefinition enabled = enabledDtDef("DT001");
            DecisionTableDefinition disabled = disabledDtDef("DT002");

            DecisionTableConfigProvider dtProvider = mock(DecisionTableConfigProvider.class);
            reloader.setDecisionTableConfigProvider(dtProvider);

            when(ruleEngine.getRules()).thenReturn(List.of());
            when(configProvider.loadEnabledRules()).thenReturn(List.of());
            when(dtProvider.loadEnabledTables()).thenReturn(List.of(enabled, disabled));

            reloader.fullReload("tester");

            verify(ruleEngine, times(1)).register(isA(DecisionTableRule.class));
        }

        @Test
        @DisplayName("决策表注册异常时被捕获")
        void fullReload_decisionTableRegisterException() {
            DecisionTableDefinition enabled = enabledDtDef("DT001");

            DecisionTableConfigProvider dtProvider = mock(DecisionTableConfigProvider.class);
            reloader.setDecisionTableConfigProvider(dtProvider);

            when(ruleEngine.getRules()).thenReturn(List.of());
            when(configProvider.loadEnabledRules()).thenReturn(List.of());
            when(dtProvider.loadEnabledTables()).thenReturn(List.of(enabled));
            doThrow(new RuntimeException("dt register error"))
                    .when(ruleEngine).register(isA(DecisionTableRule.class));

            assertThatCode(() -> reloader.fullReload("tester")).doesNotThrowAnyException();

            verify(ruleEngine).register(isA(DecisionTableRule.class));
        }

        @Test
        @DisplayName("加载评分卡规则（含启用和禁用），仅注册启用的")
        void fullReload_scorecards_mixed() {
            ScorecardDefinition enabled = enabledScDef("SC001");
            ScorecardDefinition disabled = disabledScDef("SC002");

            ScorecardConfigProvider scProvider = mock(ScorecardConfigProvider.class);
            reloader.setScorecardConfigProvider(scProvider);

            when(ruleEngine.getRules()).thenReturn(List.of());
            when(configProvider.loadEnabledRules()).thenReturn(List.of());
            when(scProvider.loadEnabledScorecards()).thenReturn(List.of(enabled, disabled));

            reloader.fullReload("tester");

            verify(ruleEngine, times(1)).register(isA(ScorecardRule.class));
        }

        @Test
        @DisplayName("评分卡注册异常时被捕获")
        void fullReload_scorecardRegisterException() {
            ScorecardDefinition enabled = enabledScDef("SC001");

            ScorecardConfigProvider scProvider = mock(ScorecardConfigProvider.class);
            reloader.setScorecardConfigProvider(scProvider);

            when(ruleEngine.getRules()).thenReturn(List.of());
            when(configProvider.loadEnabledRules()).thenReturn(List.of());
            when(scProvider.loadEnabledScorecards()).thenReturn(List.of(enabled));
            doThrow(new RuntimeException("sc register error"))
                    .when(ruleEngine).register(isA(ScorecardRule.class));

            assertThatCode(() -> reloader.fullReload("tester")).doesNotThrowAnyException();

            verify(ruleEngine).register(isA(ScorecardRule.class));
        }

        @Test
        @DisplayName("加载决策树规则（含启用和禁用），仅注册启用的")
        void fullReload_decisionTrees_mixed() {
            DecisionTreeDefinition enabled = enabledTrDef("TR001");
            DecisionTreeDefinition disabled = disabledTrDef("TR002");

            DecisionTreeConfigProvider trProvider = mock(DecisionTreeConfigProvider.class);
            reloader.setDecisionTreeConfigProvider(trProvider);

            when(ruleEngine.getRules()).thenReturn(List.of());
            when(configProvider.loadEnabledRules()).thenReturn(List.of());
            when(trProvider.loadEnabledTrees()).thenReturn(List.of(enabled, disabled));

            reloader.fullReload("tester");

            verify(ruleEngine, times(1)).register(isA(DecisionTreeRule.class));
        }

        @Test
        @DisplayName("决策树注册异常时被捕获")
        void fullReload_decisionTreeRegisterException() {
            DecisionTreeDefinition enabled = enabledTrDef("TR001");

            DecisionTreeConfigProvider trProvider = mock(DecisionTreeConfigProvider.class);
            reloader.setDecisionTreeConfigProvider(trProvider);

            when(ruleEngine.getRules()).thenReturn(List.of());
            when(configProvider.loadEnabledRules()).thenReturn(List.of());
            when(trProvider.loadEnabledTrees()).thenReturn(List.of(enabled));
            doThrow(new RuntimeException("tr register error"))
                    .when(ruleEngine).register(isA(DecisionTreeRule.class));

            assertThatCode(() -> reloader.fullReload("tester")).doesNotThrowAnyException();

            verify(ruleEngine).register(isA(DecisionTreeRule.class));
        }

        @Test
        @DisplayName("加载脚本规则（含启用和禁用），仅注册启用的")
        void fullReload_scripts_mixed() {
            ScriptDefinition enabled = enabledScrDef("SCR001");
            ScriptDefinition disabled = disabledScrDef("SCR002");

            ScriptConfigProvider scrProvider = mock(ScriptConfigProvider.class);
            reloader.setScriptConfigProvider(scrProvider);

            when(ruleEngine.getRules()).thenReturn(List.of());
            when(configProvider.loadEnabledRules()).thenReturn(List.of());
            when(scrProvider.loadEnabledScripts()).thenReturn(List.of(enabled, disabled));

            reloader.fullReload("tester");

            verify(ruleEngine, times(1)).register(isA(ScriptRule.class));
        }

        @Test
        @DisplayName("脚本规则注册异常时被捕获")
        void fullReload_scriptRegisterException() {
            ScriptDefinition enabled = enabledScrDef("SCR001");

            ScriptConfigProvider scrProvider = mock(ScriptConfigProvider.class);
            reloader.setScriptConfigProvider(scrProvider);

            when(ruleEngine.getRules()).thenReturn(List.of());
            when(configProvider.loadEnabledRules()).thenReturn(List.of());
            when(scrProvider.loadEnabledScripts()).thenReturn(List.of(enabled));
            doThrow(new RuntimeException("scr register error"))
                    .when(ruleEngine).register(isA(ScriptRule.class));

            assertThatCode(() -> reloader.fullReload("tester")).doesNotThrowAnyException();

            verify(ruleEngine).register(isA(ScriptRule.class));
        }

        @Test
        @DisplayName("全量刷新外层异常被捕获（configProvider.loadEnabledRules 抛异常）")
        void fullReload_outerException() {
            when(ruleEngine.getRules()).thenReturn(List.of());
            when(configProvider.loadEnabledRules()).thenThrow(new RuntimeException("DB error"));

            assertThatCode(() -> reloader.fullReload("tester")).doesNotThrowAnyException();

            verify(ruleEngine, never()).register(any(Rule.class));
        }

        @Test
        @DisplayName("isDynamicRule：5 种动态规则类型被注销，非动态规则保留")
        void fullReload_isDynamicRule_allTypes() {
            // 创建 5 种动态规则实例 + 1 个非动态规则
            ExpressionRule exprRule = new ExpressionRule(
                    RuleDefinition.builder().code("EXPR1").name("EXPR1").build(), evaluator);
            DecisionTableRule dtRule = new DecisionTableRule(
                    DecisionTableDefinition.builder().tableCode("DT1").tableName("DT1").build(), evaluator);
            ScorecardRule scRule = ScorecardRule.builder().code("SC1").name("SC1").build();
            DecisionTreeRule trRule = new DecisionTreeRule("TR1", "TR1", "CAT", 100, null, null, evaluator);
            ScriptRule scrRule = mock(ScriptRule.class);
            when(scrRule.getCode()).thenReturn("SCR1");
            // 非动态规则：不 stub getCode()，因为 isDynamicRule=false 时不会被调用（避免 UnnecessaryStubbing）
            Rule staticRule = mock(Rule.class);

            when(ruleEngine.getRules()).thenReturn(
                    List.of(exprRule, dtRule, scRule, trRule, scrRule, staticRule));
            when(configProvider.loadEnabledRules()).thenReturn(List.of());

            reloader.fullReload("tester");

            // 5 种动态规则被注销
            verify(ruleEngine).unregister("EXPR1");
            verify(ruleEngine).unregister("DT1");
            verify(ruleEngine).unregister("SC1");
            verify(ruleEngine).unregister("TR1");
            verify(ruleEngine).unregister("SCR1");
            // 非动态规则不被注销
            verify(ruleEngine, never()).unregister("STATIC1");
        }
    }

    // ==================== onConfigRefresh 测试 ====================

    @Nested
    @DisplayName("onConfigRefresh 配置变更监听")
    class OnConfigRefreshTests {

        @Test
        @DisplayName("热加载禁用时忽略事件")
        void onConfigRefresh_hotReloadDisabled_skip() {
            properties.setHotReloadEnabled(false);

            reloader.onConfigRefresh(RuleConfigRefreshEvent.of("R001", ChangeType.UPDATE, "tester"));

            verifyNoInteractions(configProvider);
            verify(ruleEngine, never()).unregister(any());
        }

        @Test
        @DisplayName("FULL_RELOAD 事件触发全量刷新")
        void onConfigRefresh_fullReload() {
            when(ruleEngine.getRules()).thenReturn(List.of());
            when(configProvider.loadEnabledRules()).thenReturn(List.of());

            reloader.onConfigRefresh(RuleConfigRefreshEvent.fullReload("tester"));

            verify(ruleEngine).getRules();
            verify(configProvider).loadEnabledRules();
        }

        @Test
        @DisplayName("DELETE 事件触发规则注销")
        void onConfigRefresh_delete() {
            reloader.onConfigRefresh(RuleConfigRefreshEvent.of("R001", ChangeType.DELETE, "tester"));

            verify(ruleEngine).unregister("R001");
            verify(ruleEngine, never()).register(any(Rule.class));
        }
    }

    // ==================== reloadSingle 测试 ====================

    @Nested
    @DisplayName("reloadSingle 单规则刷新")
    class ReloadSingleTests {

        @Test
        @DisplayName("表达式规则存在且启用 → 注册新版本")
        void reloadSingle_expressionEnabled() {
            RuleDefinition def = enabledExprDef("R001");
            when(configProvider.findByCode("R001")).thenReturn(def);

            reloader.onConfigRefresh(RuleConfigRefreshEvent.of("R001", ChangeType.UPDATE, "tester"));

            verify(ruleEngine).register(isA(ExpressionRule.class));
        }

        @Test
        @DisplayName("表达式规则存在但禁用 → 注销")
        void reloadSingle_expressionDisabled() {
            RuleDefinition def = disabledExprDef("R001");
            when(configProvider.findByCode("R001")).thenReturn(def);

            reloader.onConfigRefresh(RuleConfigRefreshEvent.of("R001", ChangeType.UPDATE, "tester"));

            verify(ruleEngine).unregister("R001");
            verify(ruleEngine, never()).register(any(Rule.class));
        }

        @Test
        @DisplayName("表达式未找到，决策表存在且启用 → 注册决策表")
        void reloadSingle_decisionTableEnabled() {
            DecisionTableConfigProvider dtProvider = mock(DecisionTableConfigProvider.class);
            reloader.setDecisionTableConfigProvider(dtProvider);

            when(configProvider.findByCode("R001")).thenReturn(null);
            when(dtProvider.findByCode("R001")).thenReturn(enabledDtDef("R001"));

            reloader.onConfigRefresh(RuleConfigRefreshEvent.of("R001", ChangeType.UPDATE, "tester"));

            verify(ruleEngine).register(isA(DecisionTableRule.class));
        }

        @Test
        @DisplayName("表达式未找到，决策表存在但禁用 → 注销")
        void reloadSingle_decisionTableDisabled() {
            DecisionTableConfigProvider dtProvider = mock(DecisionTableConfigProvider.class);
            reloader.setDecisionTableConfigProvider(dtProvider);

            when(configProvider.findByCode("R001")).thenReturn(null);
            when(dtProvider.findByCode("R001")).thenReturn(disabledDtDef("R001"));

            reloader.onConfigRefresh(RuleConfigRefreshEvent.of("R001", ChangeType.UPDATE, "tester"));

            verify(ruleEngine).unregister("R001");
            verify(ruleEngine, never()).register(any(Rule.class));
        }

        @Test
        @DisplayName("表达式和决策表未找到，评分卡存在且启用 → 注册评分卡")
        void reloadSingle_scorecardEnabled() {
            ScorecardConfigProvider scProvider = mock(ScorecardConfigProvider.class);
            reloader.setScorecardConfigProvider(scProvider);

            when(configProvider.findByCode("R001")).thenReturn(null);
            when(scProvider.findByCode("R001")).thenReturn(enabledScDef("R001"));

            reloader.onConfigRefresh(RuleConfigRefreshEvent.of("R001", ChangeType.UPDATE, "tester"));

            verify(ruleEngine).register(isA(ScorecardRule.class));
        }

        @Test
        @DisplayName("表达式和决策表未找到，评分卡存在但禁用 → 注销")
        void reloadSingle_scorecardDisabled() {
            ScorecardConfigProvider scProvider = mock(ScorecardConfigProvider.class);
            reloader.setScorecardConfigProvider(scProvider);

            when(configProvider.findByCode("R001")).thenReturn(null);
            when(scProvider.findByCode("R001")).thenReturn(disabledScDef("R001"));

            reloader.onConfigRefresh(RuleConfigRefreshEvent.of("R001", ChangeType.UPDATE, "tester"));

            verify(ruleEngine).unregister("R001");
            verify(ruleEngine, never()).register(any(Rule.class));
        }

        @Test
        @DisplayName("表达式/决策表/评分卡未找到，决策树存在且启用 → 注册决策树")
        void reloadSingle_decisionTreeEnabled() {
            DecisionTreeConfigProvider trProvider = mock(DecisionTreeConfigProvider.class);
            reloader.setDecisionTreeConfigProvider(trProvider);

            when(configProvider.findByCode("R001")).thenReturn(null);
            when(trProvider.findByCode("R001")).thenReturn(enabledTrDef("R001"));

            reloader.onConfigRefresh(RuleConfigRefreshEvent.of("R001", ChangeType.UPDATE, "tester"));

            verify(ruleEngine).register(isA(DecisionTreeRule.class));
        }

        @Test
        @DisplayName("表达式/决策表/评分卡未找到，决策树存在但禁用 → 注销")
        void reloadSingle_decisionTreeDisabled() {
            DecisionTreeConfigProvider trProvider = mock(DecisionTreeConfigProvider.class);
            reloader.setDecisionTreeConfigProvider(trProvider);

            when(configProvider.findByCode("R001")).thenReturn(null);
            when(trProvider.findByCode("R001")).thenReturn(disabledTrDef("R001"));

            reloader.onConfigRefresh(RuleConfigRefreshEvent.of("R001", ChangeType.UPDATE, "tester"));

            verify(ruleEngine).unregister("R001");
            verify(ruleEngine, never()).register(any(Rule.class));
        }

        @Test
        @DisplayName("其他类型未找到，脚本规则存在且启用 → 注册脚本")
        void reloadSingle_scriptEnabled() {
            ScriptConfigProvider scrProvider = mock(ScriptConfigProvider.class);
            reloader.setScriptConfigProvider(scrProvider);

            when(configProvider.findByCode("R001")).thenReturn(null);
            when(scrProvider.findByCode("R001")).thenReturn(enabledScrDef("R001"));

            reloader.onConfigRefresh(RuleConfigRefreshEvent.of("R001", ChangeType.UPDATE, "tester"));

            verify(ruleEngine).register(isA(ScriptRule.class));
        }

        @Test
        @DisplayName("其他类型未找到，脚本规则存在但禁用 → 注销")
        void reloadSingle_scriptDisabled() {
            ScriptConfigProvider scrProvider = mock(ScriptConfigProvider.class);
            reloader.setScriptConfigProvider(scrProvider);

            when(configProvider.findByCode("R001")).thenReturn(null);
            when(scrProvider.findByCode("R001")).thenReturn(disabledScrDef("R001"));

            reloader.onConfigRefresh(RuleConfigRefreshEvent.of("R001", ChangeType.UPDATE, "tester"));

            verify(ruleEngine).unregister("R001");
            verify(ruleEngine, never()).register(any(Rule.class));
        }

        @Test
        @DisplayName("所有类型均未找到 → 注销规则")
        void reloadSingle_allNotFound() {
            when(configProvider.findByCode("R001")).thenReturn(null);

            reloader.onConfigRefresh(RuleConfigRefreshEvent.of("R001", ChangeType.UPDATE, "tester"));

            verify(ruleEngine).unregister("R001");
            verify(ruleEngine, never()).register(any(Rule.class));
        }

        @Test
        @DisplayName("reloadSingle 异常被捕获（configProvider.findByCode 抛异常）")
        void reloadSingle_exception() {
            when(configProvider.findByCode("R001")).thenThrow(new RuntimeException("findByCode error"));

            assertThatCode(() -> reloader.onConfigRefresh(
                    RuleConfigRefreshEvent.of("R001", ChangeType.UPDATE, "tester")))
                    .doesNotThrowAnyException();

            verify(ruleEngine, never()).register(any(Rule.class));
            verify(ruleEngine, never()).unregister(any());
        }
    }
}
