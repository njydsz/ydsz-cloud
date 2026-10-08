package com.njydsz.agent.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 洞察报告视图对象。
 *
 * <p>用于 Controller 层返回洞察报告的展示数据（DDD-007：禁止将 Entity 泄露到 Web 层）。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
public class InsightReportVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 主键 ID */
  private String id;

  /** 唯一业务 ID */
  private String reportId;

  /** 触发用户 ID */
  private String userId;

  /** 关联对话 ID（可选） */
  private String conversationId;

  /** 报告标题 */
  private String title;

  /** 原始分析查询 */
  private String query;

  /** 数据源类型（sql / python / mixed） */
  private String dataSourceType;

  /** 原始数据分析结果 JSON */
  private String dataJson;

  /** 报告内容 JSON（含 sections 列表） */
  private String contentJson;

  /** 报告格式（html / pdf / markdown） */
  private String reportFormat;

  /** 存储路径（可选） */
  private String reportPath;

  /** 生成失败时的错误信息 */
  private String errorMessage;

  /** 生成耗时（毫秒） */
  private Integer durationMs;

  /** 报告状态 */
  private String status;

  /** 创建时间 */
  private LocalDateTime createdAt;
}
