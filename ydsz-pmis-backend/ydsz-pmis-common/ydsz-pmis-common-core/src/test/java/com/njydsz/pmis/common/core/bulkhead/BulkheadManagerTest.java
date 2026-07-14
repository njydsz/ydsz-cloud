package com.njydsz.pmis.common.core.bulkhead;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BulkheadManager 单元测试
 *
 * @author Marvin Lee
 * @since 3.5.0
 */
@DisplayName("BulkheadManager 舱壁隔离测试")
class BulkheadManagerTest {

    @Test
    @DisplayName("注册并获取许可应成功")
    void acquire_shouldSucceed() throws Exception {
        BulkheadManager manager = new BulkheadManager();
        manager.register("test-service", 2);

        BulkheadManager.Ticket ticket = manager.acquire("test-service", 1, TimeUnit.SECONDS);
        assertNotNull(ticket);
        ticket.release();
    }

    @Test
    @DisplayName("超过并发限制应超时")
    void acquire_exceedLimitShouldTimeout() throws Exception {
        BulkheadManager manager = new BulkheadManager();
        manager.register("test-service", 1);

        BulkheadManager.Ticket ticket1 = manager.acquire("test-service", 100, TimeUnit.MILLISECONDS);
        assertThrows(TimeoutException.class, () -> {
            manager.acquire("test-service", 100, TimeUnit.MILLISECONDS);
        });
        ticket1.release();
    }

    @Test
    @DisplayName("释放许可后应可再次获取")
    void acquire_afterReleaseShouldSucceed() throws Exception {
        BulkheadManager manager = new BulkheadManager();
        manager.register("test-service", 1);

        BulkheadManager.Ticket ticket = manager.acquire("test-service", 100, TimeUnit.MILLISECONDS);
        ticket.release();

        BulkheadManager.Ticket ticket2 = manager.acquire("test-service", 100, TimeUnit.MILLISECONDS);
        assertNotNull(ticket2);
        ticket2.release();
    }

    @Test
    @DisplayName("重复 release 不应导致许可泄漏")
    void release_multipleCallsShouldBeSafe() throws Exception {
        BulkheadManager manager = new BulkheadManager();
        manager.register("test-service", 1);

        BulkheadManager.Ticket ticket = manager.acquire("test-service", 100, TimeUnit.MILLISECONDS);
        ticket.release();
        ticket.release();
        ticket.release();

        BulkheadManager.Ticket ticket2 = manager.acquire("test-service", 100, TimeUnit.MILLISECONDS);
        assertNotNull(ticket2);
        ticket2.release();
    }

    @Test
    @DisplayName("未注册的舱壁应抛出异常")
    void acquire_unregisteredShouldThrow() {
        BulkheadManager manager = new BulkheadManager();
        assertThrows(IllegalArgumentException.class, () -> {
            manager.acquire("unknown", 1, TimeUnit.SECONDS);
        });
    }

    @Test
    @DisplayName("getStats 应返回正确状态")
    void getStats_shouldReturnCorrectStatus() throws Exception {
        BulkheadManager manager = new BulkheadManager();
        manager.register("test-service", 3);

        BulkheadManager.Ticket t1 = manager.acquire("test-service", 100, TimeUnit.MILLISECONDS);
        BulkheadManager.Ticket t2 = manager.acquire("test-service", 100, TimeUnit.MILLISECONDS);

        BulkheadManager.BulkheadStats stats = manager.getStats().get("test-service");
        assertEquals(1, stats.availablePermits());

        t1.release();
        t2.release();
    }
}
