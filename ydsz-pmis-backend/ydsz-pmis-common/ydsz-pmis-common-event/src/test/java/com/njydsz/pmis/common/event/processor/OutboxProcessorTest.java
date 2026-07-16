package com.njydsz.pmis.common.event.processor;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.njydsz.pmis.common.event.config.EventProperties;
import com.njydsz.pmis.common.event.gateway.EventPublishGateway;
import com.njydsz.pmis.common.event.model.OutboxMessage;
import com.njydsz.pmis.common.event.model.OutboxStatus;
import com.njydsz.pmis.common.event.repository.OutboxRepository;

/**
 * {@link OutboxProcessor} 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("OutboxProcessor 测试")
class OutboxProcessorTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private EventPublishGateway publishGateway;

    private EventProperties properties;

    private OutboxProcessor processor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new EventProperties();
        properties.setBatchSize(10);
        properties.setMaxRetries(3);
        properties.setBaseBackoffSeconds(1);
        properties.setMaxBackoffSeconds(60);
        properties.setStaleProcessingThresholdMinutes(5);
        properties.setWorkerThreads(1); // 同步执行，测试可靠
        when(outboxRepository.countByStatus()).thenReturn(Map.of());
        processor = new OutboxProcessor(outboxRepository, publishGateway, properties, null);
    }

    @Test
    @DisplayName("批量 claim 成功的消息被投递并标记为 SENT")
    void processBatch_batchClaimAndPublishSuccess() throws Exception {
        OutboxMessage msg = buildMessage("msg-1", 0);
        when(outboxRepository.findPending(10)).thenReturn(List.of(msg));
        when(outboxRepository.claimBatchForProcessing(anyList())).thenReturn(1);
        when(publishGateway.publish(msg)).thenReturn(true);

        processor.processBatch();

        verify(outboxRepository).markAsSent("msg-1");
        verify(outboxRepository, never()).markAsFailed(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("批量 claim 返回 0 时不投递")
    void processBatch_batchClaimFailed() throws Exception {
        OutboxMessage msg = buildMessage("msg-1", 0);
        when(outboxRepository.findPending(10)).thenReturn(List.of(msg));
        when(outboxRepository.claimBatchForProcessing(anyList())).thenReturn(0);

        processor.processBatch();

        verify(publishGateway, never()).publish(any());
        verify(outboxRepository, never()).markAsSent(anyString());
    }

    @Test
    @DisplayName("批量 claim 部分成功时降级为逐条 claim")
    void processBatch_partialClaim_fallbackToSingle() throws Exception {
        OutboxMessage msg = buildMessage("msg-1", 0);
        when(outboxRepository.findPending(10)).thenReturn(List.of(msg));
        when(outboxRepository.claimBatchForProcessing(anyList())).thenReturn(0); // batch claim failed
        when(outboxRepository.claimForProcessing("msg-1")).thenReturn(true);
        when(publishGateway.publish(msg)).thenReturn(true);

        processor.processBatch();

        verify(outboxRepository).claimForProcessing("msg-1");
        verify(outboxRepository).markAsSent("msg-1");
    }

    @Test
    @DisplayName("投递失败时调用 markAsFailed 并指数退避")
    void processBatch_publishFailure() throws Exception {
        OutboxMessage msg = buildMessage("msg-1", 0);
        when(outboxRepository.findPending(10)).thenReturn(List.of(msg));
        when(outboxRepository.claimBatchForProcessing(anyList())).thenReturn(1);
        when(publishGateway.publish(msg)).thenReturn(false);

        processor.processBatch();

        verify(outboxRepository).markAsFailed(eq("msg-1"), anyString(), anyLong());
        verify(outboxRepository, never()).markAsSent(anyString());
    }

    @Test
    @DisplayName("投递异常时调用 markAsFailed")
    void processBatch_publishException() throws Exception {
        OutboxMessage msg = buildMessage("msg-1", 0);
        when(outboxRepository.findPending(10)).thenReturn(List.of(msg));
        when(outboxRepository.claimBatchForProcessing(anyList())).thenReturn(1);
        when(publishGateway.publish(msg)).thenThrow(new RuntimeException("MQ down"));

        processor.processBatch();

        verify(outboxRepository).markAsFailed(eq("msg-1"), eq("MQ down"), anyLong());
    }

    @Test
    @DisplayName("空列表时不执行任何操作")
    void processBatch_emptyList() throws Exception {
        when(outboxRepository.findPending(10)).thenReturn(List.of());

        processor.processBatch();

        verify(publishGateway, never()).publish(any());
        verify(outboxRepository, never()).markAsSent(anyString());
    }

    @Test
    @DisplayName("批量投递使用 publishBatch")
    void processBatch_batchPublish() throws Exception {
        OutboxMessage msg1 = buildMessage("msg-1", 0);
        OutboxMessage msg2 = buildMessage("msg-2", 0);
        when(outboxRepository.findPending(10)).thenReturn(List.of(msg1, msg2));
        when(outboxRepository.claimBatchForProcessing(anyList())).thenReturn(2);
        when(publishGateway.publishBatch(anyList())).thenReturn(List.of(true, true));

        processor.processBatch();

        verify(outboxRepository).markAsSent("msg-1");
        verify(outboxRepository).markAsSent("msg-2");
    }

    @Test
    @DisplayName("批量投递失败时降级为逐条投递")
    void processBatch_batchPublishFallback() throws Exception {
        OutboxMessage msg1 = buildMessage("msg-1", 0);
        OutboxMessage msg2 = buildMessage("msg-2", 0);
        when(outboxRepository.findPending(10)).thenReturn(List.of(msg1, msg2));
        when(outboxRepository.claimBatchForProcessing(anyList())).thenReturn(2);
        when(publishGateway.publishBatch(anyList())).thenThrow(new RuntimeException("Batch failed"));
        when(publishGateway.publish(any())).thenReturn(true);

        processor.processBatch();

        verify(publishGateway, atLeast(2)).publish(any());
        verify(outboxRepository).markAsSent("msg-1");
        verify(outboxRepository).markAsSent("msg-2");
    }

    @Test
    @DisplayName("退避计算：baseDelay * 2^retryCount，不超过 maxBackoff")
    void calculateBackoff_withinBounds() throws Exception {
        properties.setBaseBackoffSeconds(10);
        properties.setMaxBackoffSeconds(100);

        // retry 0: 10 * 1 = 10
        OutboxMessage msg0 = buildMessage("msg-0", 0);
        when(outboxRepository.findPending(10)).thenReturn(List.of(msg0));
        when(outboxRepository.claimBatchForProcessing(anyList())).thenReturn(1);
        when(publishGateway.publish(msg0)).thenReturn(false);
        processor.processBatch();
        verify(outboxRepository).markAsFailed(eq("msg-0"), anyString(), eq(10L));
    }

    private OutboxMessage buildMessage(String id, int retryCount) {
        return OutboxMessage.builder()
                .id(id)
                .aggregateType("Order")
                .aggregateId("order-001")
                .eventType("OrderCreated")
                .payload("{\"id\":\"order-001\"}")
                .headers(Map.of())
                .status(OutboxStatus.PENDING)
                .retryCount(retryCount)
                .maxRetries(3)
                .priority(5)
                .nextRetryAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
