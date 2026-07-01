package com.njydsz.pmis.message.channel.impl;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MockSmsChannel 单元测试
 */
@DisplayName("MockSmsChannel 短信通道测试")
class MockSmsChannelTest {

    private final MockSmsChannel channel = new MockSmsChannel();

    @Test
    @DisplayName("channelType 应返回 SMS")
    void channelType() {
        assertThat(channel.channelType()).isEqualTo("SMS");
    }

    @Test
    @DisplayName("正常发送应返回成功 + MOCK-SMS 前缀追踪 ID")
    void send_ok() {
        MessageRequest req = new MessageRequest();
        req.setReceiver("13800000000");
        req.setContent("验证码 123456");
        MessageResult r = channel.send(req);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getProviderTraceId()).startsWith("MOCK-SMS-");
    }

    @Test
    @DisplayName("接收人为空应返回失败")
    void send_empty() {
        MessageRequest req = new MessageRequest();
        req.setContent("x");
        MessageResult r = channel.send(req);
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getErrorMessage()).contains("手机号");
    }
}
