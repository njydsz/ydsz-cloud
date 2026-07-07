package com.njydsz.pmis.message.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.message.channel.ChannelRouter;
import com.njydsz.pmis.message.config.MessageProperties;
import com.njydsz.pmis.message.config.RetryStrategyResolver;
import com.njydsz.pmis.message.entity.MsgLogDO;
import com.njydsz.pmis.message.enums.MessageStatusEnum;
import com.njydsz.pmis.message.event.DeadLetterAlertEvent;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import com.njydsz.pmis.message.metric.MessageMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MessageLogServiceImpl} 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("MessageLogServiceImpl 日志状态流转测试")
@ExtendWith(MockitoExtension.class)
class MessageLogServiceImplTest {

    @Mock
    private MsgLogMapper msgLogMapper;

    @Mock
    private ChannelRouter channelRouter;

    @Mock
    private RetryStrategyResolver retryStrategyResolver;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private MessageProperties messageProperties;

    @Mock
    private MessageMetrics messageMetrics;

    @InjectMocks
    private MessageLogServiceImpl messageLogService;

    @Test
    @DisplayName("markRetry 合法流转 SENDING→RETRY")
    void markRetryShouldAllowSendingToRetry() {
        MsgLogDO log = new MsgLogDO();
        log.setId("1");
        log.setStatus(MessageStatusEnum.SENDING.name());
        log.setRetryCount(0);
        when(msgLogMapper.selectById("1")).thenReturn(log);

        messageLogService.markRetry("1", LocalDateTime.now().plusMinutes(1));

        assertEquals(MessageStatusEnum.RETRY.name(), log.getStatus());
        assertEquals(1, log.getRetryCount());
        verify(msgLogMapper).updateById(log);
    }

    @Test
    @DisplayName("markRetry 非法流转 SUCCESS→RETRY 抛异常")
    void markRetryShouldRejectSuccessToRetry() {
        MsgLogDO log = new MsgLogDO();
        log.setId("1");
        log.setStatus(MessageStatusEnum.SUCCESS.name());
        when(msgLogMapper.selectById("1")).thenReturn(log);

        assertThrows(BizException.class, () -> messageLogService.markRetry("1", LocalDateTime.now()));
        verify(msgLogMapper, never()).updateById(any(MsgLogDO.class));
    }

    @Test
    @DisplayName("markDead 合法流转 RETRY→DEAD")
    void markDeadShouldAllowRetryToDead() {
        MsgLogDO log = new MsgLogDO();
        log.setId("1");
        log.setStatus(MessageStatusEnum.RETRY.name());
        log.setChannel("SMS");
        when(msgLogMapper.selectById("1")).thenReturn(log);

        messageLogService.markDead("1", "retry exhausted");

        assertEquals(MessageStatusEnum.DEAD.name(), log.getStatus());
        assertEquals("retry exhausted", log.getErrorMessage());
    }

    @Test
    @DisplayName("markDead 非法流转 SUCCESS→DEAD 抛异常")
    void markDeadShouldRejectSuccessToDead() {
        MsgLogDO log = new MsgLogDO();
        log.setId("1");
        log.setStatus(MessageStatusEnum.SUCCESS.name());
        when(msgLogMapper.selectById("1")).thenReturn(log);

        assertThrows(BizException.class, () -> messageLogService.markDead("1", "err"));
    }

    @Test
    @DisplayName("markRecalled 合法流转 SUCCESS→RECALLED")
    void markRecalledShouldAllowSuccessToRecalled() {
        MsgLogDO log = new MsgLogDO();
        log.setId("1");
        log.setStatus(MessageStatusEnum.SUCCESS.name());
        when(msgLogMapper.selectById("1")).thenReturn(log);

        messageLogService.markRecalled("1");

        assertEquals(MessageStatusEnum.RECALLED.name(), log.getStatus());
    }

    @Test
    @DisplayName("markRecalled 非法流转 FAILED→RECALLED 抛异常")
    void markRecalledShouldRejectFailedToRecalled() {
        MsgLogDO log = new MsgLogDO();
        log.setId("1");
        log.setStatus(MessageStatusEnum.FAILED.name());
        when(msgLogMapper.selectById("1")).thenReturn(log);

        assertThrows(BizException.class, () -> messageLogService.markRecalled("1"));
    }

