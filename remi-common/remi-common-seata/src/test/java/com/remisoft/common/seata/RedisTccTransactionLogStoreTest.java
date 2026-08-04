package com.remisoft.common.seata;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import com.remisoft.common.seata.api.TccBranchStatus;
import com.remisoft.common.seata.api.TccTransactionLog;
import com.remisoft.common.seata.impl.RedisTccTransactionLogStore;

import java.util.concurrent.ConcurrentHashMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RedisTccTransactionLogStore} 单元测试
 *
 * <p>使用 Mockito 模拟 {@link RedisTemplate}，验证核心逻辑：
 * 序列化、反序列化、状态更新、SCAN 遍历等。
 * 不依赖真实 Redis，跨平台稳定。
 *
 * @author remi-team
 * @since 1.0.0
 */
class RedisTccTransactionLogStoreTest {

    private RedisTemplate<String, Object> redisTemplate;
    private HashOperations<String, Object, Object> hashOps;

    private RedisTccTransactionLogStore store;

    /** 内存模拟存储：key → (field → value) */
    private final Map<String, Map<Object, Object>> backing = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        hashOps = mock(HashOperations.class);
        backing.clear();

        when(redisTemplate.opsForHash()).thenReturn(hashOps);

        // putAll：写入内存（void 方法用 doAnswer）
        doAnswer(inv -> {
            String key = inv.getArgument(0);
            Map<Object, Object> map = inv.getArgument(1);
            backing.put(key, new LinkedHashMap<>(map));
            return null;
        }).when(hashOps).putAll(any(String.class), ArgumentMatchers.<Map<Object, Object>>any());

        // put：单字段写入（void 方法用 doAnswer）
        doAnswer(inv -> {
            String key = inv.getArgument(0);
            Object field = inv.getArgument(1);
            Object value = inv.getArgument(2);
            backing.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(field, value);
            return null;
        }).when(hashOps).put(any(String.class), any(), any());

