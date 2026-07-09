package com.njydsz.pmis.message.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.message.dto.BatchProgressVO;
import com.njydsz.pmis.message.dto.BatchSendRequestDTO;
import com.njydsz.pmis.message.entity.MsgBatchDO;
import com.njydsz.pmis.message.mapper.MsgBatchMapper;
import com.njydsz.pmis.message.service.MessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 批量发送服务单元测试。
 *
 * <p>覆盖 submitBatch 异步/同步提交、getProgress 进度查询、doExecuteBatch 执行流程、
 * 接收人列表展开、最大条数校验、null 计数兜底、进度更新策略等。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("批量发送服务 BatchServiceImpl 单元测试")
class BatchServiceImplTest {

    @Mock
    private MsgBatchMapper msgBatchMapper;

    @Mock
    private MessageService messageService;

    /** 使用 spy 以便在异步提交场景下 stub executeBatchAsync 隔离执行逻辑 */
    private BatchServiceImpl batchService;

    @BeforeEach
    void setUp() {
        batchService = spy(new BatchServiceImpl(msgBatchMapper, messageService));
        TenantContext.setTenantId("1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ============ submitBatch 参数校验 ============

    @Test
    @DisplayName("异常场景：dto 为空抛出 BAD_REQUEST")
    void submitBatch_nullDto_throws() {
        BizException ex = assertThrows(BizException.class, () -> batchService.submitBatch(null));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verifyNoInteractions(msgBatchMapper);
    }

    @Test
    @DisplayName("异常场景：接收人列表为空抛出 BAD_REQUEST")
    void submitBatch_emptyReceivers_throws() {
        BatchSendRequestDTO dto = new BatchSendRequestDTO();
        dto.setReceiverList(Collections.emptyList());
        BizException ex = assertThrows(BizException.class, () -> batchService.submitBatch(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verifyNoInteractions(msgBatchMapper);
    }

    @Test
    @DisplayName("异常场景：接收人列表为 null 抛出 BAD_REQUEST")
    void submitBatch_nullReceivers_throws() {
        BatchSendRequestDTO dto = new BatchSendRequestDTO();
        dto.setReceiverList(null);
        BizException ex = assertThrows(BizException.class, () -> batchService.submitBatch(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("异常场景：超过单批最大 10000 条抛出 BAD_REQUEST")
    void submitBatch_exceedsMax_throws() {
        BatchSendRequestDTO dto = new BatchSendRequestDTO();
        List<String> receivers = new ArrayList<>();
        for (int i = 0; i < 10001; i++) {
            receivers.add("user" + i);
        }
        dto.setReceiverList(receivers);
        dto.setChannel("SMS");
        dto.setTemplateCode("TPL_001");
        BizException ex = assertThrows(BizException.class, () -> batchService.submitBatch(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        verifyNoInteractions(msgBatchMapper);
    }

    // ============ submitBatch 正常路径 ============

    @Test
    @DisplayName("正常场景：异步提交批次使用自定义 batchId，状态 PENDING")
    void submitBatch_async_customBatchId() {
        doNothing().when(batchService).executeBatchAsync(any(), any());

        BatchSendRequestDTO dto = new BatchSendRequestDTO();
        dto.setBatchId("BATCH_001");
        dto.setBatchName("测试批次");
        dto.setChannel("SMS");
        dto.setTemplateCode("TPL_001");
        dto.setBizType("ALERT");
        dto.setSenderId("user-001");
        dto.setAsync(true);
        dto.setReceiverList(List.of("13800000001", "13800000002", " 13800000003 "));
        dto.setParams(Map.of("code", "1234"));

        MsgBatchDO result = batchService.submitBatch(dto);

        assertNotNull(result);
        assertEquals("BATCH_001", result.getBatchId());
        assertEquals("PENDING", result.getStatus());
        assertEquals(3, result.getTotal());
        assertEquals(0, result.getSuccess());
        assertEquals(0, result.getFailed());
        assertEquals(0, result.getSkipped());
        assertEquals("SMS", result.getChannel());
        assertEquals("TPL_001", result.getTemplateCode());
        assertEquals("ALERT", result.getBizType());
        assertEquals("user-001", result.getSenderId());
        assertEquals("1", result.getTenantId());

        ArgumentCaptor<MsgBatchDO> captor = ArgumentCaptor.forClass(MsgBatchDO.class);
        verify(msgBatchMapper).insert(captor.capture());
        MsgBatchDO inserted = captor.getValue();
        assertEquals("BATCH_001", inserted.getBatchId());
        assertEquals("PENDING", inserted.getStatus());

        verify(batchService).executeBatchAsync(eq("BATCH_001"), any());
        // executeBatchSync 为 private 方法，无法直接 verify；通过 verifyNoInteractions(messageService) 间接断言未走同步执行
        verifyNoInteractions(messageService);
    }

    @Test
    @DisplayName("正常场景：batchId 为空时自动生成雪花 ID")
    void submitBatch_async_autoBatchId() {
        doNothing().when(batchService).executeBatchAsync(any(), any());

        BatchSendRequestDTO dto = new BatchSendRequestDTO();
        dto.setChannel("SMS");
        dto.setTemplateCode("TPL_001");
        dto.setReceiverList(List.of("13800000001"));
        dto.setAsync(true);

        MsgBatchDO result = batchService.submitBatch(dto);

        assertNotNull(result.getBatchId());
        assertTrue(StringUtils.hasText(result.getBatchId()));
    }

    @Test
    @DisplayName("正常场景：接收人空白被 trim 并过滤空值")
    void submitBatch_receiversTrimmedAndFiltered() {
        doNothing().when(batchService).executeBatchAsync(any(), any());

        BatchSendRequestDTO dto = new BatchSendRequestDTO();
        dto.setChannel("SMS");
        dto.setTemplateCode("TPL_001");
        dto.setReceiverList(List.of("  13800000001  ", "", "  ", "13800000002"));
        dto.setAsync(true);

        MsgBatchDO result = batchService.submitBatch(dto);

        // 空字符串和纯空白被过滤，仅保留 2 个有效接收人
        assertEquals(2, result.getTotal());
    }

    // ============ submitBatch 同步执行 ============

    @Test
    @DisplayName("正常场景：同步提交批次并执行，所有发送成功")
    void submitBatch_sync_allSuccess() {
        BatchSendRequestDTO dto = new BatchSendRequestDTO();
        dto.setBatchId("BATCH_SYNC_OK");
        dto.setChannel("SMS");
        dto.setTemplateCode("TPL_001");
        dto.setReceiverList(List.of("13800000001", "13800000002"));
        dto.setAsync(false);

        when(messageService.send(any(MessageRequest.class)))
                .thenReturn(MessageResult.ok("SMS", "trace-1"));

        MsgBatchDO result = batchService.submitBatch(dto);

        assertNotNull(result);
        ArgumentCaptor<MsgBatchDO> captor = ArgumentCaptor.forClass(MsgBatchDO.class);
        verify(msgBatchMapper, atLeast(2)).updateById(captor.capture());
        MsgBatchDO lastUpdate = captor.getValue();
        assertEquals("COMPLETED", lastUpdate.getStatus());
        assertEquals(2, lastUpdate.getSuccess());
        assertEquals(0, lastUpdate.getFailed());
        assertEquals(0, lastUpdate.getSkipped());
        assertNotNull(lastUpdate.getCompletedAt());
    }

    @Test
    @DisplayName("正常场景：同步执行每条请求 bizId 自动设置为 batchId")
    void submitBatch_sync_setsBizId() {
        BatchSendRequestDTO dto = new BatchSendRequestDTO();
        dto.setBatchId("BATCH_BIZ");
        dto.setChannel("SMS");
        dto.setTemplateCode("TPL_001");
        dto.setReceiverList(List.of("13800000001"));
        dto.setAsync(false);

        when(messageService.send(any(MessageRequest.class)))
                .thenReturn(MessageResult.ok("SMS", "trace"));

        batchService.submitBatch(dto);

        ArgumentCaptor<MessageRequest> reqCaptor = ArgumentCaptor.forClass(MessageRequest.class);
        verify(messageService).send(reqCaptor.capture());
        assertEquals("BATCH_BIZ", reqCaptor.getValue().getBizId());
        assertEquals("SMS", reqCaptor.getValue().getChannel());
        assertEquals("TPL_001", reqCaptor.getValue().getTemplateCode());
        assertEquals("13800000001", reqCaptor.getValue().getReceiver());
    }

    @Test
    @DisplayName("异常场景：messageService.send 抛异常时计入 failed 不中断批次")
    void submitBatch_sync_sendThrowsCountsFailed() {
        BatchSendRequestDTO dto = new BatchSendRequestDTO();
        dto.setChannel("SMS");
        dto.setTemplateCode("TPL_001");
        dto.setReceiverList(List.of("13800000001", "13800000002"));
        dto.setAsync(false);

        when(messageService.send(any(MessageRequest.class)))
                .thenThrow(new RuntimeException("网络异常"))
                .thenReturn(MessageResult.ok("SMS", "trace-2"));

        batchService.submitBatch(dto);

        ArgumentCaptor<MsgBatchDO> captor = ArgumentCaptor.forClass(MsgBatchDO.class);
        verify(msgBatchMapper, atLeast(2)).updateById(captor.capture());
        MsgBatchDO lastUpdate = captor.getValue();
        assertEquals("COMPLETED", lastUpdate.getStatus());
        assertEquals(1, lastUpdate.getSuccess());
        assertEquals(1, lastUpdate.getFailed());
    }

    @Test
    @DisplayName("边界场景：send 返回 null 或失败结果计入 failed")
    void submitBatch_sync_nullAndFailedResult() {
        BatchSendRequestDTO dto = new BatchSendRequestDTO();
        dto.setChannel("SMS");
        dto.setTemplateCode("TPL_001");
        dto.setReceiverList(List.of("13800000001", "13800000002", "13800000003"));
        dto.setAsync(false);

        when(messageService.send(any(MessageRequest.class)))
                .thenReturn(MessageResult.ok("SMS", "trace-1"))
                .thenReturn(MessageResult.fail("SMS", "限流"))
                .thenReturn(null);

        batchService.submitBatch(dto);

        ArgumentCaptor<MsgBatchDO> captor = ArgumentCaptor.forClass(MsgBatchDO.class);
        verify(msgBatchMapper, atLeast(2)).updateById(captor.capture());
        MsgBatchDO lastUpdate = captor.getValue();
        assertEquals(1, lastUpdate.getSuccess());
        assertEquals(2, lastUpdate.getFailed());
    }

    @Test
    @DisplayName("边界场景：请求列表含 null 元素计入 skipped")
    void submitBatch_sync_nullRequestSkipped() {
        // 通过 executeBatchAsync 直接测试 doExecuteBatch 的 skipped 逻辑
        MsgBatchDO batch = new MsgBatchDO();
        batch.setBatchId("BATCH_NULL_REQ");
        batch.setStatus("PENDING");
        when(msgBatchMapper.selectOne(any())).thenReturn(batch);

        List<MessageRequest> requests = new ArrayList<>();
        requests.add(null);
        requests.add(null);

        batchService.executeBatchAsync("BATCH_NULL_REQ", requests);

        ArgumentCaptor<MsgBatchDO> captor = ArgumentCaptor.forClass(MsgBatchDO.class);
        verify(msgBatchMapper, atLeast(2)).updateById(captor.capture());
        MsgBatchDO lastUpdate = captor.getValue();
        assertEquals("COMPLETED", lastUpdate.getStatus());
        assertEquals(0, lastUpdate.getSuccess());
        assertEquals(0, lastUpdate.getFailed());
        assertEquals(2, lastUpdate.getSkipped());
        verifyNoInteractions(messageService);
    }

    @Test
    @DisplayName("边界场景：每 100 条更新一次进度")
    void submitBatch_sync_updatesEvery100() {
        BatchSendRequestDTO dto = new BatchSendRequestDTO();
        dto.setChannel("SMS");
        dto.setTemplateCode("TPL_001");
        List<String> receivers = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            receivers.add("user" + i);
        }
        dto.setReceiverList(receivers);
        dto.setAsync(false);

        when(messageService.send(any(MessageRequest.class)))
                .thenReturn(MessageResult.ok("SMS", "trace"));

        batchService.submitBatch(dto);

        // updateById 调用次数：1（PROCESSING）+ 1（i=99 第 100 条）+ 1（i=149 末尾）+ 1（COMPLETED）= 4
        verify(msgBatchMapper, atLeast(4)).updateById(any(MsgBatchDO.class));
    }

    // ============ executeBatchAsync / doExecuteBatch 边界 ============

    @Test
    @DisplayName("异常场景：批次不存在时 executeBatchAsync 直接返回不抛异常")
    void executeBatchAsync_batchNotFound() {
        when(msgBatchMapper.selectOne(any())).thenReturn(null);
        List<MessageRequest> requests = List.of(new MessageRequest());

        assertDoesNotThrow(() -> batchService.executeBatchAsync("NOT_EXIST", requests));

        verify(msgBatchMapper, never()).updateById(any(MsgBatchDO.class));
        verifyNoInteractions(messageService);
    }

    @Test
    @DisplayName("正常场景：executeBatchAsync 正常执行将状态置为 COMPLETED")
    void executeBatchAsync_normal() {
        MsgBatchDO batch = new MsgBatchDO();
        batch.setBatchId("BATCH_ASYNC");
        batch.setStatus("PENDING");
        when(msgBatchMapper.selectOne(any())).thenReturn(batch);
        when(messageService.send(any(MessageRequest.class)))
                .thenReturn(MessageResult.ok("SMS", "trace"));

        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        batchService.executeBatchAsync("BATCH_ASYNC", List.of(req));

        ArgumentCaptor<MsgBatchDO> captor = ArgumentCaptor.forClass(MsgBatchDO.class);
        verify(msgBatchMapper, atLeast(2)).updateById(captor.capture());
        // 第一次更新应为 PROCESSING
        assertEquals("PROCESSING", captor.getAllValues().get(0).getStatus());
        assertNotNull(captor.getAllValues().get(0).getStartedAt());
        // 最后一次更新应为 COMPLETED
        MsgBatchDO lastUpdate = captor.getValue();
        assertEquals("COMPLETED", lastUpdate.getStatus());
        assertEquals(1, lastUpdate.getSuccess());
        assertNotNull(lastUpdate.getCompletedAt());
    }

    // ============ executeBatch(batchId) 单参 no-op ============

    @Test
    @DisplayName("兼容场景：executeBatch(batchId) 单参为 no-op 不抛异常")
    void executeBatch_singleArg_noOp() {
        assertDoesNotThrow(() -> batchService.executeBatch("BATCH_001"));
        verifyNoInteractions(msgBatchMapper);
        verifyNoInteractions(messageService);
    }

    // ============ getProgress ============

    @Test
    @DisplayName("异常场景：batchId 为空抛出 BAD_REQUEST")
    void getProgress_emptyBatchId_throws() {
        BizException ex = assertThrows(BizException.class, () -> batchService.getProgress(""));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("异常场景：批次不存在抛出 NOT_FOUND")
    void getProgress_notFound_throws() {
        when(msgBatchMapper.selectOne(any())).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> batchService.getProgress("NOT_EXIST"));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("正常场景：查询进度百分比计算正确")
    void getProgress_normal() {
        MsgBatchDO batch = new MsgBatchDO();
        batch.setBatchId("BATCH_001");
        batch.setBatchName("测试批次");
        batch.setChannel("SMS");
        batch.setTemplateCode("TPL_001");
        batch.setTotal(100);
        batch.setSuccess(60);
        batch.setFailed(20);
        batch.setSkipped(10);
        batch.setStatus("PROCESSING");

        when(msgBatchMapper.selectOne(any())).thenReturn(batch);

        BatchProgressVO vo = batchService.getProgress("BATCH_001");

        assertEquals("BATCH_001", vo.getBatchId());
        assertEquals("测试批次", vo.getBatchName());
        assertEquals("SMS", vo.getChannel());
        assertEquals("TPL_001", vo.getTemplateCode());
        assertEquals(100, vo.getTotal());
        assertEquals(60, vo.getSuccess());
        assertEquals(20, vo.getFailed());
        assertEquals(10, vo.getSkipped());
        assertEquals(90, vo.getProcessed());
        assertEquals(90.0, vo.getProgressPercent(), 0.01);
        assertEquals("PROCESSING", vo.getStatus());
    }

    @Test
    @DisplayName("边界场景：total=0 时 progressPercent=0")
    void getProgress_totalZero() {
        MsgBatchDO batch = new MsgBatchDO();
        batch.setBatchId("BATCH_002");
        batch.setTotal(0);
        batch.setSuccess(0);
        batch.setFailed(0);
        batch.setSkipped(0);
        batch.setStatus("PENDING");

        when(msgBatchMapper.selectOne(any())).thenReturn(batch);

        BatchProgressVO vo = batchService.getProgress("BATCH_002");
        assertEquals(0.0, vo.getProgressPercent(), 0.001);
        assertEquals(0, vo.getProcessed());
    }

    @Test
    @DisplayName("边界场景：total/success/failed/skipped 为 null 时按 0 处理")
    void getProgress_nullCounts() {
        MsgBatchDO batch = new MsgBatchDO();
        batch.setBatchId("BATCH_003");
        batch.setTotal(null);
        batch.setSuccess(null);
        batch.setFailed(null);
        batch.setSkipped(null);
        batch.setStatus("PENDING");

        when(msgBatchMapper.selectOne(any())).thenReturn(batch);

        BatchProgressVO vo = batchService.getProgress("BATCH_003");
        assertEquals(0, vo.getTotal());
        assertEquals(0, vo.getSuccess());
        assertEquals(0, vo.getFailed());
        assertEquals(0, vo.getSkipped());
        assertEquals(0, vo.getProcessed());
        assertEquals(0.0, vo.getProgressPercent(), 0.001);
    }

    @Test
    @DisplayName("边界场景：进度百分比四舍五入到两位小数")
    void getProgress_percentRounding() {
        // 3 个处理完成 1 个：1/3 = 33.33...%
        MsgBatchDO batch = new MsgBatchDO();
        batch.setBatchId("BATCH_ROUND");
        batch.setTotal(3);
        batch.setSuccess(1);
        batch.setFailed(0);
        batch.setSkipped(0);
        batch.setStatus("PROCESSING");

        when(msgBatchMapper.selectOne(any())).thenReturn(batch);

        BatchProgressVO vo = batchService.getProgress("BATCH_ROUND");
        // Math.round(1 * 10000.0 / 3) / 100.0 = Math.round(3333.33) / 100.0 = 3333 / 100.0 = 33.33
        assertEquals(33.33, vo.getProgressPercent(), 0.01);
        assertEquals(1, vo.getProcessed());
    }
}
