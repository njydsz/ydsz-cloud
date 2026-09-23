package com.njydsz.agent.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * DAG 工作流持久化对象（映射 ydsz_agt_dag_workflow 表）。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agt_dag_workflow")
public class DagWorkflowPO extends MpBaseEntity<String> {

  private static final long serialVersionUID = 1L;

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
}
