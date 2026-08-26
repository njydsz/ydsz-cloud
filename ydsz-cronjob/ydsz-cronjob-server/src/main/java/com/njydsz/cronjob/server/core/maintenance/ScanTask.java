package com.njydsz.cronjob.server.core.maintenance;

/**
 * 扫描任务接口（P2-O3：统一扫描器契约）。
 *
 * <p>所有周期性扫描任务（任务派发、异常修复、告警扫描、依赖巡检等）统一实现此接口，
 * 由 {@link MaintenanceScheduler} 统一调度、Leader 校验、分布式锁协调。
 *
 * <p>实现类应为 Spring Bean，并通过 {@link #name()} 提供唯一标识（用于日志、指标、锁 key）。
 *
 * <h3>设计要点</h3>
 *
 * <ul>
 *   <li><b>单一职责</b>：每个 ScanTask 仅负责一类扫描逻辑，不混合多种职责
 *   <li><b>容错隔离</b>：单个 ScanTask 执行异常不影响其他任务（由 MaintenanceScheduler 保证）
 *   <li><b>幂等安全</b>：实现类应支持多节点并发调用（MaintenanceScheduler 通过分布式锁保证单节点执行，
 *       但实现类自身也应为幂等操作以防锁失效）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ScanTask {

  /**
   * 扫描任务名称（唯一标识，用于日志、指标、锁 key）。
   *
   * <p>命名规范：{@code cronjob:scan:{功能描述}}，例如 {@code cronjob:scan:job-dispatch}、
   * {@code cronjob:scan:anomaly-recovery}。
   *
   * @return 任务名称（非空）
   */
  String name();

  /** 默认扫描间隔（毫秒，5s） */
  long DEFAULT_SCAN_INTERVAL_MS = 5000L;

  /**
   * 执行一次扫描。
   *
   * <p>实现类应自行处理所有业务异常，不应抛出checked异常。unchecked异常由
   * {@link MaintenanceScheduler} 捕获并记录日志，不会中断其他扫描任务。
   *
   * <p><b>注意</b>：此方法可能被多节点并发调用（在网络分区等极端场景下），实现类应保证幂等性。
   */
  void scan();

  /**
   * 扫描间隔（毫秒）。
   *
   * <p>默认 5000ms（5s），子类可覆写以使用不同的扫描频率。
   *
   * @return 扫描间隔（毫秒，必须 > 0）
   */
  default long intervalMs() {
    return DEFAULT_SCAN_INTERVAL_MS;
  }

  /**
   * 分布式锁 key（用于多节点互斥）。
   *
   * <p>默认使用 {@code cronjob:scan:{name()}}，子类可覆写以使用自定义锁 key。
   *
   * @return 锁 key（非空）
   */
  default String lockKey() {
    return "cronjob:scan:" + name();
  }

  /**
   * 是否启用 Leader 校验。
   *
   * <p>默认 true，仅 Leader 节点执行扫描。某些特殊扫描（如本节点心跳）可覆写为 false。
   *
   * @return true 表示仅 Leader 节点执行
   */
  default boolean requireLeader() {
    return true;
  }
}
