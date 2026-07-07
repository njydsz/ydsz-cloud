package com.njydsz.pmis.message.channel.impl;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MockPushChannel 烟雾测试：验证返回 ok 与空接收人失败分支。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class MockPushChannelTest {

    private MockPushChannel channel;

    @BeforeEach
    void setUp() {
        channel = new MockPushChannel();
    }

    @Test
    void channelType_isPush() {
        assertEquals("PUSH", channel.channelType());
    }

    @Test
    void send_returnsOk() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("device-token-abc");
        request.setContent("推送内容");

        MessageResult result = channel.send(request);

        assertTrue(result.isSuccess());
        assertEquals("PUSH", result.getChannel());
        assertTrue(result.getProviderTraceId().startsWith("MOCK-PUSH-"));
    }

    @Test
    void send_returnsFailWhenReceiverBlank() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("");

        MessageResult result = channel.send(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("推送目标"));
    }
}
