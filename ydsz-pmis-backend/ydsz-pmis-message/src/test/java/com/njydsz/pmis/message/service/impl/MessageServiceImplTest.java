package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.channel.ChannelRouter;
import com.njydsz.pmis.message.config.MessageProperties;
import com.njydsz.pmis.message.entity.MsgLogDO;
import com.njydsz.pmis.message.entity.MsgTemplateDO;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import com.njydsz.pmis.message.metric.MessageMetrics;
import com.njydsz.pmis.message.service.CanaryService;
import com.njydsz.pmis.message.service.PreferenceService;
import com.njydsz.pmis.message.service.RateLimitService;
import com.njydsz.pmis.message.service.RouteRuleService;
import com.njydsz.pmis.message.service.TemplateService;
import com.njydsz.pmis.message.template.TemplateEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MessageServiceImpl} 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("MessageServiceImpl 发送编排测试")
@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    @Mock
    private ChannelRouter channelRouter;
    @Mock
    private TemplateEngine templateEngine;
    @Mock
    private TemplateService templateService;
    @Mock
    private MsgLogMapper msgLogMapper;
    @Mock
    private RouteRuleService routeRuleService;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private PreferenceService preferenceService;
    @Mock
    private CanaryService canaryService;
    @Mock
    private MessageProperties messageProperties;
    @Mock
    private MessageMetrics messageMetrics;

    @InjectMocks
    private MessageServiceImpl messageService;

    private MessageRequest buildRequest() {
        MessageRequest req = new MessageRequest();
        req.setChannel("EMAIL");
        req.setTemplateCode("TPL_WELCOME");
        req.setReceiver("u1");
        req.setBizType("WELCOME");
        req.setBizId("b1");
        return req;
    }

    @Test
    @DisplayName("send 全流程成功")
    void sendShouldSucceed() {
        MessageRequest req = buildRequest();
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(null);
        when(canaryService.hit(anyString(), anyString())).thenReturn(false);
        when(rateLimitService.tryAcquire(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        when(rateLimitService.checkFrequency(anyString(), anyString(), anyString())).thenReturn(true);
        MsgTemplateDO tpl = new MsgTemplateDO();
        tpl.setContent("hi ${name}");
        tpl.setSubject("welcome");
        when(templateService.loadByCodeAndChannel(anyString(), anyString(), any(), anyString())).thenReturn(tpl);
        when(templateEngine.render(anyString(), any())).thenReturn("rendered");
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenReturn("trace-1");

        MessageResult result = messageService.send(req);

        assertTrue(result.isSuccess());
        assertEquals("trace-1", result.getProviderTraceId());
        verify(msgLogMapper).insert(any(MsgLogDO.class));
        verify(rateLimitService).recordFrequency(eq("u1"), eq("EMAIL"), eq("WELCOME"));
    }

    @Test
    @DisplayName("send 通道未启用返回失败")
    void sendShouldFailWhenChannelDisabled() {
        MessageRequest req = buildRequest();
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(false);

        MessageResult result = messageService.send(req);

        assertFalse(result.isSuccess());
        verify(msgLogMapper, never()).insert(any(MsgLogDO.class));
    }

    @Test
    @DisplayName("send 限流抛 BizException")
    void sendShouldThrowWhenRateLimited() {
        MessageRequest req = buildRequest();
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(null);
        when(canaryService.hit(anyString(), anyString())).thenReturn(false);
        when(rateLimitService.tryAcquire(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);

        org.junit.jupiter.api.Assertions.assertThrows(com.njydsz.pmis.common.exception.BizException.class,
                () -> messageService.send(req));
    }

    @Test
    @DisplayName("send 模板不存在返回失败")
    void sendShouldFailWhenTemplateMissing() {
        MessageRequest req = buildRequest();
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(null);
        when(canaryService.hit(anyString(), anyString())).thenReturn(false);
        when(rateLimitService.tryAcquire(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        when(rateLimitService.checkFrequency(anyString(), anyString(), anyString())).thenReturn(true);
        when(templateService.loadByCodeAndChannel(anyString(), anyString(), any(), anyString())).thenReturn(null);

        MessageResult result = messageService.send(req);

        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("send dispatch 异常落库 FAILED")
    void sendShouldRecordFailedWhenDispatchThrows() {
        MessageRequest req = buildRequest();
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(null);
        when(canaryService.hit(anyString(), anyString())).thenReturn(false);
        when(rateLimitService.tryAcquire(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        when(rateLimitService.checkFrequency(anyString(), anyString(), anyString())).thenReturn(true);
        MsgTemplateDO tpl = new MsgTemplateDO();
        tpl.setContent("c");
        when(templateService.loadByCodeAndChannel(anyString(), anyString(), any(), anyString())).thenReturn(tpl);
        when(templateEngine.render(anyString(), any())).thenReturn("c");
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenThrow(new RuntimeException("dispatch error"));

        MessageResult result = messageService.send(req);

        assertFalse(result.isSuccess());
        verify(msgLogMapper).insert(any(MsgLogDO.class));
        verify(msgLogMapper, org.mockito.Mockito.atLeastOnce()).updateById(any(MsgLogDO.class));
    }

    @Test
    @DisplayName("sendDirect 委托 send")
    void sendDirectShouldDelegate() {
        com.njydsz.pmis.message.dto.MessageSendDTO dto = new com.njydsz.pmis.message.dto.MessageSendDTO();
        dto.setChannel("SMS");
        dto.setReceiver("u1");
        dto.setContent("hi");
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(null);
        when(canaryService.hit(anyString(), anyString())).thenReturn(false);
        when(rateLimitService.tryAcquire(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        when(rateLimitService.checkFrequency(anyString(), anyString(), anyString())).thenReturn(true);
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenReturn("trace");

        MessageResult result = messageService.sendDirect(dto);

        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("pageLog 分页查询")
    void pageLogShouldReturnPage() {
        com.njydsz.pmis.message.dto.MessageLogQueryDTO query = new com.njydsz.pmis.message.dto.MessageLogQueryDTO();
        query.setPage(1);
        query.setSize(10);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<MsgLogDO> mockPage = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>();
        when(msgLogMapper.selectPage(any(com.baomidou.mybatisplus.extension.plugins.pagination.Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        assertNotNullPage(messageService.pageLog(query));
    }

    private void assertNotNullPage(com.baomidou.mybatisplus.extension.plugins.pagination.Page<MsgLogDO> p) {
        org.junit.jupiter.api.Assertions.assertNotNull(p);
    }
}