    @Test
    @DisplayName("updateReceipt 更新回执状态")
    void updateReceiptShouldSetReceiptFields() {
        MsgLogDO log = new MsgLogDO();
        log.setId("1");
        when(msgLogMapper.selectById("1")).thenReturn(log);

        LocalDateTime now = LocalDateTime.now();
        messageLogService.updateReceipt("1", "DELIVERED", now);

        assertEquals("DELIVERED", log.getReceiptStatus());
        assertEquals(now, log.getReceiptAt());
    }

    // ==================== P1-4: 死信告警测试 ====================

    @Test
    @DisplayName("markDead 达到阈值触发告警事件")
    void markDeadShouldFireAlertWhenThresholdReached() {
        MsgLogDO log = new MsgLogDO();
        log.setId("1");
        log.setStatus(MessageStatusEnum.RETRY.name());
        log.setChannel("SMS");
        when(msgLogMapper.selectById("1")).thenReturn(log);
        MessageProperties.DeadLetterAlertConfig cfg = new MessageProperties.DeadLetterAlertConfig();
        cfg.setEnabled(true);
        cfg.setThreshold(2);
        cfg.setWindowMinutes(60);
        cfg.setCooldownMinutes(30);
        when(messageProperties.getDeadLetterAlert()).thenReturn(cfg);
        when(msgLogMapper.selectCount(any())).thenReturn(2L);

        messageLogService.markDead("1", "err");

        verify(eventPublisher).publishEvent(any(DeadLetterAlertEvent.class));
    }

