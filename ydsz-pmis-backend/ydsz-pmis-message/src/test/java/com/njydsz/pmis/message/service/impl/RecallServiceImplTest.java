package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.message.entity.MsgNotificationDO;
import com.njydsz.pmis.message.enums.RecallStatusEnum;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import com.njydsz.pmis.message.mapper.MsgNotificationMapper;
import com.njydsz.pmis.message.realtime.RealtimePushService;
import com.njydsz.pmis.message.service.MessageLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RecallServiceImpl} 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("RecallServiceImpl 撤回服务测试")
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class RecallServiceImplTest {

    @Mock
    private MsgNotificationMapper msgNotificationMapper;
    @Mock
    private MsgLogMapper msgLogMapper;
    @Mock
    private RealtimePushService realtimePushService;
    @Mock
    private MessageLogService messageLogService;

    @InjectMocks
    private RecallServiceImpl recallService;

    @Test
    @DisplayName("recallNotification 校验归属通过后撤回并推送")
    void recallNotificationShouldSucceedWhenOwner() {
        MsgNotificationDO n = new MsgNotificationDO();
        n.setId("n1");
        n.setReceiverId("u1");
        n.setRecallStatus(RecallStatusEnum.NONE.name());
        when(msgNotificationMapper.selectById("n1")).thenReturn(n);

        boolean ok = recallService.recallNotification("u1", "n1");

        assertEquals(true, ok);
        assertEquals(RecallStatusEnum.RECALLED.name(), n.getRecallStatus());
        verify(msgNotificationMapper).updateById(n);
        verify(realtimePushService).pushToUser(eq("u1"), anyString(), anyString());
    }

    @Test
    @DisplayName("recallNotification 非本人撤回抛 FORBIDDEN")
    void recallNotificationShouldRejectNonOwner() {
        MsgNotificationDO n = new MsgNotificationDO();
        n.setId("n1");
        n.setReceiverId("u1");
        when(msgNotificationMapper.selectById("n1")).thenReturn(n);

        assertThrows(BizException.class, () -> recallService.recallNotification("u2", "n1"));
        verify(msgNotificationMapper, never()).updateById(any(MsgNotificationDO.class));
    }

    @Test
    @DisplayName("recallMessage 委托 MessageLogService.markRecalled")
    void recallMessageShouldDelegate() {
        boolean ok = recallService.recallMessage("log-1");
        assertEquals(true, ok);
        verify(messageLogService).markRecalled("log-1");
    }

    @Test
    @DisplayName("recallBatch 按 bizType+bizId 批量更新")
    void recallBatchShouldUpdateByBiz() {
        when(msgNotificationMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(3);
        when(msgLogMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(2);

        int total = recallService.recallBatch("contract", "c-100");

        assertEquals(5, total);
    }
}
