package com.njydsz.pmis.literule.server.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.literule.server.core.RuleCircuitBreaker.State;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RuleCircuitBreaker} 单元测试：覆盖 CLOSED/OPEN/HALF_OPEN 三态流转，
 * 包括错误率触发熔断、恢复时间触发半开、探测成功/失败的状态转换。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@DisplayName("规则熔断器 RuleCircuitBreaker 测试")
class RuleCircuitBreakerTest {

    /** 测试用参数：50% 错误率阈值，4 次最小评估，50ms OPEN 持续时间 */
    private static final double THRESHOLD = 0.5;
    private static final int MIN_EVALS = 4;
    private static final long OPEN_MS = 50;

    @Nested
    @DisplayName("构造器参数校验")
    class ConstructorCases {

        @Test
        @DisplayName("errorRateThreshold <= 0 时抛 IllegalArgumentException")
        void shouldThrowWhenThresholdNotPositive() {
            assertThatThrownBy(() -> new RuleCircuitBreaker(0.0, MIN_EVALS, OPEN_MS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("errorRateThreshold");
        }

        @Test
        @DisplayName("errorRateThreshold > 1 时抛 IllegalArgumentException")
        void shouldThrowWhenThresholdExceedsOne() {
            assertThatThrownBy(() -> new RuleCircuitBreaker(1.5, MIN_EVALS, OPEN_MS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("errorRateThreshold");
        }

        @Test
        @DisplayName("openStateMs <= 0 时抛 IllegalArgumentException")
        void shouldThrowWhenOpenStateMsNotPositive() {
            assertThatThrownBy(() -> new RuleCircuitBreaker(THRESHOLD, MIN_EVALS, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("openStateMs");
        }

        @Test
        @DisplayName("errorRateThreshold=1.0 是合法边界值，不抛异常")
        void shouldAllowThresholdEqualsOne() {
            RuleCircuitBreaker breaker = new RuleCircuitBreaker(1.0, MIN_EVALS, OPEN_MS);

            assertThat(breaker.getState("R001")).isEqualTo(State.CLOSED);
        }
    }

    @Nested
    @DisplayName("CLOSED 状态")
    class ClosedStateCases {

        @Test
        @DisplayName("新熔断器默认 CLOSED 状态，allowEvaluate 返回 true")
        void shouldBeClosedByDefault() {
            RuleCircuitBreaker breaker = new RuleCircuitBreaker(THRESHOLD, MIN_EVALS, OPEN_MS);

            assertThat(breaker.getState("R001")).isEqualTo(State.CLOSED);
            assertThat(breaker.allowEvaluate("R001")).isTrue();
        }

        @Test
        @DisplayName("未达到 minEvaluations 时即使错误率高也不熔断")
        void shouldNotOpenBeforeMinEvaluations() {
            RuleCircuitBreaker breaker = new RuleCircuitBreaker(THRESHOLD, MIN_EVALS, OPEN_MS);

            // 仅记录 3 次评估（< 4），全部失败
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);

            // 未达到 minEvaluations，仍为 CLOSED
            assertThat(breaker.getState("R001")).isEqualTo(State.CLOSED);
            assertThat(breaker.allowEvaluate("R001")).isTrue();
        }

        @Test
        @DisplayName("错误率低于阈值时不熔断")
        void shouldNotOpenWhenErrorRateBelowThreshold() {
            RuleCircuitBreaker breaker = new RuleCircuitBreaker(THRESHOLD, MIN_EVALS, OPEN_MS);

            // 4 次评估，1 次失败（错误率 25% < 50%）
            breaker.recordResult("R001", true);
            breaker.recordResult("R001", true);
            breaker.recordResult("R001", true);
            breaker.recordResult("R001", false);

            assertThat(breaker.getState("R001")).isEqualTo(State.CLOSED);
        }
    }

    @Nested
    @DisplayName("CLOSED → OPEN 状态转换")
    class ClosedToOpenCases {

        @Test
        @DisplayName("错误率达到阈值且达到 minEvaluations 时熔断为 OPEN")
        void shouldOpenWhenErrorRateReachesThreshold() {
            RuleCircuitBreaker breaker = new RuleCircuitBreaker(THRESHOLD, MIN_EVALS, OPEN_MS);

            // 4 次评估，2 次失败（错误率 50% >= 50%）
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", true);
            breaker.recordResult("R001", true);

            assertThat(breaker.getState("R001")).isEqualTo(State.OPEN);
        }

        @Test
        @DisplayName("全部失败时熔断为 OPEN")
        void shouldOpenWhenAllFailed() {
            RuleCircuitBreaker breaker = new RuleCircuitBreaker(THRESHOLD, MIN_EVALS, OPEN_MS);

            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);

            assertThat(breaker.getState("R001")).isEqualTo(State.OPEN);
        }

        @Test
        @DisplayName("OPEN 状态下 allowEvaluate 返回 false")
        void shouldReturnFalseWhenOpen() {
            RuleCircuitBreaker breaker = new RuleCircuitBreaker(THRESHOLD, MIN_EVALS, OPEN_MS);
            // 触发 OPEN
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            assertThat(breaker.getState("R001")).isEqualTo(State.OPEN);

            assertThat(breaker.allowEvaluate("R001")).isFalse();
        }
    }

    @Nested
    @DisplayName("OPEN → HALF_OPEN 状态转换")
    class OpenToHalfOpenCases {

        @Test
        @DisplayName("OPEN 状态未到恢复时间时保持 OPEN")
        void shouldRemainOpenBeforeRecoveryTime() throws InterruptedException {
            RuleCircuitBreaker breaker = new RuleCircuitBreaker(THRESHOLD, MIN_EVALS, OPEN_MS);
            // 触发 OPEN
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);

            // 等待 20ms（< 50ms）
            Thread.sleep(20);

            assertThat(breaker.allowEvaluate("R001")).isFalse();
            assertThat(breaker.getState("R001")).isEqualTo(State.OPEN);
        }

        @Test
        @DisplayName("OPEN 状态到达恢复时间后转 HALF_OPEN，allowEvaluate 返回 true")
        void shouldTransitToHalfOpenAfterRecoveryTime() throws InterruptedException {
            RuleCircuitBreaker breaker = new RuleCircuitBreaker(THRESHOLD, MIN_EVALS, OPEN_MS);
            // 触发 OPEN
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);

            // 等待超过 openStateMs
            Thread.sleep(OPEN_MS + 20);

            // allowEvaluate 触发 OPEN → HALF_OPEN
            assertThat(breaker.allowEvaluate("R001")).isTrue();
            assertThat(breaker.getState("R001")).isEqualTo(State.HALF_OPEN);
        }
    }

    @Nested
    @DisplayName("HALF_OPEN 状态转换")
    class HalfOpenCases {

        /**
         * 辅助方法：将熔断器推到 HALF_OPEN 状态
         */
        private RuleCircuitBreaker prepareHalfOpen() throws InterruptedException {
            RuleCircuitBreaker breaker = new RuleCircuitBreaker(THRESHOLD, MIN_EVALS, OPEN_MS);
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            assertThat(breaker.getState("R001")).isEqualTo(State.OPEN);
            Thread.sleep(OPEN_MS + 20);
            breaker.allowEvaluate("R001");
            assertThat(breaker.getState("R001")).isEqualTo(State.HALF_OPEN);
            return breaker;
        }

        @Test
        @DisplayName("HALF_OPEN 下探测成功 → 恢复为 CLOSED")
        void shouldTransitToClosedOnSuccessInHalfOpen() throws InterruptedException {
            RuleCircuitBreaker breaker = prepareHalfOpen();

            breaker.recordResult("R001", true);

            assertThat(breaker.getState("R001")).isEqualTo(State.CLOSED);
            // 计数器被重置
            assertThat(breaker.allowEvaluate("R001")).isTrue();
        }

        @Test
        @DisplayName("HALF_OPEN 下探测失败 → 重新 OPEN")
        void shouldTransitToOpenOnFailureInHalfOpen() throws InterruptedException {
            RuleCircuitBreaker breaker = prepareHalfOpen();

            breaker.recordResult("R001", false);

            assertThat(breaker.getState("R001")).isEqualTo(State.OPEN);
            // 重新 OPEN 后 allowEvaluate 应返回 false
            assertThat(breaker.allowEvaluate("R001")).isFalse();
        }

        @Test
        @DisplayName("HALF_OPEN 恢复 CLOSED 后计数器被重置，可再次触发熔断")
        void shouldResetCountersAfterRecovery() throws InterruptedException {
            RuleCircuitBreaker breaker = prepareHalfOpen();
            breaker.recordResult("R001", true);
            assertThat(breaker.getState("R001")).isEqualTo(State.CLOSED);

            // 重新累积失败，应能再次触发 OPEN
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            // 第 4 次失败（计数器重置后从 0 开始）
            breaker.recordResult("R001", false);

            assertThat(breaker.getState("R001")).isEqualTo(State.OPEN);
        }
    }

    @Nested
    @DisplayName("多规则独立熔断")
    class MultiRuleCases {

        @Test
        @DisplayName("不同规则的熔断状态相互独立")
        void shouldIsolateByRuleCode() {
            RuleCircuitBreaker breaker = new RuleCircuitBreaker(THRESHOLD, MIN_EVALS, OPEN_MS);

            // R001 触发熔断
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);

            // R002 仅成功评估
            breaker.recordResult("R002", true);
            breaker.recordResult("R002", true);

            assertThat(breaker.getState("R001")).isEqualTo(State.OPEN);
            assertThat(breaker.getState("R002")).isEqualTo(State.CLOSED);
            assertThat(breaker.allowEvaluate("R001")).isFalse();
            assertThat(breaker.allowEvaluate("R002")).isTrue();
        }

        @Test
        @DisplayName("reset 移除指定规则熔断器")
        void shouldResetSpecificRule() {
            RuleCircuitBreaker breaker = new RuleCircuitBreaker(THRESHOLD, MIN_EVALS, OPEN_MS);
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            assertThat(breaker.getState("R001")).isEqualTo(State.OPEN);

            breaker.reset("R001");

            // reset 后状态回到 CLOSED（默认）
            assertThat(breaker.getState("R001")).isEqualTo(State.CLOSED);
            assertThat(breaker.allowEvaluate("R001")).isTrue();
        }

        @Test
        @DisplayName("resetAll 移除全部规则熔断器")
        void shouldResetAllRules() {
            RuleCircuitBreaker breaker = new RuleCircuitBreaker(THRESHOLD, MIN_EVALS, OPEN_MS);
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            breaker.recordResult("R001", false);
            breaker.recordResult("R002", false);
            breaker.recordResult("R002", false);
            breaker.recordResult("R002", false);
            breaker.recordResult("R002", false);
            assertThat(breaker.getState("R001")).isEqualTo(State.OPEN);
            assertThat(breaker.getState("R002")).isEqualTo(State.OPEN);

            breaker.resetAll();

            assertThat(breaker.getState("R001")).isEqualTo(State.CLOSED);
            assertThat(breaker.getState("R002")).isEqualTo(State.CLOSED);
        }
    }
}
