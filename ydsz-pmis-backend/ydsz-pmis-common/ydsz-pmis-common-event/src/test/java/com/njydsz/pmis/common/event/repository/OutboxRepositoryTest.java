package com.njydsz.pmis.common.event.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import com.njydsz.pmis.common.event.model.DatabaseDialect;
import com.njydsz.pmis.common.event.model.OutboxMessage;
import com.njydsz.pmis.common.event.model.OutboxStatus;

/**
 * {@link OutboxRepository} 集成测试（H2 内存数据库）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("OutboxRepository 集成测试")
class OutboxRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private OutboxRepository repository;
    private javax.sql.DataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema-test.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new OutboxRepository(jdbcTemplate, "pmis_outbox", DatabaseDialect.UNKNOWN);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (dataSource instanceof org.springframework.jdbc.datasource.embedded.EmbeddedDatabase db) {
            db.shutdown();
        }
    }

    @Test
    @DisplayName("save + findPending 基本流程")
    void saveAndFindPending() {
        OutboxMessage msg = buildMessage("msg-1", OutboxStatus.PENDING, 0);
        repository.save(msg);

        List<OutboxMessage> pending = repository.findPending(10);
        assertEquals(1, pending.size());
        assertEquals("msg-1", pending.get(0).getId());
        assertEquals("OrderCreated", pending.get(0).getEventType());
    }

    @Test
    @DisplayName("claimForProcessing 原子抢占")
    void claimForProcessing_atomicClaim() {
        OutboxMessage msg = buildMessage("msg-1", OutboxStatus.PENDING, 0);
        repository.save(msg);

        assertTrue(repository.claimForProcessing("msg-1"));
        // 第二次 claim 应失败（状态已变为 PROCESSING）
        assertFalse(repository.claimForProcessing("msg-1"));
    }

    @Test
    @DisplayName("markAsSent 更新状态")
    void markAsSent() {
        OutboxMessage msg = buildMessage("msg-1", OutboxStatus.PENDING, 0);
        repository.save(msg);

        repository.markAsSent("msg-1");

        List<OutboxMessage> pending = repository.findPending(10);
        assertTrue(pending.isEmpty());

        Map<String, Long> counts = repository.countByStatus();
        assertEquals(1L, counts.getOrDefault(OutboxStatus.SENT.name(), 0L));
    }

    @Test
    @DisplayName("markAsFailed 增加重试计数")
    void markAsFailed() {
        OutboxMessage msg = buildMessage("msg-1", OutboxStatus.PENDING, 0);
        repository.save(msg);

        repository.markAsFailed("msg-1", "network error", 10);

        List<OutboxMessage> pending = repository.findPending(10);
        // next_retry_at 在未来，不应被查到
        assertTrue(pending.isEmpty());
    }

    @Test
    @DisplayName("reclaimStaleProcessing 回收超时消息")
    void reclaimStaleProcessing() {
        OutboxMessage msg = buildMessage("msg-1", OutboxStatus.PENDING, 0);
        repository.save(msg);
        repository.claimForProcessing("msg-1");

        // 回收（threshold=0 表示立即回收）
        int reclaimed = repository.reclaimStaleProcessing(0);
        assertEquals(1, reclaimed);

        List<OutboxMessage> pending = repository.findPending(10);
        assertEquals(1, pending.size());
    }

    @Test
    @DisplayName("deleteSentBefore 清理已投递消息")
    void deleteSentBefore() {
        OutboxMessage msg = buildMessage("msg-1", OutboxStatus.PENDING, 0);
        repository.save(msg);
        repository.markAsSent("msg-1");

        int deleted = repository.deleteSentBefore(Instant.now().plusSeconds(60));
        assertEquals(1, deleted);
    }

    @Test
    @DisplayName("existsByDeduplicationId 幂等检查")
    void existsByDeduplicationId() {
        OutboxMessage msg = buildMessage("msg-1", OutboxStatus.PENDING, 0);
        repository.save(msg);

        assertTrue(repository.existsByDeduplicationId(msg.getDeduplicationId()));
        assertFalse(repository.existsByDeduplicationId("non-existent-id"));
    }

    @Test
    @DisplayName("countByStatus 统计各状态数量")
    void countByStatus() {
        repository.save(buildMessage("msg-1", OutboxStatus.PENDING, 0));
        repository.save(buildMessage("msg-2", OutboxStatus.PENDING, 0));
        repository.save(buildMessage("msg-3", OutboxStatus.SENT, 0));

        Map<String, Long> counts = repository.countByStatus();
        assertEquals(2L, counts.getOrDefault(OutboxStatus.PENDING.name(), 0L));
        assertEquals(1L, counts.getOrDefault(OutboxStatus.SENT.name(), 0L));
    }

    @Test
    @DisplayName("优先级排序：高优先级消息排在前面")
    void findPending_orderedByPriority() {
        repository.save(buildMessageWithPriority("msg-low", 1));
        repository.save(buildMessageWithPriority("msg-high", 9));
        repository.save(buildMessageWithPriority("msg-mid", 5));

        List<OutboxMessage> pending = repository.findPending(10);
        assertEquals(3, pending.size());
        assertEquals("msg-high", pending.get(0).getId());
        assertEquals("msg-mid", pending.get(1).getId());
        assertEquals("msg-low", pending.get(2).getId());
    }

    @Test
    @DisplayName("表名校验：非法表名抛出异常")
    void invalidTableName_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new OutboxRepository(jdbcTemplate, "invalid; DROP TABLE--"));
        assertThrows(IllegalArgumentException.class, () ->
                new OutboxRepository(jdbcTemplate, "123abc"));
    }

    private OutboxMessage buildMessage(String id, OutboxStatus status, int retryCount) {
        return OutboxMessage.builder()
                .id(id)
                .aggregateType("Order")
                .aggregateId("order-" + id)
                .eventType("OrderCreated")
                .payload("{\"id\":1}")
                .headers(Map.of("source", "test"))
                .status(status)
                .retryCount(retryCount)
                .maxRetries(3)
                .priority(5)
                .nextRetryAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .schemaVersion("v1.0.0")
                .deduplicationId("dedup-" + id)
                .build();
    }

    private OutboxMessage buildMessageWithPriority(String id, int priority) {
        return OutboxMessage.builder()
                .id(id)
                .aggregateType("Order")
                .aggregateId("order-" + id)
                .eventType("OrderCreated")
                .payload("{\"id\":1}")
                .headers(Map.of())
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .maxRetries(3)
                .priority(priority)
                .nextRetryAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .deduplicationId("dedup-" + id)
                .build();
    }
}
