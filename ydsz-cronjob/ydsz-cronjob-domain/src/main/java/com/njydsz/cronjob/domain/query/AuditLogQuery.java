package com.njydsz.cronjob.domain.query;

import java.time.LocalDateTime;

import com.njydsz.common.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static lombok.AccessLevel.PROTECTED;

/**
 * 操作审计日志分页查询 Query（P0-1 PageQuery 统一接入）。
 *
 * <p>替代 AuditLogController 中原始 {@code int pageNum, int size} 参数，
 * 统一继承 {@link PageQuery}，获得分页校验、结构化排序、深度分页风险评估等能力。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = PROTECTED)
@Schema(description = "操作审计日志分页查询参数")
public class AuditLogQuery extends PageQuery {

  private static final long serialVersionUID = 1L;

  /** 操作行为编码（可选，对应 AuditAction 枚举值） */
  @Schema(description = "操作行为编码")
  private Integer action;

  /** 操作人姓名（可选） */
  @Schema(description = "操作人姓名")
  private String operatorName;

  /** 开始时间（可选） */
  @Schema(description = "开始时间")
  private LocalDateTime startTime;

  /** 结束时间（可选） */
  @Schema(description = "结束时间")
  private LocalDateTime endTime;
}
