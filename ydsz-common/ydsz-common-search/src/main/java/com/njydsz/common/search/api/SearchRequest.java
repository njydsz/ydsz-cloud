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

  /** 当前页码（从 1 开始） */
  @Min(1)
  @Schema(description = "当前页码")
  private int page;

  /** 每页大小 */
  @Min(1)
  @Schema(description = "每页大小")
  private int pageSize;

  /** 偏移量（跳过的记录数） */
  @Min(0)
  @Schema(description = "偏移量（跳过的记录数，= (page - 1) * pageSize）")
  private int offset;

  /** 排序字段 */
  @Schema(description = "排序字段")
  private String sortBy;

  /** 是否升序（默认 false 降序） */
  @Schema(description = "是否升序")
  @Builder.Default
  private boolean ascending = false;

  /** 是否启用高亮 */
  @Schema(description = "是否启用高亮")
  @Builder.Default
  private boolean highlight = true;

  /** 高亮前置标签 */
  @Schema(description = "高亮前置标签")
  private String highlightPreTag;

  /** 高亮后置标签 */
  @Schema(description = "高亮后置标签")
  private String highlightPostTag;

  /** 高亮片段最大长度 */
  @Schema(description = "高亮片段最大长度")
  private int highlightFragmentSize;

  /** 是否启用模糊匹配 */
  @Schema(description = "是否启用模糊匹配")
  @Builder.Default
  private boolean fuzzy = true;

  /** 模糊匹配最小相似度 */
  @Schema(description = "模糊匹配最小相似度")
  private double fuzzyMinSimilarity;

  /** 过滤条件列表（跨字段 AND，同字段内 OR） */
  @Schema(description = "过滤条件列表")
  @Builder.Default
  private List<SearchFilter> filters = new ArrayList<>(4);

  /** 聚合/分面配置列表 */
  @Schema(description = "聚合/分面配置列表")
  @Builder.Default
  private List<SearchAggregation> aggregations = new ArrayList<>(2);

  /** 租户 ID（用于多租户隔离） */
  @Schema(description = "租户 ID")
  private String tenantId;

  /** 用户 ID（用于数据权限过滤） */
  @Schema(description = "用户 ID")
  private String userId;

  /** 用户角色列表（用于权限过滤） */
  @Schema(description = "用户角色列表")
  @Builder.Default
  private List<String> roles = new ArrayList<>(4);

  /** 部门 ID（用于权限过滤） */
  @Schema(description = "部门 ID")
  private String deptId;

  /** 是否管理员（跳过数据权限过滤） */
  @Schema(description = "是否管理员")
  private boolean admin;

  /** 是否仅搜索标题 */
  @Schema(description = "是否仅搜索标题")
  @Builder.Default
  private boolean titleOnly = false;

  /** 游标（用于 keyset 分页） */
  @Schema(description = "游标（用于 keyset 分页）")
  private String cursor;

  // ==================== 手动 Getter/Setter（Lombok 兼容 fallback） ====================

  public String getCursor() {
    return cursor;
  }

  public void setCursor(String cursor) {
    this.cursor = cursor;
  }

  public List<SearchAggregation> getAggregations() {
    return aggregations;
  }

  public void setAggregations(List<SearchAggregation> aggregations) {
    this.aggregations = aggregations;
  }
}
