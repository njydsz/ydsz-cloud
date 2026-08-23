package com.njydsz.userinfo.domain.scim;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.njydsz.common.json.annotation.JsonProperty;

/**
 * SCIM 2.0 列表响应格式。
 *
 * <p>用于 {@code GET /Users}、{@code GET /Groups} 等列表查询端点，遵循 RFC 7644 Section 3.4.2。
 *
 * <p><b>响应结构：</b>
 *
 * <ul>
 *   <li>{@code schemas}：固定为 {@code ["urn:ietf:params:scim:api:messages:2.0:ListResponse"]}
 *   <li>{@code totalResults}：符合条件的总记录数
 *   <li>{@code itemsPerPage}：当前页实际返回条数
 *   <li>{@code startIndex}：起始位置（从 1 开始）
 *   <li>{@code Resources}：资源列表（User 或 Group）
 * </ul>
 *
 * @param <T> 资源元素类型
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScimListResponse<T> {

  /** SCIM 列表响应 Schema 标识（固定值）。 */
  @JsonProperty("schemas")
  private List<String> schemas;

  /** 符合条件的总记录数。 */
  @JsonProperty("totalResults")
  private Integer totalResults;

  /** 当前页实际返回条数。 */
  @JsonProperty("itemsPerPage")
  private Integer itemsPerPage;

  /**
   * 起始位置（从 1 开始）。
   *
   * <p>对应 SCIM 协议的 {@code startIndex} 参数，与页码的换算关系为 {@code startIndex = (page - 1) * count + 1}。
   */
  @JsonProperty("startIndex")
  private Integer startIndex;

  /** 资源列表。 */
  @JsonProperty("Resources")
  private List<T> resources;
}
