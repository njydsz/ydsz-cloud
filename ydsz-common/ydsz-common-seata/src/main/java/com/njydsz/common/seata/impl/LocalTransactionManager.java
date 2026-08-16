package com.njydsz.common.seata.impl;

import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.njydsz.common.seata.api.TransactionType;
import com.njydsz.common.seata.audit.TransactionAuditLogger;
import com.njydsz.common.seata.metrics.SeataMetrics;

/**
 * 本地事务管理器（降级实现）
 *
 * <p>当 Seata 不可用时使用，通过 {@link TransactionTemplate} 提供本地数据库事务语义。 不提供跨服务事务保证，适用于单机模式或开发环境。
 *
 * <p><b>P0-8 修复</b>：移除方法级 {@code @Transactional} 注解（自调用场景下 AOP 代理不生效， 且未注入 {@link
 * PlatformTransactionManager}），改为通过 {@link TransactionTemplate} 编程式事务管理，确保事务行为可控。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class LocalTransactionManager extends AbstractTransactionManager {

  private static final Logger LOG = LoggerFactory.getLogger(LocalTransactionManager.class);

  private final TransactionTemplate transactionTemplate;

  /**
   * 构造本地事务管理器
   *
   * @param transactionManager Spring 事务管理器（由 Spring 容器注入， 在单数据源场景下为 {@code
   *     DataSourceTransactionManager}）
   */
  public LocalTransactionManager(PlatformTransactionManager transactionManager) {
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  /**
   * 构造本地事务管理器（带指标和审计）
   *
   * @param transactionManager Spring 事务管理器（由 Spring 容器注入， 在单数据源场景下为 {@code
   *     DataSourceTransactionManager}）
   * @param metricsProvider 指标采集提供者（可选）
   * @param auditProvider 审计日志提供者（可选）
   */
  public LocalTransactionManager(
      PlatformTransactionManager transactionManager,
      ObjectProvider<SeataMetrics> metricsProvider,
      ObjectProvider<TransactionAuditLogger> auditProvider) {
    super(metricsProvider, auditProvider);
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  /**
   * 执行本地事务
   *
   * @param transactionName 事务名称（用于日志和监控）
   * @param type 事务类型
   * @param action 业务操作
   * @param <T> 返回值类型
   * @return 业务操作返回值
   * @throws Exception 事务执行异常
   */
  @Override
  public <T> T execute(String transactionName, TransactionType type, Callable<T> action)
      throws Exception {
    String xid = beginXid(transactionName);
    try {
      T result =
          transactionTemplate.execute(
              status -> {
                try {
                  return action.call();
                } catch (Exception e) {
                  status.setRollbackOnly();
                  throw new TransactionExecutionException(
                      "Local transaction failed: " + transactionName, e);
                } catch (Error e) {
                  status.setRollbackOnly();
                  throw e;
                }
              });
      LOG.debug("Local transaction committed: name={}, xid={}", transactionName, xid);
      endXid();
      return result;
    } catch (TransactionExecutionException e) {
      LOG.error(
          "Local transaction rolled back: name={}, xid={}", transactionName, xid, e.getCause());
      Throwable cause = e.getCause();
      if (cause instanceof Exception) {
        endXid(cause);
        throw (Exception) cause;
      }
      endXid(e);
      throw e;
    } catch (Exception e) {
      LOG.error("Local transaction rolled back: name={}, xid={}", transactionName, xid, e);
      endXid(e);
      throw e;
    }
  }

  /**
   * 执行本地事务（带补偿动作）
   *
   * @param transactionName 事务名称
   * @param action 正向操作
   * @param compensation 补偿操作
   * @param <T> 返回值类型
   * @return 业务操作返回值
   * @throws Exception 事务执行异常
   */
  @Override
  public <T> T executeWithCompensation(
      String transactionName, Callable<T> action, Runnable compensation) throws Exception {
    String xid = beginXid(transactionName);
    LOG.debug("Saga transaction started: name={}, xid={}", transactionName, xid);
    try {
      T result =
          transactionTemplate.execute(
              status -> {
                try {
                  return action.call();
                } catch (Exception e) {
                  status.setRollbackOnly();
                  throw new TransactionExecutionException(
                      "Saga action failed: " + transactionName, e);
                } catch (Error e) {
                  status.setRollbackOnly();
                  throw e;
                }
              });
      LOG.debug("Saga transaction completed: name={}, xid={}", transactionName, xid);
      endXid();
      return result;
    } catch (TransactionExecutionException e) {
      Throwable cause = e.getCause();
      LOG.error(
          "Saga transaction failed, executing compensation: name={}, xid={}",
          transactionName,
          xid,
          cause);
      runCompensation(transactionName, xid, compensation);
      if (cause instanceof Exception ex) {
        endXid(cause);
        throw ex;
      }
      endXid(e);
      throw e;
    } catch (Exception e) {
      LOG.error(
          "Saga transaction failed, executing compensation: name={}, xid={}",
          transactionName,
          xid,
          e);
      runCompensation(transactionName, xid, compensation);
      endXid(e);
      throw e;
    }
  }

  /** 执行补偿操作，捕获并记录补偿异常（不阻断主异常抛出） */
  private void runCompensation(String transactionName, String xid, Runnable compensation) {
    if (compensation == null) {
      return;
    }
    try {
      compensation.run();
      LOG.info("Compensation completed: name={}, xid={}", transactionName, xid);
    } catch (Exception ce) {
      LOG.error("Compensation failed: name={}, xid={}", transactionName, xid, ce);
    }
  }

  /**
   * 获取当前事务类型
   *
   * @return 本地事务类型
   */
  @Override
  public TransactionType getCurrentType() {
    return TransactionType.LOCAL;
  }

  /** 内部运行时异常，用于在 {@link TransactionTemplate} 回调中包装受检异常 */
  private static class TransactionExecutionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    TransactionExecutionException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
