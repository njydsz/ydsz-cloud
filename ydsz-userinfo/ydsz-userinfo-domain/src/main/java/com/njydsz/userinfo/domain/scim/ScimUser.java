package com.njydsz.userinfo.domain.scim;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SCIM 2.0 User 资源表示。
 *
 * <p>遵循 RFC 7643 Section 4.1 Core User Schema（{@code urn:ietf:params:scim:schemas:core:2.0:User}），
 * 是 SCIM 协议中最核心的资源类型，用于 HR 系统与身份管理系统之间的用户数据同步。
 *
 * <p><b>字段映射：</b>
 *
 * <ul>
 *   <li>{@code userName} → ydsz {@code username}（登录名）
 *   <li>{@code externalId} → HR 系统员工编号等外部标识
 *   <li>{@code name.formatted} → ydsz {@code realName}（真实姓名）
 *   <li>{@code emails[0].value} → ydsz {@code email}
 *   <li>{@code phoneNumbers[0].value} → ydsz {@code phone}
 *   <li>{@code active} → ydsz 启用/禁用状态反推
 * </ul>
 *
 * @author ydsz-team
 * @since 1.6.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimUser {

  /** SCIM Schema 标识（固定值）。 */
  @JsonProperty("schemas")
  private List<String> schemas;

  /** SCIM 资源唯一标识（对应 ydsz 用户 ID）。 */
  @JsonProperty("id")
  private String id;

  /** 外部系统标识（如 HR 系统员工编号）。 */
  @JsonProperty("externalId")
  private String externalId;

  /** 登录用户名（唯一）。 */
  @JsonProperty("userName")
  private String userName;

  /** 姓名组件。 */
  @JsonProperty("name")
  private ScimName name;

  /** 显示名称。 */
  @JsonProperty("displayName")
  private String displayName;

  /** 账号是否启用。 */
  @JsonProperty("active")
  private Boolean active;

  /** 电子邮箱列表。 */
  @JsonProperty("emails")
  private List<ScimEmail> emails;

  /** 电话号码列表。 */
  @JsonProperty("phoneNumbers")
  private List<ScimPhone> phoneNumbers;

  /** 资源元数据。 */
  @JsonProperty("meta")
  private ScimMeta meta;
}
