package com.njydsz.common.seata.impl;

import com.njydsz.common.seata.api.SagaStateMachineLog;
import com.njydsz.common.seata.api.SagaStateMachineLogStore;
import com.njydsz.common.seata.api.SagaStep;
import com.njydsz.common.seata.api.StepTimeoutException;
import com.njydsz.common.seata.api.TransactionType;
import com.njydsz.common.seata.audit.TransactionAuditLogger;
import com.njydsz.common.seata.config.SeataProperties;
import com.njydsz.common.seata.metrics.SeataMetrics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * SAGA 事务编排器
 *
 * <p>实现多步骤 SAGA 长事务编排：
 *
 * <ol>
 *   <li>按顺序执行所有正向操作
 *   <li>若某步骤失败，逆序执行已完成步骤的补偿操作
 *   <li>补偿失败时按配置重试
 * </ol>
 *
 * <p><b>P0-3 修复</b>：此前 {@code executeWithCompensation} 只是单步 try-catch， 无法处理多步编排。现在通过 {@link
 * SagaStep} 链 + 逆序补偿实现真正的 SAGA 语义。
 *
 * <p><b>P1-4 修复</b>：集成 {@link SagaStateMachineLogStore} 持久化状态机日志， 支持服务崩溃后恢复未完成的 SAGA 事务。
 *
 * <p><b>P0-5 预留</b>：当前为同步编排，可扩展为异步状态机驱动。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SagaOrchestrator extends AbstractTransactionManager {

  private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

  /** 步骤超时控制的共享调度线程池（单线程，按步骤提交延迟任务） */
  // CHECKSTYLE.OFF: ThreadPoolCreate - SAGA 步骤超时控制专用短生命周期调度池，随 JVM 关闭
  private static final ScheduledExecutorService TIMEOUT_SCHEDULER =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "saga-step-timeout");
            t.setDaemon(true);
            return t;
          });

  // CHECKSTYLE.ON: ThreadPoolCreate

  // 静态初始化块：注册 JVM 关闭钩子，确保线程池在应用退出时被关闭
  static {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  if (TIMEOUT_SCHEDULER != null && !TIMEOUT_SCHEDULER.isShutdown()) {
                    TIMEOUT_SCHEDULER.shutdown();
                  }
                },
                "saga-step-timeout-shutdown"));
  }

  private final SeataProperties properties;
  private final ObjectProvider<SagaStateMachineLogStore> stateMachineLogStoreProvider;

  /**
   * 构造 SAGA 事务编排器
   *
   * @param properties Seata 配置属性
   * @param metricsProvider 指标采集提供者（可选）
   * @param auditProvider 审计日志提供者（可选）
   */
  public SagaOrchestrator(
      SeataProperties properties,
      ObjectProvider<SeataMetrics> metricsProvider,
      ObjectProvider<TransactionAuditLogger> auditProvider) {
    this(properties, metricsProvider, auditProvider, null);
  }

  /**
   * 构造 SAGA 事务编排器（带状态机日志存储）
   *
   * @param properties Seata 配置属性
   * @param metricsProvider 指标采集提供者（可选）
   * @param auditProvider 审计日志提供者（可选）
   * @param stateMachineLogStoreProvider SAGA 状态机日志存储提供者（可选）
   */
  public SagaOrchestrator(
      SeataProperties properties,
      ObjectProvider<SeataMetrics> metricsProvider,
      ObjectProvider<TransactionAuditLogger> auditProvider,
      ObjectProvider<SagaStateMachineLogStore> stateMachineLogStoreProvider) {
    super(metricsProvider, auditProvider);
    this.properties = properties;
    this.stateMachineLogStoreProvider = stateMachineLogStoreProvider;
  }

  /**
   * 执行 SAGA 事务
   *
   * <p>按顺序执行步骤链，失败时逆序补偿已完成步骤。 支持状态机持久化（当配置 SagaStateMachineLogStore 时）。
   *
   * @param transactionName 事务名称
   * @param steps SAGA 步骤链
   * @return 最后一步的返回值
   * @throws Exception 事务异常
   */
  public Object execute(String transactionName, List<? extends SagaStep<?>> steps)
      throws Exception {
    String xid = beginXid(transactionName);
    log.info(
        "SAGA transaction started: name={}, xid={}, steps={}", transactionName, xid, steps.size());

    // 创建状态机日志（如存储可用）
    SagaStateMachineLog stateLog = createStateMachineLog(transactionName, xid, steps);

    List<SagaStep<?>> completedSteps = new ArrayList<>(steps.size());
    Object lastResult = null;

    try {
      for (int i = 0; i < steps.size(); i++) {
        SagaStep<?> step = steps.get(i);
        log.info(
            "SAGA step {}/{} forward: name={}, xid={}", i + 1, steps.size(), step.getName(), xid);

        // 更新当前步骤状态
        if (stateLog != null) {
          stateLog.setCurrentStepIndex(i);
          stateLog.setCurrentStepName(step.getName());
          stateLog.setState(SagaStateMachineLog.SagaState.EXECUTING);
          persistStateMachineLog(stateLog);
        }

        lastResult = executeStepWithTimeout(step, xid);
        completedSteps.add(step);
        log.info(
            "SAGA step {}/{} completed: name={}, xid={}", i + 1, steps.size(), step.getName(), xid);
      }

      // 全部步骤执行成功
      if (stateLog != null) {
        stateLog.setState(SagaStateMachineLog.SagaState.SUCCEEDED);
        persistStateMachineLog(stateLog);
      }

      log.info("SAGA transaction completed: name={}, xid={}", transactionName, xid);
      endXid();
      return lastResult;

    } catch (Exception e) {
      log.error(
          "SAGA transaction failed at step {}/{}, executing compensation: name={}, xid={}",
          completedSteps.size() + 1,
          steps.size(),
          transactionName,
          xid,
          e);

      // 更新状态为失败
      if (stateLog != null) {
        stateLog.setState(SagaStateMachineLog.SagaState.FAILED);
        stateLog.setLastError(e.getMessage());
        persistStateMachineLog(stateLog);
      }

      // 执行补偿
      boolean compensationSuccess = executeCompensation(completedSteps, xid, stateLog);

      if (stateLog != null && compensationSuccess) {
        stateLog.setState(SagaStateMachineLog.SagaState.COMPENSATED);
        persistStateMachineLog(stateLog);
      } else if (stateLog != null) {
        stateLog.setState(SagaStateMachineLog.SagaState.COMPENSATION_FAILED);
        persistStateMachineLog(stateLog);
      }

      endXid(e);
      throw e;
    }
  }

  /**
   * 执行 SAGA 事务（带类型安全的结果转换）
   *
   * @param transactionName 事务名称
   * @param steps SAGA 步骤链
   * @param resultType 最后一步返回值的预期类型
   * @param <T> 最后一步的返回值类型
   * @return 最后一步的返回值
   * @throws Exception 事务异常或类型不匹配
   */
  public <T> T execute(
      String transactionName, List<? extends SagaStep<?>> steps, Class<T> resultType)
      throws Exception {
    Object result = execute(transactionName, steps);
    return resultType.cast(result);
  }

  /**
   * 执行补偿操作（逆序）
   *
   * @param completedSteps 已完成的步骤
   * @param xid 全局事务 ID
   * @param stateLog 状态机日志
   * @return 补偿是否全部成功
   */
  private boolean executeCompensation(
      List<SagaStep<?>> completedSteps, String xid, SagaStateMachineLog stateLog) {
    List<SagaStep<?>> reverseSteps = new ArrayList<>(completedSteps);
    Collections.reverse(reverseSteps);

    if (stateLog != null) {
      stateLog.setState(SagaStateMachineLog.SagaState.COMPENSATING);
      persistStateMachineLog(stateLog);
    }

    boolean allSuccess = true;
    for (SagaStep<?> step : reverseSteps) {
      if (!step.hasCompensation()) {
        log.warn("SAGA step has no compensation, skipping: name={}, xid={}", step.getName(), xid);
        continue;
      }
      int result = compensateStepWithRetry(step, xid);
      if (result < 0) {
        allSuccess = false;
        log.error("SAGA compensation failed after retries: step={}, xid={}", step.getName(), xid);
      }
    }
    return allSuccess;
  }

  /**
   * 带超时控制执行步骤
   *
   * <p>使用共享调度线程池实现步骤级超时控制，避免每步骤创建新线程池。 超时后抛出 StepTimeoutException，由上层执行补偿。
   *
   * @param step SAGA 步骤
   * @param xid 全局事务 ID
   * @return 步骤执行结果
   * @throws Exception 执行异常或超时异常
   */
  private Object executeStepWithTimeout(SagaStep<?> step, String xid) throws Exception {
    if (!step.hasTimeout()) {
      return step.getForwardAction().call();
    }

    // 使用共享调度线程池提交超时控制任务
    CompletableFuture<Object> future =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return step.getForwardAction().call();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            },
            TIMEOUT_SCHEDULER);

    try {
      return future.get(step.getTimeoutMs(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException te) {
      throw new StepTimeoutException(step.getName(), step.getTimeoutMs(), xid);
    } catch (ExecutionException ee) {
      Throwable cause = ee.getCause();
      if (cause instanceof RuntimeException && cause.getCause() != null) {
        cause = cause.getCause();
      }
      if (cause instanceof Exception e) {
        throw e;
      }
      throw new Exception(cause);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new Exception("SAGA step interrupted: " + step.getName(), ie);
    }
  }

  /** 创建状态机日志 */
  private SagaStateMachineLog createStateMachineLog(
      String transactionName, String xid, List<? extends SagaStep<?>> steps) {
    SagaStateMachineLogStore store = getSagaStateMachineLogStore();
    if (store == null) {
      return null;
    }
    try {
      SagaStateMachineLog stateLog = new SagaStateMachineLog(xid, transactionName);
      // 记录步骤快照（简化实现，实际可序列化完整步骤信息）
      stateLog.setStepsSnapshot("steps:" + steps.size());
      store.save(stateLog);
      return stateLog;
    } catch (Exception e) {
      log.warn("Failed to persist SAGA state machine log, continuing without persistence", e);
      return null;
    }
  }

  /** 持久化状态机日志 */
  private void persistStateMachineLog(SagaStateMachineLog stateLog) {
    SagaStateMachineLogStore store = getSagaStateMachineLogStore();
    if (store != null && stateLog != null) {
      try {
        store.save(stateLog);
      } catch (Exception e) {
        log.warn("Failed to persist SAGA state: xid={}", stateLog.getXid(), e);
      }
    }
  }

  /** 获取 SAGA 状态机日志存储（如有） */
  private SagaStateMachineLogStore getSagaStateMachineLogStore() {
    return stateMachineLogStoreProvider != null
        ? stateMachineLogStoreProvider.getIfAvailable()
        : null;
  }

  /**
   * 带重试的补偿步骤执行
   *
   * @param step 步骤
   * @param xid 全局事务 ID
   * @return 成功时返回重试次数（0 表示首次成功），失败返回 -1
   */
  private int compensateStepWithRetry(SagaStep<?> step, String xid) {
    int maxRetries = properties != null ? properties.getSagaMaxRetries() : 0;
    long intervalMs = properties != null ? properties.getSagaRetryIntervalMs() : 2000;

    for (int attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        step.getCompensation().run();
        log.info(
            "SAGA compensation completed: step={}, xid={}, attempt={}",
            step.getName(),
            xid,
            attempt);
        return attempt;
      } catch (Exception e) {
        log.warn(
            "SAGA compensation attempt {} failed: step={}, xid={}",
            attempt + 1,
            step.getName(),
            xid,
            e);
        if (attempt < maxRetries) {
          try {
            Thread.sleep(intervalMs);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      }
    }
    return -1;
  }

  @Override
  public TransactionType getCurrentType() {
    return TransactionType.SAGA;
  }

  @Override
  public <T> T execute(String transactionName, TransactionType type, Callable<T> action)
      throws Exception {
    String xid = beginXid(transactionName);
    try {
      T result = action.call();
      endXid();
      return result;
    } catch (Exception e) {
      endXid(e);
      throw e;
    }
  }

  @Override
  public <T> T executeWithCompensation(
      String transactionName, Callable<T> action, Runnable compensation) throws Exception {
    String xid = beginXid(transactionName);
    try {
      T result = action.call();
      endXid();
      return result;
    } catch (Exception e) {
      if (compensation != null) {
        compensation.run();
      }
      endXid(e);
      throw e;
    }
  }
}
