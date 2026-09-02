package com.njydsz.userinfo.domain.scim;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.njydsz.common.json.annotation.JsonProperty;

/**
 * SCIM 2.0 标准错误响应格式。
 *
 * <p>遵循 RFC 7644 Section 3.12 的错误响应规范，用于所有 SCIM 端点的异常响应。
 *
 * <p><b>响应结构：</b>
 *
 * <ul>
 *   <li>{@code schemas}：固定为 {@code ["urn:ietf:params:scim:api:messages:2.0:Error"]}
 *   <li>{@code status}：HTTP 状态码（字符串形式，如 "404"）
 *   <li>{@code detail}：人类可读的错误描述
 *   <li>{@code scimType}：SCIM 标准错误类型（可选，如 "invalidFilter"、"uniqueness"）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScimError {

  /** SCIM 错误响应 Schema 标识（固定值）。 */
  @JsonProperty("schemas")
  private List<String> schemas;

  /** HTTP 状态码（字符串形式）。 */
  @JsonProperty("status")
  private String status;

  /** 人类可读的错误描述。 */
  @JsonProperty("detail")
  private String detail;

  /** SCIM 标准错误类型（可选）。 */
  @JsonProperty("scimType")
  private String scimType;
}
