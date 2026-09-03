package com.njydsz.cronjob.server.core.connector;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 连接器导出结果（P2-3）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class ConnectorExportResult {
  /** 错误列表初始容量 */
  private static final int ERRORS_CAPACITY = 4;

  /** 总任务数 */
  private int total;

  /** 成功数 */
  private int success;

  /** 失败数 */
  private int failed;

  /** 跳过数 */
  private int skipped;

  /** 错误详情列表 */
  private List<String> errors = new ArrayList<>(ERRORS_CAPACITY);

  /**
   * 创建成功结果。
   *
   * @param total 总任务数
   * @param success 成功数
   * @return 导出结果（跳过数 = total - success）
   */
  public static ConnectorExportResult success(int total, int success) {
    ConnectorExportResult result = new ConnectorExportResult();
    result.setTotal(total);
    result.setSuccess(success);
    result.setFailed(0);
    result.setSkipped(total - success);
    return result;
  }

  /**
   * 添加错误信息。
   *
   * @param error 错误详情
   */
  public void addError(String error) {
    errors.add(error);
  }
}
