package com.njydsz.system.server.exception;

/**
 * 配置变更审批业务异常。
 *
 * <p>用于审批流程中的业务规则校验失败（状态不允许、权限不足等）。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
public class ConfigApprovalException extends RuntimeException {

  /** 序列化版本号 */
  private static final long serialVersionUID = 1L;

  /**
   * 构造业务异常。
   *
   * @param message 异常信息
   */
  public ConfigApprovalException(String message) {
    super(message);
  }
}
