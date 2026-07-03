package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 断点调试 Hook 单元测试（P2-3）
 *
 * <p>覆盖范围：
 * <ul>
 *   <li>DefaultBreakpointHook：断点增删查、总开关、null 安全、只读视图</li>
 *   <li>DefaultRuleEngine 集成：BEFORE/AFTER 回调时机、STEP_OVER 跳过、异常吞掉、facts 快照独立</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
class BreakpointHookTest {

    // ==================== DefaultBreakpointHook 测试 ====================

    @Nested
    @DisplayName("DefaultBreakpointHook 断点注册表")
    class DefaultBreakpointHookTests {

        private DefaultBreakpointHook hook;

        @BeforeEach
        void setUp() {
            hook = new DefaultBreakpointHook();
        }

        @Test
        @DisplayName("addBreakpoint 后 hasBreakpoint 返回 true")
        void shouldReturnTrueAfterAddBreakpoint() {
            hook.addBreakpoint("R1");

            assertThat(hook.hasBreakpoint("R1")).isTrue();
        }

        @Test
        @DisplayName("未添加的规则 hasBreakpoint 返回 false")
        void shouldReturnFalseForUnregisteredRule() {
            hook.addBreakpoint("R1");

            assertThat(hook.hasBreakpoint("R2")).isFalse();
        }

        @Test
        @DisplayName("removeBreakpoint 后 hasBreakpoint 返回 false")
        void shouldReturnFalseAfterRemoveBreakpoint() {
            hook.addBreakpoint("R1");
            assertThat(hook.hasBreakpoint("R1")).isTrue();

            hook.removeBreakpoint("R1");

            assertThat(hook.hasBreakpoint("R1")).isFalse();
            assertThat(hook.getBreakpoints()).doesNotContain("R1");
        }

        @Test
        @DisplayName("clearBreakpoints 后全部断点失效")
        void shouldClearAllBreakpoints() {
            hook.addBreakpoint("R1");
            hook.addBreakpoint("R2");
            hook.addBreakpoint("R3");
            assertThat(hook.getBreakpoints()).containsExactlyInAnyOrder("R1", "R2", "R3");

            hook.clearBreakpoints();

            assertThat(hook.getBreakpoints()).isEmpty();
            assertThat(hook.hasBreakpoint("R1")).isFalse();
            assertThat(hook.hasBreakpoint("R2")).isFalse();
            assertThat(hook.hasBreakpoint("R3")).isFalse();
        }

        @Test
        @DisplayName("disabled 时 hasBreakpoint 全部返回 false")
        void shouldReturnFalseWhenDisabled() {
            hook.addBreakpoint("R1");
            assertThat(hook.hasBreakpoint("R1")).isTrue();
            assertThat(hook.isEnabled()).isTrue();

            hook.setEnabled(false);

            assertThat(hook.isEnabled()).isFalse();
            // 关闭后即使集合非空也不触发
            assertThat(hook.hasBreakpoint("R1")).isFalse();
        }

        @Test
        @DisplayName("null / blank ruleCode 安全处理：不抛异常且不入库")
        void shouldHandleNullAndBlankRuleCodeSafely() {
            // hasBreakpoint(null) 返回 false，不抛异常
            assertThat(hook.hasBreakpoint(null)).isFalse();

            // addBreakpoint 对 null / blank 静默拒绝，不入库
            hook.addBreakpoint(null);
            hook.addBreakpoint("");
            hook.addBreakpoint("   ");

            assertThat(hook.getBreakpoints()).isEmpty();
            assertThat(hook.hasBreakpoint(null)).isFalse();

            // removeBreakpoint(null) 不抛异常
            hook.removeBreakpoint(null);
        }

        @Test
        @DisplayName("getBreakpoints 返回不可修改视图")
        void shouldReturnUnmodifiableView() {
            hook.addBreakpoint("R1");
            Set<String> breakpoints = hook.getBreakpoints();
            assertThat(breakpoints).containsExactly("R1");

            // 任何修改操作都应抛 UnsupportedOperationException
            assertThatThrownBy(() -> breakpoints.add("R2"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> breakpoints.remove("R1"))
                    .isInstanceOf(UnsupportedOperationException.class);

            // 视图未被破坏
            assertThat(hook.getBreakpoints()).containsExactly("R1");
        }
    }

    // ==================== DefaultRuleEngine 集成测试 ====================

    @Nested
    @DisplayName("DefaultRuleEngine 断点 Hook 集成")
    class DefaultRuleEngineIntegrationTests {

        @Test
        @DisplayName("未设置 breakpointHook 时规则正常评估")
        void shouldEvaluateNormallyWithoutHook() {
            DefaultRuleEngine engine = new DefaultRuleEngine();
            engine.register(mockRule("R1", "规则一", true));

            RuleContext ctx = RuleContext.of(new HashMap<>(), "TEST", "JUNIT", "trace-1");

            List<RuleResult> results = engine.evaluate(ctx);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R1");
            assertThat(results.get(0).isTriggered()).isTrue();
            assertThat(engine.getBreakpointHook()).isNull();
        }

