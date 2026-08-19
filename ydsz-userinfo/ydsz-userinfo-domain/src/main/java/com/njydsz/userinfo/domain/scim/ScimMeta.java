package com.njydsz.userinfo.domain.scim;

import com.njydsz.common.json.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SCIM 资源元数据。
 *
 * <p>对应 SCIM Core Schema 的 {@code meta} 属性，记录资源类型、创建时间和最后修改时间，
 * 遵循 RFC 7643 Section 3.1。
 *
 * @author ydsz-team
 * @since 1.6.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScimMeta {

  /** 资源类型（如 "User"、"Group"）。 */
  @JsonProperty("resourceType")
  private String resourceType;

  /** 资源创建时间（ISO 8601 格式）。 */
  @JsonProperty("created")
  private String created;

  /** 资源最后修改时间（ISO 8601 格式）。 */
  @JsonProperty("lastModified")
  private String lastModified;
}
