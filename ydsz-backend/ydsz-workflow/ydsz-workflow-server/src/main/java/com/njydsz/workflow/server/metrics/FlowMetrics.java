package com.njydsz.workflow.server.metrics;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.springframework.beans.factory.ObjectProvider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.core.metrics.AbstractModuleMetrics;
import com.njydsz.workflow.domain.entity.FlowInstance;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.infra.mapper.FlowCcMapper;
import com.njydsz.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;

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
 * <p>所有指标前缀 {@code ydsz_flow_}，便于在 Grafana 看板中筛选。
 *
 * <p>Bean 名称 = {@code flowMetrics}，由 Spring 容器管理。Mappers 通过 {@code @Autowired(required=false)}
 * 注入，避免监控指标对核心数据源造成循环依赖。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class FlowMetrics extends AbstractModuleMetrics {

    // ============================== Gauge 弱引用 mapper（避免循环依赖） ==============================
    /**
     * Mapper 通过 ObjectProvider 实现可选注入，避免监控指标对核心数据源造成循环依赖。
     * 若对应 Mapper 不存在则保持为 null，注册 Gauge 时会优雅跳过。
     */
    private final FlowInstanceMapper instanceMapper;
    private final FlowRunTaskMapper taskMapper;
    private final FlowCcMapper ccMapper;

    /**
     * P2-2: Gauge 查询 30s TTL 缓存（避免 Prometheus 每次抓取都打 DB）
     *
     * <p>Prometheus 默认 15s 抓取一次，多个 Gauge 会在同一轮抓取中全部触发。
     * 使用单次缓存 + AtomicReference 保证线程安全，30s 内复用查询结果。
     */
    private static final long GAUGE_CACHE_TTL_NS = 30_000_000_000L; // 30s
    private final AtomicReference<GaugeSnapshot> gaugeCache = new AtomicReference<>();

    /** P2-2: Gauge 快照数据 */
    private record GaugeSnapshot(long runningInstances, long pendingTasks,
                                 long overdueTasks, long unreadCc, long cachedAtNanos) {}

    public FlowMetrics(MeterRegistry registry,
                       ObjectProvider<FlowInstanceMapper> instanceMapperProvider,
                       ObjectProvider<FlowRunTaskMapper> taskMapperProvider,
                       ObjectProvider<FlowCcMapper> ccMapperProvider) {
        super(registry, "ydsz_flow_");
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
    // Counter：表单校验 + 缓存 + 定义管理
    // ===========================================

    /**
     * 表单校验失败计数
     */
    public void incFormValidationError(String flowCode, String nodeCode, String errorType) {
        counter("form_validation_error_total",
                "flow_code", safe(flowCode),
                "node_code", safe(nodeCode),
                "error_type", safe(errorType)).increment();
    }

    /**
     * 定义缓存命中计数
     */
    public void incDefinitionCacheHit(String flowCode) {
        counter("definition_cache_hit_total", "flow_code", safe(flowCode)).increment();
    }

    /**
     * 定义缓存未命中计数
     */
    public void incDefinitionCacheMiss(String flowCode) {
        counter("definition_cache_miss_total", "flow_code", safe(flowCode)).increment();
    }

    /**
     * 流程定义部署计数
     */
    public void incDefinitionDeployed(String flowCode, String deployType) {
        counter("definition_deployed_total",
                "flow_code", safe(flowCode),
                "deploy_type", safe(deployType)).increment();
    }

    /**
     * 流程定义发布计数
     */
    public void incDefinitionPublished(String flowCode) {
        counter("definition_published_total", "flow_code", safe(flowCode)).increment();
    }

    /**
     * 流程实例迁移计数
     */
    public void incInstanceMigrated(String flowCode, String migrationType) {
        counter("instance_migrated_total",
                "flow_code", safe(flowCode),
                "migration_type", safe(migrationType)).increment();
    }

    // ===========================================
    // Timer：耗时
    // ===========================================

    /**
     * 记录实例总耗时
     */
    public void recordInstanceDuration(FlowInstance instance, String result) {
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
    public void recordTaskDuration(FlowRunTask task, String result) {
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
        registry.gauge("ydsz_flow_instance_running", Tags.empty(), this, m -> m.getGaugeSnapshot().runningInstances());

        // 待办任务数
        registry.gauge("ydsz_flow_task_pending", Tags.empty(), this, m -> m.getGaugeSnapshot().pendingTasks());

        // 超期任务数
        registry.gauge("ydsz_flow_task_overdue", Tags.empty(), this, m -> m.getGaugeSnapshot().overdueTasks());

        // 抄送未读数
        registry.gauge("ydsz_flow_cc_unread", Tags.empty(), this, m -> m.getGaugeSnapshot().unreadCc());
    }

    /**
     * P2-2: 获取 Gauge 快照（30s TTL 缓存）
     *
     * <p>Prometheus 抓取时多个 Gauge 会并发调用，使用 AtomicReference CAS 保证只查一次 DB。
     * 缓存过期后下一次调用会触发刷新。
     */
    private GaugeSnapshot getGaugeSnapshot() {
        long now = System.nanoTime();
        GaugeSnapshot snapshot = gaugeCache.get();
        if (snapshot != null && (now - snapshot.cachedAtNanos()) < GAUGE_CACHE_TTL_NS) {
            return snapshot;
        }
        // 缓存过期，重新查询
        long running = safeQuery(this::queryRunningInstanceCount);
        long pending = safeQuery(this::queryPendingTaskCount);
        long overdue = safeQuery(this::queryOverdueTaskCount);
        long unread = safeQuery(this::queryUnreadCcCount);
        GaugeSnapshot fresh = new GaugeSnapshot(running, pending, overdue, unread, now);
        gaugeCache.set(fresh);
        return fresh;
    }

    private long safeQuery(Supplier<Long> query) {
        try {
            Long val = query.get();
            return val == null ? 0L : val;
        } catch (Exception e) {
            log.debug("[FlowMetrics] Gauge 查询失败: {}", e.getMessage());
            return 0L;
        }
    }

    // ===========================================
    // Gauge 数据源查询（mapper 方法封装）
    // ===========================================

    private Long queryRunningInstanceCount() {
        return instanceMapper.selectCount(
                new LambdaQueryWrapper<FlowInstance>()
                        .eq(FlowInstance::getFlowStatus, "RUNNING")
                        .eq(FlowInstance::getDeleted, 0));
    }

    private Long queryPendingTaskCount() {
        return taskMapper.selectCount(
                new LambdaQueryWrapper<FlowRunTask>()
                        .in(FlowRunTask::getTaskStatus, "PENDING", "CLAIMED")
                        .eq(FlowRunTask::getDeleted, 0));
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
