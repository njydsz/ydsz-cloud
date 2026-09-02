package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * 流程定义详情视图对象。
 *
 * <p>聚合流程定义、节点列表、跳转条件和只读标识，用于流程设计器初始化加载。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowDefinitionDetailVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 流程定义基本信息 */
  private FlowDefinitionVO definition;

  /** 节点列表 */
  private List<FlowNodeVO> nodes;

  /** 跳转条件列表 */
  private List<FlowSkipVO> skips;

  /** 是否只读（如发布后不可直接编辑） */
  private Boolean readOnly;
}
