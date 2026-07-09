package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.message.dto.ReceiptCallbackDTO;
import com.njydsz.pmis.message.entity.MsgReceiptDO;
import com.njydsz.pmis.message.mapper.MsgReceiptMapper;
import com.njydsz.pmis.message.service.MessageLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回执服务单元测试。
 *
 * <p>覆盖回执回调落库、回执与日志联动更新（含 TIMEOUT 状态转换）、日志异常降级、按 logId 查询。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("回执服务 ReceiptServiceImpl 单元测试")
class ReceiptServiceImplTest {

    @Mock
    private MsgReceiptMapper msgReceiptMapper;

    @Mock
    private MessageLogService messageLogService;

    @InjectMocks
    private ReceiptServiceImpl receiptService;

    @Test
    @DisplayName("正常场景：DELIVERED 回执回调落库并联动更新日志")
    void delivered回执回调落库() {
        ReceiptCallbackDTO dto = new ReceiptCallbackDTO();
        dto.setLogId("log-001");
        dto.setProviderTraceId("pt-001");
        dto.setReceiptType("DELIVERED");
        dto.setProviderCode("aliyun");
        dto.setProviderMsg("OK");
        dto.setRawResponse("{\"code\":\"OK\"}");

        receiptService.callback(dto);

        ArgumentCaptor<MsgReceiptDO> captor = ArgumentCaptor.forClass(MsgReceiptDO.class);
        verify(msgReceiptMapper).insert(captor.capture());
        MsgReceiptDO saved = captor.getValue();
        assertEquals("log-001", saved.getLogId());
        assertEquals("pt-001", saved.getProviderTraceId());
        assertEquals("DELIVERED", saved.getReceiptType());
        assertEquals("aliyun", saved.getProviderCode());
        assertNotNull(saved.getReceiptTime());
        // 联动更新日志回执状态
        verify(messageLogService).updateReceipt(eq("log-001"), eq("DELIVERED"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("TIMEOUT 状态转换：回执类型为 TIMEOUT 时联动更新日志回执状态")
    void timeout回执状态转换() {
        ReceiptCallbackDTO dto = new ReceiptCallbackDTO();
        dto.setLogId("log-002");
        dto.setReceiptType("TIMEOUT");
        dto.setProviderCode("aliyun");

        receiptService.callback(dto);

        // 验证 TIMEOUT 状态被透传到 messageLogService.updateReceipt
        verify(messageLogService).updateReceipt(eq("log-002"), eq("TIMEOUT"), any(LocalDateTime.class));
        verify(msgReceiptMapper).insert(any(MsgReceiptDO.class));
    }

    @Test
    @DisplayName("正常场景：READ 回执回调")
    void read回执回调() {
        ReceiptCallbackDTO dto = new ReceiptCallbackDTO();
        dto.setLogId("log-003");
        dto.setReceiptType("READ");

        receiptService.callback(dto);

        verify(messageLogService).updateReceipt(eq("log-003"), eq("READ"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("正常场景：CLICKED 回执回调")
    void clicked回执回调() {
        ReceiptCallbackDTO dto = new ReceiptCallbackDTO();
        dto.setLogId("log-004");
        dto.setReceiptType("CLICKED");

        receiptService.callback(dto);

        verify(messageLogService).updateReceipt(eq("log-004"), eq("CLICKED"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("正常场景：FAILED 回执回调")
    void failed回执回调() {
        ReceiptCallbackDTO dto = new ReceiptCallbackDTO();
        dto.setLogId("log-005");
        dto.setReceiptType("FAILED");
        dto.setProviderMsg("号码不存在");

        receiptService.callback(dto);

        verify(messageLogService).updateReceipt(eq("log-005"), eq("FAILED"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("异常场景：dto 为 null 抛 BizException")
    void dto为null抛异常() {
        BizException ex = assertThrows(BizException.class, () -> receiptService.callback(null));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verify(msgReceiptMapper, never()).insert(any(MsgReceiptDO.class));
    }

    @Test
    @DisplayName("异常场景：logId 为空抛 BizException")
    void logId为空抛异常() {
        ReceiptCallbackDTO dto = new ReceiptCallbackDTO();
        dto.setReceiptType("DELIVERED");

        BizException ex = assertThrows(BizException.class, () -> receiptService.callback(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("降级场景：messageLogService 更新异常时不影响回执落库")
    void messageLogService异常不影响回执落库() {
        ReceiptCallbackDTO dto = new ReceiptCallbackDTO();
        dto.setLogId("log-006");
        dto.setReceiptType("DELIVERED");
        // 日志不存在时 messageLogService 抛异常
        doThrow(new BizException(BizErrorCode.NOT_FOUND, "日志不存在"))
                .when(messageLogService).updateReceipt(eq("log-006"), eq("DELIVERED"), any(LocalDateTime.class));

        // 不应抛异常，回执落库仍成功
        receiptService.callback(dto);

        verify(msgReceiptMapper).insert(any(MsgReceiptDO.class));
    }

    // ==================== listByLogId ====================

    @Test
    @DisplayName("正常场景：按 logId 查询回执列表")
    void 按logId查询回执列表() {
        MsgReceiptDO r1 = new MsgReceiptDO();
        r1.setLogId("log-001");
        r1.setReceiptType("DELIVERED");
        MsgReceiptDO r2 = new MsgReceiptDO();
        r2.setLogId("log-001");
        r2.setReceiptType("READ");
        when(msgReceiptMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(r1, r2));

        List<MsgReceiptDO> result = receiptService.listByLogId("log-001");

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("边界场景：logId 为空时返回空列表")
    void logId为空返回空列表() {
        List<MsgReceiptDO> result = receiptService.listByLogId(null);

        assertTrue(result.isEmpty());
        verify(msgReceiptMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("边界场景：logId 为空白时返回空列表")
    void logId为空白返回空列表() {
        List<MsgReceiptDO> result = receiptService.listByLogId("");

        assertTrue(result.isEmpty());
    }
}
