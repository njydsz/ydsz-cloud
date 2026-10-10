package com.njydsz.workflow.domain.vo;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 批量催办结果视图对象。
 *
 * <p>用于返回批量催办操作的结果统计和详细分发信息。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
public class FlowBatchUrgeResultVO {

  /** 催办总数 */
  private int totalCount;

  /** 催办成功数量 */
  private int successCount;

  /** 催办失败数量 */
  private int failedCount;

  /** 按实例分组的催办结果（实例 ID → 被催办任务 ID 列表） */
  private Map<String, List<String>> urgedByInstance;
}
