package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.channel.ChannelRouter;
import com.njydsz.pmis.message.config.MessageProperties;
import com.njydsz.pmis.message.config.RetryStrategyResolver;
import com.njydsz.pmis.message.dto.MessageLogQueryDTO;
import com.njydsz.pmis.message.dto.MessageSendDTO;
import com.njydsz.pmis.message.entity.MsgLogDO;
import com.njydsz.pmis.message.entity.MsgPreferenceDO;
import com.njydsz.pmis.message.entity.MsgRouteRuleDO;
import com.njydsz.pmis.message.entity.MsgTemplateDO;
import com.njydsz.pmis.message.filter.SensitiveWordFilter;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import com.njydsz.pmis.message.metric.MessageMetrics;
import com.njydsz.pmis.message.service.AggregateService;
import com.njydsz.pmis.message.service.CanaryService;
import com.njydsz.pmis.message.service.DedupService;
import com.njydsz.pmis.message.service.PreferenceService;
import com.njydsz.pmis.message.service.RateLimitService;
import com.njydsz.pmis.message.service.RouteRuleService;
import com.njydsz.pmis.message.service.SubscriptionService;
import com.njydsz.pmis.message.service.TemplateService;
import com.njydsz.pmis.message.template.TemplateEngine;
import com.njydsz.pmis.message.producer.RocketMQMessageProducer;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
    @Mock
    private RetryStrategyResolver retryStrategyResolver;
    @Mock
    private DedupService dedupService;

    @InjectMocks
    private MessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        // 敏感词过滤器默认透传(返回输入值),需要过滤的测试单独覆盖
        when(sensitiveWordFilter.filter(anyString())).thenAnswer(inv -> inv.getArgument(0));
        // P2-5: 多维度限流默认放行,需要限流的测试单独覆盖
        // 用 any() 而非 anyString(),因为 templateCode 可能为 null(anyString 不匹配 null)
        when(rateLimitService.checkSendLimit(any(), any(), any(), any())).thenReturn(true);
        // P0-5: 优先级感知的 5 参数版本（default 方法不会被 Mockito 执行,需单独 stub）
        when(rateLimitService.checkSendLimit(any(), any(), any(), any(), any())).thenReturn(true);
        // P2-1: 去重默认放行(非重复),需要去重拦截的测试单独覆盖
        when(dedupService.tryAcquire(anyString())).thenReturn(true);
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

        assertThrows(BizException.class,
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
        MessageSendDTO dto = new MessageSendDTO();
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
        MessageLogQueryDTO query = new MessageLogQueryDTO();
        query.setPage(1);
        query.setSize(10);
        Page<MsgLogDO> mockPage = new Page<>();
        when(msgLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        assertNotNullPage(messageService.pageLog(query));
    }

    private void assertNotNullPage(Page<MsgLogDO> p) {
        assertNotNull(p);
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

    // ============ P1-8: 多级降级链测试 ============

    /**
     * 构造带降级链的路由规则。
     */
    private MsgRouteRuleDO buildRuleWithFallbackChain(String targetChannel, String fallbackChain) {
        MsgRouteRuleDO rule = new MsgRouteRuleDO();
        rule.setTargetChannel(targetChannel);
        rule.setFallbackChain(fallbackChain);
        rule.setStatus("ENABLED");
        return rule;
    }

    @Test
    @DisplayName("P1-8: 降级链首个通道成功 → 返回成功")
    void fallbackChainShouldSucceedOnFirstFallback() {
        MessageRequest req = buildRequest();
        // 路由命中,目标通道 EMAIL,降级链 SMS,PUSH,IN_APP
        MsgRouteRuleDO rule = buildRuleWithFallbackChain("EMAIL", "SMS,PUSH,IN_APP");
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(rule);
        when(rateLimitService.tryAcquire(anyString(), anyInt())).thenReturn(true);
        when(rateLimitService.checkFrequency(anyString(), anyString(), anyString())).thenReturn(true);
        MsgTemplateDO tpl = new MsgTemplateDO();
        tpl.setContent("c");
        when(templateService.loadByCodeAndChannel(anyString(), anyString(), any(), anyString())).thenReturn(tpl);
        when(templateEngine.render(anyString(), any())).thenReturn("c");
        // 首次 EMAIL 失败,SMS 成功
        when(channelRouter.dispatch(any(MsgLogDO.class)))
                .thenThrow(new RuntimeException("email down"))
                .thenReturn("trace-sms");

        MessageResult result = messageService.send(req);

        assertTrue(result.isSuccess());
        assertEquals("SMS", result.getChannel());
    }

    @Test
    @DisplayName("P1-8: 降级链首个失败、第二个成功 → 返回成功")
    void fallbackChainShouldSucceedOnSecondFallback() {
        MessageRequest req = buildRequest();
        MsgRouteRuleDO rule = buildRuleWithFallbackChain("EMAIL", "SMS,PUSH,IN_APP");
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(rule);
        when(rateLimitService.tryAcquire(anyString(), anyInt())).thenReturn(true);
        when(rateLimitService.checkFrequency(anyString(), anyString(), anyString())).thenReturn(true);
        MsgTemplateDO tpl = new MsgTemplateDO();
        tpl.setContent("c");
        when(templateService.loadByCodeAndChannel(anyString(), anyString(), any(), anyString())).thenReturn(tpl);
        when(templateEngine.render(anyString(), any())).thenReturn("c");
        // EMAIL 失败,SMS 失败,PUSH 成功
        when(channelRouter.dispatch(any(MsgLogDO.class)))
                .thenThrow(new RuntimeException("email down"))
                .thenThrow(new RuntimeException("sms down"))
                .thenReturn("trace-push");

        MessageResult result = messageService.send(req);

        assertTrue(result.isSuccess());
        assertEquals("PUSH", result.getChannel());
    }

    @Test
    @DisplayName("P1-8: 降级链全部失败 → 转重试")
    void fallbackChainAllFailShouldGoRetry() {
        MessageRequest req = buildRequest();
        MsgRouteRuleDO rule = buildRuleWithFallbackChain("EMAIL", "SMS,PUSH");
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(rule);
        when(rateLimitService.tryAcquire(anyString(), anyInt())).thenReturn(true);
        when(rateLimitService.checkFrequency(anyString(), anyString(), anyString())).thenReturn(true);
        MsgTemplateDO tpl = new MsgTemplateDO();
        tpl.setContent("c");
        when(templateService.loadByCodeAndChannel(anyString(), anyString(), any(), anyString())).thenReturn(tpl);
        when(templateEngine.render(anyString(), any())).thenReturn("c");
        // 全部失败
        when(channelRouter.dispatch(any(MsgLogDO.class)))
                .thenThrow(new RuntimeException("email down"))
                .thenThrow(new RuntimeException("sms down"))
                .thenThrow(new RuntimeException("push down"));
        // 重试策略未达上限
        when(retryStrategyResolver.isMaxRetriesReached(anyInt(), anyString())).thenReturn(false);
        when(retryStrategyResolver.calcNextRetryAt(anyInt(), anyString()))
                .thenReturn(java.time.LocalDateTime.now().plusSeconds(2));

        MessageResult result = messageService.send(req);

        assertFalse(result.isSuccess());
        // 验证调用了 3 次 dispatch(EMAIL + SMS + PUSH)
        verify(channelRouter, times(3)).dispatch(any(MsgLogDO.class));
    }

    @Test
    @DisplayName("P1-8: fallbackChain 为空时回退到 fallbackChannel（兼容）")
    void fallbackChainShouldFallbackToSingleChannel() {
        MessageRequest req = buildRequest();
        MsgRouteRuleDO rule = new MsgRouteRuleDO();
        rule.setTargetChannel("EMAIL");
        rule.setFallbackChannel("SMS");
        rule.setStatus("ENABLED");
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(rule);
        when(rateLimitService.tryAcquire(anyString(), anyInt())).thenReturn(true);
        when(rateLimitService.checkFrequency(anyString(), anyString(), anyString())).thenReturn(true);
        MsgTemplateDO tpl = new MsgTemplateDO();
        tpl.setContent("c");
        when(templateService.loadByCodeAndChannel(anyString(), anyString(), any(), anyString())).thenReturn(tpl);
        when(templateEngine.render(anyString(), any())).thenReturn("c");
        when(channelRouter.dispatch(any(MsgLogDO.class)))
                .thenThrow(new RuntimeException("email down"))
                .thenReturn("trace-sms");

        MessageResult result = messageService.send(req);

        assertTrue(result.isSuccess());
        assertEquals("SMS", result.getChannel());
    }

    @Test
    @DisplayName("P1-8: 降级链排除当前通道避免循环")
    void fallbackChainShouldExcludeCurrentChannel() {
        MessageRequest req = buildRequest();
        // 降级链包含当前通道 EMAIL,应被过滤
        MsgRouteRuleDO rule = buildRuleWithFallbackChain("EMAIL", "EMAIL,SMS");
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(rule);
        when(rateLimitService.tryAcquire(anyString(), anyInt())).thenReturn(true);
        when(rateLimitService.checkFrequency(anyString(), anyString(), anyString())).thenReturn(true);
        MsgTemplateDO tpl = new MsgTemplateDO();
        tpl.setContent("c");
        when(templateService.loadByCodeAndChannel(anyString(), anyString(), any(), anyString())).thenReturn(tpl);
        when(templateEngine.render(anyString(), any())).thenReturn("c");
        // EMAIL 失败,SMS 成功(不应再试 EMAIL)
        when(channelRouter.dispatch(any(MsgLogDO.class)))
                .thenThrow(new RuntimeException("email down"))
                .thenReturn("trace-sms");

        MessageResult result = messageService.send(req);

        assertTrue(result.isSuccess());
        assertEquals("SMS", result.getChannel());
        // 只调用 2 次:EMAIL(原始) + SMS(降级),不重复 EMAIL
        verify(channelRouter, times(2)).dispatch(any(MsgLogDO.class));
    }

    // ============ P2-1: 智能去重测试 ============

    @Test
    @DisplayName("P2-1: 命中去重(dedupKey 重复) → 跳过发送,不落库")
    void sendShouldSkipWhenDedupHit() {
        MessageRequest req = buildRequest();
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(null);
        // 去重命中：tryAcquire 返回 false
        when(dedupService.tryAcquire(anyString())).thenReturn(false);

        MessageResult result = messageService.send(req);

        assertFalse(result.isSuccess(), "重复消息应返回失败");
        // 验证不落库、不调用 dispatch
        verify(msgLogMapper, never()).insert(any(MsgLogDO.class));
        verify(channelRouter, never()).dispatch(any(MsgLogDO.class));
        // 验证记录了 DEDUPED 指标
        verify(messageMetrics).recordSend(eq("EMAIL"), eq("DEDUPED"), eq(0L));
    }

    @Test
    @DisplayName("P2-1: 未命中去重 → 正常发送")
    void sendShouldPassWhenDedupMiss() {
        MessageRequest req = buildRequest();
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(null);
        when(rateLimitService.tryAcquire(anyString(), anyInt())).thenReturn(true);
        when(rateLimitService.checkFrequency(anyString(), anyString(), anyString())).thenReturn(true);
        // 去重未命中：tryAcquire 返回 true
        when(dedupService.tryAcquire(anyString())).thenReturn(true);
        MsgTemplateDO tpl = new MsgTemplateDO();
        tpl.setContent("hi");
        when(templateService.loadByCodeAndChannel(anyString(), anyString(), any(), anyString())).thenReturn(tpl);
        when(templateEngine.render(anyString(), any())).thenReturn("rendered");
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenReturn("trace-1");

        MessageResult result = messageService.send(req);

        assertTrue(result.isSuccess());
        verify(msgLogMapper).insert(any(MsgLogDO.class));
    }

    // ============ P2-3: 事务消息测试 ============

    @Test
    @DisplayName("P2-3: mqProducer 为 null → 降级同步发送")
    void sendTransactionallyShouldFallbackToSyncWhenNoProducer() {
        // mqProducer 默认为 null(未注入 mock)
        MessageRequest req = buildRequest();
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(null);
        when(rateLimitService.tryAcquire(anyString(), anyInt())).thenReturn(true);
        when(rateLimitService.checkFrequency(anyString(), anyString(), anyString())).thenReturn(true);
        when(dedupService.tryAcquire(anyString())).thenReturn(true);
        MsgTemplateDO tpl = new MsgTemplateDO();
        tpl.setContent("hi");
        when(templateService.loadByCodeAndChannel(anyString(), anyString(), any(), anyString())).thenReturn(tpl);
        when(templateEngine.render(anyString(), any())).thenReturn("rendered");
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenReturn("trace-tx");

        MessageResult result = messageService.sendTransactionally(req);

        assertTrue(result.isSuccess(), "降级同步发送应成功");
        verify(msgLogMapper).insert(any(MsgLogDO.class));
    }

    @Test
    @DisplayName("P2-3: mqProducer 可用 → 委托生产者发送事务消息")
    void sendTransactionallyShouldDelegateToProducer() {
        MessageRequest req = buildRequest();
        RocketMQMessageProducer mockProducer = Mockito.mock(RocketMQMessageProducer.class);
        ReflectionTestUtils.setField(messageService, "mqProducer", mockProducer);
        when(mockProducer.sendTransactionMessage(any(MessageRequest.class))).thenReturn("rocketmq-msg-id-001");

        MessageResult result = messageService.sendTransactionally(req);

        assertTrue(result.isSuccess());
        assertEquals("rocketmq-msg-id-001", result.getProviderTraceId());
        verify(mockProducer).sendTransactionMessage(any(MessageRequest.class));
        // 不应走同步发送(不落库)
        verify(msgLogMapper, never()).insert(any(MsgLogDO.class));
    }

    @Test
    @DisplayName("P2-3: 生产者异常 → 降级同步发送")
    void sendTransactionallyShouldFallbackOnProducerException() {
        MessageRequest req = buildRequest();
        RocketMQMessageProducer mockProducer = Mockito.mock(RocketMQMessageProducer.class);
        ReflectionTestUtils.setField(messageService, "mqProducer", mockProducer);
        when(mockProducer.sendTransactionMessage(any(MessageRequest.class)))
                .thenThrow(new RuntimeException("rocketmq down"));
        // 降级后走同步发送
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(null);
        when(rateLimitService.tryAcquire(anyString(), anyInt())).thenReturn(true);
        when(rateLimitService.checkFrequency(anyString(), anyString(), anyString())).thenReturn(true);
        when(dedupService.tryAcquire(anyString())).thenReturn(true);
        MsgTemplateDO tpl = new MsgTemplateDO();
        tpl.setContent("hi");
        when(templateService.loadByCodeAndChannel(anyString(), anyString(), any(), anyString())).thenReturn(tpl);
        when(templateEngine.render(anyString(), any())).thenReturn("rendered");
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenReturn("trace-fallback");

        MessageResult result = messageService.sendTransactionally(req);

        assertTrue(result.isSuccess(), "降级同步发送应成功");
        verify(msgLogMapper).insert(any(MsgLogDO.class));
    }

    // ============ P2-5: 智能定时（DND 延迟发送）测试 ============

    /** 构建一个 DND 全天生效的偏好（00:00-23:59） */
    private MsgPreferenceDO buildDndPreference() {
        MsgPreferenceDO pref = new MsgPreferenceDO();
        pref.setDndEnabled(1);
        pref.setDndStart("00:00");
        pref.setDndEnd("23:59");
        pref.setLocale("zh-CN");
        return pref;
    }

    /** 配置 SmartTimingConfig mock */
    private MessageProperties.SmartTimingConfig stubSmartTiming(boolean enabled) {
        MessageProperties.SmartTimingConfig stc = new MessageProperties.SmartTimingConfig();
        stc.setEnabled(enabled);
        when(messageProperties.getSmartTiming()).thenReturn(stc);
        return stc;
    }

    @Test
    @DisplayName("P2-5: DND 命中 + 智能定时开启 + 打扰型通道(SMS) → 延迟为 SCHEDULED 不立即发送")
    void sendShouldDeferWhenDndHitAndSmartTimingEnabled() {
        MessageRequest req = buildRequest();
        req.setChannel("SMS");
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(null);
        when(rateLimitService.tryAcquire(anyString(), anyInt())).thenReturn(true);
        when(rateLimitService.checkFrequency(anyString(), anyString(), anyString())).thenReturn(true);
        when(dedupService.tryAcquire(anyString())).thenReturn(true);
        stubSmartTiming(true);
        when(preferenceService.getByUser(anyString(), anyString(), anyString()))
                .thenReturn(buildDndPreference());
        MsgTemplateDO tpl = new MsgTemplateDO();
        tpl.setContent("hi");
        when(templateService.loadByCodeAndChannel(anyString(), anyString(), any(), anyString()))
                .thenReturn(tpl);
        when(templateEngine.render(anyString(), any())).thenReturn("rendered");

        MessageResult result = messageService.send(req);

        assertTrue(result.isSuccess(), "DND 延迟应返回成功(已入库 SCHEDULED)");
        // 验证落库了 SCHEDULED 消息
        ArgumentCaptor<MsgLogDO> captor = ArgumentCaptor.forClass(MsgLogDO.class);
        verify(msgLogMapper).insert(captor.capture());
        MsgLogDO saved = captor.getValue();
        assertEquals("SCHEDULED", saved.getStatus(), "DND 延迟消息状态应为 SCHEDULED");
        assertNotNull(saved.getScheduledAt(), "scheduledAt 应非空");
        assertTrue(saved.getScheduledAt().isAfter(LocalDateTime.now()),
                "延迟发送时间应在未来");
        // 验证不立即 dispatch
        verify(channelRouter, never()).dispatch(any(MsgLogDO.class));
        // 验证记录了 DND_DEFERRED 指标
        verify(messageMetrics).recordSend(eq("SMS"), eq("DND_DEFERRED"), eq(0L));
    }

    @Test
    @DisplayName("P2-5: DND 命中 + URGENT 优先级 → 绕过 DND 立即发送")
    void sendShouldBypassDndWhenUrgentPriority() {
        MessageRequest req = buildRequest();
        req.setChannel("SMS");
        req.setPriority("URGENT");
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(null);
        when(rateLimitService.tryAcquire(anyString(), anyInt())).thenReturn(true);
        when(rateLimitService.checkFrequency(anyString(), anyString(), anyString())).thenReturn(true);
        when(dedupService.tryAcquire(anyString())).thenReturn(true);
        stubSmartTiming(true);
        when(preferenceService.getByUser(anyString(), anyString(), anyString()))
                .thenReturn(buildDndPreference());
        MsgTemplateDO tpl = new MsgTemplateDO();
        tpl.setContent("urgent");
        when(templateService.loadByCodeAndChannel(anyString(), anyString(), any(), anyString()))
                .thenReturn(tpl);
        when(templateEngine.render(anyString(), any())).thenReturn("rendered");
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenReturn("trace-urgent");

        MessageResult result = messageService.send(req);

        assertTrue(result.isSuccess(), "URGENT 应绕过 DND 立即发送");
        verify(channelRouter).dispatch(any(MsgLogDO.class));
        // 验证不记录 DND_DEFERRED
        verify(messageMetrics, never()).recordSend(eq("SMS"), eq("DND_DEFERRED"), anyLong());
    }

    @Test
    @DisplayName("P2-5: DND 命中 + 非打扰型通道(EMAIL) → 绕过 DND 立即发送")
    void sendShouldBypassDndWhenNonDisruptiveChannel() {
        MessageRequest req = buildRequest();
        req.setChannel("EMAIL"); // EMAIL 不在默认打扰型通道列表
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(null);
        when(rateLimitService.tryAcquire(anyString(), anyInt())).thenReturn(true);
        when(rateLimitService.checkFrequency(anyString(), anyString(), anyString())).thenReturn(true);
        when(dedupService.tryAcquire(anyString())).thenReturn(true);
        stubSmartTiming(true);
        when(preferenceService.getByUser(anyString(), anyString(), anyString()))
                .thenReturn(buildDndPreference());
        MsgTemplateDO tpl = new MsgTemplateDO();
        tpl.setContent("hi");
        when(templateService.loadByCodeAndChannel(anyString(), anyString(), any(), anyString()))
                .thenReturn(tpl);
        when(templateEngine.render(anyString(), any())).thenReturn("rendered");
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenReturn("trace-email");

        MessageResult result = messageService.send(req);

        assertTrue(result.isSuccess(), "EMAIL 非打扰型通道应绕过 DND");
        verify(channelRouter).dispatch(any(MsgLogDO.class));
        verify(messageMetrics, never()).recordSend(eq("EMAIL"), eq("DND_DEFERRED"), anyLong());
    }

    @Test
    @DisplayName("P2-5: DND 命中 + 智能定时关闭 → 走旧的丢弃策略")
    void sendShouldDropWhenSmartTimingDisabled() {
        MessageRequest req = buildRequest();
        req.setChannel("SMS");
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(null);
        stubSmartTiming(false);
        when(preferenceService.getByUser(anyString(), anyString(), anyString()))
                .thenReturn(buildDndPreference());

        MessageResult result = messageService.send(req);

        assertFalse(result.isSuccess(), "智能定时关闭时应丢弃消息");
        verify(channelRouter, never()).dispatch(any(MsgLogDO.class));
        verify(msgLogMapper, never()).insert(any(MsgLogDO.class));
        verify(messageMetrics).recordSend(eq("SMS"), eq("DND_SKIPPED"), eq(0L));
    }

    @Test
    @DisplayName("P2-5: DND 命中 + 延迟超过 maxDeferHours → 丢弃")
    void sendShouldDropWhenDndDeferExceedsMax() {
        MessageRequest req = buildRequest();
        req.setChannel("SMS");
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(null);
        MessageProperties.SmartTimingConfig stc = stubSmartTiming(true);
        stc.setMaxDeferHours(-1); // 任何非负延迟都超过 -1
        when(preferenceService.getByUser(anyString(), anyString(), anyString()))
                .thenReturn(buildDndPreference());

        MessageResult result = messageService.send(req);

        assertFalse(result.isSuccess(), "延迟超过阈值应丢弃");
        verify(channelRouter, never()).dispatch(any(MsgLogDO.class));
        verify(msgLogMapper, never()).insert(any(MsgLogDO.class));
        verify(messageMetrics).recordSend(eq("SMS"), eq("DND_DROPPED"), eq(0L));
    }

    @Test
    @DisplayName("P2-5: DND 未开启 → 正常发送不受影响")
    void sendShouldPassWhenDndDisabled() {
        MessageRequest req = buildRequest();
        req.setChannel("SMS");
        when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        when(routeRuleService.match(any())).thenReturn(null);
        when(rateLimitService.tryAcquire(anyString(), anyInt())).thenReturn(true);
        when(rateLimitService.checkFrequency(anyString(), anyString(), anyString())).thenReturn(true);
        when(dedupService.tryAcquire(anyString())).thenReturn(true);
        stubSmartTiming(true);
        MsgPreferenceDO pref = buildDndPreference();
        pref.setDndEnabled(0); // DND 关闭
        when(preferenceService.getByUser(anyString(), anyString(), anyString())).thenReturn(pref);
        MsgTemplateDO tpl = new MsgTemplateDO();
        tpl.setContent("hi");
        when(templateService.loadByCodeAndChannel(anyString(), anyString(), any(), anyString()))
                .thenReturn(tpl);
        when(templateEngine.render(anyString(), any())).thenReturn("rendered");
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenReturn("trace-ok");

        MessageResult result = messageService.send(req);

        assertTrue(result.isSuccess(), "DND 关闭时应正常发送");
        verify(channelRouter).dispatch(any(MsgLogDO.class));
    }
}
