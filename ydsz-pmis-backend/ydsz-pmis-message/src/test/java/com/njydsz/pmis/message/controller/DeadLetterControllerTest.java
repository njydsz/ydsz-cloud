package com.njydsz.pmis.message.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.message.dto.MessageLogQueryDTO;
import com.njydsz.pmis.message.entity.MsgLogDO;
import com.njydsz.pmis.message.enums.MessageStatusEnum;
import com.njydsz.pmis.message.service.MessageLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DeadLetterController} 单元测试（P1-4）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DeadLetterController 死信管理测试")
@ExtendWith(MockitoExtension.class)
class DeadLetterControllerTest {

    @Mock
    private MessageLogService messageLogService;

    @InjectMocks
    private DeadLetterController deadLetterController;

    @Test
    @DisplayName("page 强制 status=DEAD 并委托 service")
    void pageShouldForceDeadStatusAndDelegate() {
        MessageLogQueryDTO query = new MessageLogQueryDTO();
        query.setChannel("SMS");
        Page<MsgLogDO> mockPage = new Page<>();
        when(messageLogService.page(any(MessageLogQueryDTO.class))).thenReturn(mockPage);

        Result<Page<MsgLogDO>> result = deadLetterController.page(query);

        assertEquals(MessageStatusEnum.DEAD.name(), query.getStatus());
        assertTrue(result.isSuccess());
        assertEquals(mockPage, result.getData());
        verify(messageLogService).page(query);
    }

    @Test
    @DisplayName("page 入参为 null 时初始化并强制 DEAD")
    void pageShouldInitQueryWhenNull() {
        Page<MsgLogDO> mockPage = new Page<>();
        when(messageLogService.page(any(MessageLogQueryDTO.class))).thenReturn(mockPage);

        Result<Page<MsgLogDO>> result = deadLetterController.page(null);

        assertTrue(result.isSuccess());
        verify(messageLogService).page(any(MessageLogQueryDTO.class));
    }

    @Test
    @DisplayName("resend 合法 logId 委托 service 并返回成功")
    void resendShouldDelegateToService() {
        Result<Void> result = deadLetterController.resend("log-1");

        assertTrue(result.isSuccess());
        verify(messageLogService).resendDead("log-1");
    }

    @Test
    @DisplayName("resend 空 logId 返回参数错误")
    void resendShouldReturnBadRequestWhenBlank() {
        Result<Void> result = deadLetterController.resend("");

        assertNotNull(result);
        assertTrue(!result.isSuccess());
        verify(messageLogService, org.mockito.Mockito.never()).resendDead(any());
    }

    @Test
    @DisplayName("resend null logId 返回参数错误")
    void resendShouldReturnBadRequestWhenNull() {
        Result<Void> result = deadLetterController.resend(null);

        assertNotNull(result);
        assertTrue(!result.isSuccess());
        verify(messageLogService, org.mockito.Mockito.never()).resendDead(any());
    }
}
