package com.njydsz.cronjob.domain.job;

/**
 * 任务执行上下文作用域（AutoCloseable）。
 *
 * <p>为 {@link JobExecutionContext} 的 ThreadLocal 状态提供 try-with-resources 语法，
 * 确保作用域结束时必定清理分片上下文与日志器，杜绝因遗漏 {@code clear()} 导致的：
 *
 * <ul>
 *   <li>上下文串扰：线程复用时读到上一个任务的日志器/分片信息
 *   <li>内存泄漏：InheritableThreadLocal 持有已结束任务的引用
 * </ul>
 *
 * <h3>用法示例</h3>
 *
 * <pre>{@code
 * try (ExecutionContextScope scope = ExecutionContextScope.of(jobLogger, shardingCtx)) {
 *     // 业务逻辑
 *     handler.execute(paramsJson, ctx);
 * } finally {
 *     // 日志器等清理（scope.close() 已在 try 退出时调用）
 * }
 * }</pre>
 *
 * <p>与直接使用 {@link JobExecutionContext#setLogger(JobLogger)} / {@link
 * JobExecutionContext#setShardingContext(ShardingContext)} 相比，本作用域提供结构化生命周期管理；旧 API 仍可独立使用，
 * 但推荐在新代码中统一使用 {@link #of(JobLogger, ShardingContext)} 工厂方法。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class ExecutionContextScope implements AutoCloseable {

  /** 私有构造，通过 {@link #of(JobLogger, ShardingContext)} 获取实例。 */
  private ExecutionContextScope() {}

  /**
   * 创建执行上下文作用域并初始化 ThreadLocal。
   *
   * <p>调用后效果等价于：
   *
   * <pre>
   * JobExecutionContext.setLogger(logger);
   * JobExecutionContext.setShardingContext(shardingContext);
   * </pre>
   *
   * @param logger 在线日志器（可 null）
   * @param shardingContext 分片上下文（可 null）
   * @return 作用域实例；退出 try 块时自动清理 ThreadLocal
   */
  public static ExecutionContextScope of(JobLogger logger, ShardingContext shardingContext) {
    if (logger != null) {
      JobExecutionContext.setLogger(logger);
    }
    if (shardingContext != null) {
      JobExecutionContext.setShardingContext(shardingContext);
    }
    return new ExecutionContextScope();
  }

  /**
   * 创建仅含日志器的作用域（非分片场景）。
   *
   * @param logger 在线日志器（可 null）
   * @return 作用域实例
   */
  public static ExecutionContextScope ofLogger(JobLogger logger) {
    if (logger != null) {
      JobExecutionContext.setLogger(logger);
    }
    return new ExecutionContextScope();
  }

  /**
   * 关闭作用域，清理当前线程的分片上下文与日志器。
   *
   * <p>幂等调用：多次 close 不会抛异常。在 try-with-resources 中自动调用。
   */
  @Override
  public void close() {
    JobExecutionContext.clear();
  }
}
