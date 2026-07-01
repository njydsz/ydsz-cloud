package com.njydsz.pmis.message.service;

import com.njydsz.pmis.message.channel.MessageChannel;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.entity.MessageLogDO;
import com.njydsz.pmis.message.entity.MessageTemplateDO;
import com.njydsz.pmis.message.mapper.MessageLogMapper;
import com.njydsz.pmis.message.mapper.MessageTemplateMapper;
import com.njydsz.pmis.message.template.DefaultTemplateEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MessageServiceImpl 单元测试
 */
@DisplayName("MessageServiceImpl 消息服务测试")
class MessageServiceImplTest {

    private MessageLogMapper logMapper;
    private MessageTemplateMapper tplMapper;
    private ApplicationContext ctx;
    private MessageServiceImpl service;

    private final TestChannel smsChannel = new TestChannel("SMS");

    @BeforeEach
    void setUp() {
        logMapper = mock(MessageLogMapper.class);
        tplMapper = mock(MessageTemplateMapper.class);
        ctx = mock(ApplicationContext.class);

        Map<String, MessageChannel> beans = new HashMap<>();
        beans.put("smsChannel", smsChannel);
        when(ctx.getBeansOfType(MessageChannel.class)).thenReturn(beans);
        when(logMapper.insert(any(MessageLogDO.class))).thenAnswer(inv -> {
            MessageLogDO l = inv.getArgument(0);
            l.setId(1L);
            return 1;
        });

        service = new MessageServiceImpl(logMapper, tplMapper, new DefaultTemplateEngine(), ctx);
        service.initChannels();
    }

    @Test
    @DisplayName("无模板直接发送 - 成功")
    void sendDirect() {
        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        req.setReceiver("13800000000");
        req.setContent("验证码 123456");
        MessageResult r = service.sendDirect(req);
        assertThat(r.isSuccess()).isTrue();
        assertThat(smsChannel.received).hasSize(1);
        assertThat(smsChannel.received.get(0).getContent()).isEqualTo("验证码 123456");
    }

    @Test
    @DisplayName("使用模板渲染 - 应替换占位符")
    void sendWithTemplate() {
        MessageTemplateDO tpl = new MessageTemplateDO();
        tpl.setTemplateCode("WELCOME");
        tpl.setChannel("SMS");
        tpl.setContent("Hello, ${name}!");
        tpl.setStatus("ENABLED");
        when(tplMapper.selectByCodeAndChannel("WELCOME", "SMS", 1L)).thenReturn(tpl);

        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        req.setTemplateCode("WELCOME");
        req.setReceiver("13900000000");
        Map<String, Object> p = new HashMap<>();
        p.put("name", "Alice");
        req.setParams(p);

        MessageResult r = service.send(req);
        assertThat(r.isSuccess()).isTrue();
        assertThat(smsChannel.received.get(0).getContent()).isEqualTo("Hello, Alice!");
    }

    @Test
    @DisplayName("模板已停用 - 抛 BizException")
    void sendWithDisabledTemplate() {
        MessageTemplateDO tpl = new MessageTemplateDO();
        tpl.setTemplateCode("DIS");
        tpl.setChannel("SMS");
        tpl.setContent("x");
        tpl.setStatus("DISABLED");
        when(tplMapper.selectByCodeAndChannel("DIS", "SMS", 1L)).thenReturn(tpl);

        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        req.setTemplateCode("DIS");
        req.setReceiver("1");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.send(req))
                .isInstanceOf(com.njydsz.pmis.common.exception.BizException.class)
                .extracting("code")
                .isEqualTo(com.njydsz.pmis.common.api.BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("模板不存在 - 抛 NOT_FOUND")
    void sendWithMissingTemplate() {
        when(tplMapper.selectByCodeAndChannel("NOPE", "SMS", 1L)).thenReturn(null);
        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        req.setTemplateCode("NOPE");
        req.setReceiver("1");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.send(req))
                .isInstanceOf(com.njydsz.pmis.common.exception.BizException.class)
                .extracting("code")
                .isEqualTo(com.njydsz.pmis.common.api.BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("不支持的通道 - 抛 BAD_REQUEST")
    void sendUnknownChannel() {
        MessageRequest req = new MessageRequest();
        req.setChannel("FAX");
        req.setReceiver("1");
        req.setContent("x");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.send(req))
                .isInstanceOf(com.njydsz.pmis.common.exception.BizException.class)
                .extracting("code")
                .isEqualTo(com.njydsz.pmis.common.api.BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("成功发送后应记录日志")
    void sendShouldLog() {
        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        req.setReceiver("13800000000");
        req.setContent("hi");
        service.sendDirect(req);

        ArgumentCaptor<MessageLogDO> captor = ArgumentCaptor.forClass(MessageLogDO.class);
        verify(logMapper).insert(captor.capture());
        MessageLogDO l = captor.getValue();
        assertThat(l.getChannel()).isEqualTo("SMS");
        assertThat(l.getStatus()).isEqualTo("SUCCESS");
        assertThat(l.getReceiver()).isEqualTo("13800000000");
        assertThat(l.getCostMs()).isNotNull().isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("通道异常后 - 记录 FAILED 日志")
    void sendShouldLogFailure() {
        smsChannel.failNext = true;
        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        req.setReceiver("13800000000");
        req.setContent("hi");
        MessageResult r = service.sendDirect(req);
        assertThat(r.isSuccess()).isFalse();
        ArgumentCaptor<MessageLogDO> captor = ArgumentCaptor.forClass(MessageLogDO.class);
        verify(logMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("接收人为空 - 抛 BAD_REQUEST")
    void sendEmptyReceiver() {
        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        req.setReceiver("");
        req.setContent("hi");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.send(req))
                .isInstanceOf(com.njydsz.pmis.common.exception.BizException.class)
                .extracting("code")
                .isEqualTo(com.njydsz.pmis.common.api.BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("listChannelTypes 返回已注册通道")
    void listChannelTypes() {
        assertThat(service.listChannelTypes()).contains("SMS");
    }

    /** 测试用通道 */
    static class TestChannel implements MessageChannel {
        final String type;
        final List<MessageRequest> received = new ArrayList<>();
        boolean failNext = false;

        TestChannel(String type) {
            this.type = type;
        }

        @Override
        public String channelType() {
            return type;
        }

        @Override
        public MessageResult send(MessageRequest request) {
            if (failNext) {
                failNext = false;
                return MessageResult.fail(type, "mock-fail");
            }
            received.add(request);
            return MessageResult.ok(type, "T-1");
        }
    }
}
