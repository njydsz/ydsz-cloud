package com.njydsz.common.util.id;

/**
 * WorkerId 分配失败异常——所有策略均无法分配唯一 workerId 时抛出。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class WorkerIdExhaustedException extends RuntimeException {

  public WorkerIdExhaustedException(String message) {
    super(message);
  }

  public WorkerIdExhaustedException(String message, Throwable cause) {
    super(message, cause);
  }
}
