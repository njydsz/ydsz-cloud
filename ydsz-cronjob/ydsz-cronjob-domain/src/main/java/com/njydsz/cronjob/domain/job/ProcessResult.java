package com.njydsz.cronjob.domain.job;

import lombok.Data;

/**
 * 任务执行结果
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class ProcessResult {

  /** 是否成功 */
  private boolean success;

  /** 结果数据（JSON 字符串） */
  private String result;

  /** 错误信息 */
  private String errorMessage;

  // 显式编写 getter/setter 以避免 Lombok 处理差异
  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  /**
   * 创建成功结果
   *
   * @return 成功结果实例
   */
  public static ProcessResult success() {
    ProcessResult r = new ProcessResult();
    r.setSuccess(true);
    return r;
  }

  /**
   * 创建成功结果（带返回数据）
   *
   * @param result 返回数据 JSON
   * @return 成功结果实例
   */
  public static ProcessResult success(String result) {
    ProcessResult r = new ProcessResult();
    r.setSuccess(true);
    r.setResult(result);
    return r;
  }

  /**
   * 创建失败结果
   *
   * @param errorMessage 错误信息
   * @return 失败结果实例
   */
  public static ProcessResult failed(String errorMessage) {
    ProcessResult r = new ProcessResult();
    r.setSuccess(false);
    r.setErrorMessage(errorMessage);
    return r;
  }
}
