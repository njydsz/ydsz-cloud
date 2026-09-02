package com.njydsz.nextwiki.domain.query;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 搜索索引查询 Query
 *
 * <p>用于搜索索引的分页搜索查询，作为 Repository 接口查询方法的入参。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
@Schema(description = "搜索索引查询参数")
public class SearchIndexQuery implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "搜索关键词")
  private String keyword;

  @Schema(description = "创建人（权限过滤）")
  private String createdBy;

  @Schema(description = "搜索范围：all / filename / content / tag")
  private String scope;

  @Schema(description = "页码（从 1 开始）")
  private Integer page;

  @Schema(description = "每页大小")
  private Integer pageSize;
}
