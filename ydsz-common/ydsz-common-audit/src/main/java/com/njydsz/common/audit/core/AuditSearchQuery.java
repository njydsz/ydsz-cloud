package com.njydsz.common.audit.core;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审计日志检索查询参数载体（ES 友好）
 *
 * <p>封装全文检索、多维过滤和分页参数，用于 {@link AuditQueryService#search(AuditSearchQuery)}。
 *
 * <p><b>字段说明：</b>
 *
 * <ul>
 *   <li>{@link #keyword} — 全文检索关键词，匹配 content/module/error_message 等文本字段；
 *       为空时跳过全文检索，等同于多维过滤查询</li>
 *   <li>{@link #operatorId / module / action / auditType} — 等值过滤条件，多个条件间为 AND 关系</li>
 *   <li>{@link #startTime / endTime} — 时间范围过滤（闭区间），为空时不限制</li>
 *   <li>{@link #tenantId} — 多租户隔离条件</li>
 *   <li>{@link #page / size} — 分页参数（页码从 1 开始，size 默认 20，最大 200）</li>
 * </ul>
 *
 * <p><b>兼容性：</b>当前 JDBC 实现通过 {@code LIKE '%keyword%'} 提供基础模糊匹配，
 * 后续接入 Elasticsearch 时底层实现可无缝升级，调用方无需修改。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditSearchQuery {

  /** 全文检索关键词（匹配 content、module、error_message 字段）；为空时跳过全文检索 */
  private String keyword;

  /** 操作人 ID（精确匹配）；可为 null */
  private String operatorId;

  /** 操作行为编码（精确匹配）；可为 null */
  private Integer action;

  /** 模块名（精确匹配）；可为 null */
  private String module;

  /** 审计类型编码（精确匹配）；可为 null */
  private Integer auditType;

  /** 起始时间（含）；可为 null */
  private LocalDateTime startTime;

  /** 结束时间（含）；可为 null */
  private LocalDateTime endTime;

  /** 租户 ID（精确匹配）；可为 null 表示不按租户过滤 */
  private String tenantId;

  /** 页码（从 1 开始，默认 1） */
  @Builder.Default
  private int page = 1;

  /** 每页大小（默认 20，最大 200） */
  @Builder.Default
  private int size = 20;

  /**
   * 规范化分页参数，确保 page >= 1 且 size 不超过 200
   */
  public void normalize() {
    if (page < 1) {
      page = 1;
    }
    if (size < 1) {
      size = 20;
    } else if (size > 200) {
      size = 200;
    }
  }
}
