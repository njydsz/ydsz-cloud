package com.njydsz.pmis.message.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.message.entity.MsgLogDO;
import com.njydsz.pmis.message.enums.MessageStatusEnum;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
}
