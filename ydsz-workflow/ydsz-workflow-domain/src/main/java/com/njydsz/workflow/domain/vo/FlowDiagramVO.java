package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * 流程图视图对象。
 *
 * <p>用于流程图查询接口（高亮当前节点），包含流程定义、节点列表和跳转列表。
 * 替代 {@code Map<String, Object>} 返回值，提供编译期类型安全。
 *
 * <p><b>架构合规说明（26.09.01 DDD 分层规范）：</b>视图对象置于 {@code domain/vo/} 包下，
 * 以 {@code VO} 结尾（符合 §34.2.1 表格：vo/ 视图对象）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowDiagramVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 流程定义基本信息 */
  private FlowDefinitionVO definition;

  /** 节点列表（每个节点带 active 标记） */
  private List<DiagramNodeVO> nodes;

  /** 跳转列表 */
  private List<FlowSkipVO> skips;

  /**
   * 流程图节点视图对象。
   *
   * <p>继承 FlowNodeVO 的所有字段，额外增加 active 标记用于前端高亮。
   */
  @Data
  public static class DiagramNodeVO extends FlowNodeVO implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 是否为当前激活节点（前端高亮） */
    private boolean active;

    /** 节点状态（RUNNING / COMPLETED / PENDING / SKIPPED） */
    private String nodeState;
  }
}
