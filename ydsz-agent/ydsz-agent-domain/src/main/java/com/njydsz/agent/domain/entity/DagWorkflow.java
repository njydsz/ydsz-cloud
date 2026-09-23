package com.njydsz.agent.domain.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.njydsz.agent.entity.base.DomainBaseEntity;

/**
 * DAG 工作流（domain 纯净 POJO，无 MP 注解）
 *
 * <p><b>DDD 分层</b>：domain 层不携带 MyBatis-Plus 注解；
 * 持久化映射由 {@code infra.entity.DagWorkflowPO} 承担。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DagWorkflow extends DomainBaseEntity<String> {

  private static final long serialVersionUID = 1L;

  /** 工作流编码（业务唯一标识） */
  private String code;

  /** 工作流名称 */
  private String name;

  /** DAG 定义（YAML DSL） */
  private String dsl;

  /** 工作流描述 */
  private String description;
}
