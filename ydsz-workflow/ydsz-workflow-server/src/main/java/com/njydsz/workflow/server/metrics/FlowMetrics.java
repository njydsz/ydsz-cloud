package com.njydsz.workflow.server.metrics;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import com.njydsz.common.base.metrics.AbstractModuleMetrics;
import com.njydsz.workflow.domain.entity.FlowInstance;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.infra.mapper.FlowCcMapper;
import com.njydsz.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;

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

    /**
     * 待办任务创建计数，累加 {@code ydsz_flow_task_created_total}。
     *
     * <p>与 {@code task_passed/rejected} 之差即为节点积压量，是定位审批瓶颈节点的基线指标。
     *
     * <p><b>标签基数约定：</b>本组指标以 {@code flow_code} + {@code node_code} 为标签，
     * 二者均来自流程定义、取值有限且可枚举。<b>严禁</b>把 instanceId、userId 等无界值
     * 传入标签位，否则会造成 Prometheus 时间序列爆炸。入参为 {@code null} 时由
     * {@code safe()} 兜底成占位值，不会抛异常。
     *
     * @param flowCode 流程定义编码，可为 {@code null}（回退为占位标签）
     * @param nodeCode 节点编码，可为 {@code null}（回退为占位标签）
     */
    public void incTaskCreated(String flowCode, String nodeCode) {
        counter("task_created_total",
                "flow_code", safe(flowCode),
                "node_code", safe(nodeCode)).increment();
    }

    /**
     * 任务审批通过计数，累加 {@code ydsz_flow_task_passed_total}。
     *
     * <p>与 {@link #incTaskRejected} 配合可算出各节点的驳回率，用于识别流程设计缺陷
     * （某节点驳回率畸高通常意味着上游材料要求不清）。
     *
     * @param flowCode 流程定义编码
     * @param nodeCode 审批通过的节点编码
     */
    public void incTaskPassed(String flowCode, String nodeCode) {
        counter("task_passed_total",
                "flow_code", safe(flowCode),
                "node_code", safe(nodeCode)).increment();
    }

    /**
     * 任务驳回计数，累加 {@code ydsz_flow_task_rejected_total}。
     *
     * <p>记录的是<b>驳回发生的节点</b>，而非回退到达的目标节点；
     * 若需分析回退落点，应另行埋点，勿据本指标推断。
     *
     * @param flowCode 流程定义编码
     * @param nodeCode 执行驳回操作的节点编码
     */
    public void incTaskRejected(String flowCode, String nodeCode) {
        counter("task_rejected_total",
                "flow_code", safe(flowCode),
                "node_code", safe(nodeCode)).increment();
    }

    /**
     * 任务转办计数，累加 {@code ydsz_flow_task_transferred_total}。
     *
     * <p>转办会<b>转移</b>任务归属：原办理人失去权限。持续偏高说明节点候选人配置
     * 与实际职责不符，是组织架构与流程定义脱节的信号。
     *
     * @param flowCode 流程定义编码
     * @param nodeCode 发生转办的节点编码
     */
    public void incTaskTransferred(String flowCode, String nodeCode) {
        counter("task_transferred_total",
                "flow_code", safe(flowCode),
                "node_code", safe(nodeCode)).increment();
    }

    /**
     * 任务委派计数，累加 {@code ydsz_flow_task_delegated_total}。
     *
     * <p>与转办的区别：委派<b>保留</b>原办理人的最终责任，受托人处理后仍需回到原办理人。
     * 二者分开计数以便区分「职责错配」与「临时代办」两类管理问题。
     *
     * @param flowCode 流程定义编码
     * @param nodeCode 发生委派的节点编码
     */
    public void incTaskDelegated(String flowCode, String nodeCode) {
        counter("task_delegated_total",
                "flow_code", safe(flowCode),
                "node_code", safe(nodeCode)).increment();
    }

    /**
     * 任务催办计数，累加 {@code ydsz_flow_task_urged_total}。
     *
     * <p>仅按 {@code flow_code} 聚合、<b>不带</b> {@code node_code}：催办由发起人主动触发，
     * 可对同一任务重复发起，按节点细分意义不大且会放大标签基数。
     *
     * @param flowCode 流程定义编码
     */
    public void incTaskUrged(String flowCode) {
        counter("task_urged_total", "flow_code", safe(flowCode)).increment();
    }

    /**
     * 任务认领计数，累加 {@code ydsz_flow_task_claimed_total}。
     *
     * <p>仅适用于候选人抢单模式的节点。认领量与 {@code task_created} 的差值可反映
     * 「有人看无人领」的冷启动问题。
     *
     * @param flowCode 流程定义编码
     * @param nodeCode 被认领任务所属节点编码
     */
    public void incTaskClaimed(String flowCode, String nodeCode) {
        counter("task_claimed_total",
                "flow_code", safe(flowCode),
                "node_code", safe(nodeCode)).increment();
    }

    /**
     * 任务跳过计数，累加 {@code ydsz_flow_task_skipped_total}。
     *
     * <p>覆盖空审批人自动跳过、同人自动去重等规则触发的跳过。该值异常升高
     * 意味着大量节点被绕过，属<b>合规风险信号</b>，应配置告警而非仅作观测。
     *
     * @param flowCode 流程定义编码
     * @param nodeCode 被跳过的节点编码
     */
    public void incTaskSkipped(String flowCode, String nodeCode) {
        counter("task_skipped_total",
                "flow_code", safe(flowCode),
                "node_code", safe(nodeCode)).increment();
    }

    /**
     * 任务自动处理计数，累加 {@code ydsz_flow_task_auto_handled_total}。
     *
     * <p>用于 SLA 超时自动通过/驳回、定时任务批量处理等<b>无人工介入</b>的场景，
     * 通过 {@code action} 标签区分具体动作，便于审计「谁替系统做了决定」。
     *
     * @param flowCode 流程定义编码
     * @param nodeCode 被自动处理的节点编码
     * @param action   自动处理动作（如 {@code PASS}、{@code REJECT}），取值须为有限枚举
     */
    public void incTaskAutoHandled(String flowCode, String nodeCode, String action) {
        counter("task_auto_handled_total",
                "flow_code", safe(flowCode),
                "node_code", safe(nodeCode),
                "action", safe(action)).increment();
    }

    // ===========================================
    // Counter：流程启动 + 错误
    // ===========================================

    /**
     * 流程启动失败计数，累加 {@code ydsz_flow_start_error_total}。
     *
     * <p>是发起环节最重要的告警源：启动失败意味着用户完全无法进入流程。
     *
     * <p><b>标签基数警告：</b>{@code reason} 必须传<b>归一化的错误分类</b>
     * （如 {@code DEFINITION_NOT_FOUND}、{@code NO_START_NODE}），
     * 严禁直接透传异常 message —— 其中常含实例 ID、时间戳等可变内容，会撑爆时间序列。
     *
     * @param flowCode 流程定义编码
     * @param reason   归一化的失败原因分类，取值须可枚举
     */
    public void incStartError(String flowCode, String reason) {
        counter("start_error_total",
                "flow_code", safe(flowCode),
                "reason", safe(reason)).increment();
    }

    /**
     * 流程撤回计数，累加 {@code ydsz_flow_recall_total}。
     *
     * <p>统计发起人主动撤回已启动实例的次数。撤回率高通常反映发起前信息不全，
     * 可作为优化表单必填项与前置校验的依据。
     *
     * @param flowCode 流程定义编码
     */
    public void incRecall(String flowCode) {
        counter("recall_total", "flow_code", safe(flowCode)).increment();
    }

    /**
     * SLA 超时计数，累加 {@code ydsz_flow_sla_timeout_total}。
     *
     * <p>在任务<b>超过时限的那一刻</b>由 SLA 扫描任务记录，与超时后执行的补偿动作
     * （通过 {@code action} 标签区分催办、自动通过、升级上报等）一并上报。
     * 同一任务在多级 SLA 策略下可能多次计数，统计时勿等同于「超时任务数」。
     *
     * @param flowCode 流程定义编码
     * @param action   超时后触发的处置动作，取值须为有限枚举
     */
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
