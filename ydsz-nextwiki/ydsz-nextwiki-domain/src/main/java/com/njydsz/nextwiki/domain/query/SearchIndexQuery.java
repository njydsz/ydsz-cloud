package com.njydsz.nextwiki.domain.query;

import java.io.Serializable;

import com.njydsz.common.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
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
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(description = "搜索索引查询参数")
public class SearchIndexQuery extends PageQuery implements Serializable {

  private static final long serialVersionUID = 1L;

  @Schema(description = "搜索关键词")
  private String keyword;

  @Schema(description = "创建人（权限过滤）")
  private String createdBy;

  @Schema(description = "搜索范围：all / filename / content / tag")
  private String scope;
}
