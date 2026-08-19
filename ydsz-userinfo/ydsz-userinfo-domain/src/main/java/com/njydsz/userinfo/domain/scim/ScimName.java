package com.njydsz.userinfo.domain.scim;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SCIM 用户姓名组件（name 子属性）。
 *
 * <p>对应 SCIM Core Schema {@code urn:ietf:params:scim:schemas:core:2.0:User} 的 {@code name} 属性，
 * 遵循 RFC 7643 Section 4.1.1。
 *
 * @author ydsz-team
 * @since 1.6.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimName {

  /** 完整格式化姓名（如 "张三" 或 "Dr. John Jonas, Jr."）。 */
  @JsonProperty("formatted")
  private String formatted;

  /** 姓氏（family name / surname）。 */
  @JsonProperty("familyName")
  private String familyName;

  /** 名字（given name / first name）。 */
  @JsonProperty("givenName")
  private String givenName;
}
