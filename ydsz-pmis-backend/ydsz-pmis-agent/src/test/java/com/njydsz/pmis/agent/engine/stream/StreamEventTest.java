package com.njydsz.pmis.agent.engine.stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StreamEvent} 单元测试（P2-1 落地）
 *
 * <p>覆盖：
 * <ul>
 *   <li>静态工厂方法构造的事件字段正确</li>
 *   <li>事件类型枚举完整</li>
 *   <li>时间戳自动填充</li>
 *   <li>DONE / ERROR 事件载荷格式</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-1)
 */
@DisplayName("StreamEvent 流式事件测试")
class StreamEventTest {

    @Nested
    @DisplayName("静态工厂方法")
    class FactoryMethodTest {

        @Test
        @DisplayName("of(type) 仅设置 type 与 timestamp")
        void shouldCreateEventWithTypeOnly() {
            StreamEvent event = StreamEvent.of(StreamEvent.Type.STEP_START);

            assertThat(event.getType()).isEqualTo(StreamEvent.Type.STEP_START);
            assertThat(event.getStepIndex()).isZero();
            assertThat(event.getPayload()).isEmpty();
            assertThat(event.getTimestamp()).isPositive();
        }

        @Test
        @DisplayName("of(type, stepIndex) 设置 type 与 stepIndex")
        void shouldCreateEventWithTypeAndStepIndex() {
            StreamEvent event = StreamEvent.of(StreamEvent.Type.STEP_END, 3);

            assertThat(event.getType()).isEqualTo(StreamEvent.Type.STEP_END);
            assertThat(event.getStepIndex()).isEqualTo(3);
            assertThat(event.getPayload()).isEmpty();
        }

        @Test
        @DisplayName("of(type, stepIndex, payload) 设置完整字段")
        void shouldCreateEventWithAllFields() {
            Map<String, Object> payload = Map.of("thought", "test");
            StreamEvent event = StreamEvent.of(StreamEvent.Type.THOUGHT, 1, payload);

            assertThat(event.getType()).isEqualTo(StreamEvent.Type.THOUGHT);
            assertThat(event.getStepIndex()).isEqualTo(1);
            assertThat(event.getPayload()).containsEntry("thought", "test");
        }

        @Test
        @DisplayName("of(type, stepIndex, null) 的 payload 为空 Map 而非 null")
        void shouldHandleNullPayload() {
            StreamEvent event = StreamEvent.of(StreamEvent.Type.ACTION, 1, null);

            assertThat(event.getPayload()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("done() 事件携带 success + totalSteps")
        void shouldCreateDoneEvent() {
            StreamEvent event = StreamEvent.done(5, true);

            assertThat(event.getType()).isEqualTo(StreamEvent.Type.DONE);
            assertThat(event.getStepIndex()).isEqualTo(5);
            assertThat(event.getPayload())
                    .containsEntry("success", true)
                    .containsEntry("totalSteps", 5);
        }

        @Test
        @DisplayName("error() 事件携带 success=false + failureReason")
        void shouldCreateErrorEvent() {
            StreamEvent event = StreamEvent.error(2, "LLM 超时");

            assertThat(event.getType()).isEqualTo(StreamEvent.Type.ERROR);
            assertThat(event.getStepIndex()).isEqualTo(2);
            assertThat(event.getPayload())
                    .containsEntry("success", false)
                    .containsEntry("failureReason", "LLM 超时");
        }

        @Test
        @DisplayName("error() 事件 reason=null 时 failureReason 为空字符串")
        void shouldHandleNullReasonInErrorEvent() {
            StreamEvent event = StreamEvent.error(0, null);

            assertThat(event.getPayload()).containsEntry("failureReason", "");
        }
    }

    @Nested
    @DisplayName("事件类型枚举")
    class TypeEnumTest {

        @Test
        @DisplayName("StreamEvent.Type 包含所有期望事件类型")
        void shouldContainAllExpectedTypes() {
            // 验证关键事件类型都存在（避免后续重构丢失）
            assertThat(StreamEvent.Type.values())
                    .contains(
                            StreamEvent.Type.STEP_START,
                            StreamEvent.Type.THOUGHT,
                            StreamEvent.Type.ACTION,
                            StreamEvent.Type.OBSERVATION,
                            StreamEvent.Type.FINAL_ANSWER,
                            StreamEvent.Type.STEP_END,
                            StreamEvent.Type.DONE,
                            StreamEvent.Type.ERROR);
        }

        @Test
        @DisplayName("StreamEvent.Type 至少包含 10 个类型")
        void shouldHaveAtLeastTenTypes() {
            assertThat(StreamEvent.Type.values()).hasSizeGreaterThanOrEqualTo(10);
        }
    }
}
