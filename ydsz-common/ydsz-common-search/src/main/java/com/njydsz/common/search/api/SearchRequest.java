package com.njydsz.common.search.api;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Data;

/**
 * 统一搜索请求
 *
 * <p>封装所有搜索引擎通用的搜索参数，包括关键词、分页、排序、过滤、高亮等。 各业务模块通过 {@code SearchProvider} 补充特定于实体的搜索逻辑。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@Schema(description = "统一搜索请求")
public class SearchRequest implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 搜索关键词 */
  @Schema(description = "搜索关键词")
  private String keyword;

  /** 搜索范围（实体类型列表），为空表示搜索全部 */
  @Schema(description = "搜索范围（实体类型列表）")
  @Builder.Default
  private List<String> types = new ArrayList<>(4);
}