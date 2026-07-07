package com.njydsz.pmis.message.channel;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.config.MessageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ChannelRouter 单元测试：验证通道注册、路由、分发与开关判断。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class ChannelRouterTest {

    private ApplicationContext applicationContext;
    private MessageProperties messageProperties;
    private ChannelRouter router;

    private MessageChannel emailChannel;
    private MessageChannel smsChannel;

    @BeforeEach
    void setUp() {
        applicationContext = mock(ApplicationContext.class);
        emailChannel = mock(MessageChannel.class);
        when(emailChannel.channelType()).thenReturn("EMAIL");
        smsChannel = mock(MessageChannel.class);
        when(smsChannel.channelType()).thenReturn("SMS");

        Map<String, MessageChannel> beans = new LinkedHashMap<>();
        beans.put("emailChannel", emailChannel);
        beans.put("smsChannel", smsChannel);
        when(applicationContext.getBeansOfType(MessageChannel.class)).thenReturn(beans);

        messageProperties = new MessageProperties();
        Map<String, Boolean> enabled = new LinkedHashMap<>();
        enabled.put("EMAIL", true);
        enabled.put("SMS", false);
        messageProperties.setChannelEnabled(enabled);

        router = new ChannelRouter(applicationContext, messageProperties);
        router.initChannels();
    }

    @Test
    void initChannels_registersAllChannelsByUppercaseType() {
        assertEquals(2, router.getChannelCache().size());
        assertSame(emailChannel, router.getChannelCache().get("EMAIL"));
        assertSame(smsChannel, router.getChannelCache().get("SMS"));
    }

    @Test
    void route_returnsChannelCaseInsensitive() {
        assertSame(emailChannel, router.route("email"));
        assertSame(emailChannel, router.route("EMAIL"));
        assertSame(smsChannel, router.route("Sms"));
    }

    @Test
    void route_throwsWhenChannelBlank() {
        assertThrows(BizException.class, () -> router.route(""));
        assertThrows(BizException.class, () -> router.route(null));
    }

    @Test
    void route_throwsWhenChannelMissing() {
        assertThrows(BizException.class, () -> router.route("XYZ"));
    }

    @Test
    void dispatch_successReturnsResult() {
        MessageRequest request = new MessageRequest();
        request.setChannel("EMAIL");
        when(emailChannel.send(request)).thenReturn(MessageResult.ok("EMAIL", "trace-1"));

        MessageResult result = router.dispatch(request);

        assertTrue(result.isSuccess());
        assertEquals("trace-1", result.getProviderTraceId());
    }

    @Test
    void dispatch_failureFromChannelReturnsFailResult() {
        MessageRequest request = new MessageRequest();
        request.setChannel("EMAIL");
        when(emailChannel.send(request)).thenReturn(MessageResult.fail("EMAIL", "smtp down"));

        MessageResult result = router.dispatch(request);

        assertFalse(result.isSuccess());
        assertEquals("smtp down", result.getErrorMessage());
    }

    @Test
    void dispatch_exceptionFromChannelCaughtAndReturnsFail() {
        MessageRequest request = new MessageRequest();
        request.setChannel("EMAIL");
        when(emailChannel.send(any(MessageRequest.class))).thenThrow(new RuntimeException("boom"));

        MessageResult result = router.dispatch(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("boom"));
    }

    @Test
    void isChannelEnabled_respectsConfigMap() {
        assertTrue(router.isChannelEnabled("EMAIL"));
        assertFalse(router.isChannelEnabled("SMS"));
    }

    @Test
    void isChannelEnabled_defaultsToTrueWhenNotConfigured() {
        // PUSH 未在 channel-enabled 中配置，默认启用
        assertTrue(router.isChannelEnabled("PUSH"));
    }

    @Test
    void isChannelEnabled_falseWhenBlank() {
        assertFalse(router.isChannelEnabled(""));
        assertFalse(router.isChannelEnabled(null));
    }

    @Test
    void isChannelEnabled_defaultsToTrueWhenMapNull() {
        messageProperties.setChannelEnabled(null);
        assertTrue(router.isChannelEnabled("SMS"));
    }
}
