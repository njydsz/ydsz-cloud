package com.njydsz.pmis.message.channel.impl;

import com.njydsz.pmis.message.channel.MessageRequest;
import com.njydsz.pmis.message.channel.MessageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MockPushChannel 单元测试
 */
@DisplayName("MockPushChannel 推送通道测试")
class MockPushChannelTest {

    private final MockPushChannel channel = new MockPushChannel();

    @Test
    @DisplayName("channelType 应返回 PUSH")
    void channelType() {
        assertThat(channel.channelType()).isEqualTo("PUSH");
    }

    @Test
    @DisplayName("正常发送应返回成功 + MOCK-PUSH 前缀追踪 ID")
    void send_ok() {
        MessageRequest req = new MessageRequest();
        req.setReceiver("device-token-abc");
        req.setContent("您有一条新工单");
        MessageResult r = channel.send(req);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getProviderTraceId()).startsWith("MOCK-PUSH-");
    }

    @Test
    @DisplayName("接收人为空应返回失败")
    void send_empty() {
        MessageRequest req = new MessageRequest();
        req.setContent("x");
        MessageResult r = channel.send(req);
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getErrorMessage()).contains("推送目标");
    }
}
