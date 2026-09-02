package com.njydsz.userinfo.domain.scim;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.njydsz.common.json.annotation.JsonProperty;

/**
 * SCIM 电话号码子属性。
 *
 * <p>对应 SCIM Core Schema {@code urn:ietf:params:scim:schemas:core:2.0:User} 的 {@code phoneNumbers}
 * 属性元素，遵循 RFC 7643 Section 4.1.1。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScimPhone {

  /** 电话号码。 */
  @JsonProperty("value")
  private String value;

  /** 显示名称（可选）。 */
  @JsonProperty("display")
  private String display;

  /** 是否为主要电话号码。 */
  @JsonProperty("primary")
  private Boolean primary;
}
