package com.njydsz.common.app.metrics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.common.auth.metrics.AuthMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * {@link AppMetrics} 单元测试
 *
 * <p>验证 AppMetrics 作为 {@link AuthMetrics} 接口实现的指标采集行为，
 * 包括 null MeterRegistry 降级、null 标签降级、指标值递增等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("AppMetrics 指标采集测试")
class AppMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private AppMetrics appMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        appMetrics = new AppMetrics(meterRegistry);
    }

    @Test
    @DisplayName("AppMetrics 实现 AuthMetrics 接口")
    void testImplementsAuthMetrics() {
        assertInstanceOf(AuthMetrics.class, appMetrics);
    }

    @Test
    @DisplayName("recordAuthSuccess() 注册成功指标并递增 Counter")
    void testRecordAuthSuccess() {
        appMetrics.recordAuthSuccess("app", 1_000_000L);

        assertEquals(1.0, meterRegistry.counter("app.auth.total",
                "result", "success", "userType", "app").count());
        assertEquals(1, meterRegistry.timer("app.auth.duration",
                "result", "success", "userType", "app").count());
    }

    @Test
    @DisplayName("recordAuthSuccess(null userType) 降级为 'app' 标签")
    void testRecordAuthSuccessNullUserType() {
        appMetrics.recordAuthSuccess(null, 500_000L);

        assertEquals(1.0, meterRegistry.counter("app.auth.total",
                "result", "success", "userType", "app").count());
    }

    @Test
    @DisplayName("recordAuthFailure() 注册失败指标并附带 reason 标签")
    void testRecordAuthFailure() {
        appMetrics.recordAuthFailure("app", "invalid_token", 200_000L);

        assertEquals(1.0, meterRegistry.counter("app.auth.total",
                "result", "failure", "userType", "app", "reason", "invalid_token").count());
        assertEquals(1, meterRegistry.timer("app.auth.duration",
                "result", "failure", "userType", "app").count());
    }

    @Test
    @DisplayName("recordAuthFailure(null reason) 降级为 'unknown' 标签")
    void testRecordAuthFailureNullReason() {
        appMetrics.recordAuthFailure("app", null, 100_000L);

        assertEquals(1.0, meterRegistry.counter("app.auth.total",
                "result", "failure", "userType", "app", "reason", "unknown").count());
    }

    @Test
    @DisplayName("recordAuthSkip() 注册跳过指标")
    void testRecordAuthSkip() {
        appMetrics.recordAuthSkip("whitelist");

        assertEquals(1.0, meterRegistry.counter("app.auth.total",
                "result", "skip", "reason", "whitelist").count());
    }

    @Test
    @DisplayName("多次调用同一指标递增 Counter")
    void testMultipleIncrements() {
        appMetrics.recordAuthSuccess("app", 100L);
        appMetrics.recordAuthSuccess("app", 200L);
        appMetrics.recordAuthSuccess("app", 300L);

        assertEquals(3.0, meterRegistry.counter("app.auth.total",
                "result", "success", "userType", "app").count());
    }

    @Test
    @DisplayName("不同 userType 标签生成不同的 Counter")
    void testDifferentUserTypeTags() {
        appMetrics.recordAuthSuccess("app", 100L);
        appMetrics.recordAuthSuccess("web", 100L);

        assertEquals(1.0, meterRegistry.counter("app.auth.total",
                "result", "success", "userType", "app").count());
        assertEquals(1.0, meterRegistry.counter("app.auth.total",
                "result", "success", "userType", "web").count());
    }

    @Test
    @DisplayName("recordSignatureVerify() 注册签名验证指标（App 特有方法）")
    void testRecordSignatureVerify() {
        appMetrics.recordSignatureVerify("success", 50_000L);

        assertEquals(1.0, meterRegistry.counter("app.signature.verify.total",
                "result", "success").count());
        assertEquals(1, meterRegistry.timer("app.signature.verify.duration",
                "result", "success").count());
    }

    @Test
    @DisplayName("MeterRegistry 为 null 时所有方法安全降级（无 NPE）")
    void testNullMeterRegistryDegradesGracefully() {
        AppMetrics nullMetrics = new AppMetrics(null);
        assertDoesNotThrow(() -> nullMetrics.recordAuthSuccess("app", 100L));
        assertDoesNotThrow(() -> nullMetrics.recordAuthFailure("app", "invalid", 100L));
        assertDoesNotThrow(() -> nullMetrics.recordAuthSkip("whitelist"));
        assertDoesNotThrow(() -> nullMetrics.recordSignatureVerify("success", 100L));
    }

    @Test
    @DisplayName("AuthMetrics 接口方法可通过接口引用调用（多态）")
    void testInvokeViaInterfaceReference() {
        AuthMetrics metrics = appMetrics;
        metrics.recordAuthSuccess("app", 100L);
        metrics.recordAuthFailure("app", "invalid_token", 100L);
        metrics.recordAuthSkip("whitelist");

        assertEquals(1.0, meterRegistry.counter("app.auth.total",
                "result", "success", "userType", "app").count());
        assertEquals(1.0, meterRegistry.counter("app.auth.total",
                "result", "failure", "userType", "app", "reason", "invalid_token").count());
        assertEquals(1.0, meterRegistry.counter("app.auth.total",
                "result", "skip", "reason", "whitelist").count());
    }
}
