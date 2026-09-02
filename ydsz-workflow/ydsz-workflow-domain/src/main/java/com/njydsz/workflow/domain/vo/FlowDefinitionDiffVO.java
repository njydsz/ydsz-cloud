package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 流程定义版本差异视图对象。
 *
 * <p>记录两个版本之间节点和跳转条件的变化详情，用于版本对比功能。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowDefinitionDiffVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 第一个版本号 */
  private Integer v1;

  /** 第二个版本号 */
  private Integer v2;

  /** 节点变更列表 */
  private List<Map<String, Object>> nodeChanges;

  /** 跳转条件变更列表 */
  private List<Map<String, Object>> skipChanges;
}
