package com.njydsz.pmis.workflow.flow.metrics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.mapper.FlowCcMapper;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowTaskMapper;
import com.njydsz.pmis.workflow.metrics.FlowMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FlowMetrics Prometheus 指标收集器单元测试
 *
 * <p>覆盖：Counter 缓存复用、Timer 缓存复用、Gauge 数据源、recordInstanceDuration。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@DisplayName("FlowMetrics Prometheus 指标单元测试")
class FlowMetricsTest {

    private MeterRegistry registry;
    private FlowInstanceMapper instanceMapper;
    private FlowTaskMapper taskMapper;
    private FlowCcMapper ccMapper;
    private FlowMetrics flowMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        instanceMapper = mock(FlowInstanceMapper.class);
        taskMapper = mock(FlowTaskMapper.class);
        ccMapper = mock(FlowCcMapper.class);
        flowMetrics = new FlowMetrics(registry);
        // 反射注入 mapper（模拟 Spring 自动装配）
        try {
            Field f1 = FlowMetrics.class.getDeclaredField("instanceMapper");
            f1.setAccessible(true);
            f1.set(flowMetrics, instanceMapper);
            Field f2 = FlowMetrics.class.getDeclaredField("taskMapper");
            f2.setAccessible(true);
            f2.set(flowMetrics, taskMapper);
            Field f3 = FlowMetrics.class.getDeclaredField("ccMapper");
            f3.setAccessible(true);
            f3.set(flowMetrics, ccMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("incInstanceCreated 触发 counter 增加")
    void testIncInstanceCreated() {
        flowMetrics.incInstanceCreated("project_initiation");
        Counter c = registry.find("pmis_flow_instance_created_total")
                .tag("flow_code", "project_initiation")
                .counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(1d);
    }

    @Test
    @DisplayName("多次 inc 同一 flowCode 复用同一 counter，累加")
    void testCounterCache() {
        flowMetrics.incInstanceCreated("contract_change");
        flowMetrics.incInstanceCreated("contract_change");
        flowMetrics.incInstanceCreated("contract_change");
        Counter c = registry.find("pmis_flow_instance_created_total")
                .tag("flow_code", "contract_change")
                .counter();
        assertThat(c.count()).isEqualTo(3d);
    }

    @Test
    @DisplayName("incInstanceFinished 携带 result 标签")
    void testIncInstanceFinished() {
        flowMetrics.incInstanceFinished("f1", "COMPLETED");
        flowMetrics.incInstanceFinished("f1", "REJECTED");
        Counter completed = registry.find("pmis_flow_instance_finished_total")
                .tag("flow_code", "f1")
                .tag("result", "COMPLETED")
                .counter();
        Counter rejected = registry.find("pmis_flow_instance_finished_total")
                .tag("flow_code", "f1")
                .tag("result", "REJECTED")
                .counter();
        assertThat(completed.count()).isEqualTo(1d);
        assertThat(rejected.count()).isEqualTo(1d);
    }

    @Test
    @DisplayName("所有任务操作 Counter 都能注册")
    void testAllTaskOperations() {
        flowMetrics.incTaskCreated("f1", "n1");
        flowMetrics.incTaskPassed("f1", "n1");
        flowMetrics.incTaskRejected("f1", "n1");
        flowMetrics.incTaskTransferred("f1", "n1");
        flowMetrics.incTaskDelegated("f1", "n1");
        flowMetrics.incTaskUrged("f1");
        flowMetrics.incTaskClaimed("f1", "n1");
        flowMetrics.incTaskSkipped("f1", "n1");
        flowMetrics.incTaskAutoHandled("f1", "n1", "AUTO_PASS");
        flowMetrics.incSlaTimeout("f1", "AUTO_PASS");
        flowMetrics.incStartError("f1", "NullPointerException");
        flowMetrics.incRecall("f1");
        assertThat(registry.find("pmis_flow_task_created_total").counter().count()).isEqualTo(1d);
        assertThat(registry.find("pmis_flow_task_passed_total").counter().count()).isEqualTo(1d);
        assertThat(registry.find("pmis_flow_task_rejected_total").counter().count()).isEqualTo(1d);
        assertThat(registry.find("pmis_flow_task_transferred_total").counter().count()).isEqualTo(1d);
        assertThat(registry.find("pmis_flow_task_delegated_total").counter().count()).isEqualTo(1d);
        assertThat(registry.find("pmis_flow_task_urged_total").counter().count()).isEqualTo(1d);
        assertThat(registry.find("pmis_flow_task_claimed_total").counter().count()).isEqualTo(1d);
        assertThat(registry.find("pmis_flow_task_skipped_total").counter().count()).isEqualTo(1d);
        assertThat(registry.find("pmis_flow_task_auto_handled_total").counter().count()).isEqualTo(1d);
        assertThat(registry.find("pmis_flow_sla_timeout_total").counter().count()).isEqualTo(1d);
        assertThat(registry.find("pmis_flow_start_error_total").counter().count()).isEqualTo(1d);
        assertThat(registry.find("pmis_flow_recall_total").counter().count()).isEqualTo(1d);
    }

    @Test
    @DisplayName("incInstanceSuspended/Activated 注册")
    void testSuspendActivate() {
        flowMetrics.incInstanceSuspended("f1");
        flowMetrics.incInstanceActivated("f1");
        assertThat(registry.find("pmis_flow_instance_suspended_total").counter().count()).isEqualTo(1d);
        assertThat(registry.find("pmis_flow_instance_activated_total").counter().count()).isEqualTo(1d);
    }

    @Test
    @DisplayName("recordInstanceDuration 记录 Timer，毫秒值 = endAt - startAt")
    void testRecordInstanceDuration() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setFlowCode("f1");
        ins.setStartAt(LocalDateTime.now().minusMinutes(5));
        ins.setEndAt(LocalDateTime.now());
        flowMetrics.recordInstanceDuration(ins, "COMPLETED");
        Timer t = registry.find("pmis_flow_instance_duration_ms")
                .tag("flow_code", "f1")
                .tag("result", "COMPLETED")
                .timer();
        assertThat(t).isNotNull();
        assertThat(t.count()).isEqualTo(1L);
        // 5 分钟 ≈ 300_000 ms
        assertThat(t.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isBetween(290_000d, 310_000d);
    }

    @Test
    @DisplayName("recordInstanceDuration startAt=null 静默返回")
    void testRecordInstanceDurationNullStart() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setFlowCode("f1");
        flowMetrics.recordInstanceDuration(ins, "COMPLETED");
        assertThat(registry.find("pmis_flow_instance_duration_ms").timers()).isEmpty();
    }

    @Test
    @DisplayName("recordTaskDuration 记录 Timer")
    void testRecordTaskDuration() {
        FlowTaskDO task = new FlowTaskDO();
        task.setFlowCode("f1");
        task.setNodeCode("n1");
        task.setCreatedAt(LocalDateTime.now().minusMinutes(3));
        task.setFinishAt(LocalDateTime.now());
        flowMetrics.recordTaskDuration(task, "PASSED");
        Timer t = registry.find("pmis_flow_task_duration_ms")
                .tag("flow_code", "f1")
                .tag("node_code", "n1")
                .tag("result", "PASSED")
                .timer();
        assertThat(t).isNotNull();
        assertThat(t.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isBetween(170_000d, 190_000d);
    }

    @Test
    @DisplayName("Gauge instance_running 从 mapper 拉取")
    void testInstanceRunningGauge() {
        when(instanceMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(7L);
        Gauge gauge = registry.find("pmis_flow_instance_running").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(7d);
    }

    @Test
    @DisplayName("Gauge task_pending 拉取 PENDING/CLAIMED 任务数")
    void testTaskPendingGauge() {
        when(taskMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(15L);
        Gauge gauge = registry.find("pmis_flow_task_pending").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(15d);
    }

    @Test
    @DisplayName("Gauge task_overdue 调用 taskMapper.countOverdue")
    void testTaskOverdueGauge() {
        when(taskMapper.countOverdue(any(), any())).thenReturn(3L);
        Gauge gauge = registry.find("pmis_flow_task_overdue").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(3d);
    }

    @Test
    @DisplayName("Gauge cc_unread 调用 ccMapper.countUnread")
    void testCcUnreadGauge() {
        when(ccMapper.countUnread()).thenReturn(42L);
        Gauge gauge = registry.find("pmis_flow_cc_unread").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(42d);
    }

    @Test
    @DisplayName("Gauge mapper 抛异常时返回 0，不抛")
    void testGaugeResilience() {
        when(instanceMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenThrow(new RuntimeException("db down"));
        Gauge gauge = registry.find("pmis_flow_instance_running").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(0d);
    }

    @Test
    @DisplayName("safe 空值替换为 unknown")
    void testSafeDefault() {
        flowMetrics.incInstanceCreated(null);
        flowMetrics.incInstanceCreated("");
        Counter c = registry.find("pmis_flow_instance_created_total")
                .tag("flow_code", "unknown")
                .counter();
        assertThat(c.count()).isEqualTo(2d);
    }

    @Test
    @DisplayName("withMetrics 包装器捕获异常并记录错误指标")
    void testWithMetrics() {
        assertThatThrownBy(() ->
                flowMetrics.<String>withMetrics("f1", "test", () -> {
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
        Counter c = registry.find("pmis_flow_start_error_total")
                .tag("flow_code", "f1")
                .tag("reason", "test:IllegalStateException")
                .counter();
        assertThat(c.count()).isEqualTo(1d);
    }

    @Test
    @DisplayName("withMetrics 正常路径不记录错误")
    void testWithMetricsNormal() {
        String result = flowMetrics.withMetrics("f1", "test", () -> "ok");
        assertThat(result).isEqualTo("ok");
        assertThat(registry.find("pmis_flow_start_error_total").counter()).isNull();
    }
}
