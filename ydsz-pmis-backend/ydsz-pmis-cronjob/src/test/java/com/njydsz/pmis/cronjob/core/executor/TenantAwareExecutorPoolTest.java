package com.njydsz.pmis.cronjob.core.executor;

import com.njydsz.pmis.cronjob.config.CronjobProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link TenantAwareExecutorPool} 单元测试。
 *
 * <p>P2-5: 覆盖场景：
 * <ul>
 *   <li>none 策略返回 null（使用全局池）</li>
 *   <li>tenant 策略按 tenantId 隔离（每个租户独立线程池）</li>
 *   <li>job_group 策略按 jobGroup 隔离</li>
 *   <li>相同 tenantId 返回相同池</li>
 *   <li>不同 tenantId 返回不同池</li>
 *   <li>shutdownAll 关闭所有线程池</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("TenantAwareExecutorPool 租户隔离线程池测试")
class TenantAwareExecutorPoolTest {

    private CronjobProperties cronjobProperties;
    private TenantAwareExecutorPool pool;

    @BeforeEach
    void setUp() {
        cronjobProperties = new CronjobProperties();
        pool = new TenantAwareExecutorPool(cronjobProperties);
    }

    @AfterEach
    void tearDown() {
        // 每个测试结束后清理线程池，避免线程泄漏
        pool.shutdownAll();
    }

    @Test
    @DisplayName("none 策略: getExecutor 返回 null（使用全局池）")
    void noneStrategy_returnsNull() {
        cronjobProperties.getExecutor().setIsolationStrategy("none");
        ExecutorService es = pool.getExecutor("tenant-1", "group-1");
        assertNull(es, "none 策略应返回 null，由调用方使用全局池");
    }

    @Test
    @DisplayName("null 策略等同于 none: 返回 null")
    void nullStrategy_returnsNull() {
        cronjobProperties.getExecutor().setIsolationStrategy(null);
        ExecutorService es = pool.getExecutor("tenant-1", "group-1");
        assertNull(es, "null 策略应返回 null");
    }

    @Test
    @DisplayName("tenant 策略: 按 tenantId 隔离")
    void tenantStrategy_isolatesByTenantId() {
        cronjobProperties.getExecutor().setIsolationStrategy("tenant");
        ExecutorService es1 = pool.getExecutor("tenant-A", "group-1");
        ExecutorService es2 = pool.getExecutor("tenant-B", "group-1");
        assertNotNull(es1, "tenant-A 应有独立线程池");
        assertNotNull(es2, "tenant-B 应有独立线程池");
        assertNotSame(es1, es2, "不同租户应有不同线程池");
    }

    @Test
    @DisplayName("job_group 策略: 按 jobGroup 隔离")
    void jobGroupStrategy_isolatesByJobGroup() {
        cronjobProperties.getExecutor().setIsolationStrategy("job_group");
        ExecutorService es1 = pool.getExecutor("tenant-A", "group-X");
        ExecutorService es2 = pool.getExecutor("tenant-A", "group-Y");
        assertNotNull(es1, "group-X 应有独立线程池");
        assertNotNull(es2, "group-Y 应有独立线程池");
        assertNotSame(es1, es2, "不同分组应有不同线程池");
    }

    @Test
    @DisplayName("tenant 策略: 相同 tenantId 返回相同池")
    void tenantStrategy_sameTenant_returnsSamePool() {
        cronjobProperties.getExecutor().setIsolationStrategy("tenant");
        ExecutorService es1 = pool.getExecutor("tenant-SAME", "group-1");
        ExecutorService es2 = pool.getExecutor("tenant-SAME", "group-2");
        ExecutorService es3 = pool.getExecutor("tenant-SAME", null);
        assertSame(es1, es2, "相同 tenantId 不同 jobGroup 应返回相同池");
        assertSame(es1, es3, "相同 tenantId null jobGroup 应返回相同池");
    }

    @Test
    @DisplayName("job_group 策略: 相同 jobGroup 返回相同池")
    void jobGroupStrategy_sameGroup_returnsSamePool() {
        cronjobProperties.getExecutor().setIsolationStrategy("job_group");
        ExecutorService es1 = pool.getExecutor("tenant-1", "group-SAME");
        ExecutorService es2 = pool.getExecutor("tenant-2", "group-SAME");
        assertSame(es1, es2, "相同 jobGroup 不同 tenantId 应返回相同池");
    }

