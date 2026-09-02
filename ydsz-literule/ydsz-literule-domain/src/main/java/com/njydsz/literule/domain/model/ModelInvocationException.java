package com.njydsz.literule.domain.model;

import com.njydsz.common.exception.custom.SysException;
import com.njydsz.literule.domain.enums.LiteruleExceptionCode;

/**
 * 模型调用异常（基础设施层异常）
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public class ModelInvocationException extends SysException {

  private static final long serialVersionUID = 1L;

  public ModelInvocationException(String message) {
    super(LiteruleExceptionCode.MODEL_INVOCATION_ERROR);
    setMessage(message);
  }

  public ModelInvocationException(String message, Throwable cause) {
    super(LiteruleExceptionCode.MODEL_INVOCATION_ERROR, cause);
    setMessage(message);
  }
}
