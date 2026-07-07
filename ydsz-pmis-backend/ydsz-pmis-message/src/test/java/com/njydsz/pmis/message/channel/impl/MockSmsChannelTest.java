package com.njydsz.pmis.message.channel.impl;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MockSmsChannel 烟雾测试：验证返回 ok 与空接收人失败分支。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class MockSmsChannelTest {

    private MockSmsChannel channel;

    @BeforeEach
    void setUp() {
        channel = new MockSmsChannel();
    }

    @Test
    void channelType_isSms() {
        assertEquals("SMS", channel.channelType());
    }

    @Test
    void send_returnsOk() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("13800000000");
        request.setContent("验证码 1234");
        request.setTemplateCode("SMS_VERIFY");

        MessageResult result = channel.send(request);

        assertTrue(result.isSuccess());
        assertEquals("SMS", result.getChannel());
        assertTrue(result.getProviderTraceId().startsWith("MOCK-SMS-"));
    }

    @Test
    void send_returnsFailWhenReceiverBlank() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("");

        MessageResult result = channel.send(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("手机号"));
    }
}
