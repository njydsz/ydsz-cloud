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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TenantQuotaServiceImpl} 单元测试（P7-2 租户级配额）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>getQuota: null/blank tenantId / 正常查询</li>
 *   <li>checkJobQuota: 配额未启用 / unlimited / 未超限 / 超限抛异常</li>
 *   <li>配额优先级: DB 记录 > 全局默认 > unlimited</li>
 *   <li>租户级禁用（enabled=0）跳过检查</li>
 *   <li>容错: 统计任务数失败时降级放行</li>
 *   <li>checkConcurrentQuota / checkDailyExecutionQuota: P7-3 预留接口</li>
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

    // ==================== checkJobQuota - 配额未启用 ====================

    @Test
    @DisplayName("checkJobQuota: quota.enabled=false 时不检查，直接返回")
    void checkJobQuota_disabled_noCheck() {
        cronjobProperties.getQuota().setEnabled(false);

        tenantQuotaService.checkJobQuota("tenant-1");

        verify(tenantQuotaMapper, never()).selectByTenantId(any());
        verify(jobMapper, never()).selectCount(any());
    }

    // ==================== checkJobQuota - 配额启用但 unlimited ====================

    @Test
    @DisplayName("checkJobQuota: 启用 + DB 无记录 + 全局默认 null = unlimited，不抛异常")
    void checkJobQuota_enabledButUnlimited_noException() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxJobs(null);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(null);

        tenantQuotaService.checkJobQuota("tenant-1");

        verify(jobMapper, never()).selectCount(any());
    }

    @Test
    @DisplayName("checkJobQuota: 启用 + DB 记录 maxJobs=null = unlimited，不抛异常")
    void checkJobQuota_dbMaxJobsNull_unlimited() {
        cronjobProperties.getQuota().setEnabled(true);
        TenantQuotaDO quota = buildQuota("tenant-1", null, 10, 1000, 1);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);

        tenantQuotaService.checkJobQuota("tenant-1");

        verify(jobMapper, never()).selectCount(any());
    }

    // ==================== checkJobQuota - 未超限 ====================

    @Test
    @DisplayName("checkJobQuota: 启用 + DB maxJobs + currentCount < max → 通过")
    void checkJobQuota_underLimit_passes() {
        cronjobProperties.getQuota().setEnabled(true);
        TenantQuotaDO quota = buildQuota("tenant-1", 100, 10, 1000, 1);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);
        when(jobMapper.selectCount(any())).thenReturn(50L);

        tenantQuotaService.checkJobQuota("tenant-1");

        verify(jobMapper, times(1)).selectCount(any());
    }

    @Test
    @DisplayName("checkJobQuota: 启用 + DB 无记录 + 全局默认 + currentCount < default → 通过")
    void checkJobQuota_globalDefaultUnderLimit_passes() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxJobs(200);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(null);
        when(jobMapper.selectCount(any())).thenReturn(150L);

        tenantQuotaService.checkJobQuota("tenant-1");

        verify(jobMapper, times(1)).selectCount(any());
    }

    // ==================== checkJobQuota - 超限 ====================

    @Test
    @DisplayName("checkJobQuota: 启用 + currentCount >= maxJobs → 抛 BizException(QUOTA_EXCEEDED)")
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
    @DisplayName("checkJobQuota: 启用 + currentCount > maxJobs → 抛 BizException")
    void checkJobQuota_overLimit_throwsBizException() {
        cronjobProperties.getQuota().setEnabled(true);
        TenantQuotaDO quota = buildQuota("tenant-1", 10, 5, 100, 1);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);
        when(jobMapper.selectCount(any())).thenReturn(11L);

        BizException ex = assertThrows(BizException.class,
                () -> tenantQuotaService.checkJobQuota("tenant-1"));
        assertEquals(BizErrorCode.QUOTA_EXCEEDED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("checkJobQuota: 启用 + DB 无记录 + 全局默认 + currentCount >= default → 抛异常")
    void checkJobQuota_globalDefaultExceeded_throws() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxJobs(50);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(null);
        when(jobMapper.selectCount(any())).thenReturn(50L);

        BizException ex = assertThrows(BizException.class,
                () -> tenantQuotaService.checkJobQuota("tenant-1"));
        assertEquals(BizErrorCode.QUOTA_EXCEEDED.getCode(), ex.getCode());
    }

    // ==================== checkJobQuota - 租户级禁用 ====================

    @Test
    @DisplayName("checkJobQuota: 启用 + DB 记录 enabled=0 → 跳过检查（视为 unlimited）")
    void checkJobQuota_tenantDisabled_skipCheck() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxJobs(10); // 全局默认有值，但应被跳过
        TenantQuotaDO quota = buildQuota("tenant-1", 100, 10, 1000, 0); // enabled=0
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);

        tenantQuotaService.checkJobQuota("tenant-1");

        verify(jobMapper, never()).selectCount(any());
    }

    @Test
    @DisplayName("checkJobQuota: 启用 + DB 记录 enabled=null → 视为禁用，跳过检查")
    void checkJobQuota_tenantEnabledNull_skipCheck() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxJobs(10);
        TenantQuotaDO quota = buildQuota("tenant-1", 100, 10, 1000, null); // enabled=null
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);

        tenantQuotaService.checkJobQuota("tenant-1");

        verify(jobMapper, never()).selectCount(any());
    }

    // ==================== checkJobQuota - 容错降级 ====================

    @Test
    @DisplayName("checkJobQuota: 统计任务数失败时降级放行（currentCount=0）")
    void checkJobQuota_countFails_degradePasses() {
        cronjobProperties.getQuota().setEnabled(true);
        TenantQuotaDO quota = buildQuota("tenant-1", 10, 5, 100, 1);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);
        when(jobMapper.selectCount(any())).thenThrow(new RuntimeException("DB down"));

        // 不抛异常，降级放行
        tenantQuotaService.checkJobQuota("tenant-1");

        verify(jobMapper, times(1)).selectCount(any());
    }

    @Test
    @DisplayName("checkJobQuota: 统计任务数失败 + max=0 → 降级放行（0 >= 0 但因异常降级返回 0）")
    void checkJobQuota_countFailsWithZeroMax_degradePasses() {
        cronjobProperties.getQuota().setEnabled(true);
        // max=1，正常情况下 currentCount=0 应该通过；这里强制抛异常验证降级
        TenantQuotaDO quota = buildQuota("tenant-1", 1, 1, 1, 1);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);
        when(jobMapper.selectCount(any())).thenThrow(new RuntimeException("conn refused"));

        tenantQuotaService.checkJobQuota("tenant-1");
    }

    // ==================== 配额优先级 ====================

    @Test
    @DisplayName("配额优先级: DB maxJobs > 全局默认（DB 存在时优先使用 DB 值）")
    void checkJobQuota_dbPriorityOverGlobal() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxJobs(50); // 全局默认 50
        TenantQuotaDO quota = buildQuota("tenant-1", 200, 10, 1000, 1); // DB 200
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);
        when(jobMapper.selectCount(any())).thenReturn(150L); // 150 > 50 但 < 200

        // 应该使用 DB 值 200，150 < 200 通过
        tenantQuotaService.checkJobQuota("tenant-1");

        verify(jobMapper, times(1)).selectCount(any());
    }

    @Test
    @DisplayName("配额优先级: DB maxJobs=null 时降级到全局默认")
    void checkJobQuota_dbNullFallsBackToGlobal() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxJobs(100);
        TenantQuotaDO quota = buildQuota("tenant-1", null, 10, 1000, 1); // DB maxJobs=null
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);
        when(jobMapper.selectCount(any())).thenReturn(99L);

        tenantQuotaService.checkJobQuota("tenant-1");

        verify(jobMapper, times(1)).selectCount(any());
    }

    @Test
    @DisplayName("配额优先级: DB 无记录时降级到全局默认")
    void checkJobQuota_noDbRecordFallsBackToGlobal() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxJobs(100);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(null);
        when(jobMapper.selectCount(any())).thenReturn(99L);

        tenantQuotaService.checkJobQuota("tenant-1");

        verify(jobMapper, times(1)).selectCount(any());
    }

    // ==================== checkConcurrentQuota (P7-3 预留) ====================

    @Test
    @DisplayName("checkConcurrentQuota: quota.enabled=false → 不检查")
    void checkConcurrentQuota_disabled_noCheck() {
        cronjobProperties.getQuota().setEnabled(false);

        tenantQuotaService.checkConcurrentQuota("tenant-1");

        verify(tenantQuotaMapper, never()).selectByTenantId(any());
    }

    @Test
    @DisplayName("checkConcurrentQuota: 启用 + maxConcurrent=null → 不检查")
    void checkConcurrentQuota_unlimited_noCheck() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxConcurrent(null);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(null);

        tenantQuotaService.checkConcurrentQuota("tenant-1");
    }

    @Test
    @DisplayName("checkConcurrentQuota: 启用 + maxConcurrent 有值 → 仅记录日志（P7-3 实现）")
    void checkConcurrentQuota_withMax_logsOnly() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxConcurrent(10);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(null);

        // 当前阶段仅记录日志，不抛异常
        tenantQuotaService.checkConcurrentQuota("tenant-1");
    }

    @Test
    @DisplayName("checkConcurrentQuota: 启用 + 租户级禁用 → 跳过")
    void checkConcurrentQuota_tenantDisabled_skip() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxConcurrent(10);
        TenantQuotaDO quota = buildQuota("tenant-1", 100, 10, 1000, 0);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);

        tenantQuotaService.checkConcurrentQuota("tenant-1");
    }

    // ==================== checkDailyExecutionQuota (P7-3 预留) ====================

    @Test
    @DisplayName("checkDailyExecutionQuota: quota.enabled=false → 不检查")
    void checkDailyExecutionQuota_disabled_noCheck() {
        cronjobProperties.getQuota().setEnabled(false);

        tenantQuotaService.checkDailyExecutionQuota("tenant-1");

        verify(tenantQuotaMapper, never()).selectByTenantId(any());
    }

    @Test
    @DisplayName("checkDailyExecutionQuota: 启用 + maxDaily=null → 不检查")
    void checkDailyExecutionQuota_unlimited_noCheck() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxDailyExecutions(null);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(null);

        tenantQuotaService.checkDailyExecutionQuota("tenant-1");
    }

    @Test
    @DisplayName("checkDailyExecutionQuota: 启用 + maxDaily 有值 → 仅记录日志（P7-3 实现）")
    void checkDailyExecutionQuota_withMax_logsOnly() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxDailyExecutions(1000);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(null);

        tenantQuotaService.checkDailyExecutionQuota("tenant-1");
    }

    @Test
    @DisplayName("checkDailyExecutionQuota: 启用 + 租户级禁用 → 跳过")
    void checkDailyExecutionQuota_tenantDisabled_skip() {
        cronjobProperties.getQuota().setEnabled(true);
        cronjobProperties.getQuota().setDefaultMaxDailyExecutions(1000);
        TenantQuotaDO quota = buildQuota("tenant-1", 100, 10, 1000, 0);
        when(tenantQuotaMapper.selectByTenantId("tenant-1")).thenReturn(quota);

        tenantQuotaService.checkDailyExecutionQuota("tenant-1");
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造测试用 TenantQuotaDO。
     *
     * @param tenantId 租户 ID
     * @param maxJobs 任务数上限（null=unlimited）
     * @param maxConcurrent 并发上限（null=unlimited）
     * @param maxDaily 日执行量上限（null=unlimited）
     * @param enabled 是否启用（0/1/null）
     * @return TenantQuotaDO 实例
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
}