        @Test
        @DisplayName("hook 已注入但未设置断点时不触发回调")
        void shouldNotInvokeHookWhenNoBreakpointSet() {
            DefaultRuleEngine engine = new DefaultRuleEngine();
            RecordingHook hook = new RecordingHook();
            engine.setBreakpointHook(hook);
            engine.register(mockRule("R1", "规则一", true));

            RuleContext ctx = RuleContext.of(new HashMap<>(), "TEST", "JUNIT", "trace-2");

            List<RuleResult> results = engine.evaluate(ctx);

            assertThat(results).hasSize(1);
            assertThat(hook.beforeCalls).isEmpty();
            assertThat(hook.afterCalls).isEmpty();
        }

        @Test
        @DisplayName("设置断点后 onBeforeEvaluate 被调用且收到 BEFORE 上下文")
        void shouldInvokeOnBeforeEvaluateWhenBreakpointSet() {
            DefaultRuleEngine engine = new DefaultRuleEngine();
            RecordingHook hook = new RecordingHook();
            engine.setBreakpointHook(hook);
            engine.register(mockRule("R1", "规则一", true));
            hook.breakpoints.add("R1");

            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 500);
            RuleContext ctx = RuleContext.of(facts, "TEST", "JUNIT", "trace-3");

            engine.evaluate(ctx);

            assertThat(hook.beforeCalls).hasSize(1);
            BreakpointHook.BreakpointContext beforeCtx = hook.beforeCalls.get(0);
            assertThat(beforeCtx.getPhase()).isEqualTo("BEFORE");
            assertThat(beforeCtx.getRuleCode()).isEqualTo("R1");
            assertThat(beforeCtx.getRuleName()).isEqualTo("规则一");
            assertThat(beforeCtx.getScenario()).isEqualTo("TEST");
            assertThat(beforeCtx.getTraceId()).isEqualTo("trace-3");
            assertThat(beforeCtx.getResult()).isNull();
            assertThat(beforeCtx.getFacts()).containsEntry("amount", 500);
        }

        @Test
        @DisplayName("onAfterEvaluate 收到已填充的 result 与 elapsedMs")
        void shouldInvokeOnAfterEvaluateWithResultAndElapsed() {
            DefaultRuleEngine engine = new DefaultRuleEngine();
            RecordingHook hook = new RecordingHook();
            engine.setBreakpointHook(hook);
            // 使用带 sleep 的规则，保证 elapsedMs > 0
            engine.register(slowMockRule("R1", "慢规则", true, 5L));
            hook.breakpoints.add("R1");

            RuleContext ctx = RuleContext.of(new HashMap<>(), "TEST", "JUNIT", "trace-4");

            List<RuleResult> results = engine.evaluate(ctx);

            assertThat(results).hasSize(1);
            assertThat(hook.afterCalls).hasSize(1);
            BreakpointHook.BreakpointContext afterCtx = hook.afterCalls.get(0);
            assertThat(afterCtx.getPhase()).isEqualTo("AFTER");
            assertThat(afterCtx.getRuleCode()).isEqualTo("R1");
            assertThat(afterCtx.getResult()).isNotNull();
            assertThat(afterCtx.getResult().isTriggered()).isTrue();
            assertThat(afterCtx.getResult().getSeverity()).isEqualTo(RuleSeverity.YELLOW);
            assertThat(afterCtx.getElapsedMs()).isGreaterThan(0L);
            assertThat(afterCtx.getException()).isNull();
        }

        @Test
        @DisplayName("STEP_OVER 动作跳过规则评估，结果列表不包含该规则")
        void shouldSkipRuleWhenActionIsStepOver() {
            DefaultRuleEngine engine = new DefaultRuleEngine();
            RecordingHook hook = new RecordingHook();
            hook.action = BreakpointHook.BreakpointAction.STEP_OVER;
            engine.setBreakpointHook(hook);
            engine.register(mockRule("R1", "跳过规则", true));
            engine.register(mockRule("R2", "正常规则", true));
            // 仅 R1 设置断点，R2 正常评估
            hook.breakpoints.add("R1");

            RuleContext ctx = RuleContext.of(new HashMap<>(), "TEST", "JUNIT", "trace-5");

            List<RuleResult> results = engine.evaluate(ctx);

            // R1 被 STEP_OVER 跳过，只有 R2 触发
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R2");
            // onBeforeEvaluate 仅对 R1 调用过一次
            assertThat(hook.beforeCalls).hasSize(1);
            assertThat(hook.beforeCalls.get(0).getRuleCode()).isEqualTo("R1");
            // R1 被跳过，不会触发 onAfterEvaluate
            assertThat(hook.afterCalls).isEmpty();
        }

