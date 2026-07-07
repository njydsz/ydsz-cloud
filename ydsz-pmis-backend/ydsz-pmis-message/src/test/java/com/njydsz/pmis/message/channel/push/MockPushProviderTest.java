package com.njydsz.pmis.message.channel.push;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MockPushProvider 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
class MockPushProviderTest {

    private MockPushProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MockPushProvider();
    }

    @Test
    void providerType_isMock() {
        assertEquals("mock", provider.providerType());
    }

    @Test
    void send_returnsOk() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("cid-001");
        request.setContent("推送内容");
        request.setSubject("标题");

        MessageResult result = provider.send(request, null);

        assertTrue(result.isSuccess());
        assertEquals("PUSH", result.getChannel());
        assertTrue(result.getProviderTraceId().startsWith("MOCK-PUSH-"));
    }
}
