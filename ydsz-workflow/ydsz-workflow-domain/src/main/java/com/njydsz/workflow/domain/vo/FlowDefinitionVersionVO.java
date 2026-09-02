package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 流程定义版本视图对象。
 *
 * <p>记录某一流程定义的版本信息，用于版本管理和发布记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class FlowDefinitionVersionVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 版本记录主键 ID */
  private String id;

  /** 流程编码 */
  private String flowCode;

  /** 版本号 */
  private Integer version;

  /** 流程名称 */
  private String flowName;

  /** 状态 */
  private String status;

  /** 发布人 */
  private String publishedBy;

  /** 发布时间 */
  private LocalDateTime publishedAt;
}
