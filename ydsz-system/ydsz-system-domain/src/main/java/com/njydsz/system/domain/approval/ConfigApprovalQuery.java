package com.njydsz.system.domain.approval;

import lombok.Data;

/**
 * 配置变更审批单据查询参数。
 *
 * <p>支持分页、状态筛选和资源类型筛选。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Data
public class ConfigApprovalQuery {

  /** 默认每页大小 */
  private static final int DEFAULT_PAGE_SIZE = 20;

  /** 页码（从 1 开始） */
  private Integer pageNum = 1;

  /** 每页大小 */
  private Integer pageSize = DEFAULT_PAGE_SIZE;

  /** 审批状态筛选（PENDING / APPROVED / REJECTED / WITHDRAWN，为空表示全部） */
  private String status;

  /** 资源类型筛选（CONFIG / DICT / VARIABLE，为空表示全部） */
  private String resourceType;

  /** 按发起人 ID 筛选（查询「我已发起的」时使用） */
  private String submitterId;

  /** 按当前审批人 ID 筛选（查询「待我审批的」时使用） */
  private String approverId;
}
