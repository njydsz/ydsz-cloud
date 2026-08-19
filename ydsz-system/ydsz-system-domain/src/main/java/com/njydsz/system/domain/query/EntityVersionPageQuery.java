package com.njydsz.system.domain.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 实体版本分页查询条件（P2-3 分页优化）。
 *
 * <p>用于 EntityVersion 的分页查询，统一管理 Config/Dict/Variable 版本的翻页参数。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>继承 {@link BaseQuery} 获取标准分页参数（pageNum / pageSize）
 *   <li>资源类型 + 资源键为必填条件（版本按资源隔离）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see BaseQuery 分页基类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "实体版本分页查询条件")
public class EntityVersionPageQuery {

  /** 资源类型（CONFIG/DICT/VARIABLE） */
  @Schema(description = "资源类型", example = "CONFIG", requiredMode = Schema.RequiredMode.REQUIRED)
  private String resourceType;

  /** 资源唯一标识（如配置键、字典类型编码、变量键） */
  @Schema(description = "资源唯一标识", example = "api.max-qps", requiredMode = Schema.RequiredMode.REQUIRED)
  private String resourceKey;

  /** 页码（1-based，默认 1） */
  @Schema(description = "页码", example = "1", minimum = "1", defaultValue = "1")
  @Builder.Default
  private Integer pageNum = 1;

  /** 每页条数（默认 20，最大 500） */
  @Schema(description = "每页条数", example = "20", minimum = "1", maximum = "500", defaultValue = "20")
  @Builder.Default
  private Integer pageSize = 20;
}
