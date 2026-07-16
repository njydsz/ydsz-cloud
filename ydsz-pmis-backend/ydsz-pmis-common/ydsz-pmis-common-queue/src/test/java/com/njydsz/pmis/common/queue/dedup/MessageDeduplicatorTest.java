package com.njydsz.pmis.common.queue.dedup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 消息去重器测试
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
class MessageDeduplicatorTest {

    @Test
    void testFirstMessageNotDuplicate() {
        MessageDeduplicator dedup = new MessageDeduplicator(60000);
        assertFalse(dedup.isDuplicate("trace-001"));
    }

    @Test
    void testDuplicateAfterMark() {
        MessageDeduplicator dedup = new MessageDeduplicator(60000);
        dedup.markProcessed("trace-001");
        assertTrue(dedup.isDuplicate("trace-001"));
    }

    @Test
    void testCheckAndMarkAtomic() {
        MessageDeduplicator dedup = new MessageDeduplicator(60000);
        assertFalse(dedup.checkAndMark("trace-001"));
        assertTrue(dedup.checkAndMark("trace-001"));
    }

    @Test
    void testExpiredRecordNotDuplicate() {
        MessageDeduplicator dedup = new MessageDeduplicator(100);
        dedup.markProcessed("trace-001");
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertFalse(dedup.isDuplicate("trace-001"));
    }

    @Test
    void testNullTraceIdNotDuplicate() {
        MessageDeduplicator dedup = new MessageDeduplicator(60000);
        assertFalse(dedup.isDuplicate(null));
        assertFalse(dedup.isDuplicate(""));
    }

    @Test
    void testClearResetsRecords() {
        MessageDeduplicator dedup = new MessageDeduplicator(60000);
        dedup.markProcessed("trace-001");
        dedup.markProcessed("trace-002");
        assertEquals(2, dedup.getRecordCount());
        dedup.clear();
        assertEquals(0, dedup.getRecordCount());
        assertFalse(dedup.isDuplicate("trace-001"));
    }

    @Test
    void testMultipleTraceIdsIndependent() {
        MessageDeduplicator dedup = new MessageDeduplicator(60000);
        dedup.markProcessed("trace-001");
        assertTrue(dedup.isDuplicate("trace-001"));
        assertFalse(dedup.isDuplicate("trace-002"));
        dedup.markProcessed("trace-002");
        assertTrue(dedup.isDuplicate("trace-002"));
    }
}
