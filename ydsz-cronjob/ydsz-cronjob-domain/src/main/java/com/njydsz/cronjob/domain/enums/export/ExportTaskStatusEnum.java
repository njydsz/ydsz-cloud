package com.njydsz.cronjob.domain.enums.export;

/**
 * 异步导出任务状态枚举。
 *
 * <p>定义导出任务生命周期中的全部状态。
 *
 * @author ydsz-team
 * @since 26.10.13
 */
public enum ExportTaskStatusEnum {

  /** 待处理：任务已创建，等待 Worker 消费 */
  PENDING("PENDING", "待处理"),

  /** 处理中：Worker 正在生成导出文件 */
  PROCESSING("PROCESSING", "处理中"),

  /** 成功：文件已生成，可下载 */
  SUCCEEDED("SUCCEEDED", "导出成功"),

  /** 失败：生成过程中发生异常 */
  FAILED("FAILED", "导出失败"),

  /** 已取消：用户主动取消任务 */
  CANCELED("CANCELED", "已取消");

  private final String code;
  private final String description;

  ExportTaskStatusEnum(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public String getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }

  /**
   * 判断是否为终态。
   *
   * @param statusCode 状态码
   * @return true=终态
   */
  public static boolean isTerminal(String statusCode) {
    return SUCCEEDED.getCode().equals(statusCode)
        || FAILED.getCode().equals(statusCode)
        || CANCELED.getCode().equals(statusCode);
  }

  /**
   * 根据状态码获取枚举。
   *
   * @param code 状态码
   * @return 枚举值，未匹配返回 null
   */
  public static ExportTaskStatusEnum fromCode(String code) {
    for (ExportTaskStatusEnum status : values()) {
      if (status.getCode().equals(code)) {
        return status;
      }
    }
    return null;
  }
}
