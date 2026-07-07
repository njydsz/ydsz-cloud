package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.workflow.entity.FlowThirdPartyLogDO;
import com.njydsz.pmis.workflow.mapper.FlowThirdPartyLogMapper;
import com.njydsz.pmis.workflow.service.FlowThirdPartyLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowThirdPartyLogServiceImpl 单元测试
 *
 * <p>P0-2: 三方审批回调日志状态流转测试。
 * 覆盖 PENDING → SUCCESS/FAIL 状态流转，以及异常容错场景。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("三方审批回调日志状态流转")
class FlowThirdPartyLogServiceImplTest {

    @Mock
    private FlowThirdPartyLogMapper logMapper;

    @InjectMocks
    private FlowThirdPartyLogServiceImpl logService;

    private static final Long LOG_ID = 1001L;

    // ==================== savePending ====================

    @Test
    @DisplayName("savePending - 正常落库，设置 PENDING 状态 + createdAt + 返回 ID")
    void savePendingShouldSetStatusAndReturnId() {
        FlowThirdPartyLogDO logEntry = new FlowThirdPartyLogDO();
        logEntry.setPlatform("DINGTALK");
        logEntry.setEventType("bpmsTaskChange");
        logEntry.setCallbackData("{}");
        // 模拟 MyBatis-Plus 回填主键
        when(logMapper.insert(any(FlowThirdPartyLogDO.class))).thenAnswer(invocation -> {
            ((FlowThirdPartyLogDO) invocation.getArgument(0)).setId(LOG_ID);
            return 1;
        });

        Long id = logService.savePending(logEntry);

        assertThat(id).isEqualTo(LOG_ID);

        ArgumentCaptor<FlowThirdPartyLogDO> captor = ArgumentCaptor.forClass(FlowThirdPartyLogDO.class);
        verify(logMapper).insert(captor.capture());
        FlowThirdPartyLogDO inserted = captor.getValue();
        assertThat(inserted.getHandleStatus()).isEqualTo(FlowThirdPartyLogService.STATUS_PENDING);
        assertThat(inserted.getCreatedAt()).isNotNull();
        assertThat(inserted.getPlatform()).isEqualTo("DINGTALK");
        assertThat(inserted.getEventType()).isEqualTo("bpmsTaskChange");
    }

    @Test
    @DisplayName("savePending - 已有 createdAt 时不覆盖")
    void savePendingShouldNotOverwriteExistingCreatedAt() {
        LocalDateTime fixed = LocalDateTime.of(2026, 1, 1, 10, 0);
        FlowThirdPartyLogDO logEntry = new FlowThirdPartyLogDO();
        logEntry.setPlatform("FEISHU");
        logEntry.setEventType("approval.approved");
        logEntry.setCreatedAt(fixed);

        logService.savePending(logEntry);

        ArgumentCaptor<FlowThirdPartyLogDO> captor = ArgumentCaptor.forClass(FlowThirdPartyLogDO.class);
        verify(logMapper).insert(captor.capture());
        // 已有 createdAt 应被保留
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(fixed);
    }

    @Test
    @DisplayName("savePending - logEntry 为 null 时返回 null，不调用 insert")
    void savePendingWithNullShouldReturnNull() {
        Long id = logService.savePending(null);

        assertThat(id).isNull();
        verify(logMapper, never()).insert(any(FlowThirdPartyLogDO.class));
    }

    @Test
    @DisplayName("savePending - 落库异常时返回 null（不抛出，不阻塞主流程）")
    void savePendingShouldSwallowExceptionAndReturnNull() {
        FlowThirdPartyLogDO logEntry = new FlowThirdPartyLogDO();
        logEntry.setPlatform("WECOM");
        logEntry.setEventType("sys_approval_change");
        when(logMapper.insert(any(FlowThirdPartyLogDO.class)))
                .thenThrow(new RuntimeException("DB connection lost"));

        Long id = logService.savePending(logEntry);

        assertThat(id).isNull();
    }

    // ==================== updateSuccess ====================

    @Test
    @DisplayName("updateSuccess - 调用 updateStatus(id, SUCCESS, null)")
    void updateSuccessShouldCallUpdateStatusWithSuccess() {
        logService.updateSuccess(LOG_ID);

        verify(logMapper).updateStatus(eq(LOG_ID), eq(FlowThirdPartyLogService.STATUS_SUCCESS), isNull());
    }

    @Test
    @DisplayName("updateSuccess - id 为 null 时不调用 updateStatus")
    void updateSuccessWithNullIdShouldBeNoOp() {
        logService.updateSuccess(null);

        verify(logMapper, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("updateSuccess - 异常时吞掉不抛出")
    void updateSuccessShouldSwallowException() {
        doThrow(new RuntimeException("DB error"))
                .when(logMapper).updateStatus(eq(LOG_ID), any(), any());

        // 不应抛出异常
        logService.updateSuccess(LOG_ID);

        verify(logMapper).updateStatus(eq(LOG_ID), eq(FlowThirdPartyLogService.STATUS_SUCCESS), isNull());
    }

    // ==================== updateFailed ====================

    @Test
    @DisplayName("updateFailed - 调用 updateStatus(id, FAIL, errorMsg)")
    void updateFailedShouldCallUpdateStatusWithFail() {
        logService.updateFailed(LOG_ID, "account not mapped");

        verify(logMapper).updateStatus(eq(LOG_ID), eq(FlowThirdPartyLogService.STATUS_FAIL), eq("account not mapped"));
    }

    @Test
    @DisplayName("updateFailed - id 为 null 时不调用 updateStatus")
    void updateFailedWithNullIdShouldBeNoOp() {
        logService.updateFailed(null, "error");

        verify(logMapper, never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("updateFailed - errorMsg 超长时截断到 512 字符")
    void updateFailedShouldTruncateLongErrorMsg() {
        String longMsg = "x".repeat(600);

        logService.updateFailed(LOG_ID, longMsg);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(logMapper).updateStatus(eq(LOG_ID), eq(FlowThirdPartyLogService.STATUS_FAIL), msgCaptor.capture());
        assertThat(msgCaptor.getValue()).hasSize(512);
    }

    @Test
    @DisplayName("updateFailed - errorMsg 为 null 时正常调用")
    void updateFailedWithNullMsgShouldStillCallUpdateStatus() {
        logService.updateFailed(LOG_ID, null);

        verify(logMapper).updateStatus(eq(LOG_ID), eq(FlowThirdPartyLogService.STATUS_FAIL), isNull());
    }

    @Test
    @DisplayName("updateFailed - 异常时吞掉不抛出")
    void updateFailedShouldSwallowException() {
        doThrow(new RuntimeException("DB error"))
                .when(logMapper).updateStatus(eq(LOG_ID), any(), any());

        // 不应抛出异常
        logService.updateFailed(LOG_ID, "some error");

        verify(logMapper).updateStatus(eq(LOG_ID), eq(FlowThirdPartyLogService.STATUS_FAIL), eq("some error"));
    }

    // ==================== 状态常量 ====================

    @Test
    @DisplayName("状态常量值正确")
    void statusConstantsShouldBeCorrect() {
        assertThat(FlowThirdPartyLogService.STATUS_PENDING).isEqualTo("PENDING");
        assertThat(FlowThirdPartyLogService.STATUS_SUCCESS).isEqualTo("SUCCESS");
        assertThat(FlowThirdPartyLogService.STATUS_FAIL).isEqualTo("FAIL");
    }
}
