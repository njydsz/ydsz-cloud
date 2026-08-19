package com.njydsz.userinfo.domain.scim;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SCIM 电子邮箱子属性。
 *
 * <p>对应 SCIM Core Schema {@code urn:ietf:params:scim:schemas:core:2.0:User} 的 {@code emails}
 * 属性元素，遵循 RFC 7643 Section 4.1.1。
 *
 * @author ydsz-team
 * @since 1.6.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimEmail {

  /** 电子邮箱地址。 */
  @JsonProperty("value")
  private String value;

  /** 显示名称（可选）。 */
  @JsonProperty("display")
  private String display;

  /** 是否为主邮箱。 */
  @JsonProperty("primary")
  private Boolean primary;
}
