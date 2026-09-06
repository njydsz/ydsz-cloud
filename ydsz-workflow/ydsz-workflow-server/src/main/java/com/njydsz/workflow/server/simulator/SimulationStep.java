package com.njydsz.workflow.server.simulator;

import java.io.Serial;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模拟步骤
 *
 * <p>记录流程模拟执行过程中的单个步骤信息。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimulationStep implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 步骤序号 */
  private int stepIndex;

  /** 节点编码 */
  private String nodeCode;

  /** 节点名称 */
  private String nodeName;

  /** 节点类型 */
  private String nodeType;

  /** 步骤描述 */
  private String description;

  /** 跳转条件（如有） */
  private String condition;
}