    @Test
    @DisplayName("markDead 未达阈值不触发告警")
    void markDeadShouldNotFireAlertWhenBelowThreshold() {
        MsgLogDO log = new MsgLogDO();
        log.setId("1");
        log.setStatus(MessageStatusEnum.RETRY.name());
        log.setChannel("SMS");
        when(msgLogMapper.selectById("1")).thenReturn(log);
        MessageProperties.DeadLetterAlertConfig cfg = new MessageProperties.DeadLetterAlertConfig();
        cfg.setEnabled(true);
        cfg.setThreshold(10);
        when(messageProperties.getDeadLetterAlert()).thenReturn(cfg);
        when(msgLogMapper.selectCount(any())).thenReturn(5L);

        messageLogService.markDead("1", "err");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("markDead 告警关闭时不查询不告警")
    void markDeadShouldSkipAlertWhenDisabled() {
        MsgLogDO log = new MsgLogDO();
        log.setId("1");
        log.setStatus(MessageStatusEnum.RETRY.name());
        log.setChannel("SMS");
        when(msgLogMapper.selectById("1")).thenReturn(log);
        MessageProperties.DeadLetterAlertConfig cfg = new MessageProperties.DeadLetterAlertConfig();
        cfg.setEnabled(false);
        when(messageProperties.getDeadLetterAlert()).thenReturn(cfg);

        messageLogService.markDead("1", "err");

        verify(msgLogMapper, never()).selectCount(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("markDead 冷却期内不重复告警")
    void markDeadShouldNotFireAlertWithinCooldown() {
        MsgLogDO log1 = new MsgLogDO();
        log1.setId("1");
        log1.setStatus(MessageStatusEnum.RETRY.name());
        log1.setChannel("SMS");
        MsgLogDO log2 = new MsgLogDO();
        log2.setId("2");
        log2.setStatus(MessageStatusEnum.RETRY.name());
        log2.setChannel("SMS");
        when(msgLogMapper.selectById("1")).thenReturn(log1);
        when(msgLogMapper.selectById("2")).thenReturn(log2);
        MessageProperties.DeadLetterAlertConfig cfg = new MessageProperties.DeadLetterAlertConfig();
        cfg.setEnabled(true);
        cfg.setThreshold(1);
        cfg.setWindowMinutes(60);
        cfg.setCooldownMinutes(30);
        when(messageProperties.getDeadLetterAlert()).thenReturn(cfg);
        when(msgLogMapper.selectCount(any())).thenReturn(5L);

        messageLogService.markDead("1", "err1");
        messageLogService.markDead("2", "err2");

        verify(eventPublisher, times(1)).publishEvent(any(DeadLetterAlertEvent.class));
    }

    // ==================== P1-4: 死信手动重发测试 ====================

    @Test
    @DisplayName("resendDead 成功: DEAD→SENDING→SUCCESS")
    void resendDeadShouldSucceed() {
        MsgLogDO log = new MsgLogDO();
        log.setId("1");
        log.setStatus(MessageStatusEnum.DEAD.name());
        log.setChannel("SMS");
        log.setRetryCount(3);
        log.setTraceId("trace-1");
        when(msgLogMapper.selectById("1")).thenReturn(log);
        when(channelRouter.dispatch(log)).thenReturn("provider-trace-1");

        messageLogService.resendDead("1");

        assertEquals(MessageStatusEnum.SUCCESS.name(), log.getStatus());
        assertEquals("provider-trace-1", log.getProviderTraceId());
        assertEquals(0, log.getRetryCount());
        assertNull(log.getNextRetryAt());
        assertNull(log.getErrorMessage());
        verify(channelRouter).dispatch(log);
        verify(messageMetrics).recordSend(eq("SMS"), eq("SUCCESS"), anyLong());
        verify(msgLogMapper, times(2)).updateById(log);
    }

    @Test
    @DisplayName("resendDead 失败: DEAD→SENDING→RETRY")
    void resendDeadShouldTransitToRetryOnFailure() {
        MsgLogDO log = new MsgLogDO();
        log.setId("1");
        log.setStatus(MessageStatusEnum.DEAD.name());
        log.setChannel("SMS");
        log.setRetryCount(3);
        when(msgLogMapper.selectById("1")).thenReturn(log);
        when(channelRouter.dispatch(log)).thenThrow(new RuntimeException("channel down"));
        LocalDateTime nextRetryAt = LocalDateTime.now().plusMinutes(2);
        when(retryStrategyResolver.calcNextRetryAt(1, "SMS")).thenReturn(nextRetryAt);

        messageLogService.resendDead("1");

        assertEquals(MessageStatusEnum.RETRY.name(), log.getStatus());
        assertEquals(1, log.getRetryCount());
        assertEquals(nextRetryAt, log.getNextRetryAt());
        assertEquals("channel down", log.getErrorMessage());
        verify(messageMetrics).recordRetry("SMS");
        verify(msgLogMapper, times(2)).updateById(log);
    }

    @Test
    @DisplayName("resendDead 非 DEAD 状态抛异常")
    void resendDeadShouldRejectNonDeadState() {
        MsgLogDO log = new MsgLogDO();
        log.setId("1");
        log.setStatus(MessageStatusEnum.SUCCESS.name());
        when(msgLogMapper.selectById("1")).thenReturn(log);

        assertThrows(BizException.class, () -> messageLogService.resendDead("1"));
        verify(channelRouter, never()).dispatch(any(MsgLogDO.class));
    }

    @Test
    @DisplayName("resendDead 重置 errorMessage 与 nextRetryAt")
    void resendDeadShouldResetRetryContext() {
        MsgLogDO log = new MsgLogDO();
        log.setId("1");
        log.setStatus(MessageStatusEnum.DEAD.name());
        log.setChannel("EMAIL");
        log.setRetryCount(3);
        log.setErrorMessage("old error");
        log.setNextRetryAt(LocalDateTime.now().plusMinutes(5));
        when(msgLogMapper.selectById("1")).thenReturn(log);
        when(channelRouter.dispatch(log)).thenReturn("prov-1");

        messageLogService.resendDead("1");

        // 成功后 errorMessage/nextRetryAt 应被清空
        assertEquals(MessageStatusEnum.SUCCESS.name(), log.getStatus());
        assertNull(log.getErrorMessage());
        assertNull(log.getNextRetryAt());
        assertEquals(0, log.getRetryCount());
    }
}
