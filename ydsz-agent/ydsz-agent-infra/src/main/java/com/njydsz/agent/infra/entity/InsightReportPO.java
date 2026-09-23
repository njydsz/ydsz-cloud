package com.njydsz.agent.infra.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseAuditEntity;

/**
 * 洞察报告持久化对象（映射 ydsz_agt_insight_report 表）。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agt_insight_report")
public class InsightReportPO extends MpBaseAuditEntity<Long> {

  private static final long serialVersionUID = 1L;

  /** 自增主键（与数据库 SERIAL/BIGSERIAL 自增列对齐）。 */
  @TableId(type = IdType.AUTO)
  private Long id;

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

  /** 报告状态编码（draft / completed / failed / exported） */
  private String status;

  /** 报告格式（html / pdf / markdown） */
  private String reportFormat;

  /** 存储路径（可选） */
  private String reportPath;

  /** 生成失败时的错误信息 */
  private String errorMessage;

  /** 生成耗时（毫秒） */
  private Integer durationMs;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
