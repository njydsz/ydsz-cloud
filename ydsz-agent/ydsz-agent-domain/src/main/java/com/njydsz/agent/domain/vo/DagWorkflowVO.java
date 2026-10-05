package com.njydsz.agent.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * DAG 工作流视图对象。
 *
 * <p>用于 Controller 层返回 DAG 工作流的展示数据（DDD-007：禁止将 Entity 泄露到 Web 层）。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
public class DagWorkflowVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 主键 ID */
  private String id;

  /** 工作流编码（业务唯一标识） */
  private String workflowCode;

  /** 工作流名称 */
  private String name;

  /** DAG 定义（YAML DSL） */
  private String dsl;

  /** 工作流描述 */
  private String description;

  /** 分类（用于分组检索） */
  private String category;

  /** 是否已发布 */
  private Boolean isPublished;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;
}
