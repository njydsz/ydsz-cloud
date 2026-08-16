package com.njydsz.message.domain.dto.core;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息编排流程执行结果 VO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrchestrationResultVO {

  /** 流程 ID */
  private String flowId;

  /** 流程状态: RUNNING / COMPLETED / FAILED / ABORTED */
  private String status;

  /** 成功节点数 */
  private int successCount;

  /** 失败节点数 */
  private int failedCount;

  /** 跳过节点数 */
  private int skippedCount;

  /** 总节点数 */
  private int totalCount;

  /** 各节点执行结果（key=nodeId, value=状态描述） */
  private Map<String, String> nodeResults;

  /** 错误信息 */
  private String errorMessage;
}
