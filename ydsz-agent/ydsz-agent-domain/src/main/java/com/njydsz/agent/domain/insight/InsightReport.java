package com.njydsz.agent.domain.insight;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseAuditEntity;

/**
 * 洞察报告领域实体（映射 ydsz_agt_insight_report 表）。
 *
 * <p>记录 BI 洞察报告从创建到导出的完整生命周期，包含原始数据分析结果 JSON（dataJson）、
 * 报告内容 JSON（含 sections）、状态、格式和错误信息。
 *
 * <p><b>线程安全</b>：持久化实体，可变；仅在单请求/单事务内使用，勿跨线程共享。
 *
 * @author ydsz-team
 * @since 26.09.07
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_agt_insight_report")
public class InsightReport extends MpBaseAuditEntity<Long> {

  /** 自增主键（覆盖基类 ASSIGN_ID 为 AUTO，与数据库 SERIAL/BIGSERIAL 自增列对齐）。 */
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

  /** 创建时间（覆盖基类审计字段，与数据库 created_at 列对齐）。 */
  @TableField("created_at")
  private LocalDateTime createdAt;

  /** 更新时间（覆盖基类审计字段，与数据库 updated_at 列对齐）。 */
  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
