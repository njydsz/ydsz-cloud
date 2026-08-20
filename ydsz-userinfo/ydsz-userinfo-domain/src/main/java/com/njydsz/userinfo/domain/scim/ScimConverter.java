package com.njydsz.userinfo.domain.scim;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import com.njydsz.userinfo.domain.dto.UserAccountDTO;
import com.njydsz.userinfo.domain.enums.EnableStatusEnum;
import com.njydsz.userinfo.domain.vo.UserAccountVO;

/**
 * SCIM 2.0 与 ydsz 用户模型转换器。
 *
 * <p>提供 SCIM User 资源与 ydsz 内部用户模型（VO/DTO）之间的双向转换能力，
 * 遵循 RFC 7643 字段映射约定。所有方法均为纯静态无状态方法，线程安全。
 *
 * <p><b>字段映射关系：</b>
 *
 * <ul>
 *   <li>{@code userName} ↔ {@code username}
 *   <li>{@code externalId} → HR 系统外部标识（暂存 username 备注）
 *   <li>{@code name.formatted} ↔ {@code realName}
 *   <li>{@code emails[0].value} ↔ {@code email}
 *   <li>{@code phoneNumbers[0].value} ↔ {@code phone}
 *   <li>{@code active=true} ↔ {@code status=1}（ENABLED）
 *   <li>{@code active=false} ↔ {@code status=0}（DISABLED）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.6.0
 */
public final class ScimConverter {

  /** ISO 8601 日期时间格式。 */
  private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  /** SCIM Core User Schema 标识。 */
  private static final List<String> USER_SCHEMA =
      Collections.singletonList("urn:ietf:params:scim:schemas:core:2.0:User");

  /** 私有构造器防止实例化。 */
  private ScimConverter() {
  }

  /**
   * 将 ydsz 用户 VO 转换为 SCIM User 资源。
   *
   * @param vo ydsz 用户 VO，不可为 null
   * @return SCIM User 资源
   */
  public static ScimUser toScimUser(UserAccountVO vo) {
    if (vo == null) {
      return null;
    }

    ScimUser.ScimUserBuilder builder = ScimUser.builder()
        .schemas(USER_SCHEMA)
        .id(vo.getId())
        .userName(vo.getUsername())
        .meta(ScimMeta.builder()
            .resourceType("User")
            .created(vo.getCreatedAt() != null ? vo.getCreatedAt().format(ISO_FORMATTER) : null)
            .lastModified(vo.getUpdatedAt() != null ? vo.getUpdatedAt().format(ISO_FORMATTER) : null)
            .build());

    // 姓名映射：realName → name.formatted
    if (vo.getRealName() != null && !vo.getRealName().isEmpty()) {
      builder.name(ScimName.builder().formatted(vo.getRealName()).build());
      builder.displayName(vo.getRealName());
    }

    // 状态映射：1 → true, 0 → false
    if (vo.getStatus() != null) {
      builder.active(vo.getStatus() == 1);
    }

    // 邮箱映射
    if (vo.getEmail() != null && !vo.getEmail().isEmpty()) {
      builder.emails(Collections.singletonList(
          ScimEmail.builder().value(vo.getEmail()).primary(true).build()));
    }

    // 手机号映射
    if (vo.getPhone() != null && !vo.getPhone().isEmpty()) {
      builder.phoneNumbers(Collections.singletonList(
          ScimPhone.builder().value(vo.getPhone()).primary(true).build()));
    }

    return builder.build();
  }

