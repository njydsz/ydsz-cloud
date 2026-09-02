package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * 批量启动流程结果视图对象。
 *
 * <p>用于返回批量启动流程实例操作的成功与失败统计信息。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowBatchStartResultVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 成功启动的数量 */
  private int successCount;

  /** 启动失败的数量 */
  private int failedCount;

  /** 成功创建的实例 ID 列表 */
  private List<String> instanceIds;

  /** 启动失败的条目列表 */
  private List<FailedItemVO> failedItems;

  /**
   * 批量启动失败条目。
   */
  @Data
  public static class FailedItemVO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 条目序号 */
    private int index;

    /** 流程编码 */
    private String flowCode;

    /** 失败原因 */
    private String reason;
  }
}
