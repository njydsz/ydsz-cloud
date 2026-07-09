package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.message.channel.ChannelRouter;
import com.njydsz.pmis.message.config.MessageProperties;
import com.njydsz.pmis.message.config.RetryStrategyResolver;
import com.njydsz.pmis.message.dto.MessageLogQueryDTO;
import com.njydsz.pmis.message.entity.MsgLogDO;
import com.njydsz.pmis.message.enums.MessageStatusEnum;
import com.njydsz.pmis.message.enums.RecallStatusEnum;
import com.njydsz.pmis.message.event.DeadLetterAlertEvent;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import com.njydsz.pmis.message.metric.MessageMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 消息日志服务单元测试。
 *
 * <p>覆盖分页查询、状态流转校验（markRetry/markDead/markRecalled）、回执更新（含 TIMEOUT）、
 * 死信重发、死信告警冷却。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("消息日志服务 MessageLogServiceImpl 单元测试")
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

    // ==================== getById ====================

    @Test
    @DisplayName("正常场景：按 ID 查询日志")
    void 按ID查询日志() {
        MsgLogDO entity = new MsgLogDO();
        entity.setId("1");
        entity.setStatus(MessageStatusEnum.SUCCESS.name());
        when(msgLogMapper.selectById("1")).thenReturn(entity);

        MsgLogDO result = messageLogService.getById("1");

        assertEquals("1", result.getId());
    }

    @Test
    @DisplayName("异常场景：ID 为空抛 BizException")
    void id为空抛异常() {
        BizException ex = assertThrows(BizException.class, () -> messageLogService.getById(null));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("异常场景：日志不存在抛 NOT_FOUND")
    void 日志不存在抛异常() {
        when(msgLogMapper.selectById("999")).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> messageLogService.getById("999"));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ==================== page ====================

    @Test
    @DisplayName("正常场景：分页查询日志")
    void 分页查询日志() {
        MessageLogQueryDTO query = new MessageLogQueryDTO();
        query.setPage(1);
        query.setSize(10);
        query.setChannel("SMS");
        query.setStatus("SUCCESS");
        Page<MsgLogDO> mockPage = new Page<>();
        when(msgLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<MsgLogDO> result = messageLogService.page(query);

        assertNotNull(result);
        verify(msgLogMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("边界场景：query 为 null 时使用默认分页")
    void query为null使用默认分页() {
        Page<MsgLogDO> mockPage = new Page<>();
        when(msgLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<MsgLogDO> result = messageLogService.page(null);

        assertNotNull(result);
    }

    @Test
    @DisplayName("边界场景：size 超过 MAX_SIZE 时截断为 200")
    void size超过最大值时截断() {
        MessageLogQueryDTO query = new MessageLogQueryDTO();
        query.setPage(1);
        query.setSize(500);
        Page<MsgLogDO> mockPage = new Page<>();
        when(msgLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        messageLogService.page(query);

        ArgumentCaptor<Page<MsgLogDO>> captor = ArgumentCaptor.forClass(Page.class);
        verify(msgLogMapper).selectPage(captor.capture(), any(LambdaQueryWrapper.class));
        assertEquals(200, captor.getValue().getSize());
    }

    // ==================== markRetry ====================

    @Test
    @DisplayName("正常场景：PENDING → RETRY 状态流转")
    void pendingToRetry状态流转() {
        MsgLogDO entity = new MsgLogDO();
        entity.setId("1");
        entity.setStatus(MessageStatusEnum.PENDING.name());
        entity.setRetryCount(0);
        when(msgLogMapper.selectById("1")).thenReturn(entity);

        LocalDateTime nextRetryAt = LocalDateTime.now().plusMinutes(5);
        messageLogService.markRetry("1", nextRetryAt);

        assertEquals(MessageStatusEnum.RETRY.name(), entity.getStatus());
        assertEquals(nextRetryAt, entity.getNextRetryAt());
        assertEquals(1, entity.getRetryCount());
        verify(msgLogMapper).updateById(entity);
    }

    @Test
    @DisplayName("边界场景：retryCount 为 null 时从 1 开始计数")
    void retryCount为null从1开始() {
        MsgLogDO entity = new MsgLogDO();
        entity.setId("1");
        entity.setStatus(MessageStatusEnum.SENDING.name());
        entity.setRetryCount(null);
        when(msgLogMapper.selectById("1")).thenReturn(entity);

        messageLogService.markRetry("1", LocalDateTime.now().plusMinutes(5));

        assertEquals(1, entity.getRetryCount());
    }

    @Test
    @DisplayName("异常场景：SUCCESS → RETRY 非法流转抛 BizException")
    void successToRetry非法流转() {
        MsgLogDO entity = new MsgLogDO();
        entity.setId("1");
        entity.setStatus(MessageStatusEnum.SUCCESS.name());
        when(msgLogMapper.selectById("1")).thenReturn(entity);

        BizException ex = assertThrows(BizException.class,
                () -> messageLogService.markRetry("1", LocalDateTime.now()));
        assertEquals(BizErrorCode.BIZ_ERROR.getCode(), ex.getCode());
        verify(msgLogMapper, never()).updateById(any(MsgLogDO.class));
    }

    // ==================== markDead ====================

    @Test
    @DisplayName("正常场景：RETRY → DEAD 状态流转")
    void retryToDead状态流转() {
        MsgLogDO entity = new MsgLogDO();
        entity.setId("1");
        entity.setStatus(MessageStatusEnum.RETRY.name());
        entity.setChannel("SMS");
        when(msgLogMapper.selectById("1")).thenReturn(entity);
        // 告警配置关闭
        MessageProperties.DeadLetterAlertConfig alertCfg = new MessageProperties.DeadLetterAlertConfig();
        alertCfg.setEnabled(false);
        lenient().when(messageProperties.getDeadLetterAlert()).thenReturn(alertCfg);

        messageLogService.markDead("1", "发送失败");

        assertEquals(MessageStatusEnum.DEAD.name(), entity.getStatus());
        assertEquals("发送失败", entity.getErrorMessage());
        verify(msgLogMapper).updateById(entity);
    }

    @Test
    @DisplayName("异常场景：SUCCESS → DEAD 非法流转抛 BizException")
    void successToDead非法流转() {
        MsgLogDO entity = new MsgLogDO();
        entity.setId("1");
        entity.setStatus(MessageStatusEnum.SUCCESS.name());
        when(msgLogMapper.selectById("1")).thenReturn(entity);

        BizException ex = assertThrows(BizException.class,
                () -> messageLogService.markDead("1", "err"));
        assertEquals(BizErrorCode.BIZ_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("告警场景：死信数量达到阈值时发布告警事件")
    void 死信数量达到阈值发布告警事件() {
        MsgLogDO entity = new MsgLogDO();
        entity.setId("1");
        entity.setStatus(MessageStatusEnum.RETRY.name());
        entity.setChannel("SMS");
        when(msgLogMapper.selectById("1")).thenReturn(entity);
        MessageProperties.DeadLetterAlertConfig alertCfg = new MessageProperties.DeadLetterAlertConfig();
        alertCfg.setEnabled(true);
        alertCfg.setThreshold(5);
        alertCfg.setWindowMinutes(60);
        alertCfg.setCooldownMinutes(30);
        when(messageProperties.getDeadLetterAlert()).thenReturn(alertCfg);
        // 窗口内死信数量 = 5（达到阈值）
        when(msgLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

        messageLogService.markDead("1", "err");

        verify(eventPublisher).publishEvent(any(DeadLetterAlertEvent.class));
    }

    @Test
    @DisplayName("告警场景：死信数量未达阈值时不发布告警")
    void 死信数量未达阈值不告警() {
        MsgLogDO entity = new MsgLogDO();
        entity.setId("1");
        entity.setStatus(MessageStatusEnum.RETRY.name());
        entity.setChannel("SMS");
        when(msgLogMapper.selectById("1")).thenReturn(entity);
        MessageProperties.DeadLetterAlertConfig alertCfg = new MessageProperties.DeadLetterAlertConfig();
        alertCfg.setEnabled(true);
        alertCfg.setThreshold(10);
        when(messageProperties.getDeadLetterAlert()).thenReturn(alertCfg);
        when(msgLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        messageLogService.markDead("1", "err");

        verify(eventPublisher, never()).publishEvent(any());
    }

    // ==================== updateReceipt（TIMEOUT 状态） ====================

    @Test
    @DisplayName("正常场景：更新回执状态为 TIMEOUT")
    void 更新回执状态为TIMEOUT() {
        MsgLogDO entity = new MsgLogDO();
        entity.setId("1");
        entity.setStatus(MessageStatusEnum.SUCCESS.name());
        entity.setReceiptStatus("NONE");
        when(msgLogMapper.selectById("1")).thenReturn(entity);

        LocalDateTime receiptAt = LocalDateTime.now();
        messageLogService.updateReceipt("1", "TIMEOUT", receiptAt);

        assertEquals("TIMEOUT", entity.getReceiptStatus());
        assertEquals(receiptAt, entity.getReceiptAt());
        verify(msgLogMapper).updateById(entity);
    }

    @Test
    @DisplayName("正常场景：更新回执状态为 DELIVERED")
    void 更新回执状态为DELIVERED() {
        MsgLogDO entity = new MsgLogDO();
        entity.setId("1");
        entity.setReceiptStatus("NONE");
        when(msgLogMapper.selectById("1")).thenReturn(entity);

        messageLogService.updateReceipt("1", "DELIVERED", LocalDateTime.now());

        assertEquals("DELIVERED", entity.getReceiptStatus());
    }

    @Test
    @DisplayName("正常场景：更新回执状态为 READ")
    void 更新回执状态为READ() {
        MsgLogDO entity = new MsgLogDO();
        entity.setId("1");
        entity.setReceiptStatus("DELIVERED");
        when(msgLogMapper.selectById("1")).thenReturn(entity);

        messageLogService.updateReceipt("1", "READ", LocalDateTime.now());

        assertEquals("READ", entity.getReceiptStatus());
    }

    // ==================== markRecalled ====================

    @Test
    @DisplayName("正常场景：SUCCESS → RECALLED 状态流转")
    void successToRecalled状态流转() {
        MsgLogDO entity = new MsgLogDO();
        entity.setId("1");
        entity.setStatus(MessageStatusEnum.SUCCESS.name());
        when(msgLogMapper.selectById("1")).thenReturn(entity);

        messageLogService.markRecalled("1");

        assertEquals(MessageStatusEnum.RECALLED.name(), entity.getStatus());
        assertEquals(RecallStatusEnum.RECALLED.name(), entity.getRecallStatus());
        assertNotNull(entity.getRecallAt());
        verify(msgLogMapper).updateById(entity);
    }

    @Test
    @DisplayName("异常场景：DEAD → RECALLED 非法流转抛 BizException")
    void deadToRecalled非法流转() {
        MsgLogDO entity = new MsgLogDO();
        entity.setId("1");
        entity.setStatus(MessageStatusEnum.DEAD.name());
        when(msgLogMapper.selectById("1")).thenReturn(entity);

        BizException ex = assertThrows(BizException.class, () -> messageLogService.markRecalled("1"));
        assertEquals(BizErrorCode.BIZ_ERROR.getCode(), ex.getCode());
    }

    // ==================== resendDead ====================

    @Test
    @DisplayName("正常场景：死信重发成功")
    void 死信重发成功() {
        MsgLogDO entity = new MsgLogDO();
        entity.setId("1");
        entity.setStatus(MessageStatusEnum.DEAD.name());
        entity.setChannel("SMS");
        entity.setRetryCount(3);
        entity.setTraceId("trace-001");
        when(msgLogMapper.selectById("1")).thenReturn(entity);
        when(channelRouter.dispatch(entity)).thenReturn("provider-001");

        messageLogService.resendDead("1");

        assertEquals(MessageStatusEnum.SUCCESS.name(), entity.getStatus());
        assertEquals("provider-001", entity.getProviderTraceId());
        assertEquals(0, entity.getRetryCount());
        verify(messageMetrics).recordSend(eq("SMS"), eq("SUCCESS"), anyLong());
    }

    @Test
    @DisplayName("降级场景：死信重发失败转 RETRY")
    void 死信重发失败转RETRY() {
        MsgLogDO entity = new MsgLogDO();
        entity.setId("1");
        entity.setStatus(MessageStatusEnum.DEAD.name());
        entity.setChannel("SMS");
        entity.setTraceId("trace-001");
        when(msgLogMapper.selectById("1")).thenReturn(entity);
        when(channelRouter.dispatch(entity)).thenThrow(new RuntimeException("通道不可用"));
        when(retryStrategyResolver.calcNextRetryAt(eq(1), eq("SMS"))).thenReturn(LocalDateTime.now().plusMinutes(5));

        messageLogService.resendDead("1");

        assertEquals(MessageStatusEnum.RETRY.name(), entity.getStatus());
        assertEquals(1, entity.getRetryCount());
        assertNotNull(entity.getNextRetryAt());
        verify(messageMetrics).recordRetry("SMS");
    }

    @Test
    @DisplayName("异常场景：非死信状态重发抛 BizException")
    void 非死信状态重发抛异常() {
        MsgLogDO entity = new MsgLogDO();
        entity.setId("1");
        entity.setStatus(MessageStatusEnum.SUCCESS.name());
        when(msgLogMapper.selectById("1")).thenReturn(entity);

        BizException ex = assertThrows(BizException.class, () -> messageLogService.resendDead("1"));
        assertEquals(BizErrorCode.BIZ_ERROR.getCode(), ex.getCode());
        verify(channelRouter, never()).dispatch(any(MsgLogDO.class));
    }
}
