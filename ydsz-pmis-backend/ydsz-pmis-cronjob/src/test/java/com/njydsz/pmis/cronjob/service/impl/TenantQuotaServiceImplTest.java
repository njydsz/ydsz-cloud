package com.njydsz.pmis.cronjob.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.entity.TenantQuotaDO;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import com.njydsz.pmis.cronjob.mapper.TenantQuotaMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TenantQuotaServiceImpl} 单元测试（P7-2 / P7-3 租户级配额）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>P7-2 任务数配额: 启用/禁用/unlimited/未超限/超限/优先级/容错</li>
 *   <li>P7-3 并发配额: Redis 计数器检查/超限/降级</li>
 *   <li>P7-3 日执行配额: Redis 日计数器检查/超限/降级</li>
 *   <li>P7-3 recordExecutionStart: INCR + TTL 设置/降级</li>
 *   <li>P7-3 recordExecutionEnd: DECR + 负数重置/降级</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("TenantQuotaServiceImpl 租户级配额服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantQuotaServiceImplTest {

    @Mock
    private TenantQuotaMapper tenantQuotaMapper;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private CronjobProperties cronjobProperties;

    @InjectMocks
    private TenantQuotaServiceImpl tenantQuotaService;

    @BeforeEach
    void setUp() throws Exception {
        cronjobProperties = new CronjobProperties();
        // 通过反射注入 cronjobProperties（@InjectMocks 无法注入非 @Mock 的具体类）
        java.lang.reflect.Field f = TenantQuotaServiceImpl.class.getDeclaredField("cronjobProperties");
        f.setAccessible(true);
        f.set(tenantQuotaService, cronjobProperties);
        // 绑定 redisTemplate.opsForValue() → valueOps
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ==================== getQuota ====================

    @Test
    @DisplayName("getQuota: tenantId 为 null 时返回 null")
    void getQuota_nullTenantId_returnsNull() {
        assertNull(tenantQuotaService.getQuota(null));
        verify(tenantQuotaMapper, never()).selectByTenantId(any());
    }

    @Test
    @DisplayName("getQuota: tenantId 为空串时返回 null")
    void getQuota_blankTenantId_returnsNull() {
        assertNull(tenantQuotaService.getQuota("  "));
        verify(tenantQuotaMapper, never()).selectByTenantId(any());
    }

    @Test
    @DisplayName("getQuota: 正常查询返回配额记录")
    void getQuota_validTenantId_returnsRecord() {
        TenantQuotaDO quota = buildQuota("tenant-1", 100, 10, 1000, 1);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);

        TenantQuotaDO result = tenantQuotaService.getQuota("tenant-1");

        assertEquals("tenant-1", result.getTenantId());
        assertEquals(100, result.getMaxJobs());
        verify(tenantQuotaMapper, times(1)).selectByTenantId("tenant-1");
    }

    @Test
    @DisplayName("getQuota: 记录不存在时返回 null")
    void getQuota_notExists_returnsNull() {
        when(tenantQuotaMapper.selectByTenantId("unknown")).thenReturn(null);
        assertNull(tenantQuotaService.getQuota("unknown"));
    }

    // ==================== P7-2: checkJobQuota ====================

    @Test
    @DisplayName("P7-2: checkJobQuota 禁用时直接返回")
    void checkJobQuota_disabled_noCheck() {
        cronjobProperties.getQuota().setEnabled(false);

        tenantQuotaService.checkJobQuota("tenant-1");

        verify(tenantQuotaMapper, never()).selectByTenantId(any());
        verify(jobMapper, never()).selectCount(any());
    }

    @Test
    @DisplayName("P7-2: checkJobQuota 启用 + 无 DB 记录 + 全局默认 null = unlimited")
    void checkJobQuota_enabledButUnlimited_noException() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxJobs(null);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(null);

        tenantQuotaService.checkJobQuota("tenant-1");

        verify(jobMapper, never()).selectCount(any());
    }

    @Test
    @DisplayName("P7-2: checkJobQuota 启用 + DB maxJobs=null = unlimited")
    void checkJobQuota_dbMaxJobsNull_unlimited() {
        cronjobProperties.getQuota().setEnabled(true);
        TenantQuotaDO quota = buildQuota("tenant-1", null, 10, 1000, 1);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);

        tenantQuotaService.checkJobQuota("tenant-1");

        verify(jobMapper, never()).selectCount(any());
    }

    @Test
    @DisplayName("P7-2: checkJobQuota 未超限 → 通过")
    void checkJobQuota_underLimit_passes() {
        cronjobProperties.getQuota().setEnabled(true);
        TenantQuotaDO quota = buildQuota("tenant-1", 100, 10, 1000, 1);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);
        when(jobMapper.selectCount(any())).thenReturn(50L);

        tenantQuotaService.checkJobQuota("tenant-1");

        verify(jobMapper, times(1)).selectCount(any());
    }

    @Test
    @DisplayName("P7-2: checkJobQuota 超限 → 抛 BizException")
    void checkJobQuota_exceeded_throwsBizException() {
        cronjobProperties.getQuota().setEnabled(true);
        TenantQuotaDO quota = buildQuota("tenant-1", 100, 10, 1000, 1);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);
        when(jobMapper.selectCount(any())).thenReturn(100L);

        BizException ex = assertThrows(BizException.class,
                () -> tenantQuotaService.checkJobQuota("tenant-1"));
        assertEquals(BizErrorCode.QUOTA_EXCEEDED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("P7-2: checkJobQuota 全局默认超限 → 抛异常")
    void checkJobQuota_globalDefaultExceeded_throws() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxJobs(50);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(null);
        when(jobMapper.selectCount(any())).thenReturn(50L);

        BizException ex = assertThrows(BizException.class,
                () -> tenantQuotaService.checkJobQuota("tenant-1"));
        assertEquals(BizErrorCode.QUOTA_EXCEEDED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("P7-2: checkJobQuota 租户级禁用（enabled=0）→ 跳过")
    void checkJobQuota_tenantDisabled_skipCheck() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxJobs(10);
        TenantQuotaDO quota = buildQuota("tenant-1", 100, 10, 1000, 0);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);

        tenantQuotaService.checkJobQuota("tenant-1");

        verify(jobMapper, never()).selectCount(any());
    }

    @Test
    @DisplayName("P7-2: checkJobQuota 统计失败 → 降级放行")
    void checkJobQuota_countFails_degradePasses() {
        cronjobProperties.getQuota().setEnabled(true);
        TenantQuotaDO quota = buildQuota("tenant-1", 10, 5, 100, 1);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);
        when(jobMapper.selectCount(any())).thenThrow(new RuntimeException("DB down"));

        tenantQuotaService.checkJobQuota("tenant-1");

        verify(jobMapper, times(1)).selectCount(any());
    }

    @Test
    @DisplayName("P7-2: 配额优先级 DB > 全局默认")
    void checkJobQuota_dbPriorityOverGlobal() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxJobs(50);
        TenantQuotaDO quota = buildQuota("tenant-1", 200, 10, 1000, 1);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);
        when(jobMapper.selectCount(any())).thenReturn(150L);

        tenantQuotaService.checkJobQuota("tenant-1");

        verify(jobMapper, times(1)).selectCount(any());
    }

    // ==================== P7-3: checkConcurrentQuota ====================

    @Test
    @DisplayName("P7-3: checkConcurrentQuota 禁用 → 不检查")
    void checkConcurrentQuota_disabled_noCheck() {
        cronjobProperties.getQuota().setEnabled(false);

        tenantQuotaService.checkConcurrentQuota("tenant-1");

        verify(tenantQuotaMapper, never()).selectByTenantId(any());
        verify(valueOps, never()).get(anyString());
    }

    @Test
    @DisplayName("P7-3: checkConcurrentQuota 启用 + maxConcurrent=null = unlimited")
    void checkConcurrentQuota_unlimited_noCheck() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxConcurrent(null);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(null);

        tenantQuotaService.checkConcurrentQuota("tenant-1");

        verify(valueOps, never()).get(anyString());
    }

    @Test
    @DisplayName("P7-3: checkConcurrentQuota 启用 + 未超限 → 通过")
    void checkConcurrentQuota_underLimit_passes() {
        cronjobProperties.getQuota().setEnabled(true);
        TenantQuotaDO quota = buildQuota("tenant-1", 100, 10, 1000, 1);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);
        when(valueOps.get("pmis:quota:concurrent:tenant-1")).thenReturn("5");

        tenantQuotaService.checkConcurrentQuota("tenant-1");

        verify(valueOps, times(1)).get("pmis:quota:concurrent:tenant-1");
    }

    @Test
    @DisplayName("P7-3: checkConcurrentQuota 启用 + 超限 → 抛 BizException")
    void checkConcurrentQuota_exceeded_throws() {
        cronjobProperties.getQuota().setEnabled(true);
        TenantQuotaDO quota = buildQuota("tenant-1", 100, 10, 1000, 1);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);
        when(valueOps.get("pmis:quota:concurrent:tenant-1")).thenReturn("10");

        BizException ex = assertThrows(BizException.class,
                () -> tenantQuotaService.checkConcurrentQuota("tenant-1"));
        assertEquals(BizErrorCode.QUOTA_EXCEEDED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("P7-3: checkConcurrentQuota Redis key 不存在 → current=0 通过")
    void checkConcurrentQuota_keyNotExists_passes() {
        cronjobProperties.getQuota().setEnabled(true);
        TenantQuotaDO quota = buildQuota("tenant-1", 100, 10, 1000, 1);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);
        when(valueOps.get("pmis:quota:concurrent:tenant-1")).thenReturn(null);

        tenantQuotaService.checkConcurrentQuota("tenant-1");
    }

    @Test
    @DisplayName("P7-3: checkConcurrentQuota Redis 失败 → 降级放行（current=0）")
    void checkConcurrentQuota_redisFails_degradePasses() {
        cronjobProperties.getQuota().setEnabled(true);
        TenantQuotaDO quota = buildQuota("tenant-1", 100, 10, 1000, 1);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis down"));

        tenantQuotaService.checkConcurrentQuota("tenant-1");
    }

    @Test
    @DisplayName("P7-3: checkConcurrentQuota 租户级禁用 → 跳过")
    void checkConcurrentQuota_tenantDisabled_skip() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxConcurrent(10);
        TenantQuotaDO quota = buildQuota("tenant-1", 100, 10, 1000, 0);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);

        tenantQuotaService.checkConcurrentQuota("tenant-1");

        verify(valueOps, never()).get(anyString());
    }

    @Test
    @DisplayName("P7-3: checkConcurrentQuota 全局默认生效")
    void checkConcurrentQuota_globalDefault_passes() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxConcurrent(10);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(null);
        when(valueOps.get("pmis:quota:concurrent:tenant-1")).thenReturn("5");

        tenantQuotaService.checkConcurrentQuota("tenant-1");

        verify(valueOps, times(1)).get("pmis:quota:concurrent:tenant-1");
    }

    // ==================== P7-3: checkDailyExecutionQuota ====================

    @Test
    @DisplayName("P7-3: checkDailyExecutionQuota 禁用 → 不检查")
    void checkDailyExecutionQuota_disabled_noCheck() {
        cronjobProperties.getQuota().setEnabled(false);

        tenantQuotaService.checkDailyExecutionQuota("tenant-1");

        verify(valueOps, never()).get(anyString());
    }

    @Test
    @DisplayName("P7-3: checkDailyExecutionQuota 启用 + maxDaily=null = unlimited")
    void checkDailyExecutionQuota_unlimited_noCheck() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxDailyExecutions(null);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(null);

        tenantQuotaService.checkDailyExecutionQuota("tenant-1");

        verify(valueOps, never()).get(anyString());
    }

    @Test
    @DisplayName("P7-3: checkDailyExecutionQuota 启用 + 未超限 → 通过")
    void checkDailyExecutionQuota_underLimit_passes() {
        cronjobProperties.getQuota().setEnabled(true);
        TenantQuotaDO quota = buildQuota("tenant-1", 100, 10, 1000, 1);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);
        when(valueOps.get(startsWithDailyPrefix())).thenReturn("500");

        tenantQuotaService.checkDailyExecutionQuota("tenant-1");
    }

    @Test
    @DisplayName("P7-3: checkDailyExecutionQuota 启用 + 超限 → 抛异常")
    void checkDailyExecutionQuota_exceeded_throws() {
        cronjobProperties.getQuota().setEnabled(true);
        TenantQuotaDO quota = buildQuota("tenant-1", 100, 10, 1000, 1);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);
        when(valueOps.get(startsWithDailyPrefix())).thenReturn("1000");

        BizException ex = assertThrows(BizException.class,
                () -> tenantQuotaService.checkDailyExecutionQuota("tenant-1"));
        assertEquals(BizErrorCode.QUOTA_EXCEEDED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("P7-3: checkDailyExecutionQuota Redis 失败 → 降级放行")
    void checkDailyExecutionQuota_redisFails_degradePasses() {
        cronjobProperties.getQuota().setEnabled(true);
        TenantQuotaDO quota = buildQuota("tenant-1", 100, 10, 1000, 1);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis down"));

        tenantQuotaService.checkDailyExecutionQuota("tenant-1");
    }

    @Test
    @DisplayName("P7-3: checkDailyExecutionQuota 租户级禁用 → 跳过")
    void checkDailyExecutionQuota_tenantDisabled_skip() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxDailyExecutions(1000);
        TenantQuotaDO quota = buildQuota("tenant-1", 100, 10, 1000, 0);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);

        tenantQuotaService.checkDailyExecutionQuota("tenant-1");

        verify(valueOps, never()).get(anyString());
    }

    // ==================== P7-3: recordExecutionStart ====================

    @Test
    @DisplayName("P7-3: recordExecutionStart 禁用 → 不操作 Redis")
    void recordExecutionStart_disabled_noRedis() {
        cronjobProperties.getQuota().setEnabled(false);

        tenantQuotaService.recordExecutionStart("tenant-1");

        verify(valueOps, never()).increment(anyString());
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("P7-3: recordExecutionStart tenantId=null → 不操作 Redis")
    void recordExecutionStart_nullTenantId_noRedis() {
        cronjobProperties.getQuota().setEnabled(true);

        tenantQuotaService.recordExecutionStart(null);

        verify(valueOps, never()).increment(anyString());
    }

    @Test
    @DisplayName("P7-3: recordExecutionStart 正常 INCR 并发 + 日执行")
    void recordExecutionStart_normal_incrBoth() {
        cronjobProperties.getQuota().setEnabled(true);
        when(valueOps.increment(anyString())).thenReturn(1L);

        tenantQuotaService.recordExecutionStart("tenant-1");

        // 并发 + 日执行共 2 次 INCR
        verify(valueOps, times(2)).increment(anyString());
        verify(redisTemplate, times(2)).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("P7-3: recordExecutionStart INCR 返回 >1 → 不设置 TTL")
    void recordExecutionStart_incrGreaterThanOne_noTtl() {
        cronjobProperties.getQuota().setEnabled(true);
        when(valueOps.increment(anyString())).thenReturn(5L);

        tenantQuotaService.recordExecutionStart("tenant-1");

        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("P7-3: recordExecutionStart INCR 并发失败 → 降级，仍 INCR 日执行")
    void recordExecutionStart_concurrentFails_stillIncrDaily() {
        cronjobProperties.getQuota().setEnabled(true);
        when(valueOps.increment("pmis:quota:concurrent:tenant-1"))
                .thenThrow(new RuntimeException("Redis down"));
        when(valueOps.increment(anyString())).thenReturn(1L); // daily key 仍能成功

        tenantQuotaService.recordExecutionStart("tenant-1");

        verify(valueOps, times(2)).increment(anyString()); // 两次调用（一次并发失败，一次日执行成功）
    }

    // ==================== P7-3: recordExecutionEnd ====================

    @Test
    @DisplayName("P7-3: recordExecutionEnd 禁用 → 不操作 Redis")
    void recordExecutionEnd_disabled_noRedis() {
        cronjobProperties.getQuota().setEnabled(false);

        tenantQuotaService.recordExecutionEnd("tenant-1");

        verify(valueOps, never()).decrement(anyString());
    }

    @Test
    @DisplayName("P7-3: recordExecutionEnd 正常 DECR")
    void recordExecutionEnd_normal_decr() {
        cronjobProperties.getQuota().setEnabled(true);
        when(valueOps.decrement("pmis:quota:concurrent:tenant-1")).thenReturn(3L);

        tenantQuotaService.recordExecutionEnd("tenant-1");

        verify(valueOps, times(1)).decrement("pmis:quota:concurrent:tenant-1");
        // DECR 后非负数，不应 set 为 0
        verify(valueOps, never()).set(anyString(), anyString());
    }

    @Test
    @DisplayName("P7-3: recordExecutionEnd DECR 返回负数 → 重置为 0")
    void recordExecutionEnd_negativeValue_resetToZero() {
        cronjobProperties.getQuota().setEnabled(true);
        when(valueOps.decrement("pmis:quota:concurrent:tenant-1")).thenReturn(-1L);

        tenantQuotaService.recordExecutionEnd("tenant-1");

        verify(valueOps, times(1)).set("pmis:quota:concurrent:tenant-1", "0");
    }

    @Test
    @DisplayName("P7-3: recordExecutionEnd DECR 失败 → 不影响主流程")
    void recordExecutionEnd_decrFails_noException() {
        cronjobProperties.getQuota().setEnabled(true);
        when(valueOps.decrement(anyString())).thenThrow(new RuntimeException("Redis down"));

        // 不抛异常
        tenantQuotaService.recordExecutionEnd("tenant-1");
    }

    @Test
    @DisplayName("P7-3: recordExecutionEnd tenantId=null → 不操作 Redis")
    void recordExecutionEnd_nullTenantId_noRedis() {
        cronjobProperties.getQuota().setEnabled(true);

        tenantQuotaService.recordExecutionEnd(null);

        verify(valueOps, never()).decrement(anyString());
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造测试用 TenantQuotaDO。
     */
    private TenantQuotaDO buildQuota(String tenantId, Integer maxJobs,
                                     Integer maxConcurrent, Integer maxDaily, Integer enabled) {
        TenantQuotaDO q = new TenantQuotaDO();
        q.setId("quota-" + tenantId);
        q.setTenantId(tenantId);
        q.setMaxJobs(maxJobs);
        q.setMaxConcurrent(maxConcurrent);
        q.setMaxDailyExecutions(maxDaily);
        q.setEnabled(enabled);
        return q;
    }

    /**
     * ArgumentMatcher：匹配 daily key 前缀（pmis:quota:daily:tenant-1:yyyyMMdd）。
     */
    private static String startsWithDailyPrefix() {
        return org.mockito.ArgumentMatchers.argThat(s -> s != null && s.startsWith("pmis:quota:daily:tenant-1:"));
    }
}
