package com.njydsz.pmis.message.channel.sms;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MockSmsProvider 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
class MockSmsProviderTest {

    private MockSmsProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MockSmsProvider();
    }

    @Test
    void providerType_isMock() {
        assertEquals("mock", provider.providerType());
    }

    @Test
    void send_returnsOk() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("13800000000");
        request.setContent("验证码 1234");
        request.setTemplateCode("SMS_VERIFY");

        MessageResult result = provider.send(request, null);

        assertTrue(result.isSuccess());
        assertEquals("SMS", result.getChannel());
        assertTrue(result.getProviderTraceId().startsWith("MOCK-SMS-"));
    }
}