    @Test
    @DisplayName("不同 tenantId 返回不同池")
    void differentTenantIds_returnDifferentPools() {
        cronjobProperties.getExecutor().setIsolationStrategy("tenant");
        ExecutorService es1 = pool.getExecutor("tenant-1", null);
        ExecutorService es2 = pool.getExecutor("tenant-2", null);
        ExecutorService es3 = pool.getExecutor("tenant-3", null);
        assertNotEquals(es1, es2, "tenant-1 vs tenant-2 应不同");
        assertNotEquals(es1, es3, "tenant-1 vs tenant-3 应不同");
        assertNotEquals(es2, es3, "tenant-2 vs tenant-3 应不同");
    }

    @Test
    @DisplayName("tenant 策略: tenantId 为空时回退全局池（返回 null）")
    void tenantStrategy_emptyTenantId_returnsNull() {
        cronjobProperties.getExecutor().setIsolationStrategy("tenant");
        assertNull(pool.getExecutor(null, "group-1"), "null tenantId 应回退全局池");
        assertNull(pool.getExecutor("", "group-1"), "空 tenantId 应回退全局池");
        assertNull(pool.getExecutor("   ", "group-1"), "空白 tenantId 应回退全局池");
    }

    @Test
    @DisplayName("job_group 策略: jobGroup 为空时回退全局池（返回 null）")
    void jobGroupStrategy_emptyJobGroup_returnsNull() {
        cronjobProperties.getExecutor().setIsolationStrategy("job_group");
        assertNull(pool.getExecutor("tenant-1", null), "null jobGroup 应回退全局池");
        assertNull(pool.getExecutor("tenant-1", ""), "空 jobGroup 应回退全局池");
    }

    @Test
    @DisplayName("getGlobalExecutor 始终返回 null（全局池由 DefaultTaskDispatcher 管理）")
    void getGlobalExecutor_returnsNull() {
        cronjobProperties.getExecutor().setIsolationStrategy("tenant");
        assertNull(pool.getGlobalExecutor(), "getGlobalExecutor 应返回 null");
    }

    @Test
    @DisplayName("shutdownAll 关闭所有线程池且清空映射")
    void shutdownAll_closesAllPools() {
        cronjobProperties.getExecutor().setIsolationStrategy("tenant");
        cronjobProperties.getExecutor().setTenantPoolSize(1);
        ExecutorService es1 = pool.getExecutor("tenant-A", null);
        ExecutorService es2 = pool.getExecutor("tenant-B", null);
        assertNotNull(es1);
        assertNotNull(es2);

        pool.shutdownAll();

        // 关闭后再获取应创建新池（旧的已被清空）
        ExecutorService es1New = pool.getExecutor("tenant-A", null);
        assertNotNull(es1New, "shutdownAll 后应能创建新池");
        assertNotSame(es1, es1New, "新池应与旧池不同（旧池已被关闭）");
        // 旧池应已关闭
        assertEquals(true, es1.isShutdown(), "旧池应已 shutdown");
        assertEquals(true, es2.isShutdown(), "旧池应已 shutdown");
    }

    @Test
    @DisplayName("shutdownAll 在无线程池时不抛异常")
    void shutdownAll_emptyPoolMap_noException() {
        // 未创建任何线程池时调用 shutdownAll
        pool.shutdownAll();
        // 验证不抛异常即可
    }

    @Test
    @DisplayName("并发调用 getExecutor 同一 key 返回相同池（computeIfAbsent 幂等）")
    void concurrentGetExecutor_sameKey_returnsSamePool() throws InterruptedException {
        cronjobProperties.getExecutor().setIsolationStrategy("tenant");
        cronjobProperties.getExecutor().setTenantPoolSize(1);
        ExecutorService[] results = new ExecutorService[10];
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> results[idx] = pool.getExecutor("tenant-CONCURRENT", null));
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        // 所有线程应获得相同线程池实例
        for (int i = 1; i < 10; i++) {
            assertSame(results[0], results[i], "线程 " + i + " 应获得相同线程池");
        }
    }

    @Test
    @DisplayName("自定义 tenantPoolSize 与 queueCapacity 生效")
    void customPoolSizeAndQueueCapacity_applied() {
        cronjobProperties.getExecutor().setIsolationStrategy("tenant");
        cronjobProperties.getExecutor().setTenantPoolSize(2);
        cronjobProperties.getExecutor().setTenantPoolQueueCapacity(50);
        ExecutorService es = pool.getExecutor("tenant-CUSTOM", null);
        assertNotNull(es);
        // 验证线程池参数（通过 instanceof 校验）
        assertEquals(true, es instanceof java.util.concurrent.ThreadPoolExecutor,
                "应为 ThreadPoolExecutor 实例");
        java.util.concurrent.ThreadPoolExecutor tpe =
                (java.util.concurrent.ThreadPoolExecutor) es;
        assertEquals(2, tpe.getCorePoolSize(), "核心线程数应为 2");
        assertEquals(2, tpe.getMaximumPoolSize(), "最大线程数应为 2");
    }
}
