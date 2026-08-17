package com.njydsz.workflow.server.metrics;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

import com.njydsz.common.sentry.adapter.SentryMetricsAdapter;
import com.njydsz.workflow.infra.entity.FlowInstanceDO;
import com.njydsz.workflow.infra.entity.FlowRunTaskDO;
import com.njydsz.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;

/**
 * 流程引擎 Prometheus 指标收集器
 *
 * <p>基于 Micrometer 暴露 6 个核心指标（通过 Spring Boot Actuator /actuator/prometheus）：
 *
 * <ul>
 *   <li><b>Counter（3 个）</b>：
 *       <ul>
 *         <li>{@code ydsz_flow_instance_total{status}} — 流程实例计数（created/completed/rejected/terminated/suspended/activated）
 *         <li>{@code ydsz_flow_task_total{action}} — 任务操作计数（created/passed/rejected/transferred/delegated/claimed/skipped/auto_handled/urged）
 *         <li>{@code ydsz_flow_error_total{type}} — 错误计数（start_error/sla_timeout/form_validation/...）
 *       </ul>
 *   <li><b>Timer（2 个）</b>：
 *       <ul>
 *         <li>{@code ydsz_flow_instance_duration_ms{flow_code,result}} — 流程实例总耗时
 *         <li>{@code ydsz_flow_task_duration_ms{flow_code,node_code,result}} — 任务处理耗时
 *       </ul>
 *   <li><b>Gauge（1 个）</b>：
 *       <ul>
 *         <li>{@code ydsz_flow_count{type}} — 实时业务量（type=instance_running/task_pending/task_overdue）
 *       </ul>
 * </ul>
 *
 * <p>所有指标前缀 {@code ydsz_flow_}，便于在 Grafana 看板中筛选。
 *
 * <p><b>标签基数约定：</b>所有标签值必须来自有限枚举（flow_code、node_code、status、action、type），
 * <b>严禁</b>把 instanceId、userId、异常 message 等无界值传入标签位，否则会造成 Prometheus 时间序列爆炸。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@ConditionalOnClass(MeterRegistry.class)
public class FlowMetrics extends SentryMetricsAdapter {

  /** Gauge 查询 30s TTL 缓存（避免 Prometheus 每次抓取都打 DB） */
  private static final long GAUGE_CACHE_TTL_NS = 30_000_000_000L;

  /** Gauge 类型标签值：运行中实例数 */
  public static final String TYPE_INSTANCE_RUNNING = "instance_running";

  /** Gauge 类型标签值：待办任务数 */
  public static final String TYPE_TASK_PENDING = "task_pending";

  /** Gauge 类型标签值：超期任务数 */
  public static final String TYPE_TASK_OVERDUE = "task_overdue";

  private final FlowInstanceMapper instanceMapper;
  private final FlowRunTaskMapper taskMapper;

  private final AtomicReference<GaugeSnapshot> gaugeCache = new AtomicReference<>();

  /** Gauge 快照数据 */
  private record GaugeSnapshot(
      long runningInstances,
      long pendingTasks,
      long overdueTasks,
      long cachedAtNanos) {}

  public FlowMetrics(
      ObjectProvider<FlowInstanceMapper> instanceMapperProvider,
      ObjectProvider<FlowRunTaskMapper> taskMapperProvider) {
    super("ydsz_flow_");
    this.instanceMapper = instanceMapperProvider.getIfAvailable();
    this.taskMapper = taskMapperProvider.getIfAvailable();
    registerGauges();
  }

  // ===========================================
  // Counter：流程实例（按 status 标签区分）
  // ===========================================

  /**
   * 递增流程实例计数。
   *
   * @param flowCode 流程定义编码
   * @param status 实例状态：created/completed/rejected/terminated/suspended/activated
   */
  public void incInstance(String flowCode, String status) {
    counter("instance_total", "flow_code", safe(flowCode), "status", safe(status)).increment();
  }

  // ===========================================
  // Counter：任务操作（按 action 标签区分）
  // ===========================================

  /**
   * 递增任务操作计数。
   *
   * @param flowCode 流程定义编码
   * @param nodeCode 节点编码
   * @param action 操作类型：created/passed/rejected/transferred/delegated/claimed/skipped/auto_handled/urged
   */
  public void incTask(String flowCode, String nodeCode, String action) {
    counter(
            "task_total",
            "flow_code",
            safe(flowCode),
            "node_code",
            safe(nodeCode),
            "action",
            safe(action))
        .increment();
  }

  // ===========================================
  // Counter：错误（按 type 标签区分）
  // ===========================================

  /**
   * 递增错误计数。
   *
   * @param flowCode 流程定义编码
   * @param type 错误类型：start_error/sla_timeout/form_validation/...
   */
  public void incError(String flowCode, String type) {
    counter("error_total", "flow_code", safe(flowCode), "type", safe(type)).increment();
  }

  // ===========================================
  // Timer：耗时
  // ===========================================

  /**
   * 记录流程实例总耗时。
   *
   * @param instance 流程实例
   * @param result 结果状态
   */
  public void recordInstanceDuration(FlowInstanceDO instance, String result) {
    if (instance == null || instance.getStartAt() == null) {
      return;
    }
    long millis;
    if (instance.getEndAt() != null) {
      millis = Duration.between(instance.getStartAt(), instance.getEndAt()).toMillis();
    } else {
      millis = Duration.between(instance.getStartAt(), LocalDateTime.now()).toMillis();
    }
    if (millis < 0) {
      return;
    }
    timer("instance_duration_ms", "flow_code", safe(instance.getFlowCode()), "result", safe(result))
        .record(Duration.ofMillis(millis));
  }

  /**
   * 记录任务处理耗时。
   *
   * @param task 任务
   * @param result 结果状态
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
    timer(
            "task_duration_ms",
            "flow_code",
            safe(task.getFlowCode()),
            "node_code",
            safe(task.getNodeCode()),
            "result",
            safe(result))
        .record(Duration.ofMillis(millis));
  }

  // ===========================================
  // Gauge：实时业务量（合并为 1 个指标 + type 标签）
  // ===========================================

  private void registerGauges() {
    // P2-2: 将 3 个独立 Gauge 合并为 1 个，通过 type 标签区分，减少指标数量
    gauge("count", () -> (double) getGaugeSnapshot().runningInstances(), "type", TYPE_INSTANCE_RUNNING);
    gauge("count", () -> (double) getGaugeSnapshot().pendingTasks(), "type", TYPE_TASK_PENDING);
    gauge("count", () -> (double) getGaugeSnapshot().overdueTasks(), "type", TYPE_TASK_OVERDUE);
  }

  /**
   * 刷新 Gauge 值（由定时任务调用）。
   *
   * <p>查询 DB 获取最新指标值并更新到缓存，Prometheus 抓取时自动读取。
   */
  public void refreshGauges() {
    // 触发缓存刷新，实际值在 Prometheus 抓取时通过 getGaugeSnapshot() 获取
    getGaugeSnapshot();
  }

  private GaugeSnapshot getGaugeSnapshot() {
    long now = System.nanoTime();
    GaugeSnapshot snapshot = gaugeCache.get();
    if (snapshot != null && (now - snapshot.cachedAtNanos()) < GAUGE_CACHE_TTL_NS) {
      return snapshot;
    }
    long running = safeQuery(this::queryRunningInstanceCount);
    long pending = safeQuery(this::queryPendingTaskCount);
    long overdue = safeQuery(this::queryOverdueTaskCount);
    GaugeSnapshot fresh = new GaugeSnapshot(running, pending, overdue, now);
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

  private Long queryRunningInstanceCount() {
    if (instanceMapper == null) return 0L;
    return instanceMapper.selectCount(
        new LambdaQueryWrapper<FlowInstanceDO>()
            .eq(FlowInstanceDO::getFlowStatus, "RUNNING")
            .eq(FlowInstanceDO::getDeleted, 0));
  }

  private Long queryPendingTaskCount() {
    if (taskMapper == null) return 0L;
    return taskMapper.selectCount(
        new LambdaQueryWrapper<FlowRunTaskDO>()
            .in(FlowRunTaskDO::getTaskStatus, "PENDING", "CLAIMED")
            .eq(FlowRunTaskDO::getDeleted, 0));
  }

  private Long queryOverdueTaskCount() {
    if (taskMapper == null) return 0L;
    return taskMapper.countOverdue(null, null);
  }
}
