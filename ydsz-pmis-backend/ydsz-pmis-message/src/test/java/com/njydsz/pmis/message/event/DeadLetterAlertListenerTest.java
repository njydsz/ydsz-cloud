package com.njydsz.pmis.message.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link DeadLetterAlertListener} 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DeadLetterAlertListener 死信告警监听器测试")
class DeadLetterAlertListenerTest {

    private final DeadLetterAlertListener listener = new DeadLetterAlertListener();

    @Test
    @DisplayName("正常处理告警事件不抛异常")
    void onDeadLetterAlertShouldNotThrow() {
        DeadLetterAlertEvent event = new DeadLetterAlertEvent(
                this, "SMS", 15L, 10, 60);

        assertDoesNotThrow(() -> listener.onDeadLetterAlert(event));
    }

    @Test
    @DisplayName("事件字段正确回传")
    void eventShouldCarryCorrectFields() {
        DeadLetterAlertEvent event = new DeadLetterAlertEvent(
                this, "EMAIL", 20L, 15, 30);

        assertEquals("EMAIL", event.getChannel());
        assertEquals(20L, event.getCurrentCount());
        assertEquals(15, event.getThreshold());
        assertEquals(30, event.getWindowMinutes());
        assertNotNull(event.getTriggeredAt());
    }

    @Test
    @DisplayName("空通道事件也能安全处理")
    void onDeadLetterAlertWithNullChannelShouldNotThrow() {
        DeadLetterAlertEvent event = new DeadLetterAlertEvent(
                this, null, 0L, 1, 1);

        assertDoesNotThrow(() -> listener.onDeadLetterAlert(event));
    }
}
