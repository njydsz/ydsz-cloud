package com.njydsz.agent.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * DAG 工作流（domain 层持久化实体，YDIZ-DDD-007 单包模式）
 *
 * <p><b>YDIZ-DDD-007</b>：domain Entity 直接携带 MyBatis-Plus ORM 注解，
 * infra 层通过依赖 domain 模块引用本类，禁止自建 PO/DO 副本。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agt_dag_workflow")
public class DagWorkflow extends MpBaseEntity<String> {

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
