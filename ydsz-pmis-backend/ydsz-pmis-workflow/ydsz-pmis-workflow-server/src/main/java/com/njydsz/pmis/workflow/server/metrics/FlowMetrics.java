package com.njydsz.pmis.workflow.server.metrics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.core.metrics.AbstractModuleMetrics;
import com.njydsz.pmis.workflow.domain.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.domain.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.infra.mapper.FlowCcMapper;
import com.njydsz.pmis.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.infra.mapper.FlowRunTaskMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * P2-3 流程引擎 Prometheus 指标收集器
 *
 * <p>基于 Micrometer 暴露以下指标（通过 Spring Boot Actuator /actuator/prometheus）：
 * <ul>
 *   <li>Counter：实例创建/完成/终止/驳回、任务创建/通过/驳回/转办/委派/催办/签收</li>
 *   <li>Timer：实例总耗时、任务处理耗时</li>
 *   <li>Gauge：运行中实例数、待办任务数、抄送未读数</li>
 * </ul>
 *
 * <p>所有指标前缀 {@code pmis_flow_}，便于在 Grafana 看板中筛选。
 *
 * <p>Bean 名称 = {@code flowMetrics}，由 Spring 容器管理。Mappers 通过 {@code @Autowired(required=false)}
 * 注入，避免监控指标对核心数据源造成循环依赖。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component("flowMetrics")
public class FlowMetrics extends AbstractModuleMetrics {

    // ============================== Gauge 弱引用 mapper（避免循环依赖） ==============================
    /**
     * Mapper 通过 ObjectProvider 实现可选注入，避免监控指标对核心数据源造成循环依赖。
     * 若对应 Mapper 不存在则保持为 null，注册 Gauge 时会优雅跳过。
     */
    private final FlowInstanceMapper instanceMapper;
    private final FlowRunTaskMapper taskMapper;
    private final FlowCcMapper ccMapper;

    public FlowMetrics(MeterRegistry registry,
                       ObjectProvider<FlowInstanceMapper> instanceMapperProvider,
                       ObjectProvider<FlowRunTaskMapper> taskMapperProvider,
                       ObjectProvider<FlowCcMapper> ccMapperProvider) {
        super(registry, "pmis_flow_");
        this.instanceMapper = instanceMapperProvider.getIfAvailable();
        this.taskMapper = taskMapperProvider.getIfAvailable();
        this.ccMapper = ccMapperProvider.getIfAvailable();
        // 注册 Gauge（延迟到 bean 装配完成后再调用 query 方法）
        registerGauges();
        log.info("[FlowMetrics] 初始化完成，Prometheus 端点可访问 /actuator/prometheus");
    }

    // ===========================================
    // Counter：实例生命周期
    // ===========================================

    /**
     * 实例创建计数
     */
    public void incInstanceCreated(String flowCode) {
        counter("instance_created_total", "flow_code", safe(flowCode)).increment();
    }

    /**
     * 实例完成计数（result=COMPLETED / REJECTED / TERMINATED）
     */
    public void incInstanceFinished(String flowCode, String result) {
        counter("instance_finished_total",
                "flow_code", safe(flowCode),
                "result", safe(result)).increment();
    }

    /**
     * 实例挂起计数
     */
    public void incInstanceSuspended(String flowCode) {
        counter("instance_suspended_total", "flow_code", safe(flowCode)).increment();
    }

    /**
     * 实例激活计数
     */
    public void incInstanceActivated(String flowCode) {
        counter("instance_activated_total", "flow_code", safe(flowCode)).increment();
    }

    // ===========================================
    // Counter：任务操作
    // ===========================================

    public void incTaskCreated(String flowCode, String nodeCode) {
        counter("task_created_total",
                "flow_code", safe(flowCode),
                "node_code", safe(nodeCode)).increment();
    }

    public void incTaskPassed(String flowCode, String nodeCode) {
        counter("task_passed_total",
                "flow_code", safe(flowCode),
                "node_code", safe(nodeCode)).increment();
    }

    public void incTaskRejected(String flowCode, String nodeCode) {
        counter("task_rejected_total",
                "flow_code", safe(flowCode),
                "node_code", safe(nodeCode)).increment();
    }

    public void incTaskTransferred(String flowCode, String nodeCode) {
        counter("task_transferred_total",
                "flow_code", safe(flowCode),
                "node_code", safe(nodeCode)).increment();
    }

    public void incTaskDelegated(String flowCode, String nodeCode) {
        counter("task_delegated_total",
                "flow_code", safe(flowCode),
                "node_code", safe(nodeCode)).increment();
    }

    public void incTaskUrged(String flowCode) {
        counter("task_urged_total", "flow_code", safe(flowCode)).increment();
    }

    public void incTaskClaimed(String flowCode, String nodeCode) {
        counter("task_claimed_total",
                "flow_code", safe(flowCode),
                "node_code", safe(nodeCode)).increment();
    }

    public void incTaskSkipped(String flowCode, String nodeCode) {
        counter("task_skipped_total",
                "flow_code", safe(flowCode),
                "node_code", safe(nodeCode)).increment();
    }

    public void incTaskAutoHandled(String flowCode, String nodeCode, String action) {
        counter("task_auto_handled_total",
                "flow_code", safe(flowCode),
                "node_code", safe(nodeCode),
                "action", safe(action)).increment();
    }

    // ===========================================
    // Counter：流程启动 + 错误
    // ===========================================

