package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.message.channel.ChannelRouter;
import com.njydsz.pmis.message.config.MessageProperties;
import com.njydsz.pmis.message.config.RetryStrategyResolver;
import com.njydsz.pmis.message.dto.BatchSendResult;
import com.njydsz.pmis.message.dto.MessageLogQueryDTO;
import com.njydsz.pmis.message.dto.MessageSendDTO;
import com.njydsz.pmis.message.entity.MsgCanaryDO;
import com.njydsz.pmis.message.entity.MsgLogDO;
import com.njydsz.pmis.message.entity.MsgPreferenceDO;
import com.njydsz.pmis.message.entity.MsgRouteRuleDO;
import com.njydsz.pmis.message.entity.MsgTemplateDO;
import com.njydsz.pmis.message.filter.SensitiveWordFilter;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import com.njydsz.pmis.message.metric.MessageMetrics;
import com.njydsz.pmis.message.producer.RocketMQMessageProducer;
import com.njydsz.pmis.message.service.AggregateService;
import com.njydsz.pmis.message.service.CanaryService;
import com.njydsz.pmis.message.service.DedupService;
import com.njydsz.pmis.message.service.DeliveryTimeOptimizer;
import com.njydsz.pmis.message.service.MessageTraceService;
import com.njydsz.pmis.message.service.PreferenceService;
import com.njydsz.pmis.message.service.RateLimitService;
import com.njydsz.pmis.message.service.RouteRuleService;
import com.njydsz.pmis.message.service.SubscriptionService;
import com.njydsz.pmis.message.service.TemplateService;
import com.njydsz.pmis.message.template.RichMediaRenderer;
import com.njydsz.pmis.message.template.TemplateEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 消息发送核心编排服务单元测试。
 *
 * <p>覆盖 send / sendDirect / batchSend / sendTransactionally / pageLog 五大入口，
 * 重点验证通道校验、路由、灰度、订阅、偏好(DND)、去重、限流、模板加载、分发、
 * 降级链、重试、定时消息、级联发送等核心编排逻辑。
 *
 * <p>该 ServiceImpl 依赖多达 19 个外部组件，使用 {@link Strictness#LENIENT} 宽松模式
 * 避免每个测试都要精确匹配全部 stub。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("消息发送核心服务 MessageServiceImpl 单元测试")
class MessageServiceImplTest {

    @Mock private ChannelRouter channelRouter;
    @Mock private TemplateEngine templateEngine;
    @Mock private TemplateService templateService;
    @Mock private MsgLogMapper msgLogMapper;
    @Mock private RouteRuleService routeRuleService;
    @Mock private RateLimitService rateLimitService;
    @Mock private CanaryService canaryService;
    @Mock private MessageProperties messageProperties;
    @Mock private MessageMetrics messageMetrics;
    @Mock private SubscriptionService subscriptionService;
    @Mock private PreferenceService preferenceService;
    @Mock private AggregateService aggregateService;
    @Mock private SensitiveWordFilter sensitiveWordFilter;
    @Mock private RetryStrategyResolver retryStrategyResolver;
    @Mock private DedupService dedupService;
    @Mock private MessageTraceService messageTraceService;
    @Mock private DeliveryTimeOptimizer deliveryTimeOptimizer;
    @Mock private RichMediaRenderer richMediaRenderer;
    @Mock private ObjectProvider<RocketMQMessageProducer> mqProducerProvider;
    @Mock private RocketMQMessageProducer mqProducer;

    @InjectMocks
    private MessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("1");
        // 通用宽松 stub：通道启用、无路由/灰度/订阅拦截、无限流、无偏好
        lenient().when(channelRouter.isChannelEnabled(anyString())).thenReturn(true);
        lenient().when(messageProperties.getChannelEnabled()).thenReturn(null);
        lenient().when(messageProperties.getDefaultPriority()).thenReturn("NORMAL");
        lenient().when(messageProperties.getCost()).thenReturn(null);
        lenient().when(routeRuleService.match(any())).thenReturn(null);
        lenient().when(canaryService.matchConfig(anyString(), anyString())).thenReturn(null);
        lenient().when(subscriptionService.isBlocked(anyString(), anyString(), anyString())).thenReturn(false);
        lenient().when(preferenceService.getByUser(anyString(), anyString(), anyString())).thenReturn(null);
        lenient().when(dedupService.tryAcquire(anyString())).thenReturn(true);
        lenient().when(rateLimitService.tryAcquire(nullable(String.class), anyInt())).thenReturn(true);
        lenient().when(rateLimitService.checkSendLimit(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class))).thenReturn(true);
        lenient().when(rateLimitService.checkSendLimit(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class))).thenReturn(true);
        lenient().when(rateLimitService.checkFrequency(nullable(String.class), nullable(String.class), nullable(String.class))).thenReturn(true);
        lenient().when(richMediaRenderer.extractFromParams(any())).thenReturn(null);
        lenient().when(deliveryTimeOptimizer.getOptimalDeliveryTime(anyString(), anyString())).thenReturn(null);
        lenient().when(sensitiveWordFilter.filter(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** 构造基础发送请求 */
    private MessageRequest buildRequest() {
        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        req.setTemplateCode("TPL_001");
        req.setReceiver("13800000001");
        req.setBizType("ALERT");
        req.setBizId("BIZ_001");
        req.setMessageId("MSG_001");
        req.setParams(Map.of("code", "1234"));
        return req;
    }

    /** 构造模板 DO */
    private MsgTemplateDO buildTemplate() {
        MsgTemplateDO tpl = new MsgTemplateDO();
        tpl.setTemplateCode("TPL_001");
        tpl.setChannel("SMS");
        tpl.setContent("验证码: ${code}");
        tpl.setSubject("通知");
        return tpl;
    }

    // ============ send() 参数校验 ============

    @Test
    @DisplayName("异常场景：request 为 null 返回失败")
    void send_nullRequest_returnsFail() {
        MessageResult result = messageService.send(null);
        assertFalse(result.isSuccess());
        verifyNoInteractionsWithCore();
    }

    @Test
    @DisplayName("异常场景：channel 为空返回失败")
    void send_emptyChannel_returnsFail() {
        MessageRequest req = buildRequest();
        req.setChannel("");
        MessageResult result = messageService.send(req);
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("异常场景：channel 为 null 返回失败")
    void send_nullChannel_returnsFail() {
        MessageRequest req = buildRequest();
        req.setChannel(null);
        MessageResult result = messageService.send(req);
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("异常场景：通道未启用返回失败")
    void send_channelDisabled_returnsFail() {
        when(channelRouter.isChannelEnabled("SMS")).thenReturn(false);
        MessageRequest req = buildRequest();
        MessageResult result = messageService.send(req);
        assertFalse(result.isSuccess());
        verify(channelRouter).isChannelEnabled("SMS");
    }

    // ============ send() 正常成功路径 ============

    @Test
    @DisplayName("正常场景：全流程发送成功返回 SUCCESS")
    void send_normalSuccess() {
        MessageRequest req = buildRequest();
        MsgTemplateDO tpl = buildTemplate();
        when(templateService.loadByCodeAndChannel(eq("TPL_001"), eq("SMS"), any(), eq("1")))
                .thenReturn(tpl);
        when(templateEngine.render(eq("验证码: ${code}"), any())).thenReturn("验证码: 1234");
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenReturn("provider-trace-001");

        MessageResult result = messageService.send(req);

        assertTrue(result.isSuccess());
        assertEquals("SMS", result.getChannel());
        assertEquals("provider-trace-001", result.getProviderTraceId());

        ArgumentCaptor<MsgLogDO> logCaptor = ArgumentCaptor.forClass(MsgLogDO.class);
        verify(msgLogMapper).insert(logCaptor.capture());
        MsgLogDO inserted = logCaptor.getValue();
        assertEquals("SMS", inserted.getChannel());
        assertEquals("ALERT", inserted.getBizType());
        assertEquals("13800000001", inserted.getReceiver());
        assertEquals("TPL_001", inserted.getTemplateCode());
        assertEquals("PENDING", inserted.getStatus());
        assertEquals("MSG_001", inserted.getMsgId());
        assertEquals("NONE", inserted.getReceiptStatus());
        assertEquals(0, inserted.getRetryCount());
        assertEquals("1", inserted.getTenantId());

        verify(channelRouter).dispatch(any(MsgLogDO.class));
        verify(rateLimitService).recordFrequency("13800000001", "SMS", "ALERT");
        verify(messageMetrics).recordSend(eq("SMS"), eq("SUCCESS"), anyInt());
    }

    @Test
    @DisplayName("正常场景：无 templateCode 直传内容发送成功")
    void send_directContentSuccess() {
        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        req.setReceiver("13800000001");
        req.setContent("直传内容");
        req.setMessageId("MSG_DIRECT");
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenReturn("trace-direct");

        MessageResult result = messageService.send(req);

        assertTrue(result.isSuccess());
        verify(templateService, never()).loadByCodeAndChannel(any(), any(), any(), any());
        verify(sensitiveWordFilter).filter("直传内容");
    }

    // ============ send() 订阅/去重/限流拦截 ============

    @Test
    @DisplayName("异常场景：用户已退订返回失败")
    void send_subscriptionBlocked_returnsFail() {
        when(subscriptionService.isBlocked("13800000001", "TPL_001", "SMS")).thenReturn(true);
        MessageRequest req = buildRequest();

        MessageResult result = messageService.send(req);

        assertFalse(result.isSuccess());
        verify(messageMetrics).recordSend("SMS", "BLOCKED", 0);
        verify(channelRouter, never()).dispatch(any(MsgLogDO.class));
    }

    @Test
    @DisplayName("异常场景：去重重复返回失败")
    void send_dedupDuplicate_returnsFail() {
        when(dedupService.tryAcquire(anyString())).thenReturn(false);
        MessageRequest req = buildRequest();

        MessageResult result = messageService.send(req);

        assertFalse(result.isSuccess());
        verify(messageMetrics).recordSend("SMS", "DEDUPED", 0);
        verify(channelRouter, never()).dispatch(any(MsgLogDO.class));
    }

    @Test
    @DisplayName("异常场景：通道令牌桶限流抛 BizException")
    void send_rateLimitTokenBucket_throws() {
        when(rateLimitService.tryAcquire(anyString(), anyInt())).thenReturn(false);
        MessageRequest req = buildRequest();

        BizException ex = assertThrows(BizException.class, () -> messageService.send(req));
        assertEquals(BizErrorCode.RATE_LIMIT.getCode(), ex.getCode());
        verify(messageMetrics).recordSend("SMS", "FAILED", 0);
    }

    @Test
    @DisplayName("异常场景：多维度限流抛 BizException")
    void send_multiDimRateLimit_throws() {
        when(rateLimitService.checkSendLimit(nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class)))
                .thenReturn(false);
        MessageRequest req = buildRequest();

        BizException ex = assertThrows(BizException.class, () -> messageService.send(req));
        assertEquals(BizErrorCode.RATE_LIMIT.getCode(), ex.getCode());
        verify(messageMetrics).recordSend("SMS", "RATE_LIMITED", 0);
    }

    @Test
    @DisplayName("异常场景：频率超限抛 BizException")
    void send_frequencyExceeded_throws() {
        when(rateLimitService.checkFrequency(anyString(), anyString(), anyString())).thenReturn(false);
        MessageRequest req = buildRequest();

        BizException ex = assertThrows(BizException.class, () -> messageService.send(req));
        assertEquals(BizErrorCode.RATE_LIMIT.getCode(), ex.getCode());
    }

    // ============ send() 模板加载 ============

    @Test
    @DisplayName("异常场景：模板不存在返回失败")
    void send_templateNotFound_returnsFail() {
        when(templateService.loadByCodeAndChannel(any(), any(), any(), any())).thenReturn(null);
        MessageRequest req = buildRequest();

        MessageResult result = messageService.send(req);

        assertFalse(result.isSuccess());
        verify(channelRouter, never()).dispatch(any(MsgLogDO.class));
    }

    @Test
    @DisplayName("正常场景：模板渲染后内容经敏感词过滤")
    void send_templateRenderedAndFiltered() {
        MessageRequest req = buildRequest();
        MsgTemplateDO tpl = buildTemplate();
        when(templateService.loadByCodeAndChannel(any(), any(), any(), any())).thenReturn(tpl);
        when(templateEngine.render(eq("验证码: ${code}"), any())).thenReturn("验证码: 1234");
        when(sensitiveWordFilter.filter("验证码: 1234")).thenReturn("验证码: ****");
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenReturn("trace");

        messageService.send(req);

        ArgumentCaptor<MsgLogDO> logCaptor = ArgumentCaptor.forClass(MsgLogDO.class);
        verify(msgLogMapper).insert(logCaptor.capture());
        assertEquals("验证码: ****", logCaptor.getValue().getContent());
    }

    // ============ send() 分发失败/重试/降级 ============

    @Test
    @DisplayName("异常场景：分发失败未达重试上限转为 RETRY")
    void send_dispatchFail_retry() {
        MessageRequest req = buildRequest();
        MsgTemplateDO tpl = buildTemplate();
        when(templateService.loadByCodeAndChannel(any(), any(), any(), any())).thenReturn(tpl);
        when(templateEngine.render(any(), any())).thenReturn("rendered");
        when(channelRouter.dispatch(any(MsgLogDO.class)))
                .thenThrow(new RuntimeException("通道异常"));
        when(retryStrategyResolver.isMaxRetriesReached(anyInt(), anyString())).thenReturn(false);
        when(retryStrategyResolver.calcNextRetryAt(anyInt(), anyString()))
                .thenReturn(LocalDateTime.now().plusMinutes(5));

        MessageResult result = messageService.send(req);

        assertFalse(result.isSuccess());
        verify(messageMetrics).recordRetry("SMS");
        // 验证最终状态为 RETRY
        ArgumentCaptor<MsgLogDO> logCaptor = ArgumentCaptor.forClass(MsgLogDO.class);
        verify(msgLogMapper, atLeast(2)).updateById(logCaptor.capture());
        assertEquals("RETRY", logCaptor.getValue().getStatus());
        assertNotNull(logCaptor.getValue().getNextRetryAt());
    }

    @Test
    @DisplayName("异常场景：分发失败已达重试上限转为 FAILED")
    void send_dispatchFail_maxRetries_failed() {
        MessageRequest req = buildRequest();
        MsgTemplateDO tpl = buildTemplate();
        when(templateService.loadByCodeAndChannel(any(), any(), any(), any())).thenReturn(tpl);
        when(templateEngine.render(any(), any())).thenReturn("rendered");
        when(channelRouter.dispatch(any(MsgLogDO.class)))
                .thenThrow(new RuntimeException("通道彻底不可用"));
        when(retryStrategyResolver.isMaxRetriesReached(anyInt(), anyString())).thenReturn(true);

        MessageResult result = messageService.send(req);

        assertFalse(result.isSuccess());
        verify(messageMetrics).recordSend(eq("SMS"), eq("FAILED"), anyInt());
        ArgumentCaptor<MsgLogDO> logCaptor = ArgumentCaptor.forClass(MsgLogDO.class);
        verify(msgLogMapper, atLeast(2)).updateById(logCaptor.capture());
        assertEquals("FAILED", logCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("正常场景：降级链首个通道成功返回 SUCCESS")
    void send_fallbackChain_success() {
        MessageRequest req = buildRequest();
        MsgTemplateDO tpl = buildTemplate();
        when(templateService.loadByCodeAndChannel(any(), any(), any(), any())).thenReturn(tpl);
        when(templateEngine.render(any(), any())).thenReturn("rendered");

        // 原始通道 SMS 失败
        when(channelRouter.dispatch(any(MsgLogDO.class)))
                .thenThrow(new RuntimeException("SMS 通道异常"))
                .thenReturn("email-trace"); // 第二次调用(EMAIL 降级)成功

        // 路由规则带降级链
        MsgRouteRuleDO rule = new MsgRouteRuleDO();
        rule.setId("RULE_001");
        rule.setTargetChannel("SMS");
        rule.setFallbackChain("EMAIL,INAPP");
        when(routeRuleService.match(any())).thenReturn(rule);

        MessageResult result = messageService.send(req);

        assertTrue(result.isSuccess());
        assertEquals("EMAIL", result.getChannel());
    }

    @Test
    @DisplayName("异常场景：降级链全部失败走重试逻辑")
    void send_fallbackChainAllFail_retry() {
        MessageRequest req = buildRequest();
        MsgTemplateDO tpl = buildTemplate();
        when(templateService.loadByCodeAndChannel(any(), any(), any(), any())).thenReturn(tpl);
        when(templateEngine.render(any(), any())).thenReturn("rendered");

        // 所有通道都失败
        when(channelRouter.dispatch(any(MsgLogDO.class)))
                .thenThrow(new RuntimeException("全部失败"));
        when(retryStrategyResolver.isMaxRetriesReached(anyInt(), anyString())).thenReturn(false);
        when(retryStrategyResolver.calcNextRetryAt(anyInt(), anyString()))
                .thenReturn(LocalDateTime.now().plusMinutes(3));

        MsgRouteRuleDO rule = new MsgRouteRuleDO();
        rule.setId("RULE_001");
        rule.setFallbackChain("EMAIL,INAPP");
        when(routeRuleService.match(any())).thenReturn(rule);

        MessageResult result = messageService.send(req);

        assertFalse(result.isSuccess());
        verify(messageMetrics).recordRetry("SMS");
    }

    // ============ send() 灰度命中 ============

    @Test
    @DisplayName("正常场景：灰度命中切换实验模板和通道")
    void send_canaryMatch_switchesTemplateAndChannel() {
        MessageRequest req = buildRequest();
        MsgCanaryDO canary = new MsgCanaryDO();
        canary.setExperimentTemplateCode("TPL_EXP");
        canary.setExperimentChannel("EMAIL");
        when(canaryService.matchConfig("TPL_001", "13800000001")).thenReturn(canary);

        MsgTemplateDO tpl = new MsgTemplateDO();
        tpl.setTemplateCode("TPL_EXP");
        tpl.setChannel("EMAIL");
        tpl.setContent("实验内容: ${code}");
        when(templateService.loadByCodeAndChannel(eq("TPL_EXP"), eq("EMAIL"), any(), any()))
                .thenReturn(tpl);
        when(templateEngine.render(any(), any())).thenReturn("实验内容: 1234");
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenReturn("exp-trace");

        MessageResult result = messageService.send(req);

        assertTrue(result.isSuccess());
        ArgumentCaptor<MsgLogDO> logCaptor = ArgumentCaptor.forClass(MsgLogDO.class);
        verify(msgLogMapper).insert(logCaptor.capture());
        MsgLogDO inserted = logCaptor.getValue();
        assertEquals("EMAIL", inserted.getChannel());
        assertEquals("TPL_EXP", inserted.getTemplateCode());
        assertEquals(1, inserted.getCanary());
        assertEquals("TPL_001", inserted.getCanaryKey());
    }

    // ============ send() 定时消息 ============

    @Test
    @DisplayName("正常场景：scheduledAt 在未来时落库 SCHEDULED 不立即发送")
    void send_scheduledMessage_persistsScheduled() {
        MessageRequest req = buildRequest();
        req.setScheduledAt(LocalDateTime.now().plusHours(2));
        MsgTemplateDO tpl = buildTemplate();
        when(templateService.loadByCodeAndChannel(any(), any(), any(), any())).thenReturn(tpl);
        when(templateEngine.render(any(), any())).thenReturn("rendered");

        MessageResult result = messageService.send(req);

        assertTrue(result.isSuccess());
        ArgumentCaptor<MsgLogDO> logCaptor = ArgumentCaptor.forClass(MsgLogDO.class);
        verify(msgLogMapper).insert(logCaptor.capture());
        MsgLogDO inserted = logCaptor.getValue();
        assertEquals("SCHEDULED", inserted.getStatus());
        assertNotNull(inserted.getScheduledAt());
        verify(channelRouter, never()).dispatch(any(MsgLogDO.class));
    }

    // ============ send() 聚合 ============

    @Test
    @DisplayName("正常场景：digestEnabled=1 时加入聚合批次不立即发送")
    void send_aggregateEnabled_joinsBatch() {
        MessageRequest req = buildRequest();
        MsgTemplateDO tpl = buildTemplate();
        when(templateService.loadByCodeAndChannel(any(), any(), any(), any())).thenReturn(tpl);
        when(templateEngine.render(any(), any())).thenReturn("rendered");

        MsgPreferenceDO pref = new MsgPreferenceDO();
        pref.setDigestEnabled(1);
        when(preferenceService.getByUser("13800000001", "SMS", "ALERT")).thenReturn(pref);

        MessageResult result = messageService.send(req);

        assertTrue(result.isSuccess());
        verify(aggregateService).appendOrStart("ALERT", "13800000001", "SMS", "1");
        verify(channelRouter, never()).dispatch(any(MsgLogDO.class));
    }

    // ============ send() 级联发送 ============

    @Test
    @DisplayName("正常场景：父消息成功后触发级联子消息")
    void send_cascadeTriggered_afterSuccess() {
        MessageRequest child = new MessageRequest();
        child.setChannel("EMAIL");
        child.setReceiver("13800000001");
        child.setMessageId("MSG_CHILD");

        MessageRequest req = buildRequest();
        req.setCascadeTo(List.of(child));

        MsgTemplateDO tpl = buildTemplate();
        when(templateService.loadByCodeAndChannel(any(), any(), any(), any())).thenReturn(tpl);
        when(templateEngine.render(any(), any())).thenReturn("rendered");
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenReturn("trace");

        messageService.send(req);

        // 父消息成功后应触发级联：channelRouter.dispatch 至少被调用 2 次（父+子）
        verify(channelRouter, atLeast(2)).dispatch(any(MsgLogDO.class));
    }

    // ============ send() DND 免打扰 ============

    @Test
    @DisplayName("异常场景：DND 时段且智能定时未启用返回失败")
    void send_dndPeriod_smartTimingDisabled_returnsFail() {
        MessageRequest req = buildRequest();
        MsgPreferenceDO pref = new MsgPreferenceDO();
        pref.setDndEnabled(1);
        pref.setDndStart("00:00");
        pref.setDndEnd("23:59");
        when(preferenceService.getByUser("13800000001", "SMS", "ALERT")).thenReturn(pref);

        MessageProperties.SmartTimingConfig stc = new MessageProperties.SmartTimingConfig();
        stc.setEnabled(false);
        when(messageProperties.getSmartTiming()).thenReturn(stc);

        MessageResult result = messageService.send(req);

        assertFalse(result.isSuccess());
        verify(messageMetrics).recordSend("SMS", "DND_SKIPPED", 0);
        verify(channelRouter, never()).dispatch(any(MsgLogDO.class));
    }

    @Test
    @DisplayName("正常场景：URGENT 优先级绕过 DND")
    void send_urgentBypassDnd() {
        MessageRequest req = buildRequest();
        req.setPriority("URGENT");

        MsgPreferenceDO pref = new MsgPreferenceDO();
        pref.setDndEnabled(1);
        pref.setDndStart("00:00");
        pref.setDndEnd("23:59");
        when(preferenceService.getByUser("13800000001", "SMS", "ALERT")).thenReturn(pref);

        MessageProperties.SmartTimingConfig stc = new MessageProperties.SmartTimingConfig();
        stc.setEnabled(true);
        stc.setUrgentBypassDnd(true);
        when(messageProperties.getSmartTiming()).thenReturn(stc);

        MsgTemplateDO tpl = buildTemplate();
        when(templateService.loadByCodeAndChannel(any(), any(), any(), any())).thenReturn(tpl);
        when(templateEngine.render(any(), any())).thenReturn("rendered");
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenReturn("urgent-trace");

        MessageResult result = messageService.send(req);

        assertTrue(result.isSuccess());
    }

    // ============ sendDirect() ============

    @Test
    @DisplayName("异常场景：sendDirect dto 为 null 返回失败")
    void sendDirect_nullDto_returnsFail() {
        MessageResult result = messageService.sendDirect(null);
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("正常场景：sendDirect 转换 DTO 后发送成功")
    void sendDirect_normalSuccess() {
        MessageSendDTO dto = new MessageSendDTO();
        dto.setChannel("SMS");
        dto.setTemplateCode("TPL_001");
        dto.setReceiver("13800000001");
        dto.setBizType("ALERT");
        dto.setBizId("BIZ_001");
        dto.setMessageId("MSG_DIRECT_001");
        dto.setParams(Map.of("code", "1234"));

        MsgTemplateDO tpl = buildTemplate();
        when(templateService.loadByCodeAndChannel(any(), any(), any(), any())).thenReturn(tpl);
        when(templateEngine.render(any(), any())).thenReturn("验证码: 1234");
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenReturn("trace");

        MessageResult result = messageService.sendDirect(dto);

        assertTrue(result.isSuccess());
    }

    // ============ batchSend() ============

    @Test
    @DisplayName("边界场景：batchSend requests 为 null 返回空结果")
    void batchSend_nullRequests_returnsEmpty() {
        BatchSendResult result = messageService.batchSend(null, "BATCH_001");
        assertEquals(0, result.getTotal());
        assertEquals(0, result.getSuccess());
    }

    @Test
    @DisplayName("边界场景：batchSend batchId 为空返回空结果")
    void batchSend_emptyBatchId_returnsEmpty() {
        BatchSendResult result = messageService.batchSend(List.of(buildRequest()), "");
        assertEquals(0, result.getTotal());
    }

    @Test
    @DisplayName("正常场景：batchSend 混合结果统计正确")
    void batchSend_mixedResults() {
        MessageRequest req1 = buildRequest();
        req1.setMessageId("MSG_1");
        MessageRequest req2 = buildRequest();
        req2.setMessageId("MSG_2");
        MessageRequest req3 = buildRequest();
        req3.setMessageId("MSG_3");

        MsgTemplateDO tpl = buildTemplate();
        when(templateService.loadByCodeAndChannel(any(), any(), any(), any())).thenReturn(tpl);
        when(templateEngine.render(any(), any())).thenReturn("rendered");
        // req1 dispatch 成功 → success; req2 dispatch 抛异常 → failed; req3 dispatch 返回 null(成功) → success
        when(channelRouter.dispatch(any(MsgLogDO.class)))
                .thenReturn("trace-1")
                .thenThrow(new RuntimeException("fail"))
                .thenReturn(null);

        BatchSendResult result = messageService.batchSend(List.of(req1, req2, req3), "BATCH_MIX");

        assertEquals(3, result.getTotal());
        // req1 → success, req2 → failed(异常被 batchSend try-catch 捕获), req3 → success(dispatch 返回 null 不抛异常)
        assertEquals(2, result.getSuccess());
        assertEquals(1, result.getFailed());
    }

    @Test
    @DisplayName("边界场景：batchSend 超过 100 条限制截断")
    void batchSend_exceedsLimit_truncated() {
        MsgTemplateDO tpl = buildTemplate();
        when(templateService.loadByCodeAndChannel(any(), any(), any(), any())).thenReturn(tpl);
        when(templateEngine.render(any(), any())).thenReturn("rendered");
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenReturn("trace");

        List<MessageRequest> requests = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            MessageRequest req = buildRequest();
            req.setMessageId("MSG_" + i);
            requests.add(req);
        }

        BatchSendResult result = messageService.batchSend(requests, "BATCH_LARGE");

        // 限制为 100 条
        assertEquals(100, result.getTotal());
    }

    @Test
    @DisplayName("边界场景：batchSend 含 null 元素计入 skipped")
    void batchSend_nullElement_skipped() {
        MsgTemplateDO tpl = buildTemplate();
        when(templateService.loadByCodeAndChannel(any(), any(), any(), any())).thenReturn(tpl);
        when(templateEngine.render(any(), any())).thenReturn("rendered");
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenReturn("trace");

        MessageRequest req = buildRequest();
        BatchSendResult result = messageService.batchSend(
                Arrays.asList(req, null, null), "BATCH_NULL");

        assertEquals(3, result.getTotal());
        assertEquals(1, result.getSuccess());
        assertEquals(2, result.getSkipped());
    }

    // ============ sendTransactionally() ============

    @Test
    @DisplayName("异常场景：sendTransactionally request 为 null 抛 BizException")
    void sendTransactionally_nullRequest_throws() {
        BizException ex = assertThrows(BizException.class,
                () -> messageService.sendTransactionally(null));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("正常场景：RocketMQ 未配置降级为同步发送")
    void sendTransactionally_noMq_fallbackToSync() {
        when(mqProducerProvider.getIfAvailable()).thenReturn(null);
        MessageRequest req = buildRequest();
        MsgTemplateDO tpl = buildTemplate();
        when(templateService.loadByCodeAndChannel(any(), any(), any(), any())).thenReturn(tpl);
        when(templateEngine.render(any(), any())).thenReturn("rendered");
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenReturn("sync-trace");

        MessageResult result = messageService.sendTransactionally(req);

        assertTrue(result.isSuccess());
        verify(mqProducer, never()).sendTransactionMessage(any());
    }

    @Test
    @DisplayName("正常场景：RocketMQ 事务消息发送成功")
    void sendTransactionally_mqSuccess() {
        when(mqProducerProvider.getIfAvailable()).thenReturn(mqProducer);
        when(mqProducer.sendTransactionMessage(any())).thenReturn("mq-msg-id");
        MessageRequest req = buildRequest();

        MessageResult result = messageService.sendTransactionally(req);

        assertTrue(result.isSuccess());
        assertEquals("mq-msg-id", result.getProviderTraceId());
        verify(mqProducer).sendTransactionMessage(req);
    }

    @Test
    @DisplayName("异常场景：RocketMQ 发送失败降级为同步发送")
    void sendTransactionally_mqFail_fallbackToSync() {
        when(mqProducerProvider.getIfAvailable()).thenReturn(mqProducer);
        when(mqProducer.sendTransactionMessage(any())).thenThrow(new RuntimeException("MQ 不可用"));
        MessageRequest req = buildRequest();
        MsgTemplateDO tpl = buildTemplate();
        when(templateService.loadByCodeAndChannel(any(), any(), any(), any())).thenReturn(tpl);
        when(templateEngine.render(any(), any())).thenReturn("rendered");
        when(channelRouter.dispatch(any(MsgLogDO.class))).thenReturn("fallback-trace");

        MessageResult result = messageService.sendTransactionally(req);

        assertTrue(result.isSuccess());
        assertEquals("fallback-trace", result.getProviderTraceId());
    }

    // ============ pageLog() ============

    @Test
    @DisplayName("正常场景：pageLog 带过滤条件查询")
    void pageLog_withFilters() {
        MessageLogQueryDTO query = new MessageLogQueryDTO();
        query.setPage(1);
        query.setSize(20);
        query.setChannel("SMS");
        query.setStatus("SUCCESS");
        query.setReceiver("13800000001");

        Page<MsgLogDO> mockPage = new Page<>(1, 20);
        mockPage.setRecords(List.of(new MsgLogDO()));
        when(msgLogMapper.selectPage(any(), any())).thenReturn(mockPage);

        Page<MsgLogDO> result = messageService.pageLog(query);

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        verify(msgLogMapper).selectPage(any(), any());
    }

    @Test
    @DisplayName("边界场景：pageLog size 超过 MAX_SIZE 截断为 200")
    void pageLog_sizeExceedsMax_truncated() {
        MessageLogQueryDTO query = new MessageLogQueryDTO();
        query.setPage(1);
        query.setSize(500);

        Page<MsgLogDO> mockPage = new Page<>(1, 200);
        when(msgLogMapper.selectPage(any(), any())).thenReturn(mockPage);

        Page<MsgLogDO> result = messageService.pageLog(query);

        assertEquals(200, result.getSize());
    }

    @Test
    @DisplayName("边界场景：pageLog query 为 null 使用默认分页")
    void pageLog_nullQuery_defaultPaging() {
        Page<MsgLogDO> mockPage = new Page<>(1, 10);
        when(msgLogMapper.selectPage(any(), any())).thenReturn(mockPage);

        Page<MsgLogDO> result = messageService.pageLog(null);

        assertNotNull(result);
        verify(msgLogMapper).selectPage(any(), any());
    }

    // ============ 辅助方法 ============

    private void verifyNoInteractionsWithCore() {
        verify(channelRouter, never()).dispatch(any(MsgLogDO.class));
        verify(msgLogMapper, never()).insert(any(MsgLogDO.class));
    }
}
