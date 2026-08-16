package com.njydsz.literule.domain.model;

import com.njydsz.common.exception.custom.SysException;

/**
 * 模型调用异常（基础设施层异常）
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public class ModelInvocationException extends SysException {

  private static final long serialVersionUID = 1L;

  public ModelInvocationException(String message) {
    super(message);
  }

  public ModelInvocationException(String message, Throwable cause) {
    super(message);
    initCause(cause);
  }
}
