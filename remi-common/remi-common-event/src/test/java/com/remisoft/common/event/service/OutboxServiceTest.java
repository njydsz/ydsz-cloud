package com.remisoft.common.event.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.remisoft.common.event.config.EventProperties;
import com.remisoft.common.event.model.OutboxMessage;
import com.remisoft.common.event.model.OutboxStatus;
import com.remisoft.common.event.repository.OutboxRepository;

/**
 * {@link OutboxService} 单元测试。
 *
 * @author remi-team
 * @since 1.0.0
 */
@DisplayName("OutboxService 测试")
class OutboxServiceTest {

    @Mock
    private OutboxRepository outboxRepository;

    private EventProperties properties;

    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        properties = new EventProperties();
        properties.setMaxRetries(5);
        properties.setMaxPayloadSizeBytes(1024);
        properties.setDefaultPriority(5);
        properties.setDefaultSchemaVersion("v1.0.0");
        properties.setEnableTenantIsolation(false);
        // autoDedup 默认 false：不自动生成 deduplicationId
        outboxService = new OutboxService(outboxRepository, properties, null);
    }

    @Test
    @DisplayName("appendToOutbox 基本写入（autoDedup=false，不生成 deduplicationId）")
    void appendToOutbox_basic() {
        outboxService.appendToOutbox("Order", "order-001", "OrderCreated", "{\"id\":1}");

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(captor.capture());

        OutboxMessage saved = captor.getValue();
        assertEquals("Order", saved.getAggregateType());
        assertEquals("order-001", saved.getAggregateId());
        assertEquals("OrderCreated", saved.getEventType());
        assertEquals("{\"id\":1}", saved.getPayload());
        assertEquals(OutboxStatus.PENDING, saved.getStatus());
        assertEquals(0, saved.getRetryCount());
        assertEquals(5, saved.getMaxRetries());
        assertNotNull(saved.getId());
        assertNull(saved.getDeduplicationId()); // autoDedup=false，不生成
        assertEquals("v1.0.0", saved.getSchemaVersion());
        assertEquals(5, saved.getPriority());
    }

    @Test
    @DisplayName("appendToOutbox 带 headers")
    void appendToOutbox_withHeaders() {
        outboxService.appendToOutbox("Order", "order-001", "OrderCreated",
                "{\"id\":1}", Map.of("source", "api"));

        verify(outboxRepository).save(any(OutboxMessage.class));
    }

    @Test
    @DisplayName("payload 超过大小限制时抛出异常")
    void appendToOutbox_payloadTooLarge() {
        String largePayload = "x".repeat(2048);

        assertThrows(IllegalArgumentException.class, () ->
                outboxService.appendToOutbox("Order", "order-001", "OrderCreated", largePayload));

        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("显式传入 deduplicationId 时进行幂等检查")
    void appendToOutbox_explicitDedup_checked() {
        when(outboxRepository.existsByDeduplicationId("custom-dedup-id")).thenReturn(true);

        outboxService.appendToOutbox("Order", "order-001", "OrderCreated",
                "{\"id\":1}", Map.of(), 5, "v1.0.0", null, "custom-dedup-id");

        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("autoDedup=true 时自动生成 deduplicationId 并检查")
    void appendToOutbox_autoDedup_enabled() {
        properties.setAutoDedup(true);
        when(outboxRepository.existsByDeduplicationId(anyString())).thenReturn(false);

        outboxService.appendToOutbox("Order", "order-001", "OrderCreated", "{\"id\":1}");

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(captor.capture());

        OutboxMessage saved = captor.getValue();
        assertNotNull(saved.getDeduplicationId());
        assertEquals(64, saved.getDeduplicationId().length()); // SHA-256 hex truncated to 32 chars
    }

    @Test
    @DisplayName("autoDedup=true 且重复时跳过写入")
    void appendToOutbox_autoDedup_duplicateSkipped() {
        properties.setAutoDedup(true);
        when(outboxRepository.existsByDeduplicationId(anyString())).thenReturn(true);

        outboxService.appendToOutbox("Order", "order-001", "OrderCreated", "{\"id\":1}");

        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("完整参数写入：优先级、schemaVersion、contentType、deduplicationId")
    void appendToOutbox_fullParams() {
        when(outboxRepository.existsByDeduplicationId("custom-dedup-id")).thenReturn(false);

        outboxService.appendToOutbox("Order", "order-001", "OrderCreated",
                "{\"id\":1}", Map.of("source", "api"),
                9, "v2.0.0", "application/vnd.remi.order.v2+json", "custom-dedup-id");

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(captor.capture());

        OutboxMessage saved = captor.getValue();
        assertEquals(9, saved.getPriority());
        assertEquals("v2.0.0", saved.getSchemaVersion());
        assertEquals("application/vnd.remi.order.v2+json", saved.getContentType());
        assertEquals("custom-dedup-id", saved.getDeduplicationId());
    }

    @Test
    @DisplayName("显式设置优先级 0 时保留 0（不被默认值覆盖）")
    void appendToOutbox_priorityZero_preserved() {
        outboxService.appendToOutbox("Order", "order-001", "OrderCreated",
                "{\"id\":1}", Map.of(), 0, "v1.0.0", null, null);

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxRepository).save(captor.capture());

        OutboxMessage saved = captor.getValue();
        assertEquals(0, saved.getPriority());
    }
}