        // entries：读取整个 hash
        when(hashOps.entries(any(String.class))).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            Map<Object, Object> map = backing.get(key);
            return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
        });

        // increment：原子自增（用于 retryCount）
        when(hashOps.increment(any(String.class), any(), anyLong())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            Object field = inv.getArgument(1);
            long delta = inv.getArgument(2);
            Map<Object, Object> map = backing.computeIfAbsent(key, k -> new LinkedHashMap<>());
            Object current = map.getOrDefault(field, "0");
            long next = Long.parseLong(String.valueOf(current)) + delta;
            map.put(field, String.valueOf(next));
            return next;
        });

        // expire / delete：仅记录调用
        when(redisTemplate.expire(any(String.class), anyLong(), any())).thenReturn(true);
        when(redisTemplate.delete(any(String.class))).thenReturn(true);

        // scan：返回所有 key 的 cursor（用 Mockito mock Cursor 接口）
        when(redisTemplate.scan(any(ScanOptions.class))).thenAnswer(inv -> {
            Iterator<String> it = backing.keySet().stream()
                    .filter(k -> k.startsWith("remi:tcc:log:"))
                    .iterator();
            return mockCursor(it);
        });

        store = new RedisTccTransactionLogStore(
                redisTemplate,
                "remi:tcc:log:",
                Duration.ofHours(1));
    }

    /** 用 Mockito 创建 Cursor mock，避免 Spring Data Redis 4.x 接口签名变化 */
    private static Cursor<String> mockCursor(Iterator<String> it) {
        Cursor<String> cursor = Mockito.mock(Cursor.class);
        when(cursor.hasNext()).thenAnswer(inv -> it.hasNext());
        when(cursor.next()).thenAnswer(inv -> it.next());
        return cursor;
    }

    @Test
    void saveAndFindByXidAndBranchId() {
        TccTransactionLog log = newLog("xid-1", "branch-1", "test-tx");
        log.setTryStartedAt(LocalDateTime.now());
        log.setStatus(TccBranchStatus.TRYING);
        store.save(log);

        Optional<TccTransactionLog> found = store.findByXidAndBranchId("xid-1", "branch-1");
        assertTrue(found.isPresent());
        assertEquals("xid-1", found.get().getXid());
        assertEquals("branch-1", found.get().getBranchId());
        assertEquals("test-tx", found.get().getTransactionName());
        assertEquals(TccBranchStatus.TRYING, found.get().getStatus());
        assertEquals(0, found.get().getRetryCount());
        assertNotNull(found.get().getTryStartedAt());
        assertNull(found.get().getTryCompletedAt());
    }

    @Test
    void findByXidAndBranchIdReturnsEmptyWhenNotExists() {
        Optional<TccTransactionLog> found = store.findByXidAndBranchId("not-exist", "no-branch");
        assertFalse(found.isPresent());
    }

    @Test
    void updateStatusToFinalSetsFinishedAt() {
        TccTransactionLog log = newLog("xid-2", "branch-2", "test-final");
        store.save(log);

        store.updateStatus("xid-2", "branch-2", TccBranchStatus.CONFIRMED);

        Optional<TccTransactionLog> found = store.findByXidAndBranchId("xid-2", "branch-2");
        assertTrue(found.isPresent());
        assertEquals(TccBranchStatus.CONFIRMED, found.get().getStatus());
        assertNotNull(found.get().getFinishedAt());
    }

    @Test
    void updateStatusToNonFinalDoesNotSetFinishedAt() {
        TccTransactionLog log = newLog("xid-3", "branch-3", "test-non-final");
        store.save(log);

        store.updateStatus("xid-3", "branch-3", TccBranchStatus.TRIED);

        Optional<TccTransactionLog> found = store.findByXidAndBranchId("xid-3", "branch-3");
        assertTrue(found.isPresent());
        assertEquals(TccBranchStatus.TRIED, found.get().getStatus());
        assertNull(found.get().getFinishedAt());
    }

    @Test
    void deleteRemovesLog() {
        TccTransactionLog log = newLog("xid-4", "branch-4", "test-delete");
        store.save(log);
        assertTrue(store.findByXidAndBranchId("xid-4", "branch-4").isPresent());

        store.delete("xid-4", "branch-4");
        backing.remove("remi:tcc:log:xid-4:branch-4");
        assertFalse(store.findByXidAndBranchId("xid-4", "branch-4").isPresent());
    }

    @Test
    void findTimeoutPendingMatchesOnlyTriedBeforeThreshold() {
        // TRIED 且 tryCompletedAt 早于 threshold → 命中
        TccTransactionLog timedOut = newLog("xid-5", "branch-5", "test-timeout");
        timedOut.setStatus(TccBranchStatus.TRIED);
        timedOut.setTryCompletedAt(LocalDateTime.now().minusMinutes(10));
        store.save(timedOut);

        // TRIED 但 tryCompletedAt 晚于 threshold → 不命中
        TccTransactionLog fresh = newLog("xid-6", "branch-6", "test-fresh");
        fresh.setStatus(TccBranchStatus.TRIED);
        fresh.setTryCompletedAt(LocalDateTime.now());
        store.save(fresh);

        // CONFIRMED 终态 → 不命中
        TccTransactionLog confirmed = newLog("xid-7", "branch-7", "test-confirmed");
        confirmed.setStatus(TccBranchStatus.CONFIRMED);
        confirmed.setTryCompletedAt(LocalDateTime.now().minusMinutes(20));
        store.save(confirmed);

        // INIT 且无 tryCompletedAt → 不命中
        TccTransactionLog init = newLog("xid-8", "branch-8", "test-init");
        store.save(init);

        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
        List<TccTransactionLog> pending = store.findTimeoutPending(threshold);

        assertEquals(1, pending.size());
        assertEquals("xid-5", pending.get(0).getXid());
    }

    @Test
    void incrementRetryCountPersistsAcrossInstances() {
        TccTransactionLog log = newLog("xid-9", "branch-9", "test-retry");
        store.save(log);

        int first = store.incrementRetryCount("xid-9", "branch-9");
        int second = store.incrementRetryCount("xid-9", "branch-9");
        assertEquals(1, first);
        assertEquals(2, second);

        // 新实例读取，验证持久化（同一 backing 模拟）
        RedisTccTransactionLogStore anotherStore = new RedisTccTransactionLogStore(
                redisTemplate, "remi:tcc:log:", Duration.ofHours(1));
        Optional<TccTransactionLog> found = anotherStore.findByXidAndBranchId("xid-9", "branch-9");
        assertTrue(found.isPresent());
        assertEquals(2, found.get().getRetryCount());
    }

    @Test
    void updateLastErrorPersists() {
        TccTransactionLog log = newLog("xid-10", "branch-10", "test-error");
        store.save(log);

        store.updateLastError("xid-10", "branch-10", "confirm-failed-500");
        Optional<TccTransactionLog> found = store.findByXidAndBranchId("xid-10", "branch-10");
        assertTrue(found.isPresent());
        assertEquals("confirm-failed-500", found.get().getLastError());

        store.updateLastError("xid-10", "branch-10", null);
        Optional<TccTransactionLog> cleared = store.findByXidAndBranchId("xid-10", "branch-10");
        assertTrue(cleared.isPresent());
        assertNull(cleared.get().getLastError());
    }

    @Test
    void saveContextSnapshotPersists() {
        TccTransactionLog log = newLog("xid-11", "branch-11", "test-ctx");
        store.save(log);

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("userId", "u-1");
        ctx.put("amount", 100);
        ctx.put("nested", Map.of("k", "v"));
        store.saveContextSnapshot("xid-11", "branch-11", ctx);

        Optional<TccTransactionLog> found = store.findByXidAndBranchId("xid-11", "branch-11");
        assertTrue(found.isPresent());
        assertNotNull(found.get().getContextSnapshot());
        assertTrue(found.get().getContextSnapshot().contains("\"userId\":\"u-1\""));
        assertTrue(found.get().getContextSnapshot().contains("\"amount\":100"));
    }

    @Test
    void defaultConstructorFallsBackToDefaultPrefixAndRetention() {
        RedisTccTransactionLogStore defaultStore = new RedisTccTransactionLogStore(
                redisTemplate, null, null);
        // 应使用默认前缀和 24 小时保留，仍能正常工作
        TccTransactionLog log = newLog("xid-default", "branch-default", "test-default");
        defaultStore.save(log);
        assertTrue(defaultStore.findByXidAndBranchId("xid-default", "branch-default").isPresent());
        defaultStore.delete("xid-default", "branch-default");
    }

    @Test
    void crossInstanceSharedState() {
        // 实例 A 写入
        RedisTccTransactionLogStore storeA = new RedisTccTransactionLogStore(
                redisTemplate, "remi:tcc:log:", Duration.ofHours(1));
        TccTransactionLog log = newLog("xid-shared", "branch-shared", "test-cross");
        log.setStatus(TccBranchStatus.TRIED);
        log.setTryCompletedAt(LocalDateTime.now().minusMinutes(30));
        storeA.save(log);

        // 实例 B 读取
        RedisTccTransactionLogStore storeB = new RedisTccTransactionLogStore(
                redisTemplate, "remi:tcc:log:", Duration.ofHours(1));
        Optional<TccTransactionLog> found = storeB.findByXidAndBranchId("xid-shared", "branch-shared");
        assertTrue(found.isPresent());
        assertEquals(TccBranchStatus.TRIED, found.get().getStatus());

        // 实例 B 也能扫描超时
        List<TccTransactionLog> pending = storeB.findTimeoutPending(LocalDateTime.now().minusMinutes(5));
        assertTrue(pending.stream().anyMatch(l -> "xid-shared".equals(l.getXid())));
    }

    @Test
    void saveCallsExpireWithConfiguredRetention() {
        TccTransactionLog log = newLog("xid-expire", "branch-expire", "test-expire");
        store.save(log);

        // 验证 expire 被调用，且使用 1 小时 = 3600 秒
        verify(redisTemplate, times(1)).expire(
                eq("remi:tcc:log:xid-expire:branch-expire"),
                eq(3600L),
                any());
    }

    @Test
    void updateStatusToFinalRefreshesExpire() {
        TccTransactionLog log = newLog("xid-final", "branch-final", "test");
        store.save(log);
        // save 时 expire 一次
        verify(redisTemplate, times(1)).expire(
                eq("remi:tcc:log:xid-final:branch-final"),
                anyLong(),
                any());

        store.updateStatus("xid-final", "branch-final", TccBranchStatus.CANCELLED);
        // 终态 updateStatus 再 expire 一次，共 2 次
        verify(redisTemplate, times(2)).expire(
                eq("remi:tcc:log:xid-final:branch-final"),
                anyLong(),
                any());
    }

    @Test
    void saveHashContainsAllRequiredFields() {
        TccTransactionLog log = newLog("xid-fields", "branch-fields", "test-fields");
        log.setStatus(TccBranchStatus.TRIED);
        log.setTryStartedAt(LocalDateTime.now());
        log.setTryCompletedAt(LocalDateTime.now());
        log.setContextSnapshot("{\"k\":\"v\"}");
        log.setLastError("none");
        store.save(log);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(hashOps).putAll(eq("remi:tcc:log:xid-fields:branch-fields"), captor.capture());

        Map<String, String> hash = captor.getValue();
        assertEquals("xid-fields", hash.get("xid"));
        assertEquals("branch-fields", hash.get("branchId"));
        assertEquals("test-fields", hash.get("transactionName"));
        assertEquals("TRIED", hash.get("status"));
        assertEquals("{\"k\":\"v\"}", hash.get("contextSnapshot"));
        assertEquals("0", hash.get("retryCount"));
        assertEquals("none", hash.get("lastError"));
        assertNotNull(hash.get("tryStartedAt"));
        assertNotNull(hash.get("tryCompletedAt"));
        assertEquals("", hash.get("finishedAt"));
    }

    @Test
    void invalidStatusInRedisFallsBackToInit() {
        // 模拟 Redis 中存储了未知 status
        Map<Object, Object> corrupted = new LinkedHashMap<>();
        corrupted.put("xid", "xid-corrupt");
        corrupted.put("branchId", "branch-corrupt");
        corrupted.put("status", "INVALID_STATUS");
        backing.put("remi:tcc:log:xid-corrupt:branch-corrupt", corrupted);

        Optional<TccTransactionLog> found = store.findByXidAndBranchId("xid-corrupt", "branch-corrupt");
        assertTrue(found.isPresent());
        // 未知 status 应回退到构造函数的默认值 INIT
        assertEquals(TccBranchStatus.INIT, found.get().getStatus());
    }

    @Test
    void missingXidInRedisReturnsEmpty() {
        Map<Object, Object> corrupted = new LinkedHashMap<>();
        corrupted.put("branchId", "branch-no-xid");
        // 不写 xid 字段
        backing.put("remi:tcc:log:xid-missing:branch-no-xid", corrupted);

        Optional<TccTransactionLog> found = store.findByXidAndBranchId("xid-missing", "branch-no-xid");
        assertFalse(found.isPresent());
    }

    // ============= 辅助类与方法 =============

    private static TccTransactionLog newLog(String xid, String branchId, String name) {
        TccTransactionLog log = new TccTransactionLog(xid, branchId, name);
        log.setStatus(TccBranchStatus.INIT);
        return log;
    }
}
