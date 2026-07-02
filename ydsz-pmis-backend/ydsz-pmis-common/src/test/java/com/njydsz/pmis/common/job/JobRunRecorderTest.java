package com.njydsz.pmis.common.job;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JobRunRecorder 单元测试（P1-9）
 *
 * <p>验证 MDC key 已从 {@code provider_trace_id} 统一为 {@code traceId}，
 * 与全局 logback {@code %X{traceId:-}} 一致，确保 Job 日志能与请求链路关联。</p>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("JobRunRecorder Job 运行记录器测试")
class JobRunRecorderTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("MDC_TRACE_ID 常量应为 traceId（与全局 TraceIdFilter 统一）")
    void mdcTraceIdConstantShouldBeTraceId() {
        assertThat(JobRunRecorder.MDC_TRACE_ID).isEqualTo("traceId");
        assertThat(JobRunRecorder.MDC_JOB_KEY).isEqualTo("job_key");
    }

    @Test
    @DisplayName("ensureTraceId 应从 MDC 复用现有 traceId")
    void ensureTraceIdReusesFromMdc() {
        MDC.put(JobRunRecorder.MDC_TRACE_ID, "existing-trace-123");
        assertThat(JobRunRecorder.ensureTraceId()).isEqualTo("existing-trace-123");
    }

    @Test
    @DisplayName("ensureTraceId 在 MDC 为空时应生成以 JOB- 开头的新 traceId")
    void ensureTraceIdGeneratesNewWhenMdcEmpty() {
        MDC.clear();
        String traceId = JobRunRecorder.ensureTraceId();
        assertThat(traceId).startsWith("JOB-");
        assertThat(traceId).hasSizeGreaterThan(4);
    }

    @Test
    @DisplayName("run(Supplier) 成功时应返回成功结果")
    void runSupplierSuccess() {
        JobRunRecorder.JobRunResult<String> result =
                JobRunRecorder.run("testJob", "{}", () -> "ok");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("ok");
        assertThat(result.getError()).isNull();
        assertThat(result.getCostMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.getTraceId()).isNotEmpty();
    }

    @Test
    @DisplayName("run(Callable) 成功时应返回成功结果")
    void runCallableSuccess() throws Exception {
        JobRunRecorder.JobRunResult<String> result =
                JobRunRecorder.run("testJob", "{}",
                        () -> JobRunRecorder.JobRunResult.success("done", 10));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo("done");
        assertThat(result.getCostMs()).isEqualTo(10);
    }

    @Test
    @DisplayName("run(Callable) 业务抛异常时应返回失败结果且异常透传")
    void runCallableFailure() throws Exception {
        JobRunRecorder.JobRunResult<String> result =
                JobRunRecorder.run("testJob", "{}", () -> {
                    throw new RuntimeException("biz error");
                });

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getData()).isNull();
        assertThat(result.getError()).isInstanceOf(RuntimeException.class);
        assertThat(result.getError().getMessage()).isEqualTo("biz error");
    }

    @Test
    @DisplayName("run(Supplier) 业务抛 RuntimeException 时应返回失败结果")
    void runSupplierFailure() {
        Supplier<String> supplier = () -> {
            throw new RuntimeException("supplier error");
        };
        JobRunRecorder.JobRunResult<String> result =
                JobRunRecorder.run("testJob", "{}", supplier);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isInstanceOf(RuntimeException.class);
        assertThat(result.getError().getMessage()).isEqualTo("supplier error");
    }

    @Test
    @DisplayName("JobRunResult.toMap 成功时应包含 traceId/costMs/success/data")
    void toMapSuccess() {
        JobRunRecorder.JobRunResult<String> result =
                JobRunRecorder.JobRunResult.success("data", 100);
        Map<String, Object> map = result.toMap();

        assertThat(map.get("traceId")).isEqualTo(result.getTraceId());
        assertThat(map.get("costMs")).isEqualTo(100L);
        assertThat(map.get("success")).isEqualTo(true);
        assertThat(map.get("data")).isEqualTo("data");
        assertThat(map).doesNotContainKey("error");
    }

    @Test
    @DisplayName("JobRunResult.toMap 失败时应包含 error")
    void toMapFailure() {
        Exception e = new RuntimeException("fail");
        JobRunRecorder.JobRunResult<String> result =
                JobRunRecorder.JobRunResult.failure(e, 50);
        Map<String, Object> map = result.toMap();

        assertThat(map.get("success")).isEqualTo(false);
        assertThat(map.get("error")).isEqualTo("fail");
        assertThat(map.get("costMs")).isEqualTo(50L);
    }

    @Test
    @DisplayName("run 执行期间 MDC 应注入 traceId（与 logback %X{traceId:-} 对齐）")
    void runInjectsMdcTraceId() throws Exception {
        MDC.clear();
        AtomicReference<String> mdcTraceDuringRun = new AtomicReference<>();

        JobRunRecorder.run("testJob", "{}", () -> {
            mdcTraceDuringRun.set(MDC.get(JobRunRecorder.MDC_TRACE_ID));
            return JobRunRecorder.JobRunResult.success("ok", 0);
        });

        // 执行期间 MDC 应有 traceId
        assertThat(mdcTraceDuringRun.get()).isNotEmpty();
    }

    @Test
    @DisplayName("run 应复用 MDC 中已有的 traceId（与请求链路关联）")
    void runReusesExistingMdcTraceId() throws Exception {
        MDC.put(JobRunRecorder.MDC_TRACE_ID, "request-trace-abc");

        AtomicReference<String> traceDuringRun = new AtomicReference<>();
        JobRunRecorder.JobRunResult<String> result =
                JobRunRecorder.run("testJob", "{}", () -> {
                    traceDuringRun.set(MDC.get(JobRunRecorder.MDC_TRACE_ID));
                    return JobRunRecorder.JobRunResult.success("ok", 0);
                });

        // 执行期间的 traceId 应是预先设置的请求链路 traceId
        assertThat(traceDuringRun.get()).isEqualTo("request-trace-abc");
        // 结果的 traceId 也应一致
        assertThat(result.getTraceId()).isEqualTo("request-trace-abc");
    }

    @Test
    @DisplayName("run 执行后 MDC 中注入的 traceId 应被清理")
    void runCleansMdcAfterExecution() throws Exception {
        MDC.clear();

        JobRunRecorder.run("testJob", "{}",
                () -> JobRunRecorder.JobRunResult.success("ok", 0));

        // 执行后 MDC 中的 traceId 应被移除
        assertThat(MDC.get(JobRunRecorder.MDC_TRACE_ID)).isNull();
        assertThat(MDC.get(JobRunRecorder.MDC_JOB_KEY)).isNull();
    }
}
