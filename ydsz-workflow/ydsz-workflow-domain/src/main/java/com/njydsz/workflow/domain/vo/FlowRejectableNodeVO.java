package com.njydsz.workflow.domain.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 可驳回节点视图对象。
 *
 * <p>标识流程中可以执行驳回操作的已完成节点，用于驳回功能的可选节点展示。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
public class FlowRejectableNodeVO {

  /** 节点编码 */
  private String nodeCode;

  /** 节点名称 */
  private String nodeName;

  /** 节点完成时间 */
  private LocalDateTime completedAt;

  /** 完成该节点的操作人 */
  private String completedBy;
}
