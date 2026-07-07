package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.message.dto.ReceiptCallbackDTO;
import com.njydsz.pmis.message.entity.MsgReceiptDO;
import com.njydsz.pmis.message.mapper.MsgReceiptMapper;
import com.njydsz.pmis.message.service.MessageLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReceiptServiceImpl} 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ReceiptServiceImpl 回执服务测试")
@ExtendWith(MockitoExtension.class)
class ReceiptServiceImplTest {

    @Mock
    private MsgReceiptMapper msgReceiptMapper;

    @Mock
    private MessageLogService messageLogService;

    @InjectMocks
    private ReceiptServiceImpl receiptService;

    @Test
    @DisplayName("callback 落库并联动更新日志回执")
    void callbackShouldInsertAndUpdateLog() {
        ReceiptCallbackDTO dto = new ReceiptCallbackDTO();
        dto.setLogId("log-1");
        dto.setReceiptType("DELIVERED");

        receiptService.callback(dto);

        verify(msgReceiptMapper).insert(any(MsgReceiptDO.class));
        verify(messageLogService).updateReceipt(eq("log-1"), eq("DELIVERED"), any());
    }

    @Test
    @DisplayName("listByLogId 返回回执列表")
    void listByLogIdShouldReturnList() {
        MsgReceiptDO r = new MsgReceiptDO();
        r.setLogId("log-1");
        when(msgReceiptMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(r));

        List<MsgReceiptDO> result = receiptService.listByLogId("log-1");
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("listByLogId 空 logId 返回空列表")
    void listByLogIdShouldReturnEmptyWhenBlank() {
        List<MsgReceiptDO> result = receiptService.listByLogId("");
        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("callback 日志不存在时仅记录不影响落库")
    void callbackShouldNotFailWhenLogMissing() {
        ReceiptCallbackDTO dto = new ReceiptCallbackDTO();
        dto.setLogId("log-x");
        dto.setReceiptType("READ");
        org.mockito.Mockito.doThrow(new com.njydsz.pmis.common.exception.BizException("not found"))
                .when(messageLogService).updateReceipt(anyString(), anyString(), any());

        receiptService.callback(dto); // 不应抛出
        verify(msgReceiptMapper).insert(any(MsgReceiptDO.class));
    }
}