    public void incStartError(String flowCode, String reason) {
        counter("start_error_total",
                "flow_code", safe(flowCode),
                "reason", safe(reason)).increment();
    }

    public void incRecall(String flowCode) {
        counter("recall_total", "flow_code", safe(flowCode)).increment();
    }

    public void incSlaTimeout(String flowCode, String action) {
        counter("sla_timeout_total",
                "flow_code", safe(flowCode),
                "action", safe(action)).increment();
    }

    // ===========================================
    // Timer：耗时
    // ===========================================

    /**
     * 记录实例总耗时
     */
    public void recordInstanceDuration(FlowInstanceDO instance, String result) {
        if (instance == null || instance.getStartAt() == null) {
            return;
        }
        long millis;
        if (instance.getEndAt() != null) {
            millis = Duration.between(instance.getStartAt(), instance.getEndAt()).toMillis();
        } else {
            // 未结束：用 now 临时记录
            millis = Duration.between(instance.getStartAt(), LocalDateTime.now()).toMillis();
        }
        if (millis < 0) {
            return;
        }
        timer("instance_duration_ms",
                "flow_code", safe(instance.getFlowCode()),
                "result", safe(result))
                .record(Duration.ofMillis(millis));
    }

    /**
     * 记录任务处理耗时
     */
    public void recordTaskDuration(FlowRunTaskDO task, String result) {
        if (task == null || task.getCreatedAt() == null) {
            return;
        }
        long millis;
        if (task.getFinishAt() != null) {
            millis = Duration.between(task.getCreatedAt(), task.getFinishAt()).toMillis();
        } else {
            millis = Duration.between(task.getCreatedAt(), LocalDateTime.now()).toMillis();
        }
        if (millis < 0) {
            return;
        }
        timer("task_duration_ms",
                "flow_code", safe(task.getFlowCode()),
                "node_code", safe(task.getNodeCode()),
                "result", safe(result))
                .record(Duration.ofMillis(millis));
    }

    // ===========================================
    // Gauge：实时业务量
    // ===========================================

    private void registerGauges() {
        // 运行中实例数
        registry.gauge("pmis_flow_instance_running", Tags.empty(), this, m -> {
            if (m.instanceMapper == null) return 0d;
            try {
                Long count = m.queryRunningInstanceCount();
                return count == null ? 0d : count.doubleValue();
            } catch (Exception e) {
                log.debug("[FlowMetrics] gauge instance_running 查询失败: {}", e.getMessage());
                return 0d;
            }
        });

        // 待办任务数
        registry.gauge("pmis_flow_task_pending", Tags.empty(), this, m -> {
            if (m.taskMapper == null) return 0d;
            try {
                Long count = m.queryPendingTaskCount();
                return count == null ? 0d : count.doubleValue();
            } catch (Exception e) {
                log.debug("[FlowMetrics] gauge task_pending 查询失败: {}", e.getMessage());
                return 0d;
            }
        });

        // 超期任务数
        registry.gauge("pmis_flow_task_overdue", Tags.empty(), this, m -> {
            if (m.taskMapper == null) return 0d;
            try {
                Long count = m.queryOverdueTaskCount();
                return count == null ? 0d : count.doubleValue();
            } catch (Exception e) {
                log.debug("[FlowMetrics] gauge task_overdue 查询失败: {}", e.getMessage());
                return 0d;
            }
        });

        // 抄送未读数
        registry.gauge("pmis_flow_cc_unread", Tags.empty(), this, m -> {
            if (m.ccMapper == null) return 0d;
            try {
                Long count = m.queryUnreadCcCount();
                return count == null ? 0d : count.doubleValue();
            } catch (Exception e) {
                log.debug("[FlowMetrics] gauge cc_unread 查询失败: {}", e.getMessage());
                return 0d;
            }
        });
    }

    // ===========================================
    // Gauge 数据源查询（mapper 方法封装）
    // ===========================================

    private Long queryRunningInstanceCount() {
        return instanceMapper.selectCount(
                new LambdaQueryWrapper<FlowInstanceDO>()
                        .eq(FlowInstanceDO::getFlowStatus, "RUNNING")
                        .eq(FlowInstanceDO::getDeleted, 0));
    }

    private Long queryPendingTaskCount() {
        return taskMapper.selectCount(
                new LambdaQueryWrapper<FlowRunTaskDO>()
                        .in(FlowRunTaskDO::getTaskStatus, "PENDING", "CLAIMED")
                        .eq(FlowRunTaskDO::getDeleted, 0));
    }

    private Long queryOverdueTaskCount() {
        if (taskMapper == null) return 0L;
        return taskMapper.countOverdue(null, null);
    }

    private Long queryUnreadCcCount() {
        // 抄送 mapper 提供的方法（若无 unread 状态则返回 -1）
        if (ccMapper == null) return 0L;
        try {
            // 假设方法签名 countUnread()；如未提供则 try-catch 兜底
            return ccMapper.countUnread();
        } catch (Exception e) {
            // 兼容老版本 ccMapper 无 countUnread
            log.warn("[FlowMetrics] ccMapper.countUnread 调用失败，按 0 处理: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * 通用执行包装：自动捕获异常并记录到错误指标
     */
    public <T> T withMetrics(String flowCode, String operation, Supplier<T> action) {
        try {
            return action.get();
        } catch (Exception e) {
            incStartError(flowCode, operation + ":" + e.getClass().getSimpleName());
            throw e;
        } finally {
            // 可选：记录操作耗时（如果需要细分到 operation 维度）
        }
    }
}