        @Test
        @DisplayName("hook 抛出异常被引擎吞掉，不影响规则评估结果")
        void shouldSwallowHookExceptionAndContinueEvaluation() {
            DefaultRuleEngine engine = new DefaultRuleEngine();
            BreakpointHook throwingHook = new BreakpointHook() {
                @Override
                public BreakpointAction onBeforeEvaluate(BreakpointContext context) {
                    throw new RuntimeException("模拟 onBeforeEvaluate 异常");
                }

                @Override
                public void onAfterEvaluate(BreakpointContext context) {
                    throw new RuntimeException("模拟 onAfterEvaluate 异常");
                }

                @Override
                public boolean hasBreakpoint(String ruleCode) {
                    return "R1".equals(ruleCode);
                }
            };
            engine.setBreakpointHook(throwingHook);
            engine.register(mockRule("R1", "异常 hook 规则", true));

            RuleContext ctx = RuleContext.of(new HashMap<>(), "TEST", "JUNIT", "trace-6");

            List<RuleResult> results = engine.evaluate(ctx);

            // hook 异常被吞掉，规则仍正常触发
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R1");
            assertThat(results.get(0).isTriggered()).isTrue();
        }

        @Test
        @DisplayName("hook 修改 facts 快照不影响实际 context.facts")
        void shouldIsolateFactsSnapshotFromContext() {
            DefaultRuleEngine engine = new DefaultRuleEngine();
            AtomicBoolean snapshotModified = new AtomicBoolean(false);
            BreakpointHook hook = new BreakpointHook() {
                @Override
                public boolean hasBreakpoint(String ruleCode) {
                    return "R1".equals(ruleCode);
                }

                @Override
                public BreakpointAction onBeforeEvaluate(BreakpointContext context) {
                    // 修改 hook 收到的 facts 快照副本
                    context.getFacts().put("amount", 999);
                    context.getFacts().put("injected", "hack");
                    snapshotModified.set(true);
                    return BreakpointAction.CONTINUE;
                }
            };
            engine.setBreakpointHook(hook);
            engine.register(mockRule("R1", "规则一", true));

            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 100);
            RuleContext ctx = RuleContext.of(facts, "TEST", "JUNIT", "trace-7");

            engine.evaluate(ctx);

            // 快照确实被 hook 修改过
            assertThat(snapshotModified.get()).isTrue();
            // 但实际 context.facts 未受影响（LinkedHashMap 副本不回写）
            assertThat(ctx.getFacts().get("amount")).isEqualTo(100);
            assertThat(ctx.getFacts()).doesNotContainKey("injected");
            assertThat(ctx.getFacts()).hasSize(1);
        }
    }

    // ==================== 辅助类与方法 ====================

    /**
     * 构造简单 Rule 桩
     *
     * @param code      规则编码
     * @param name      规则名称
     * @param triggered 是否触发
     */
    private Rule mockRule(String code, String name, boolean triggered) {
        return new Rule() {
            @Override public String getCode() { return code; }
            @Override public String getName() { return name; }
            @Override public String getCategory() { return "TEST"; }
            @Override public RuleResult evaluate(RuleContext context) {
                return triggered
                        ? RuleResult.triggered(code, name, "TEST", RuleSeverity.YELLOW, "t", "d")
                        : RuleResult.notTriggered(code);
            }
        };
    }

    /**
     * 构造带 sleep 的 Rule 桩，保证 elapsedMs > 0
     *
     * @param code      规则编码
     * @param name      规则名称
     * @param triggered 是否触发
     * @param sleepMs   评估前 sleep 毫秒数
     */
    private Rule slowMockRule(String code, String name, boolean triggered, long sleepMs) {
        return new Rule() {
            @Override public String getCode() { return code; }
            @Override public String getName() { return name; }
            @Override public String getCategory() { return "TEST"; }
            @Override public RuleResult evaluate(RuleContext context) {
                if (sleepMs > 0) {
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return triggered
                        ? RuleResult.triggered(code, name, "TEST", RuleSeverity.YELLOW, "t", "d")
                        : RuleResult.notTriggered(code);
            }
        };
    }

    /**
     * 记录型 BreakpointHook，用于断言 hook 调用情况
     */
    static class RecordingHook implements BreakpointHook {
        final List<BreakpointContext> beforeCalls = new ArrayList<>();
        final List<BreakpointContext> afterCalls = new ArrayList<>();
        final Set<String> breakpoints = ConcurrentHashMap.newKeySet();
        volatile BreakpointAction action = BreakpointAction.CONTINUE;

        @Override
        public BreakpointAction onBeforeEvaluate(BreakpointContext ctx) {
            beforeCalls.add(ctx);
            return action;
        }

        @Override
        public void onAfterEvaluate(BreakpointContext ctx) {
            afterCalls.add(ctx);
        }

        @Override
        public boolean hasBreakpoint(String ruleCode) {
            return breakpoints.contains(ruleCode);
        }
    }
}