  /**
   * 将 SCIM User 资源转换为 ydsz 用户统一 DTO。
   *
   * <p>SCIM 的 {@code userName} 映射为 {@code username}，{@code name.formatted} 映射为 {@code realName}。
   * SCIM 创建用户时不设置密码（需通过后续邀请流程设置）。
   *
   * @param scimUser SCIM User 资源，不可为 null
   * @return 用户统一 DTO（id 为 null，表示创建场景）
   */
  public static UserAccountDTO toCreateDTO(ScimUser scimUser) {
    if (scimUser == null) {
      return null;
    }

    UserAccountDTO dto = new UserAccountDTO();
    dto.setUsername(scimUser.getUserName());
    dto.setExternalId(scimUser.getExternalId());

    // 姓名映射
    if (scimUser.getName() != null) {
      String formatted = scimUser.getName().getFormatted();
      if (formatted != null && !formatted.isEmpty()) {
        dto.setRealName(formatted);
      } else if (scimUser.getName().getGivenName() != null) {
        // 如果没有 formatted，拼接 givenName + familyName
        String familyName = scimUser.getName().getFamilyName();
        String givenName = scimUser.getName().getGivenName();
        StringBuilder sb = new StringBuilder();
        if (familyName != null) {
          sb.append(familyName);
        }
        if (givenName != null) {
          sb.append(givenName);
        }
        dto.setRealName(sb.toString());
      }
    }

    // 邮箱映射：优先取 primary 邮箱，否则取第一个
    if (scimUser.getEmails() != null && !scimUser.getEmails().isEmpty()) {
      String email = scimUser.getEmails().stream()
          .filter(e -> Boolean.TRUE.equals(e.getPrimary()))
          .findFirst()
          .map(ScimEmail::getValue)
          .orElse(scimUser.getEmails().get(0).getValue());
      dto.setEmail(email);
    }

    // 手机号映射
    if (scimUser.getPhoneNumbers() != null && !scimUser.getPhoneNumbers().isEmpty()) {
      dto.setPhone(scimUser.getPhoneNumbers().get(0).getValue());
    }

    // 状态映射
    if (Boolean.FALSE.equals(scimUser.getActive())) {
      dto.setStatus(EnableStatusEnum.DISABLED);
    } else {
      dto.setStatus(EnableStatusEnum.ENABLED);
    }

    return dto;
  }

  /**
   * 将 SCIM User 资源转换为 ydsz 用户统一 DTO（更新场景）。
   *
   * <p>SCIM PUT 请求的语义为全量更新，所有非空字段将覆盖 ydsz 用户对应字段。
   *
   * @param scimUser SCIM User 资源（需包含 id），不可为 null
   * @return 用户统一 DTO（含 id，表示更新场景）
   */
  public static UserAccountDTO toUpdateDTO(ScimUser scimUser) {
    if (scimUser == null) {
      return null;
    }

    UserAccountDTO dto = new UserAccountDTO();
    dto.setId(scimUser.getId());

    // 姓名映射
    if (scimUser.getName() != null) {
      String formatted = scimUser.getName().getFormatted();
      if (formatted != null && !formatted.isEmpty()) {
        dto.setRealName(formatted);
      } else if (scimUser.getName().getGivenName() != null) {
        String familyName = scimUser.getName().getFamilyName();
        String givenName = scimUser.getName().getGivenName();
        StringBuilder sb = new StringBuilder();
        if (familyName != null) {
          sb.append(familyName);
        }
        if (givenName != null) {
          sb.append(givenName);
        }
        dto.setRealName(sb.toString());
      }
    }

    // 邮箱映射
    if (scimUser.getEmails() != null && !scimUser.getEmails().isEmpty()) {
      dto.setEmail(scimUser.getEmails().get(0).getValue());
    }

    // 手机号映射
    if (scimUser.getPhoneNumbers() != null && !scimUser.getPhoneNumbers().isEmpty()) {
      dto.setPhone(scimUser.getPhoneNumbers().get(0).getValue());
    }

    // 状态映射
    if (Boolean.FALSE.equals(scimUser.getActive())) {
      dto.setStatus(EnableStatusEnum.DISABLED);
    } else if (Boolean.TRUE.equals(scimUser.getActive())) {
      dto.setStatus(EnableStatusEnum.ENABLED);
    }

    return dto;
  }
}
