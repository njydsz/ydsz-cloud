package com.njydsz.cronjob.domain.query;

import com.njydsz.common.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static lombok.AccessLevel.PROTECTED;

/**
 * 任务分页查询 Query（P0-1 PageQuery 统一接入）。
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
@Schema(description = "任务分页查询参数")
public class JobQuery extends PageQuery {

  private static final long serialVersionUID = 1L;

  /** 关键字（任务名/JOB_KEY/Handler 模糊匹配，可选） */
  @Schema(description = "搜索关键字")
  private String keyword;

  /** 状态过滤（NORMAL/PAUSED/STOPPED，可选） */
  @Schema(description = "任务状态过滤")
  private String status;

  /** 分组过滤（可选） */
  @Schema(description = "任务分组过滤")
  private String group;
}
