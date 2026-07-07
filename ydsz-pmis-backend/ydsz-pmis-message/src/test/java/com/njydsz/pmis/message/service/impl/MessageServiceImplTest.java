package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.channel.ChannelRouter;
import com.njydsz.pmis.message.config.MessageProperties;
import com.njydsz.pmis.message.entity.MsgLogDO;
import com.njydsz.pmis.message.entity.MsgTemplateDO;
import com.njydsz.pmis.message.filter.SensitiveWordFilter;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import com.njydsz.pmis.message.metric.MessageMetrics;
import com.njydsz.pmis.message.service.AggregateService;
import com.njydsz.pmis.message.service.CanaryService;
import com.njydsz.pmis.message.service.PreferenceService;
import com.njydsz.pmis.message.service.RateLimitService;
import com.njydsz.pmis.message.service.RouteRuleService;
import com.njydsz.pmis.message.service.SubscriptionService;
import com.njydsz.pmis.message.service.TemplateService;
import com.njydsz.pmis.message.template.TemplateEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
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
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private AggregateService aggregateService;
    @Mock
    private SensitiveWordFilter sensitiveWordFilter;

    @InjectMocks
    private MessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        // 敏感词过滤器默认透传(返回输入值),需要过滤的测试单独覆盖
        when(sensitiveWordFilter.filter(anyString())).thenAnswer(inv -> inv.getArgument(0));
        // P2-5: 多维度限流默认放行,需要限流的测试单独覆盖
        // 用 any() 而非 anyString(),因为 templateCode 可能为 null(anyString 不匹配 null)
        when(rateLimitService.checkSendLimit(any(), any(), any(), any())).thenReturn(true);
    }

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
        when(rateLimitService.tryAcquire(anyString(), anyInt())).thenReturn(true);
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
        when(rateLimitService.tryAcquire(anyString(), anyInt())).thenReturn(false);

        org.junit.jupiter.api.Assertions.assertThrows(com.njydsz.pmis.common.exception.BizException.class,
                () -> messageService.send(req));
    }

    @Test
    @DisplayName("send 模板不存在返回失败")
    void sendShouldFailWhenTemplateMissing() {
        MessageRequest req = buildRequest();
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(null);
        when(rateLimitService.tryAcquire(anyString(), anyInt())).thenReturn(true);
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
        when(rateLimitService.tryAcquire(anyString(), anyInt())).thenReturn(true);
        when(rateLimitService.checkFrequency(anyString(), anyString(), anyString())).thenReturn(true);
        MsgTemplateDO tpl = new MsgTemplateDO();
        tpl.setContent("c");
        when(templateService.loadByCodeAndChannel(anyString(), anyString(), any(), anyString())).thenReturn(tpl);
        when(templateEngine.render(anyString(), any())).thenReturn("c");
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenThrow(new RuntimeException("dispatch error"));

        MessageResult result = messageService.send(req);

        assertFalse(result.isSuccess());
        verify(msgLogMapper).insert(any(MsgLogDO.class));
        verify(msgLogMapper, Mockito.atLeastOnce()).updateById(any(MsgLogDO.class));
    }

    @Test
    @DisplayName("sendDirect 委托 send")
    void sendDirectShouldDelegate() {
        com.njydsz.pmis.message.dto.MessageSendDTO dto = new com.njydsz.pmis.message.dto.MessageSendDTO();
        dto.setChannel("SMS");
        dto.setReceiver("u1");
        dto.setContent("hi");
        dto.setBizType("DIRECT");
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(null);
        when(rateLimitService.tryAcquire(anyString(), anyInt())).thenReturn(true);
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

    // ============ P2-6: 级联发送测试 ============

    /**
     * 构造级联子消息(直传内容,不走模板)。
     */
    private MessageRequest buildCascadeChild(String channel, String receiver, String messageId) {
        MessageRequest child = new MessageRequest();
        child.setChannel(channel);
        child.setReceiver(receiver);
        child.setContent("cascade content");
        child.setBizType("CASCADE");
        child.setMessageId(messageId);
        return child;
    }

    /**
     * 设置全流程通过的 mock(通道/路由/限流/频率)。
     */
    private void stubFullFlowSuccess() {
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(null);
        when(rateLimitService.tryAcquire(anyString(), anyInt())).thenReturn(true);
        when(rateLimitService.checkFrequency(anyString(), anyString(), anyString())).thenReturn(true);
        MsgTemplateDO tpl = new MsgTemplateDO();
        tpl.setContent("hi");
        tpl.setSubject("s");
        when(templateService.loadByCodeAndChannel(anyString(), anyString(), any(), anyString())).thenReturn(tpl);
        when(templateEngine.render(anyString(), any())).thenReturn("rendered");
    }

    @Test
    @DisplayName("P2-6: 父消息发送成功后触发全部级联子消息")
    void sendShouldTriggerCascadeWhenParentSuccess() {
        MessageRequest parent = buildRequest();
        parent.setMessageId("parent-msg-001");
        MessageRequest child1 = buildCascadeChild("SMS", "u2", "child-msg-001");
        MessageRequest child2 = buildCascadeChild("PUSH", "u3", "child-msg-002");
        parent.setCascadeTo(Arrays.asList(child1, child2));

        stubFullFlowSuccess();
        when(channelRouter.dispatch(any(MsgLogDO.class)))
                .thenReturn("trace-parent")
                .thenReturn("trace-child1")
                .thenReturn("trace-child2");

        MessageResult result = messageService.send(parent);

        assertTrue(result.isSuccess());
        // 1 parent + 2 children = 3 inserts
        verify(msgLogMapper, times(3)).insert(any(MsgLogDO.class));
        // 验证 parentMsgId 追溯关系
        ArgumentCaptor<MsgLogDO> captor = ArgumentCaptor.forClass(MsgLogDO.class);
        verify(msgLogMapper, times(3)).insert(captor.capture());
        List<MsgLogDO> inserted = captor.getAllValues();
        assertNull(inserted.get(0).getParentMsgId(), "顶层消息 parentMsgId 应为 null");
        assertEquals("parent-msg-001", inserted.get(1).getParentMsgId(), "子消息1 parentMsgId 应为父 msgId");
        assertEquals("parent-msg-001", inserted.get(2).getParentMsgId(), "子消息2 parentMsgId 应为父 msgId");
    }

    @Test
    @DisplayName("P2-6: 父消息发送失败不触发级联")
    void sendShouldNotTriggerCascadeWhenParentFails() {
        MessageRequest parent = buildRequest();
        parent.setMessageId("parent-fail-001");
        MessageRequest child = buildCascadeChild("SMS", "u2", "child-fail-001");
        parent.setCascadeTo(Collections.singletonList(child));

        stubFullFlowSuccess();
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenThrow(new RuntimeException("parent dispatch error"));

        MessageResult result = messageService.send(parent);

        assertFalse(result.isSuccess());
        // 只有父消息落库,子消息不触发
        verify(msgLogMapper, times(1)).insert(any(MsgLogDO.class));
    }

    @Test
    @DisplayName("P2-6: 单条级联失败不影响其他级联")
    void sendShouldContinueCascadeWhenOneChildFails() {
        MessageRequest parent = buildRequest();
        parent.setMessageId("parent-mixed-001");
        MessageRequest child1 = buildCascadeChild("SMS", "u2", "child-mixed-001");
        MessageRequest child2 = buildCascadeChild("PUSH", "u3", "child-mixed-002");
        parent.setCascadeTo(Arrays.asList(child1, child2));

        stubFullFlowSuccess();
        when(channelRouter.dispatch(any(MsgLogDO.class)))
                .thenReturn("trace-parent")                                      // parent 成功
                .thenThrow(new RuntimeException("child1 dispatch error"))         // child1 失败
                .thenReturn("trace-child2");                                      // child2 成功

        MessageResult result = messageService.send(parent);

        assertTrue(result.isSuccess(), "父消息应成功");
        // 1 parent + 1 child1(失败但仍落库) + 1 child2 = 3 inserts
        verify(msgLogMapper, times(3)).insert(any(MsgLogDO.class));
    }

    @Test
    @DisplayName("P2-6: 级联深度超限跳过 - 链式级联 depth 0..5 共 6 条,depth 6+ 跳过")
    void sendShouldSkipCascadeWhenDepthExceedsMax() {
        // 构造 7 层链式级联: level0 -> level1 -> ... -> level7
        // MAX_CASCADE_DEPTH = 5, level 0-5 被发送(6 条), level 6-7 跳过
        MessageRequest root = buildRequest();
        root.setMessageId("level-0");
        MessageRequest current = root;
        for (int i = 1; i <= 7; i++) {
            MessageRequest child = buildCascadeChild("SMS", "u" + i, "level-" + i);
            current.setCascadeTo(Collections.singletonList(child));
            current = child;
        }

        stubFullFlowSuccess();
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenReturn("trace");

        messageService.send(root);

        // depth 0-5 = 6 次发送; depth 5 时 triggerCascade 检查 depth+1=6 > 5 跳过
        verify(msgLogMapper, times(6)).insert(any(MsgLogDO.class));
    }

    @Test
    @DisplayName("P2-6: 无级联配置时正常发送不触发级联")
    void sendShouldNotTriggerCascadeWhenCascadeToNull() {
        MessageRequest req = buildRequest();
        stubFullFlowSuccess();
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenReturn("trace");

        MessageResult result = messageService.send(req);

        assertTrue(result.isSuccess());
        verify(msgLogMapper, times(1)).insert(any(MsgLogDO.class));
    }
}
