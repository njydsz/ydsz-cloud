package com.njydsz.agent.domain.vo;


import com.njydsz.common.excel.annotation.ExcelProperty;

import lombok.Data;

/**
 * Agent 定义 Excel 导出 VO。
 *
 * <p>通过 {@code @ExcelProperty} 注解定义列顺序与列宽，
 * 由 {@link com.njydsz.common.excel.core.ExcelFacade} 流式写出到 HTTP 响应。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
public class AgentDefinitionExportVO {

/** Agent 业务编码 */
  @ExcelProperty(value = "Agent编码", order = 1, width = 22)
  private String agentCode;

  /** Agent 名称 */
  @ExcelProperty(value = "Agent名称", order = 2, width = 22)
  private String agentName;

  /** Agent 类型 */
  @ExcelProperty(value = "Agent类型", order = 3, width = 14)
  private String agentType;

  /** 描述 */
  @ExcelProperty(value = "描述", order = 4, width = 30)
  private String description;

  /** 模型配置 */
  @ExcelProperty(value = "模型配置", order = 5, width = 25)
  private String modelConfig;

  /** 启用工具列表（JSON 字符串） */
  @ExcelProperty(value = "启用工具", order = 6, width = 25)
  private String toolNames;

  /** 温度（LLM 采样温度，取值范围 0~2） */
  @ExcelProperty(value = "温度", order = 7, width = 8)
  private String temperature;

  /** 最大生成 Token 数 */
  @ExcelProperty(value = "最大Token", order = 8, width = 12)
  private String maxTokens;

  /** 创建人 */
  @ExcelProperty(value = "创建人", order = 9, width = 14)
  private String createdBy;

  /** 创建时间 */
  @ExcelProperty(value = "创建时间", order = 10, width = 20)
  private String createdAt;

  /** 更新人 */
  @ExcelProperty(value = "更新人", order = 11, width = 14)
  private String updatedBy;

  /** 更新时间 */
  @ExcelProperty(value = "更新时间", order = 12, width = 20)
  private String updatedAt;
}
