package com.njydsz.literule.server.cep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.njydsz.literule.domain.expression.ExpressionEngine;
import com.njydsz.literule.domain.vo.RuleContextVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link CEPEngine} 单元测试
 *
 * <p>验证复杂事件处理（CEP）引擎的核心行为：模式注册/注销、事件投递、滚动窗口计数、
 * 命中监听、状态清理。使用 Mockito 模拟 {@link ExpressionEngine} 以实现纯单元测试
 * （不依赖真实 LiteExprEngine 运行时）。
 *
 * @author ydsz-team
 * @since 26.09.16
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class CEPEngineTest {

  /** 测试用模式 ID */
  private static final String PATTERN_ID = "login-fail-pattern";

  /** 测试用规则编码 */
  private static final String RULE_CODE = "R_LOGIN_BRUTE_FORCE";

  /** 测试用事件类型 */
  private static final String EVENT_TYPE_LOGIN_FAILED = "LOGIN_FAILED";

  /** 测试用分区键 */
  private static final String PARTITION_KEY = "user-001";

  private ExpressionEngine mockEvaluator;

  @BeforeEach
  void setUp() {
    mockEvaluator = mock(ExpressionEngine.class);
  }

  /**
   * 创建测试用 CEP 引擎
   *
   * @param threshold 触发阈值
   * @param windowMinutes 窗口分钟数
   * @return 配置好的 CEPEngine 实例
   */
  private CEPEngine createEngine(double threshold, long windowMinutes) {
    CEPEngine engine = new CEPEngine(mockEvaluator);
    CEPPattern pattern = CEPPattern.builder()
        .id(PATTERN_ID)
        .ruleCode(RULE_CODE)
        .name("3 分钟内 5 次登录失败")
        .eventType(EVENT_TYPE_LOGIN_FAILED)
        .window(Duration.ofMinutes(windowMinutes))
        .threshold(threshold)
        .build();
    engine.registerPattern(pattern);
    return engine;
  }

  /**
   * 创建测试用事件
   *
   * @param type 事件类型
   * @param partitionKey 分区键
   * @param timestamp 时间戳
   * @return CEPEvent 实例
   */
  private CEPEvent createEvent(String type, String partitionKey, Instant timestamp) {
    return CEPEvent.builder()
        .type(type)
        .partitionKey(partitionKey)
        .timestamp(timestamp)
        .build();
  }

  @Nested
  @DisplayName("模式注册与注销")
  class PatternRegistration {

    @Test
    @DisplayName("注册模式后 patternCount 应返回 1")
    void registerPatternShouldIncreaseCount() {
      CEPEngine engine = new CEPEngine(mockEvaluator);
      assertThat(engine.patternCount()).isZero();

      CEPPattern pattern = CEPPattern.builder()
          .id(PATTERN_ID)
          .ruleCode(RULE_CODE)
          .eventType(EVENT_TYPE_LOGIN_FAILED)
          .window(Duration.ofMinutes(3))
          .threshold(5.0)
          .build();
      engine.registerPattern(pattern);

      assertThat(engine.patternCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("注册 null 模式应抛出 IllegalArgumentException")
    void registerNullPatternShouldThrowException() {
      CEPEngine engine = new CEPEngine(mockEvaluator);
      assertThatThrownBy(() -> engine.registerPattern(null))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("注册 id 为 null 的模式应抛出 IllegalArgumentException")
    void registerPatternWithNullIdShouldThrowException() {
      CEPEngine engine = new CEPEngine(mockEvaluator);
      CEPPattern invalidPattern = CEPPattern.builder()
          .id(null)
          .ruleCode(RULE_CODE)
          .build();
      assertThatThrownBy(() -> engine.registerPattern(invalidPattern))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("注销模式后 patternCount 应返回 0")
    void unregisterPatternShouldDecreaseCount() {
      CEPEngine engine = createEngine(5.0, 3);
      assertThat(engine.patternCount()).isEqualTo(1);

      engine.unregisterPattern(PATTERN_ID);
      assertThat(engine.patternCount()).isZero();
    }

    @Test
    @DisplayName("注销 null 模式 ID 应为 no-op")
    void unregisterNullPatternIdShouldBeNoop() {
      CEPEngine engine = createEngine(5.0, 3);
      assertThatCode(() -> engine.unregisterPattern(null)).doesNotThrowAnyException();
      assertThat(engine.patternCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("listPatterns 应返回已注册的模式列表")
    void listPatternsShouldReturnRegisteredPatterns() {
      CEPEngine engine = createEngine(5.0, 3);
      List<CEPPattern> patterns = engine.listPatterns();

      assertThat(patterns).hasSize(1);
      assertThat(patterns.get(0).getId()).isEqualTo(PATTERN_ID);
      assertThat(patterns.get(0).getRuleCode()).isEqualTo(RULE_CODE);
    }

    @Test
    @DisplayName("listPatterns 返回的列表应为不可修改")
    void listPatternsShouldBeUnmodifiable() {
      CEPEngine engine = createEngine(5.0, 3);
      List<CEPPattern> patterns = engine.listPatterns();

      assertThatThrownBy(() -> patterns.add(
          CEPPattern.builder().id("p2").build()))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("事件投递与类型匹配")
  // YDIZ-WARN-001 允许保留：测试用例 mock CEP 事件，原始类型转换由 fixture 保证
  @SuppressWarnings("unchecked")
  class EventFeeding {

    @Test
    @DisplayName("投递 null 事件应为 no-op")
    void feedNullEventShouldBeNoop() {
      CEPEngine engine = createEngine(2.0, 3);
      assertThatCode(() -> engine.feed(null)).doesNotThrowAnyException();
      assertThat(engine.totalHits()).isZero();
    }

    @Test
    @DisplayName("未注册模式时投递事件应无命中")
    void feedWithNoPatternShouldNotHit() {
      CEPEngine engine = new CEPEngine(mockEvaluator);
      CEPEvent event = createEvent(EVENT_TYPE_LOGIN_FAILED, PARTITION_KEY, Instant.now());

      engine.feed(event);

      assertThat(engine.totalHits()).isZero();
    }

    @Test
    @DisplayName("事件类型不匹配时应跳过评估")
    void unmatchedEventTypeShouldBeSkipped() {
      CEPEngine engine = createEngine(1.0, 3);
      CEPEvent event = createEvent("LOGIN_SUCCESS", PARTITION_KEY, Instant.now());

      engine.feed(event);

      assertThat(engine.totalHits()).isZero();
    }

    @Test
    @DisplayName("单次事件投递（阈值=1）应触发命中")
    void singleEventFeedWithThresholdOneShouldHit() {
      CEPEngine engine = createEngine(1.0, 3);
      List<CEPHit> hits = new CopyOnWriteArrayList<>();
      engine.addListener(hits::add);

      CEPEvent event = createEvent(EVENT_TYPE_LOGIN_FAILED, PARTITION_KEY, Instant.now());
      engine.feed(event);

      assertThat(engine.totalHits()).isEqualTo(1);
      assertThat(hits).hasSize(1);
      assertThat(hits.get(0).getPatternId()).isEqualTo(PATTERN_ID);
    }

    @Test
    @DisplayName("滚动窗口达到阈值后应触发命中")
    void tumblingWindowThresholdReachedShouldHit() {
      CEPEngine engine = createEngine(3.0, 3);
      List<CEPHit> hits = new CopyOnWriteArrayList<>();
      engine.addListener(hits::add);

      Instant now = Instant.now();
      engine.feed(createEvent(EVENT_TYPE_LOGIN_FAILED, PARTITION_KEY, now));
      engine.feed(createEvent(EVENT_TYPE_LOGIN_FAILED, PARTITION_KEY, now.plusSeconds(10)));
      engine.feed(createEvent(EVENT_TYPE_LOGIN_FAILED, PARTITION_KEY, now.plusSeconds(20)));

      assertThat(engine.totalHits()).isEqualTo(1);
      assertThat(hits).hasSize(1);
      assertThat(hits.get(0).getMatchedEvents()).hasSize(3);
    }

    @Test
    @DisplayName("滚动窗口命中后应清空（下一个窗口从零开始）")
    void tumblingWindowShouldClearAfterHit() {
      CEPEngine engine = createEngine(2.0, 3);
      List<CEPHit> hits = new CopyOnWriteArrayList<>();
      engine.addListener(hits::add);

      Instant now = Instant.now();
      engine.feed(createEvent(EVENT_TYPE_LOGIN_FAILED, PARTITION_KEY, now));
      engine.feed(createEvent(EVENT_TYPE_LOGIN_FAILED, PARTITION_KEY, now.plusSeconds(5)));
      engine.feed(createEvent(EVENT_TYPE_LOGIN_FAILED, PARTITION_KEY, now.plusSeconds(10)));
      engine.feed(createEvent(EVENT_TYPE_LOGIN_FAILED, PARTITION_KEY, now.plusSeconds(15)));

      assertThat(engine.totalHits()).isEqualTo(2);
    }

    @Test
    @DisplayName("不同分区键的事件应在独立窗口中计数")
    void differentPartitionKeysShouldHaveIndependentWindows() {
      CEPEngine engine = createEngine(2.0, 3);
      List<CEPHit> hits = new CopyOnWriteArrayList<>();
      engine.addListener(hits::add);

      Instant now = Instant.now();
      engine.feed(createEvent(EVENT_TYPE_LOGIN_FAILED, "user-A", now));
      engine.feed(createEvent(EVENT_TYPE_LOGIN_FAILED, "user-B", now));

      assertThat(engine.totalHits()).isZero();

      engine.feed(createEvent(EVENT_TYPE_LOGIN_FAILED, "user-A", now.plusSeconds(5)));
      assertThat(hits).hasSize(1);
      assertThat(hits.get(0).getContext().get("partitionKey")).isEqualTo("user-A");
    }
  }

  @Nested
  @DisplayName("监听器管理")
  class ListenerManagement {

    @Test
    @DisplayName("添加 null 监听器应为 no-op")
    void addNullListenerShouldBeNoop() {
      CEPEngine engine = createEngine(1.0, 3);
      assertThatCode(() -> engine.addListener(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("移除监听器后不应再收到通知")
    void removedListenerShouldNotBeNotified() {
      CEPEngine engine = createEngine(1.0, 3);
      List<CEPHit> hits = new ArrayList<>();
      java.util.function.Consumer<CEPHit> listener = hits::add;

      engine.addListener(listener);
      engine.removeListener(listener);

      engine.feed(createEvent(EVENT_TYPE_LOGIN_FAILED, PARTITION_KEY, Instant.now()));

      assertThat(hits).isEmpty();
    }

    @Test
    @DisplayName("多个监听器应全部收到通知")
    void multipleListenersShouldAllBeNotified() {
      CEPEngine engine = createEngine(1.0, 3);
      List<CEPHit> hits1 = new CopyOnWriteArrayList<>();
      List<CEPHit> hits2 = new CopyOnWriteArrayList<>();
      engine.addListener(hits1::add);
      engine.addListener(hits2::add);

      engine.feed(createEvent(EVENT_TYPE_LOGIN_FAILED, PARTITION_KEY, Instant.now()));

      assertThat(hits1).hasSize(1);
      assertThat(hits2).hasSize(1);
    }

    @Test
    @DisplayName("监听器抛出异常不应影响其他监听器")
    void listenerExceptionShouldNotAffectOthers() {
      CEPEngine engine = createEngine(1.0, 3);
      List<CEPHit> hits = new CopyOnWriteArrayList<>();

      engine.addListener(hit -> { throw new RuntimeException("测试异常"); });
      engine.addListener(hits::add);

      assertThatCode(() -> engine.feed(
          createEvent(EVENT_TYPE_LOGIN_FAILED, PARTITION_KEY, Instant.now())))
          .doesNotThrowAnyException();
      assertThat(hits).hasSize(1);
    }
  }

  @Nested
  @DisplayName("状态清理")
  class StateCleanup {

    @Test
    @DisplayName("clearPartition 应清理指定分区状态")
    void clearPartitionShouldRemovePartitionState() {
      CEPEngine engine = createEngine(5.0, 3);
      engine.feed(createEvent(EVENT_TYPE_LOGIN_FAILED, PARTITION_KEY, Instant.now()));

      engine.clearPartition(PATTERN_ID, PARTITION_KEY);

      assertThat(engine.totalHits()).isZero();
    }

    @Test
    @DisplayName("clearPartition 传入 null 参数应为 no-op")
    void clearPartitionWithNullShouldBeNoop() {
      CEPEngine engine = createEngine(5.0, 3);
      assertThatCode(() -> engine.clearPartition(null, PARTITION_KEY)).doesNotThrowAnyException();
      assertThatCode(() -> engine.clearPartition(PATTERN_ID, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("clearAll 应清理全部事件队列")
    void clearAllShouldRemoveAllQueues() {
      CEPEngine engine = createEngine(5.0, 3);
      engine.feed(createEvent(EVENT_TYPE_LOGIN_FAILED, PARTITION_KEY, Instant.now()));

      engine.clearAll();

      assertThat(engine.totalHits()).isZero();
    }

    @Test
    @DisplayName("totalHits 方法应返回累计命中次数")
    void totalHitsShouldAccumulate() {
      CEPEngine engine = createEngine(1.0, 3);

      engine.feed(createEvent(EVENT_TYPE_LOGIN_FAILED, PARTITION_KEY, Instant.now()));
      engine.feed(createEvent(EVENT_TYPE_LOGIN_FAILED, "user-002", Instant.now()));

      assertThat(engine.totalHits()).isEqualTo(2);
    }
  }
}
