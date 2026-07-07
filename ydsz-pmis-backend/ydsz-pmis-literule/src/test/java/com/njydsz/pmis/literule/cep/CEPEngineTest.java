package com.njydsz.pmis.literule.cep;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CEPEngine 单元测试
 *
 * <p>测试 CEP 引擎的模式注册/注销、四种模式类型（时间窗口、序列、聚合、缺失）、
 * 事件推送与命中、多模式并行、边界条件（空事件/null 字段/重复事件）、
 * 过期状态清理等核心能力，目标覆盖率 100%。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("CEPEngine 单元测试")
class CEPEngineTest {

    private CEPEngine engine;

    @BeforeEach
    void setUp() {
        engine = new CEPEngine();
    }

    // ==================== 辅助方法 ====================

    /** 构造事件（指定类型和时间戳） */
    private CEPEvent event(String type, Instant ts) {
        return CEPEvent.builder().type(type).timestamp(ts).build();
    }

    /** 构造事件（指定类型、时间戳、分区键） */
    private CEPEvent event(String type, Instant ts, String partitionKey) {
        return CEPEvent.builder().type(type).timestamp(ts).partitionKey(partitionKey).build();
    }

    /** 构造事件（指定类型、时间戳、属性） */
    private CEPEvent event(String type, Instant ts, Map<String, Object> attrs) {
        return CEPEvent.builder().type(type).timestamp(ts).attributes(attrs).build();
    }

    /** 构造收集命中结果的监听器 */
    private List<CEPHit> hitCollector() {
        List<CEPHit> hits = new ArrayList<>();
        engine.addListener(hits::add);
        return hits;
    }

    /** 基准时间 */
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    // ==================== 模式注册与注销 ====================

    @Nested
    @DisplayName("模式注册与注销")
    class RegisterTest {

        @Test
        @DisplayName("注册 null 模式 - 抛出 IllegalArgumentException")
        void shouldThrowWhenRegisterNullPattern() {
            assertThatThrownBy(() -> engine.registerPattern(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("pattern");
        }

        @Test
        @DisplayName("注册 id 为 null 的模式 - 抛出 IllegalArgumentException")
        void shouldThrowWhenRegisterPatternWithNullId() {
            CEPPattern pattern = CEPPattern.builder()
                    .id(null)
                    .type(CEPPattern.PatternType.TIME_WINDOW)
                    .build();

            assertThatThrownBy(() -> engine.registerPattern(pattern))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("pattern.id");
        }

        @Test
        @DisplayName("注册有效 TIME_WINDOW 模式 - patternCount 递增")
        void shouldRegisterTimeWindowPattern() {
            CEPPattern pattern = CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 3);

            engine.registerPattern(pattern);

            assertThat(engine.patternCount()).isEqualTo(1);
            assertThat(engine.listPatterns()).hasSize(1);
            assertThat(engine.listPatterns().get(0).getId()).isEqualTo("p1");
        }

        @Test
        @DisplayName("注册 SEQUENCE 模式 - 同时初始化序列状态表")
        void shouldRegisterSequencePattern() {
            CEPPattern pattern = CEPPatternFactory.sequence("p1", "R1",
                    Duration.ofMinutes(3), List.of(
                            CEPPattern.SequenceStep.builder().order(1).eventType("A").build()));

            engine.registerPattern(pattern);

            assertThat(engine.patternCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("注册相同 id 模式 - 覆盖原模式")
        void shouldOverwritePatternWithSameId() {
            CEPPattern p1 = CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 3);
            CEPPattern p2 = CEPPatternFactory.timeWindow("p1", "R2", "E",
                    Duration.ofMinutes(5), 5);

            engine.registerPattern(p1);
            engine.registerPattern(p2);

            assertThat(engine.patternCount()).isEqualTo(1);
            assertThat(engine.listPatterns().get(0).getRuleCode()).isEqualTo("R2");
        }

        @Test
        @DisplayName("注销 null 模式 id - 空操作")
        void shouldNoOpWhenUnregisterNullId() {
            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 3));

            engine.unregisterPattern(null);

            assertThat(engine.patternCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("注销已注册模式 - 从注册表中移除")
        void shouldUnregisterExistingPattern() {
            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 3));

            engine.unregisterPattern("p1");

            assertThat(engine.patternCount()).isZero();
            assertThat(engine.listPatterns()).isEmpty();
        }

        @Test
        @DisplayName("注销不存在的模式 - 无副作用")
        void shouldNoOpWhenUnregisterNonExistent() {
            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 3));

            engine.unregisterPattern("non-existent");

            assertThat(engine.patternCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("listPatterns - 返回不可修改的副本")
        void shouldReturnUnmodifiableListPatterns() {
            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 3));

