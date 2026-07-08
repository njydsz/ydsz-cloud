package com.njydsz.pmis.agent.service.impl;

import com.njydsz.pmis.agent.config.TokenQuotaProperties;
import com.njydsz.pmis.agent.dto.QuotaSummary;
import com.njydsz.pmis.agent.dto.TokenUsage;
import com.njydsz.pmis.agent.entity.TokenQuotaDO;
import com.njydsz.pmis.agent.entity.TokenUsageLogDO;
import com.njydsz.pmis.agent.mapper.TokenQuotaMapper;
import com.njydsz.pmis.agent.mapper.TokenUsageLogMapper;
import com.njydsz.pmis.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 默认 Token 配额服务单元测试（P2-4 落地）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>checkQuota：enabled 开关、配额充足/不足、Mapper 不可用、自动初始化</li>
 *   <li>recordUsage：写入明细、递增配额、enabled=false 仅写明细、异常容错</li>
 *   <li>getQuotaSummary：配额存在/不存在、Mapper 不可用</li>
 *   <li>resetQuota：重置成功、配额不存在、Mapper 不可用</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-4)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultTokenQuotaService 配额服务")
class DefaultTokenQuotaServiceTest {

    @Mock
    private ObjectProvider<TokenQuotaMapper> quotaMapperProvider;

    @Mock
    private ObjectProvider<TokenUsageLogMapper> usageLogMapperProvider;

    @Mock
    private TokenQuotaMapper quotaMapper;

    @Mock
    private TokenUsageLogMapper usageLogMapper;

    private TokenQuotaProperties enabledProps;
    private TokenQuotaProperties disabledProps;

    @BeforeEach
    void setUp() {
        enabledProps = new TokenQuotaProperties();
        enabledProps.setEnabled(true);
        enabledProps.setDefaultMonthlyQuota(1000000L);
        enabledProps.setAutoInit(true);

        disabledProps = new TokenQuotaProperties();
        disabledProps.setEnabled(false);
        disabledProps.setDefaultMonthlyQuota(1000000L);
        disabledProps.setAutoInit(true);
    }

    // ==================== 辅助方法 ====================

    /** 构造 enabled 服务 + Mapper 可用 */
    private DefaultTokenQuotaService enabledService() {
        when(quotaMapperProvider.getIfAvailable()).thenReturn(quotaMapper);
        when(usageLogMapperProvider.getIfAvailable()).thenReturn(usageLogMapper);
        return new DefaultTokenQuotaService(quotaMapperProvider, usageLogMapperProvider, enabledProps);
    }

    /** 构造 disabled 服务 */
    private DefaultTokenQuotaService disabledService() {
        when(quotaMapperProvider.getIfAvailable()).thenReturn(quotaMapper);
        when(usageLogMapperProvider.getIfAvailable()).thenReturn(usageLogMapper);
        return new DefaultTokenQuotaService(quotaMapperProvider, usageLogMapperProvider, disabledProps);
    }

    /** 构造 Mapper 不可用的服务 */
    private DefaultTokenQuotaService noMapperService() {
        when(quotaMapperProvider.getIfAvailable()).thenReturn(null);
        when(usageLogMapperProvider.getIfAvailable()).thenReturn(null);
        return new DefaultTokenQuotaService(quotaMapperProvider, usageLogMapperProvider, enabledProps);
    }

    /** 构造配额记录 */
    private TokenQuotaDO quota(String tenantId, String month, long total, long used, String status) {
        TokenQuotaDO q = new TokenQuotaDO();
        q.setId("quota-" + tenantId + "-" + month);
        q.setTenantId(tenantId);
        q.setQuotaMonth(month);
        q.setTotalQuota(total);
        q.setUsedTokens(used);
        q.setStatus(status);
        return q;
    }

    /** 构造 TokenUsage */
    private TokenUsage usage(int prompt, int completion) {
        return TokenUsage.builder()
                .tenantId("1")
                .traceId("trace-001")
                .provider("mock")
                .promptTokens(prompt)
                .completionTokens(completion)
                .totalTokens(prompt + completion)
                .costMs(100L)
                .build();
    }

