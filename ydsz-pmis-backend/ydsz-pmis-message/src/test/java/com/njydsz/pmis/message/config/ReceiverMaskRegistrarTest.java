package com.njydsz.pmis.message.config;

import com.njydsz.pmis.common.sensitive.SensitiveUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ReceiverMaskRegistrar} 单元测试。
 *
 * <p>验证 receiver 智能脱敏：手机号 / 邮箱 / 用户 ID 形态识别。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ReceiverMaskRegistrar 接收人脱敏测试")
class ReceiverMaskRegistrarTest {

    @Test
    @DisplayName("11 位手机号脱敏: 138****8000")
    void shouldMaskPhoneReceiver() {
        assertEquals("138****8000", ReceiverMaskRegistrar.maskReceiver("13812348000"));
    }

    @Test
    @DisplayName("邮箱脱敏: a***@example.com")
    void shouldMaskEmailReceiver() {
        assertEquals("a***@example.com", ReceiverMaskRegistrar.maskReceiver("abc@example.com"));
    }

    @Test
    @DisplayName("短用户 ID(<=4) 全替换为 ****")
    void shouldMaskShortUserId() {
        assertEquals("****", ReceiverMaskRegistrar.maskReceiver("u1"));
        assertEquals("****", ReceiverMaskRegistrar.maskReceiver("ab"));
        assertEquals("****", ReceiverMaskRegistrar.maskReceiver("abcd"));
    }

    @Test
    @DisplayName("长用户 ID 保留前 2 后 2")
    void shouldMaskLongUserId() {
        assertEquals("US***01", ReceiverMaskRegistrar.maskReceiver("USER20260101_001"));
        assertEquals("us***-1", ReceiverMaskRegistrar.maskReceiver("user-1234-abc-dep-1"));
    }

    @Test
    @DisplayName("空值原样返回")
    void shouldReturnOriginalWhenBlank() {
        assertEquals(null, ReceiverMaskRegistrar.maskReceiver(null));
        assertEquals("", ReceiverMaskRegistrar.maskReceiver(""));
    }

    @Test
    @DisplayName("register 后 SensitiveUtil.CUSTOM 走 maskReceiver 逻辑")
    void shouldRegisterDefaultHandlerToSensitiveUtil() {
        new ReceiverMaskRegistrar().register();
        // CUSTOM 策略固定调用 "default" handler
        String masked = SensitiveUtil.desensitize("13812348000", com.njydsz.pmis.common.sensitive.SensitiveStrategy.CUSTOM);
        assertEquals("138****8000", masked);
    }
}