            List<CEPPattern> patterns = engine.listPatterns();
            assertThat(patterns).hasSize(1);
            assertThatThrownBy(() -> patterns.add(null))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("patternCount - 反映当前注册数")
        void shouldReturnCorrectPatternCount() {
            assertThat(engine.patternCount()).isZero();

            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 3));
            engine.registerPattern(CEPPatternFactory.timeWindow("p2", "R2", "F",
                    Duration.ofMinutes(3), 3));

            assertThat(engine.patternCount()).isEqualTo(2);
        }
    }

    // ==================== 监听器管理 ====================

    @Nested
    @DisplayName("监听器管理")
    class ListenerTest {

        @Test
        @DisplayName("添加 null 监听器 - 空操作")
        void shouldNotAddNullListener() {
            engine.addListener(null);

            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 1));
            engine.feed(event("E", T0));

            // 无监听器收到事件，但不应抛异常
            assertThat(engine.totalHits()).isEqualTo(1);
        }

        @Test
        @DisplayName("移除监听器 - 不再接收命中回调")
        void shouldRemoveListener() {
            List<CEPHit> hits = new ArrayList<>();
            Consumer<CEPHit> listener = hits::add;
            engine.addListener(listener);

            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 1));

            engine.feed(event("E", T0));
            assertThat(hits).hasSize(1);

            engine.removeListener(listener);
            engine.feed(event("E", T0.plusSeconds(1)));
            assertThat(hits).hasSize(1); // 仍然只有 1 次
        }

        @Test
        @DisplayName("移除未注册的监听器 - 无副作用")
        void shouldNoOpWhenRemoveUnregisteredListener() {
            Consumer<CEPHit> listener = h -> {};
            engine.removeListener(listener); // 无异常即通过
        }

        @Test
        @DisplayName("监听器抛异常 - 不影响其他监听器")
        void shouldNotAffectOtherListenersWhenOneThrows() {
            List<CEPHit> hits = new ArrayList<>();
            Consumer<CEPHit> badListener = h -> { throw new RuntimeException("监听器异常"); };
            Consumer<CEPHit> goodListener = hits::add;

            engine.addListener(badListener);
            engine.addListener(goodListener);

            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 1));
            engine.feed(event("E", T0));

            // 异常监听器不影响正常监听器
            assertThat(hits).hasSize(1);
            assertThat(engine.totalHits()).isEqualTo(1);
        }
    }

    // ==================== 时间窗口模式 ====================

    @Nested
    @DisplayName("时间窗口模式")
    class TimeWindowTest {

        @Test
        @DisplayName("窗口内事件数达到阈值 - 命中")
        void shouldHitWhenThresholdReached() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "LOGIN_FAILED",
                    Duration.ofMinutes(3), 3));

            engine.feed(event("LOGIN_FAILED", T0));
            engine.feed(event("LOGIN_FAILED", T0.plusSeconds(60)));
            assertThat(hits).isEmpty();

            engine.feed(event("LOGIN_FAILED", T0.plusSeconds(120)));
            assertThat(hits).hasSize(1);

            CEPHit hit = hits.get(0);
            assertThat(hit.getPatternId()).isEqualTo("p1");
            assertThat(hit.getRuleCode()).isEqualTo("R1");
            assertThat(hit.getMatchedEvents()).hasSize(3);
            assertThat(hit.getMetric()).isEqualTo(3.0);
            assertThat(hit.getContext()).containsKey("partitionKey");
            assertThat(hit.getContext()).containsKey("triggerType");
        }

        @Test
        @DisplayName("窗口外事件被裁剪 - 不命中")
        void shouldTrimEventsOutsideWindow() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 3));

            // 早期事件会被后续事件的窗口裁剪掉
            engine.feed(event("E", T0));
            engine.feed(event("E", T0.plusMinutes(1)));
            // T+10m 的窗口起点为 T+7m，T0 和 T+1m 都在窗口外被移除
            engine.feed(event("E", T0.plusMinutes(10)));

            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("事件类型不匹配 - 不处理")
        void shouldSkipMismatchedEventType() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "LOGIN_FAILED",
                    Duration.ofMinutes(3), 1));

            engine.feed(event("OTHER", T0));

            assertThat(hits).isEmpty();
            assertThat(engine.totalHits()).isZero();
        }

        @Test
        @DisplayName("eventTypes 列表匹配 - 命中")
        void shouldMatchEventTypesList() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPattern.builder()
                    .id("p1")
                    .type(CEPPattern.PatternType.TIME_WINDOW)
                    .ruleCode("R1")
                    .eventTypes(List.of("LOGIN_FAILED", "LOGIN_TIMEOUT"))
                    .window(Duration.ofMinutes(3))
                    .threshold(1)
                    .build();
            engine.registerPattern(pattern);

            engine.feed(event("LOGIN_TIMEOUT", T0));

            assertThat(hits).hasSize(1);
        }

        @Test
        @DisplayName("eventTypes 列表不匹配 - 不处理")
        void shouldSkipWhenEventTypesListNotMatched() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPattern.builder()
                    .id("p1")
                    .type(CEPPattern.PatternType.TIME_WINDOW)
                    .ruleCode("R1")
                    .eventTypes(List.of("LOGIN_FAILED", "LOGIN_TIMEOUT"))
                    .window(Duration.ofMinutes(3))
                    .threshold(1)
                    .build();
            engine.registerPattern(pattern);

            engine.feed(event("OTHER", T0));

            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("eventType 和 eventTypes 均为 null - 匹配所有类型")
        void shouldMatchAllWhenNoTypeSpecified() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPattern.builder()
                    .id("p1")
                    .type(CEPPattern.PatternType.TIME_WINDOW)
                    .ruleCode("R1")
                    .window(Duration.ofMinutes(3))
                    .threshold(1)
                    .build();
            engine.registerPattern(pattern);

            engine.feed(event("ANYTHING", T0));

            assertThat(hits).hasSize(1);
        }

        @Test
        @DisplayName("eventTypes 为空列表 - 匹配所有类型")
        void shouldMatchAllWhenEventTypesEmpty() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPattern.builder()
                    .id("p1")
                    .type(CEPPattern.PatternType.TIME_WINDOW)
                    .ruleCode("R1")
                    .eventTypes(List.of())
                    .window(Duration.ofMinutes(3))
                    .threshold(1)
                    .build();
            engine.registerPattern(pattern);

            engine.feed(event("ANYTHING", T0));

            assertThat(hits).hasSize(1);
        }

        @Test
        @DisplayName("过滤器表达式通过 - 事件被处理")
        void shouldProcessWhenFilterPasses() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPattern.builder()
                    .id("p1")
                    .type(CEPPattern.PatternType.TIME_WINDOW)
                    .ruleCode("R1")
                    .eventType("E")
                    .window(Duration.ofMinutes(3))
                    .threshold(1)
                    .filter("amount > 50")
                    .build();
            engine.registerPattern(pattern);

            Map<String, Object> attrs = new HashMap<>();
            attrs.put("amount", 100);
            engine.feed(event("E", T0, attrs));

            assertThat(hits).hasSize(1);
        }

        @Test
        @DisplayName("过滤器表达式不通过 - 事件被过滤")
        void shouldFilterOutWhenFilterFails() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPattern.builder()
                    .id("p1")
                    .type(CEPPattern.PatternType.TIME_WINDOW)
                    .ruleCode("R1")
                    .eventType("E")
                    .window(Duration.ofMinutes(3))
                    .threshold(1)
                    .filter("amount > 50")
                    .build();
            engine.registerPattern(pattern);

            Map<String, Object> attrs = new HashMap<>();
            attrs.put("amount", 10);
            engine.feed(event("E", T0, attrs));

            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("过滤器表达式异常 - 视为不通过")
        void shouldFilterOutWhenFilterThrows() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPattern.builder()
                    .id("p1")
                    .type(CEPPattern.PatternType.TIME_WINDOW)
                    .ruleCode("R1")
                    .eventType("E")
                    .window(Duration.ofMinutes(3))
                    .threshold(1)
                    .filter("##invalid##")
                    .build();
            engine.registerPattern(pattern);

            engine.feed(event("E", T0));

            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("过滤器为空白 - 不做过滤")
        void shouldNotFilterWhenFilterIsBlank() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPattern.builder()
                    .id("p1")
                    .type(CEPPattern.PatternType.TIME_WINDOW)
                    .ruleCode("R1")
                    .eventType("E")
                    .window(Duration.ofMinutes(3))
                    .threshold(1)
                    .filter("   ")
                    .build();
            engine.registerPattern(pattern);

            engine.feed(event("E", T0));

            assertThat(hits).hasSize(1);
        }
    }

    // ==================== 序列模式 ====================

    @Nested
    @DisplayName("序列模式")
    class SequenceTest {

        @Test
        @DisplayName("完整序列 A→B→C - 命中")
        void shouldHitWhenSequenceComplete() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPatternFactory.sequence("p1", "R1",
                    Duration.ofMinutes(10), List.of(
                            CEPPattern.SequenceStep.builder().order(1).eventType("A").build(),
                            CEPPattern.SequenceStep.builder().order(2).eventType("B").build(),
                            CEPPattern.SequenceStep.builder().order(3).eventType("C").build()));
            engine.registerPattern(pattern);

            engine.feed(event("A", T0));
            engine.feed(event("B", T0.plusSeconds(1)));
            assertThat(hits).isEmpty();

            engine.feed(event("C", T0.plusSeconds(2)));
            assertThat(hits).hasSize(1);

            CEPHit hit = hits.get(0);
            assertThat(hit.getMatchedEvents()).hasSize(3);
            assertThat(hit.getMetric()).isZero();
        }

        @Test
        @DisplayName("序列未完成 - 不命中")
        void shouldNotHitWhenSequenceIncomplete() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPatternFactory.sequence("p1", "R1",
                    Duration.ofMinutes(10), List.of(
                            CEPPattern.SequenceStep.builder().order(1).eventType("A").build(),
                            CEPPattern.SequenceStep.builder().order(2).eventType("B").build()));
            engine.registerPattern(pattern);

            engine.feed(event("A", T0));

            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("序列步骤类型不匹配 - 不匹配")
        void shouldNotMatchWhenStepTypeMismatch() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPatternFactory.sequence("p1", "R1",
                    Duration.ofMinutes(10), List.of(
                            CEPPattern.SequenceStep.builder().order(1).eventType("A").build()));
            engine.registerPattern(pattern);

            engine.feed(event("X", T0));

            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("序列步骤过滤器不通过 - 不匹配")
        void shouldNotMatchWhenStepFilterFails() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPatternFactory.sequence("p1", "R1",
                    Duration.ofMinutes(10), List.of(
                            CEPPattern.SequenceStep.builder()
                                    .order(1).eventType("A").filter("amount > 50").build()));
            engine.registerPattern(pattern);

            Map<String, Object> attrs = new HashMap<>();
            attrs.put("amount", 10);
            engine.feed(event("A", T0, attrs));

            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("序列步骤过滤器通过 - 匹配")
        void shouldMatchWhenStepFilterPasses() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPatternFactory.sequence("p1", "R1",
                    Duration.ofMinutes(10), List.of(
                            CEPPattern.SequenceStep.builder()
                                    .order(1).eventType("A").filter("amount > 50").build(),
                            CEPPattern.SequenceStep.builder().order(2).eventType("B").build()));
            engine.registerPattern(pattern);

            Map<String, Object> attrs = new HashMap<>();
            attrs.put("amount", 100);
            engine.feed(event("A", T0, attrs));
            engine.feed(event("B", T0.plusSeconds(1)));

            assertThat(hits).hasSize(1);
        }

        @Test
        @DisplayName("minGap 间隔过短 - 重置序列")
        void shouldResetWhenMinGapNotSatisfied() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPatternFactory.sequence("p1", "R1",
                    Duration.ofMinutes(10), List.of(
                            CEPPattern.SequenceStep.builder().order(1).eventType("A").build(),
                            CEPPattern.SequenceStep.builder()
                                    .order(2).eventType("B").minGap(Duration.ofSeconds(10)).build()));
            engine.registerPattern(pattern);

            engine.feed(event("A", T0));
            // 间隔仅 5 秒，小于 minGap=10s → 重置
            engine.feed(event("B", T0.plusSeconds(5)));

            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("minGap 间隔满足 - 继续序列")
        void shouldContinueWhenMinGapSatisfied() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPatternFactory.sequence("p1", "R1",
                    Duration.ofMinutes(10), List.of(
                            CEPPattern.SequenceStep.builder().order(1).eventType("A").build(),
                            CEPPattern.SequenceStep.builder()
                                    .order(2).eventType("B").minGap(Duration.ofSeconds(10)).build()));
            engine.registerPattern(pattern);

            engine.feed(event("A", T0));
            engine.feed(event("B", T0.plusSeconds(15)));

            assertThat(hits).hasSize(1);
        }

        @Test
        @DisplayName("maxGap 间隔超长 - 重置序列")
        void shouldResetWhenMaxGapExceeded() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPatternFactory.sequence("p1", "R1",
                    Duration.ofMinutes(10), List.of(
                            CEPPattern.SequenceStep.builder().order(1).eventType("A").build(),
                            CEPPattern.SequenceStep.builder()
                                    .order(2).eventType("B").maxGap(Duration.ofSeconds(10)).build()));
            engine.registerPattern(pattern);

            engine.feed(event("A", T0));
            // 间隔 20 秒，超过 maxGap=10s → 重置
            engine.feed(event("B", T0.plusSeconds(20)));

            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("maxGap 间隔满足 - 继续序列")
        void shouldContinueWhenMaxGapSatisfied() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPatternFactory.sequence("p1", "R1",
                    Duration.ofMinutes(10), List.of(
                            CEPPattern.SequenceStep.builder().order(1).eventType("A").build(),
                            CEPPattern.SequenceStep.builder()
                                    .order(2).eventType("B").maxGap(Duration.ofSeconds(10)).build()));
            engine.registerPattern(pattern);

            engine.feed(event("A", T0));
            engine.feed(event("B", T0.plusSeconds(5)));

            assertThat(hits).hasSize(1);
        }

        @Test
        @DisplayName("序列完成后重置 - 可再次匹配")
        void shouldResetAfterCompletion() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPatternFactory.sequence("p1", "R1",
                    Duration.ofMinutes(10), List.of(
                            CEPPattern.SequenceStep.builder().order(1).eventType("A").build(),
                            CEPPattern.SequenceStep.builder().order(2).eventType("B").build()));
            engine.registerPattern(pattern);

            engine.feed(event("A", T0));
            engine.feed(event("B", T0.plusSeconds(1)));
            engine.feed(event("A", T0.plusSeconds(2)));
            engine.feed(event("B", T0.plusSeconds(3)));

            assertThat(hits).hasSize(2);
        }

        @Test
        @DisplayName("空序列步骤 - 直接返回")
        void shouldReturnWhenSequenceIsEmpty() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPattern.builder()
                    .id("p1")
                    .type(CEPPattern.PatternType.SEQUENCE)
                    .ruleCode("R1")
                    .window(Duration.ofMinutes(10))
                    .sequence(List.of())
                    .build();
            engine.registerPattern(pattern);

            engine.feed(event("A", T0));

            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("null 序列步骤 - 直接返回")
        void shouldReturnWhenSequenceIsNull() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPattern.builder()
                    .id("p1")
                    .type(CEPPattern.PatternType.SEQUENCE)
                    .ruleCode("R1")
                    .window(Duration.ofMinutes(10))
                    .sequence(null)
                    .build();
            engine.registerPattern(pattern);

            engine.feed(event("A", T0));

            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("非连续步骤序号 - findStep 找不到下一步时重置")
        void shouldResetWhenNextStepNotFound() {
            List<CEPHit> hits = hitCollector();
            // 步骤 1 和 3，缺少 2
            CEPPattern pattern = CEPPatternFactory.sequence("p1", "R1",
                    Duration.ofMinutes(10), List.of(
                            CEPPattern.SequenceStep.builder().order(1).eventType("A").build(),
                            CEPPattern.SequenceStep.builder().order(3).eventType("B").build()));
            engine.registerPattern(pattern);

            engine.feed(event("A", T0));
            // currentStep=1，findStep(2)=null → 重置 → findStep(1)=stepA → B≠A → 不匹配
            engine.feed(event("B", T0.plusSeconds(1)));

            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("无 order=1 的步骤 - 重置后仍找不到步骤直接返回")
        void shouldReturnWhenNoStep1Exists() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPatternFactory.sequence("p1", "R1",
                    Duration.ofMinutes(10), List.of(
                            CEPPattern.SequenceStep.builder().order(2).eventType("B").build()));
            engine.registerPattern(pattern);

            engine.feed(event("B", T0));

            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("分区间序列状态隔离")
        void shouldIsolateSequenceStateByPartition() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPatternFactory.sequence("p1", "R1",
                    Duration.ofMinutes(10), List.of(
                            CEPPattern.SequenceStep.builder().order(1).eventType("A").build(),
                            CEPPattern.SequenceStep.builder().order(2).eventType("B").build()));
            engine.registerPattern(pattern);

            // p1 分区匹配第一步
            engine.feed(event("A", T0, "p1"));
            // p2 分区匹配完整序列
            engine.feed(event("A", T0, "p2"));
            engine.feed(event("B", T0.plusSeconds(1), "p2"));

            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).getContext().get("partitionKey")).isEqualTo("p2");
        }
    }

    // ==================== 聚合模式 ====================

    @Nested
    @DisplayName("聚合模式")
    class AggregateTest {

        @Test
        @DisplayName("COUNT 聚合达到阈值 - 命中")
        void shouldHitWhenCountReachesThreshold() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.aggregate("p1", "R1", "E", "amount",
                    CEPPattern.AggregateFunction.COUNT, Duration.ofMinutes(5), 2));

            engine.feed(event("E", T0));
            assertThat(hits).isEmpty();

            engine.feed(event("E", T0.plusSeconds(1)));
            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).getMetric()).isEqualTo(2.0);
        }

        @Test
        @DisplayName("SUM 聚合达到阈值 - 命中")
        void shouldHitWhenSumReachesThreshold() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.aggregate("p1", "R1", "E", "amount",
                    CEPPattern.AggregateFunction.SUM, Duration.ofMinutes(5), 100));

            Map<String, Object> a1 = new HashMap<>();
            a1.put("amount", 30);
            engine.feed(event("E", T0, a1));
            assertThat(hits).isEmpty();

            Map<String, Object> a2 = new HashMap<>();
            a2.put("amount", 80);
            engine.feed(event("E", T0.plusSeconds(1), a2));
            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).getMetric()).isEqualTo(110.0);
        }

        @Test
        @DisplayName("AVG 聚合达到阈值 - 命中")
        void shouldHitWhenAvgReachesThreshold() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.aggregate("p1", "R1", "E", "amount",
                    CEPPattern.AggregateFunction.AVG, Duration.ofMinutes(5), 50));

            Map<String, Object> a1 = new HashMap<>();
            a1.put("amount", 40);
            engine.feed(event("E", T0, a1));
            assertThat(hits).isEmpty();

            Map<String, Object> a2 = new HashMap<>();
            a2.put("amount", 60);
            engine.feed(event("E", T0.plusSeconds(1), a2));
            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).getMetric()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("MIN 聚合达到阈值 - 命中")
        void shouldHitWhenMinReachesThreshold() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.aggregate("p1", "R1", "E", "amount",
                    CEPPattern.AggregateFunction.MIN, Duration.ofMinutes(5), 5));

            Map<String, Object> a1 = new HashMap<>();
            a1.put("amount", 10);
            engine.feed(event("E", T0, a1));

            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).getMetric()).isEqualTo(10.0);
        }

        @Test
        @DisplayName("MAX 聚合达到阈值 - 命中")
        void shouldHitWhenMaxReachesThreshold() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.aggregate("p1", "R1", "E", "amount",
                    CEPPattern.AggregateFunction.MAX, Duration.ofMinutes(5), 15));

            Map<String, Object> a1 = new HashMap<>();
            a1.put("amount", 10);
            engine.feed(event("E", T0, a1));
            assertThat(hits).isEmpty();

            Map<String, Object> a2 = new HashMap<>();
            a2.put("amount", 20);
            engine.feed(event("E", T0.plusSeconds(1), a2));
            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).getMetric()).isEqualTo(20.0);
        }

        @Test
        @DisplayName("聚合值未达阈值 - 不命中")
        void shouldNotHitWhenAggregateBelowThreshold() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.aggregate("p1", "R1", "E", "amount",
                    CEPPattern.AggregateFunction.SUM, Duration.ofMinutes(5), 100));

            Map<String, Object> a1 = new HashMap<>();
            a1.put("amount", 30);
            engine.feed(event("E", T0, a1));

            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("null 聚合函数 - metric=0")
        void shouldReturnZeroWhenAggregateFunctionNull() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPattern.builder()
                    .id("p1")
                    .type(CEPPattern.PatternType.AGGREGATE)
                    .ruleCode("R1")
                    .eventType("E")
                    .aggregateField("amount")
                    .aggregateFunction(null)
                    .window(Duration.ofMinutes(5))
                    .threshold(0)
                    .build();
            engine.registerPattern(pattern);

            engine.feed(event("E", T0));

            // func==null → metric=0 → 0>=0 → 命中
            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).getMetric()).isZero();
        }

        @Test
        @DisplayName("null 聚合字段 - 各值按 0 计算")
        void shouldUseZeroWhenAggregateFieldNull() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPattern.builder()
                    .id("p1")
                    .type(CEPPattern.PatternType.AGGREGATE)
                    .ruleCode("R1")
                    .eventType("E")
                    .aggregateField(null)
                    .aggregateFunction(CEPPattern.AggregateFunction.SUM)
                    .window(Duration.ofMinutes(5))
                    .threshold(0)
                    .build();
            engine.registerPattern(pattern);

            engine.feed(event("E", T0));

            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).getMetric()).isZero();
        }

        @Test
        @DisplayName("窗口外事件被裁剪 - 仅聚合窗口内事件")
        void shouldTrimOldEventsInAggregate() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.aggregate("p1", "R1", "E", "amount",
                    CEPPattern.AggregateFunction.SUM, Duration.ofMinutes(3), 100));

            Map<String, Object> a1 = new HashMap<>();
            a1.put("amount", 50);
            engine.feed(event("E", T0, a1));

            // T+10m 窗口起点为 T+7m，T0 的事件被裁剪
            Map<String, Object> a2 = new HashMap<>();
            a2.put("amount", 60);
            engine.feed(event("E", T0.plusMinutes(10), a2));

            // 仅 60 在窗口内，60 < 100 → 不命中
            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("事件类型不匹配 - 不处理")
        void shouldSkipMismatchedTypeInAggregate() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.aggregate("p1", "R1", "E", "amount",
                    CEPPattern.AggregateFunction.COUNT, Duration.ofMinutes(5), 1));

            engine.feed(event("X", T0));

            assertThat(hits).isEmpty();
        }
    }

    // ==================== 缺失模式 ====================

    @Nested
    @DisplayName("缺失模式")
    class AbsenceTest {

        @Test
        @DisplayName("期待事件出现 - 清空队列不命中")
        void shouldClearQueueWhenExpectedEventAppears() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.absence("p1", "R1", "HEARTBEAT",
                    Duration.ofMinutes(5), 3));

            // 投放非期待事件，积累队列
            engine.feed(event("OTHER", T0));
            engine.feed(event("OTHER", T0.plusSeconds(1)));
            // 期待事件出现，清空队列
            engine.feed(event("HEARTBEAT", T0.plusSeconds(2)));
            // 再投放一个非期待事件，队列仅 1 个
            engine.feed(event("OTHER", T0.plusSeconds(3)));

            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("窗口内未出现期待事件达到阈值 - 命中")
        void shouldHitWhenExpectedEventAbsent() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.absence("p1", "R1", "HEARTBEAT",
                    Duration.ofMinutes(5), 3));

            engine.feed(event("OTHER", T0));
            engine.feed(event("OTHER", T0.plusSeconds(1)));
            assertThat(hits).isEmpty();

            engine.feed(event("OTHER", T0.plusSeconds(2)));
            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).getMetric()).isEqualTo(3.0);
        }

        @Test
        @DisplayName("缺失模式窗口裁剪 - 旧事件被移除")
        void shouldTrimOldEventsInAbsence() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.absence("p1", "R1", "HEARTBEAT",
                    Duration.ofMinutes(2), 3));

            engine.feed(event("OTHER", T0));
            engine.feed(event("OTHER", T0.plusMinutes(1)));
            // T+10m 窗口起点为 T+8m，旧事件被裁剪
            engine.feed(event("OTHER", T0.plusMinutes(10)));

            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("eventType 为 null - 所有事件都积累")
        void shouldAccumulateAllWhenEventTypeNull() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPattern.builder()
                    .id("p1")
                    .type(CEPPattern.PatternType.ABSENCE)
                    .ruleCode("R1")
                    .eventType(null)
                    .window(Duration.ofMinutes(5))
                    .threshold(2)
                    .build();
            engine.registerPattern(pattern);

            engine.feed(event("A", T0));
            engine.feed(event("B", T0.plusSeconds(1)));

            assertThat(hits).hasSize(1);
        }

        @Test
        @DisplayName("未达阈值 - 不命中")
        void shouldNotHitWhenBelowThreshold() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.absence("p1", "R1", "HEARTBEAT",
                    Duration.ofMinutes(5), 5));

            engine.feed(event("OTHER", T0));
            engine.feed(event("OTHER", T0.plusSeconds(1)));

            assertThat(hits).isEmpty();
        }
    }

    // ==================== 多模式并行 ====================

    @Nested
    @DisplayName("多模式并行")
    class MultiPatternTest {

        @Test
        @DisplayName("事件匹配多个模式 - 分别命中")
        void shouldHitMultiplePatterns() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 1));
            engine.registerPattern(CEPPatternFactory.timeWindow("p2", "R2", "E",
                    Duration.ofMinutes(3), 1));

            engine.feed(event("E", T0));

            assertThat(hits).hasSize(2);
            assertThat(hits).extracting(CEPHit::getPatternId)
                    .containsExactlyInAnyOrder("p1", "p2");
        }

        @Test
        @DisplayName("事件仅匹配一个模式 - 只有该模式命中")
        void shouldHitOnlyMatchingPattern() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 1));
            engine.registerPattern(CEPPatternFactory.timeWindow("p2", "R2", "F",
                    Duration.ofMinutes(3), 1));

            engine.feed(event("E", T0));

            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).getPatternId()).isEqualTo("p1");
        }

        @Test
        @DisplayName("不同分区并行处理 - 独立命中")
        void shouldProcessPartitionsIndependently() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 2));

            engine.feed(event("E", T0, "p1"));
            engine.feed(event("E", T0, "p2"));
            engine.feed(event("E", T0.plusSeconds(1), "p1"));
            engine.feed(event("E", T0.plusSeconds(1), "p2"));

            assertThat(hits).hasSize(2);
        }
    }

    // ==================== 边界与异常 ====================

    @Nested
    @DisplayName("边界与异常")
    class BoundaryTest {

        @Test
        @DisplayName("feed null 事件 - 空操作")
        void shouldNoOpWhenFeedNullEvent() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 1));

            engine.feed(null);

            assertThat(hits).isEmpty();
            assertThat(engine.totalHits()).isZero();
        }

        @Test
        @DisplayName("事件 timestamp 为 null - 异常被捕获不传播")
        void shouldCatchExceptionWhenTimestampIsNull() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 1));

            // timestamp 为 null 会导致 handleTimeWindow NPE，但被 feed 捕获
            CEPEvent badEvent = CEPEvent.builder().type("E").timestamp(null).build();
            engine.feed(badEvent);

            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("事件属性为 null - 过滤器正常处理")
        void shouldHandleNullAttributesWithFilter() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPattern.builder()
                    .id("p1")
                    .type(CEPPattern.PatternType.TIME_WINDOW)
                    .ruleCode("R1")
                    .eventType("E")
                    .window(Duration.ofMinutes(3))
                    .threshold(1)
                    .filter("amount > 50")
                    .build();
            engine.registerPattern(pattern);

            CEPEvent e = CEPEvent.builder().type("E").timestamp(T0).attributes(null).build();
            engine.feed(e);

            // attributes 为 null → 过滤器无法获取 amount → 求值失败 → false
            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("重复投递同一事件 - 队列中重复计数")
        void shouldCountDuplicateEvents() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 2));

            CEPEvent e = event("E", T0);
            engine.feed(e);
            engine.feed(e);

            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).getMatchedEvents()).hasSize(2);
        }

        @Test
        @DisplayName("多模式处理时其中一个异常 - 不影响其他模式")
        void shouldNotAffectOtherPatternsWhenOneThrows() {
            List<CEPHit> hits = hitCollector();
            // p1 会因 null timestamp 抛异常
            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 1));
            // p2 类型不同，不会处理该事件
            engine.registerPattern(CEPPatternFactory.timeWindow("p2", "R2", "F",
                    Duration.ofMinutes(3), 1));

            CEPEvent badEvent = CEPEvent.builder().type("E").timestamp(null).build();
            engine.feed(badEvent); // p1 异常被捕获，p2 类型不匹配跳过

            assertThat(hits).isEmpty();
        }
    }

    // ==================== 状态清理 ====================

    @Nested
    @DisplayName("状态清理")
    class ClearTest {

        @Test
        @DisplayName("clearPartition - null patternId 空操作")
        void shouldNoOpWhenClearPartitionWithNullPatternId() {
            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 1));
            engine.clearPartition(null, "default");
            // 无异常即通过
        }

        @Test
        @DisplayName("clearPartition - null partitionKey 空操作")
        void shouldNoOpWhenClearPartitionWithNullPartitionKey() {
            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 1));
            engine.clearPartition("p1", null);
            // 无异常即通过
        }

        @Test
        @DisplayName("clearPartition - 未注册模式 空操作")
        void shouldNoOpWhenClearPartitionForUnregisteredPattern() {
            engine.clearPartition("non-existent", "default");
            // 无异常即通过
        }

        @Test
        @DisplayName("clearPartition - 清理 TIME_WINDOW 队列")
        void shouldClearTimeWindowQueue() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 3));

            engine.feed(event("E", T0));
            engine.feed(event("E", T0.plusSeconds(1)));

            // 清理分区后队列被清空
            engine.clearPartition("p1", "default");

            // 再投一个事件，如果队列未清空则 count=3 命中；清空后 count=1 不命中
            engine.feed(event("E", T0.plusSeconds(2)));
            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("clearPartition - 清理 SEQUENCE 状态")
        void shouldClearSequenceState() {
            List<CEPHit> hits = hitCollector();
            CEPPattern pattern = CEPPatternFactory.sequence("p1", "R1",
                    Duration.ofMinutes(10), List.of(
                            CEPPattern.SequenceStep.builder().order(1).eventType("A").build(),
                            CEPPattern.SequenceStep.builder().order(2).eventType("B").build()));
            engine.registerPattern(pattern);

            engine.feed(event("A", T0));

            // 清理序列状态
            engine.clearPartition("p1", "default");

            // 再投 B，如果状态未清空则命中；清空后 B 不会匹配第一步 A
            engine.feed(event("B", T0.plusSeconds(1)));
            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("clearAll - 清理所有队列和状态")
        void shouldClearAllQueuesAndStates() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 3));

            engine.feed(event("E", T0));
            engine.feed(event("E", T0.plusSeconds(1)));

            engine.clearAll();

            // 队列已清空，再投一个事件不会命中
            engine.feed(event("E", T0.plusSeconds(2)));
            assertThat(hits).isEmpty();
        }
    }

    // ==================== 统计与查询 ====================

    @Nested
    @DisplayName("统计与查询")
    class StatsTest {

        @Test
        @DisplayName("totalHits - 初始为 0")
        void shouldReturnZeroTotalHitsInitially() {
            assertThat(engine.totalHits()).isZero();
        }

        @Test
        @DisplayName("totalHits - 命中后递增")
        void shouldIncrementTotalHitsOnHit() {
            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 1));

            engine.feed(event("E", T0));
            assertThat(engine.totalHits()).isEqualTo(1);

            engine.feed(event("E", T0.plusSeconds(1)));
            assertThat(engine.totalHits()).isEqualTo(2);
        }

        @Test
        @DisplayName("totalHits - 不命中时不递增")
        void shouldNotIncrementTotalHitsWhenNoHit() {
            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 5));

            engine.feed(event("E", T0));
            assertThat(engine.totalHits()).isZero();
        }

        @Test
        @DisplayName("命中结果包含完整上下文信息")
        void shouldBuildHitWithFullContext() {
            List<CEPHit> hits = hitCollector();
            engine.registerPattern(CEPPatternFactory.timeWindow("p1", "R1", "E",
                    Duration.ofMinutes(3), 1));

            engine.feed(event("E", T0, "tenant-1"));

            assertThat(hits).hasSize(1);
            CEPHit hit = hits.get(0);
            assertThat(hit.getPatternId()).isEqualTo("p1");
            assertThat(hit.getRuleCode()).isEqualTo("R1");
            assertThat(hit.getHitAt()).isNotNull();
            assertThat(hit.getContext().get("partitionKey")).isEqualTo("tenant-1");
            assertThat(hit.getContext().get("triggerType")).isEqualTo("E");
        }
    }
}
