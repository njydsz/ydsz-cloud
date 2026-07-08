package com.njydsz.pmis.message.channel.impl;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * InAppChannel 烟雾测试：验证返回 ok 与空接收人失败分支。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class InAppChannelTest {

    private InAppChannel channel;

    @BeforeEach
    void setUp() {
        channel = new InAppChannel();
    }

    @Test
    void channelType_isInApp() {
        assertEquals("INAPP", channel.channelType());
    }

    @Test
    void send_returnsOk() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("user-001");
        request.setBizType("ALERT");
        request.setContent("您有一条新告警");

        MessageResult result = channel.send(request);

        assertTrue(result.isSuccess());
        assertEquals("INAPP", result.getChannel());
        assertTrue(result.getProviderTraceId().startsWith("INAPP-"));
    }

    @Test
    void send_returnsFailWhenReceiverBlank() {
        MessageRequest request = new MessageRequest();
        request.setReceiver("");

        MessageResult result = channel.send(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("接收人"));
    }
}
