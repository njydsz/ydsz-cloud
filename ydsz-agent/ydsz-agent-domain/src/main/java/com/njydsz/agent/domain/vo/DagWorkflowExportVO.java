package com.njydsz.agent.domain.vo;


import com.njydsz.common.excel.annotation.ExcelProperty;

import lombok.Data;

/**
 * DAG 工作流 Excel 导出 VO。
 *
 * <p>用于将 DAG 工作流持久化列表导出为 Excel，便于归档、离线巡检。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Data
public class DagWorkflowExportVO {

/** 工作流编码（业务唯一标识） */
  @ExcelProperty(value = "工作流编码", order = 1, width = 22)
  private String workflowCode;

  /** 工作流名称 */
  @ExcelProperty(value = "工作流名称", order = 2, width = 22)
  private String name;

  /** 描述 */
  @ExcelProperty(value = "描述", order = 3, width = 30)
  private String description;

  /** 分类 */
  @ExcelProperty(value = "分类", order = 4, width = 14)
  private String category;

  /** 主键 ID */
  @ExcelProperty(value = "ID", order = 5, width = 22)
  private String id;

  /** 创建人 ID */
  @ExcelProperty(value = "创建人ID", order = 6, width = 14)
  private String createdBy;

  /** 创建时间 */
  @ExcelProperty(value = "创建时间", order = 7, width = 20)
  private String createdAt;
}
