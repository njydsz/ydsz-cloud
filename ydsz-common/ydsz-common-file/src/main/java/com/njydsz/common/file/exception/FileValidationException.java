package com.njydsz.common.file.exception;

import com.njydsz.common.exception.custom.BusinessException;

/**
 * 文件校验异常。
 *
 * <p>当文件上传校验失败时（如文件大小超限、扩展名不允许、Content-Type 不匹配等）抛出此异常。
 *
 * <p>继承 {@link BusinessException}，使用 {@link FileExceptionCode#FILE_UPLOAD_FAILED} 错误码，
 * 便于全局异常处理器统一识别并返回标准化错误响应。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FileExceptionCode
 * @see BusinessException
 */
public class FileValidationException extends BusinessException {

  private static final long serialVersionUID = 1L;

  /**
   * 构造文件校验异常
   *
   * @param message 异常信息
   */
  public FileValidationException(String message) {
    super(FileExceptionCode.FILE_UPLOAD_FAILED);
    setMessage(message);
  }

  /**
   * 构造文件校验异常（带原因）
   *
   * @param message 异常信息
   * @param cause 导致此异常的原始原因
   */
  public FileValidationException(String message, Throwable cause) {
    super(FileExceptionCode.FILE_UPLOAD_FAILED, cause);
    setMessage(message);
  }
}
