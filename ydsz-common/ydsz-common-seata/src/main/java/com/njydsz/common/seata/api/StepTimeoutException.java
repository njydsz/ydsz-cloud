package com.njydsz.common.seata.api;

/**
 * SAGA 步骤超时异常
 *
 * <p>当某个 SAGA 步骤的执行时间超过 {@link SagaStep#getTimeoutMs()} 时抛出， 由 {@link
 * com.njydsz.common.seata.impl.SagaOrchestrator} 捕获并触发补偿流程。
 *
 * <p><b>P2-6 新增</b>：支持步骤级超时控制。
 *
 * @author ydsz-team
 * @since 1.3.0
 */
public class StepTimeoutException extends Exception {

  private final String stepName;
  private final long timeoutMs;
  private final String xid;

  /**
   * 构造步骤超时异常
   *
   * @param stepName 超时的步骤名称
   * @param timeoutMs 配置的超时时间（毫秒）
   * @param xid 全局事务 ID
   */
  public StepTimeoutException(String stepName, long timeoutMs, String xid) {
    super(String.format("SAGA step '%s' timed out after %dms (xid=%s)", stepName, timeoutMs, xid));
    this.stepName = stepName;
    this.timeoutMs = timeoutMs;
    this.xid = xid;
  }

  /**
   * 获取超时的步骤名称
   *
   * @return 步骤名称
   */
  public String getStepName() {
    return stepName;
  }

  /**
   * 获取配置的超时时间
   *
   * @return 超时时间（毫秒）
   */
  public long getTimeoutMs() {
    return timeoutMs;
  }

  /**
   * 获取全局事务 ID
   *
   * @return XID
   */
  public String getXid() {
    return xid;
  }
}
