package com.njydsz.workflow.domain.query;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.njydsz.common.domain.query.PageQuery;


/**
 * 流程实例分页查询参数。
 *
 * <p>用于流程实例的多维分页查询，支持按业务类型、发起人、状态、时间范围等条件过滤。
 *
 * <p><b>筛选条件：</b>所有字段均为可选，未传则不作为筛选条件。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FlowInstancePageQuery extends PageQuery {

  /** 业务类型（精确匹配，如 PROJECT/CONTRACT/LEAVE） */
  private String businessType;

  /** 发起人 ID（精确匹配） */
  private String initiatorId;

  /** 流程状态（精确匹配，如 RUNNING/COMPLETED/REJECTED） */
  private String flowStatus;

  /** 开始时间下界（可选） */
  private LocalDateTime startTime;

  /** 开始时间上界（可选） */
  private LocalDateTime endTime;

  /** 租户 ID（可选） */
  private String tenantId;

  /** 数据权限 SQL 片段（可选，由数据权限拦截器注入） */
  private String dataScopeFilter;
}
