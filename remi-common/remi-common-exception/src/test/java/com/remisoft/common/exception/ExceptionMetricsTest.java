package com.remisoft.common.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.remisoft.common.exception.code.UnifiedExceptionCode;
import com.remisoft.common.exception.custom.BusinessException;
import com.remisoft.common.exception.custom.SysException;
import com.remisoft.common.exception.metrics.ExceptionMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * {@link ExceptionMetrics} 单元测试
 *
 * @author remi-team
 * @since 1.0.0
 */
@DisplayName("ExceptionMetrics 指标统计测试")
class ExceptionMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private ExceptionMetrics exceptionMetrics;

    @BeforeEach
    void setup() {
        meterRegistry = new SimpleMeterRegistry();
        exceptionMetrics = new ExceptionMetrics(meterRegistry);
    }

    @Test
    @DisplayName("recordException() 正确记录 AbstractYdszException 的 tag")
    void testRecordBusinessException() {
        BusinessException ex = new BusinessException(UnifiedExceptionCode.NOT_FOUND);
        exceptionMetrics.recordException(ex);

        assertEquals(1.0, meterRegistry.counter(ExceptionMetrics.METRIC_EXCEPTION_COUNT,
                ExceptionMetrics.TAG_TYPE, "BusinessException",
                ExceptionMetrics.TAG_LEVEL, "ERROR",
                ExceptionMetrics.TAG_CATEGORY, "BUSINESS",
                ExceptionMetrics.TAG_CODE, "A04051"
        ).count());
    }

    @Test
    @DisplayName("recordException() 记录非 AbstractYdszException 时 tag 为 UNKNOWN/N/A")
    void testRecordGenericException() {
        RuntimeException ex = new RuntimeException("something went wrong");
        exceptionMetrics.recordException(ex);

        assertEquals(1.0, meterRegistry.counter(ExceptionMetrics.METRIC_EXCEPTION_COUNT,
                ExceptionMetrics.TAG_TYPE, "RuntimeException",
                ExceptionMetrics.TAG_LEVEL, "UNKNOWN",
                ExceptionMetrics.TAG_CATEGORY, "UNKNOWN",
                ExceptionMetrics.TAG_CODE, "N/A"
        ).count());
    }

    @Test
    @DisplayName("recordException() 多次调用计数递增")
    void testMultipleRecords() {
        SysException ex = new SysException(UnifiedExceptionCode.INTERNAL_ERROR);
        exceptionMetrics.recordException(ex);
        exceptionMetrics.recordException(ex);
        exceptionMetrics.recordException(ex);

        assertEquals(3.0, meterRegistry.counter(ExceptionMetrics.METRIC_EXCEPTION_COUNT,
                ExceptionMetrics.TAG_TYPE, "SysException",
                ExceptionMetrics.TAG_CODE, "B01051"
        ).count());
    }

    @Test
    @DisplayName("setEnabled(false) 后不记录指标")
    void testDisabled() {
        exceptionMetrics.setEnabled(false);
        BusinessException ex = new BusinessException(UnifiedExceptionCode.FAIL);
        exceptionMetrics.recordException(ex);

        assertEquals(0, meterRegistry.getMeters().size());
    }

    @Test
    @DisplayName("recordHandlerDuration() 记录处理耗时")
    void testRecordDuration() {
        SysException ex = new SysException();
        exceptionMetrics.recordHandlerDuration(150, ex);

        assertEquals(1, meterRegistry.timer(ExceptionMetrics.METRIC_HANDLER_DURATION,
                ExceptionMetrics.TAG_TYPE, "SysException"
        ).count());
    }

    @Test
    @DisplayName("recordExceptionWithTags() 添加额外标签")
    void testRecordWithExtraTags() {
        BusinessException ex = new BusinessException(UnifiedExceptionCode.FAIL);
        exceptionMetrics.recordExceptionWithTags(ex, "path", "/api/users");

        assertEquals(1.0, meterRegistry.counter(ExceptionMetrics.METRIC_EXCEPTION_COUNT,
                ExceptionMetrics.TAG_TYPE, "BusinessException",
                ExceptionMetrics.TAG_LEVEL, "ERROR",
                ExceptionMetrics.TAG_CATEGORY, "BUSINESS",
                ExceptionMetrics.TAG_CODE, "A01051",
                "path", "/api/users"
        ).count());
    }

    @Test
    @DisplayName("meterRegistry 为 null 时不抛异常")
    void testNullMeterRegistry() {
        ExceptionMetrics metrics = new ExceptionMetrics(null);
        assertDoesNotThrow(() -> metrics.recordException(new BusinessException()));
    }
}
