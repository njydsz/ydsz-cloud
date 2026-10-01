package com.njydsz.cronjob.domain.query;

import com.njydsz.common.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static lombok.AccessLevel.PROTECTED;

/**
 * 事件存储分页查询 Query（P0-1 PageQuery 统一接入）。
 *
 * <p>替代 EventStoreController 中原始 {@code int pageNum, int size} 参数，
 * 统一继承 {@link PageQuery}，获得分页校验、结构化排序、深度分页风险评估等能力。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(access = PROTECTED)
@Schema(description = "事件存储分页查询参数")
public class EventStoreQuery extends PageQuery {

  private static final long serialVersionUID = 1L;

  /** 事件类型（可选，如 JOB_CREATED、JOB_TRIGGERED） */
  @Schema(description = "事件类型")
  private String eventType;
}
