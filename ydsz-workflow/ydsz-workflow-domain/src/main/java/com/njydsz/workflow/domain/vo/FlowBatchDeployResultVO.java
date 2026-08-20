package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * 批量部署流程结果视图对象。
 *
 * <p>用于返回批量部署流程定义操作的成功与失败统计信息。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowBatchDeployResultVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 成功部署的数量 */
  private int successCount;

  /** 部署失败的数量 */
  private int failedCount;

  /** 成功部署的定义 ID 列表 */
  private List<String> definitionIds;

  /** 部署失败的条目列表 */
  private List<FailedDeployItemVO> failedItems;

  /**
   * 批量部署失败条目。
   */
  @Data
  public static class FailedDeployItemVO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 文件名 */
    private String fileName;

    /** 失败原因 */
    private String reason;
  }
}
