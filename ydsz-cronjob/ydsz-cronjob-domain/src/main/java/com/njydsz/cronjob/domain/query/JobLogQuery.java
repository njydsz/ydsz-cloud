package com.njydsz.cronjob.domain.query;

import com.njydsz.common.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static lombok.AccessLevel.PROTECTED;

/**
 * 任务执行日志分页查询 Query（P0-1 PageQuery 统一接入）。
 *
 * <p>替代 JobController 中原始 {@code int pageNum, int size} 参数，
 * 统一继承 {@link PageQuery}，获得分页校验、结构化排序、深度分页风险评估等能力。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = PROTECTED)
@Schema(description = "任务执行日志分页查询参数")
public class JobLogQuery extends PageQuery {

  private static final long serialVersionUID = 1L;

  /** 任务 JOB_KEY 过滤（可选） */
  @Schema(description = "任务 JOB_KEY 过滤")
  private String jobKey;

  /** 状态过滤（SUCCESS/FAILED/TIMEOUT，可选） */
  @Schema(description = "执行状态过滤")
  private String status;
}