    // ==================== checkQuota 测试 ====================

    @Nested
    @DisplayName("checkQuota 配额检查")
    class CheckQuotaTest {

        @Test
        @DisplayName("enabled=false 时不检查配额")
        void shouldSkipWhenDisabled() {
            DefaultTokenQuotaService service = disabledService();

            service.checkQuota("1", 10000);

            verify(quotaMapper, never()).selectByTenantAndMonth(anyString(), anyString());
        }

        @Test
        @DisplayName("配额充足时不抛异常")
        void shouldNotThrowWhenQuotaSufficient() {
            DefaultTokenQuotaService service = enabledService();
            when(quotaMapper.selectByTenantAndMonth(anyString(), anyString()))
                    .thenReturn(quota("1", "202607", 1000000L, 1000L, "ACTIVE"));

            service.checkQuota("1", 5000);

            // 不抛异常即可
        }

        @Test
        @DisplayName("配额不足时抛 BizException")
        void shouldThrowWhenQuotaInsufficient() {
            DefaultTokenQuotaService service = enabledService();
            when(quotaMapper.selectByTenantAndMonth(anyString(), anyString()))
                    .thenReturn(quota("1", "202607", 10000L, 9000L, "ACTIVE"));

            assertThatThrownBy(() -> service.checkQuota("1", 5000))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("配额刚好等于需求时不抛异常")
        void shouldNotThrowWhenQuotaExactlyMatches() {
            DefaultTokenQuotaService service = enabledService();
            when(quotaMapper.selectByTenantAndMonth(anyString(), anyString()))
                    .thenReturn(quota("1", "202607", 10000L, 5000L, "ACTIVE"));

            service.checkQuota("1", 5000);
        }

        @Test
        @DisplayName("Mapper 不可用时不抛异常")
        void shouldNotThrowWhenMapperUnavailable() {
            DefaultTokenQuotaService service = noMapperService();

            service.checkQuota("1", 10000);
        }

        @Test
        @DisplayName("autoInit=true 时自动初始化配额")
        void shouldAutoInitQuotaWhenNotExists() {
            DefaultTokenQuotaService service = enabledService();
            when(quotaMapper.selectByTenantAndMonth(anyString(), anyString()))
                    .thenReturn(null);

            service.checkQuota("1", 5000);

            ArgumentCaptor<TokenQuotaDO> captor = ArgumentCaptor.forClass(TokenQuotaDO.class);
            verify(quotaMapper, times(1)).insert(captor.capture());
            assertThat(captor.getValue().getTotalQuota()).isEqualTo(1000000L);
            assertThat(captor.getValue().getUsedTokens()).isZero();
            assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("autoInit=false 时不初始化配额")
        void shouldNotInitWhenAutoInitFalse() {
            enabledProps.setAutoInit(false);
            DefaultTokenQuotaService service = enabledService();
            when(quotaMapper.selectByTenantAndMonth(anyString(), anyString()))
                    .thenReturn(null);

            service.checkQuota("1", 5000);

            verify(quotaMapper, never()).insert(any(TokenQuotaDO.class));
        }

        @Test
        @DisplayName("自动初始化时并发异常不传播，重新查询")
        void shouldHandleConcurrentInit() {
            DefaultTokenQuotaService service = enabledService();
            TokenQuotaDO existing = quota("1", "202607", 1000000L, 0L, "ACTIVE");
            when(quotaMapper.selectByTenantAndMonth(anyString(), anyString()))
                    .thenReturn(null)        // 第一次查询返回 null
                    .thenReturn(existing);   // insert 异常后重新查询返回已存在
            when(quotaMapper.insert(any(TokenQuotaDO.class))).thenThrow(new RuntimeException("主键冲突"));

            service.checkQuota("1", 5000);

            // 不抛异常
        }
    }

    // ==================== recordUsage 测试 ====================

    @Nested
    @DisplayName("recordUsage 记录使用量")
    class RecordUsageTest {

        @Test
        @DisplayName("usage=null 直接返回")
        void shouldReturnNullUsage() {
            DefaultTokenQuotaService service = enabledService();

            service.recordUsage(null);

            verify(usageLogMapper, never()).insert(any(TokenUsageLogDO.class));
            verify(quotaMapper, never()).incrementUsedTokens(anyString(), anyLong());
        }

        @Test
        @DisplayName("enabled=true 时写入明细 + 递增配额")
        void shouldWriteLogAndIncrementWhenEnabled() {
            DefaultTokenQuotaService service = enabledService();
            when(quotaMapper.selectByTenantAndMonth(anyString(), anyString()))
                    .thenReturn(quota("1", "202607", 1000000L, 0L, "ACTIVE"));
            when(quotaMapper.incrementUsedTokens(anyString(), anyLong())).thenReturn(1);

            service.recordUsage(usage(100, 50));

            // 验证写入明细
            ArgumentCaptor<TokenUsageLogDO> logCaptor = ArgumentCaptor.forClass(TokenUsageLogDO.class);
            verify(usageLogMapper, times(1)).insert(logCaptor.capture());
            assertThat(logCaptor.getValue().getPromptTokens()).isEqualTo(100);
            assertThat(logCaptor.getValue().getCompletionTokens()).isEqualTo(50);
            assertThat(logCaptor.getValue().getTotalTokens()).isEqualTo(150);

            // 验证递增配额
            verify(quotaMapper, times(1)).incrementUsedTokens(anyString(), eq(150L));
        }

        @Test
        @DisplayName("enabled=false 时仅写明细，不递增配额")
        void shouldOnlyWriteLogWhenDisabled() {
            DefaultTokenQuotaService service = disabledService();

            service.recordUsage(usage(100, 50));

            verify(usageLogMapper, times(1)).insert(any(TokenUsageLogDO.class));
            verify(quotaMapper, never()).incrementUsedTokens(anyString(), anyLong());
        }

        @Test
        @DisplayName("Mapper 不可用时不抛异常")
        void shouldNotThrowWhenMapperUnavailable() {
            DefaultTokenQuotaService service = noMapperService();

            service.recordUsage(usage(100, 50));
        }

        @Test
        @DisplayName("total=0 时不递增配额")
        void shouldNotIncrementWhenTotalZero() {
            DefaultTokenQuotaService service = enabledService();
            when(quotaMapper.selectByTenantAndMonth(anyString(), anyString()))
                    .thenReturn(quota("1", "202607", 1000000L, 0L, "ACTIVE"));

            service.recordUsage(usage(0, 0));

            verify(usageLogMapper, times(1)).insert(any(TokenUsageLogDO.class));
            verify(quotaMapper, never()).incrementUsedTokens(anyString(), anyLong());
        }

        @Test
        @DisplayName("递增失败（配额超限）不抛异常")
        void shouldNotThrowWhenIncrementFails() {
            DefaultTokenQuotaService service = enabledService();
            when(quotaMapper.selectByTenantAndMonth(anyString(), anyString()))
                    .thenReturn(quota("1", "202607", 1000L, 900L, "ACTIVE"));
            when(quotaMapper.incrementUsedTokens(anyString(), anyLong())).thenReturn(0);

            service.recordUsage(usage(100, 50));
        }

        @Test
        @DisplayName("写入明细异常不传播")
        void shouldSwallowLogInsertException() {
            DefaultTokenQuotaService service = enabledService();
            when(quotaMapper.selectByTenantAndMonth(anyString(), anyString()))
                    .thenReturn(quota("1", "202607", 1000000L, 0L, "ACTIVE"));
            when(quotaMapper.incrementUsedTokens(anyString(), anyLong())).thenReturn(1);
            when(usageLogMapper.insert(any(TokenUsageLogDO.class))).thenThrow(new RuntimeException("DB 故障"));

            service.recordUsage(usage(100, 50));
        }

        @Test
        @DisplayName("递增配额异常不传播")
        void shouldSwallowIncrementException() {
            DefaultTokenQuotaService service = enabledService();
            when(quotaMapper.selectByTenantAndMonth(anyString(), anyString()))
                    .thenReturn(quota("1", "202607", 1000000L, 0L, "ACTIVE"));
            when(quotaMapper.incrementUsedTokens(anyString(), anyLong()))
                    .thenThrow(new RuntimeException("DB 故障"));

            service.recordUsage(usage(100, 50));
        }
    }

    // ==================== getQuotaSummary 测试 ====================

    @Nested
    @DisplayName("getQuotaSummary 查询概览")
    class GetQuotaSummaryTest {

        @Test
        @DisplayName("配额存在返回实际值")
        void shouldReturnActualQuota() {
            DefaultTokenQuotaService service = enabledService();
            when(quotaMapper.selectByTenantAndMonth(anyString(), anyString()))
                    .thenReturn(quota("1", "202607", 1000000L, 300000L, "ACTIVE"));

            QuotaSummary summary = service.getQuotaSummary("1");

            assertThat(summary.getTenantId()).isEqualTo("1");
            assertThat(summary.getTotalQuota()).isEqualTo(1000000L);
            assertThat(summary.getUsedTokens()).isEqualTo(300000L);
            assertThat(summary.getRemainingTokens()).isEqualTo(700000L);
            assertThat(summary.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("配额不存在返回默认值")
        void shouldReturnDefaultWhenQuotaNotExists() {
            DefaultTokenQuotaService service = enabledService();
            when(quotaMapper.selectByTenantAndMonth(anyString(), anyString()))
                    .thenReturn(null);

            QuotaSummary summary = service.getQuotaSummary("1");

            assertThat(summary.getTotalQuota()).isEqualTo(1000000L);
            assertThat(summary.getUsedTokens()).isZero();
            assertThat(summary.getRemainingTokens()).isEqualTo(1000000L);
            assertThat(summary.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("Mapper 不可用返回默认值")
        void shouldReturnDefaultWhenMapperUnavailable() {
            DefaultTokenQuotaService service = noMapperService();

            QuotaSummary summary = service.getQuotaSummary("1");

            assertThat(summary.getTotalQuota()).isEqualTo(1000000L);
            assertThat(summary.getUsedTokens()).isZero();
        }
    }

    // ==================== resetQuota 测试 ====================

    @Nested
    @DisplayName("resetQuota 重置配额")
    class ResetQuotaTest {

        @Test
        @DisplayName("配额存在时重置成功")
        void shouldResetWhenQuotaExists() {
            DefaultTokenQuotaService service = enabledService();
            TokenQuotaDO existing = quota("1", "202607", 1000000L, 800000L, "RUNOUT");
            when(quotaMapper.selectByTenantAndMonth(anyString(), anyString()))
                    .thenReturn(existing);

            service.resetQuota("1");

            ArgumentCaptor<TokenQuotaDO> captor = ArgumentCaptor.forClass(TokenQuotaDO.class);
            verify(quotaMapper, times(1)).updateById(captor.capture());
            assertThat(captor.getValue().getUsedTokens()).isZero();
            assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
            assertThat(captor.getValue().getResetAt()).isNotNull();
        }

        @Test
        @DisplayName("配额不存在时不抛异常")
        void shouldNotThrowWhenQuotaNotExists() {
            DefaultTokenQuotaService service = enabledService();
            when(quotaMapper.selectByTenantAndMonth(anyString(), anyString()))
                    .thenReturn(null);

            service.resetQuota("1");

            verify(quotaMapper, never()).updateById(any(TokenQuotaDO.class));
        }

        @Test
        @DisplayName("Mapper 不可用时不抛异常")
        void shouldNotThrowWhenMapperUnavailable() {
            DefaultTokenQuotaService service = noMapperService();

            service.resetQuota("1");
        }
    }
}
