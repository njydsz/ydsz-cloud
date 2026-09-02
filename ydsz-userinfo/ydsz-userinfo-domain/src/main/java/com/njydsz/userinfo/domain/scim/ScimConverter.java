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
 * @since 26.09.01
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

    applyRealName(builder, vo.getRealName());
    applyStatus(builder, vo.getStatus());
    applyEmail(builder, vo.getEmail());
    applyPhone(builder, vo.getPhone());

    return builder.build();
  }

  /** 应用姓名映射：realName → name.formatted。 */
  private static void applyRealName(ScimUser.ScimUserBuilder builder, String realName) {
    if (realName != null && !realName.isEmpty()) {
      builder.name(ScimName.builder().formatted(realName).build());
      builder.displayName(realName);
    }
  }

  /** 应用状态映射：1 → true, 0 → false。 */
  private static void applyStatus(ScimUser.ScimUserBuilder builder, Integer status) {
    if (status != null) {
      builder.active(status == 1);
    }
  }

  /** 应用邮箱映射：非空时映射为 primary 邮箱。 */
  private static void applyEmail(ScimUser.ScimUserBuilder builder, String email) {
    if (email != null && !email.isEmpty()) {
      builder.emails(Collections.singletonList(
          ScimEmail.builder().value(email).primary(true).build()));
    }
  }

  /** 应用手机号映射：非空时映射为 primary 手机号。 */
  private static void applyPhone(ScimUser.ScimUserBuilder builder, String phone) {
    if (phone != null && !phone.isEmpty()) {
      builder.phoneNumbers(Collections.singletonList(
          ScimPhone.builder().value(phone).primary(true).build()));
    }
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

    dto.setRealName(resolveRealName(scimUser));
    dto.setEmail(resolvePrimaryEmail(scimUser));
    dto.setPhone(resolveFirstPhone(scimUser));
    applyActiveStatus(dto, scimUser.getActive());

    return dto;
  }

  /**
   * 解析 SCIM 用户姓名：优先 formatted，否则拼接 familyName + givenName。
   *
   * @param scimUser SCIM User 资源
   * @return 解析后的姓名；无姓名信息返回 null
   */
  private static String resolveRealName(ScimUser scimUser) {
    if (scimUser.getName() == null) {
      return null;
    }
    String formatted = scimUser.getName().getFormatted();
    if (formatted != null && !formatted.isEmpty()) {
      return formatted;
    }
    String givenName = scimUser.getName().getGivenName();
    if (givenName == null) {
      return null;
    }
    String familyName = scimUser.getName().getFamilyName();
    StringBuilder sb = new StringBuilder();
    if (familyName != null) {
      sb.append(familyName);
    }
    sb.append(givenName);
    return sb.toString();
  }

  /**
   * 解析 SCIM 用户邮箱：优先 primary 邮箱，否则取第一个。
   *
   * @param scimUser SCIM User 资源
   * @return 解析后的邮箱；无邮箱返回 null
   */
  private static String resolvePrimaryEmail(ScimUser scimUser) {
    if (scimUser.getEmails() == null || scimUser.getEmails().isEmpty()) {
      return null;
    }
    return scimUser.getEmails().stream()
        .filter(e -> Boolean.TRUE.equals(e.getPrimary()))
        .findFirst()
        .map(ScimEmail::getValue)
        .orElse(scimUser.getEmails().get(0).getValue());
  }

  /**
   * 解析 SCIM 用户手机号：取第一个手机号。
   *
   * @param scimUser SCIM User 资源
   * @return 解析后的手机号；无手机号返回 null
   */
  private static String resolveFirstPhone(ScimUser scimUser) {
    if (scimUser.getPhoneNumbers() == null || scimUser.getPhoneNumbers().isEmpty()) {
      return null;
    }
    return scimUser.getPhoneNumbers().get(0).getValue();
  }

  /**
   * 应用 SCIM active 状态到 DTO。
   *
   * @param dto 目标 DTO
   * @param active SCIM active 状态（true=启用 / false=禁用）
   */
  private static void applyActiveStatus(UserAccountDTO dto, Boolean active) {
    if (Boolean.FALSE.equals(active)) {
      dto.setStatus(EnableStatusEnum.DISABLED);
    } else {
      dto.setStatus(EnableStatusEnum.ENABLED);
    }
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

    dto.setRealName(resolveRealName(scimUser));
    dto.setEmail(resolveFirstEmail(scimUser));
    dto.setPhone(resolveFirstPhone(scimUser));
    if (Boolean.FALSE.equals(scimUser.getActive())) {
      dto.setStatus(EnableStatusEnum.DISABLED);
    } else if (Boolean.TRUE.equals(scimUser.getActive())) {
      dto.setStatus(EnableStatusEnum.ENABLED);
    }

    return dto;
  }

  /**
   * 解析 SCIM 用户邮箱：取第一个邮箱（更新场景语义）。
   *
   * @param scimUser SCIM User 资源
   * @return 解析后的邮箱；无邮箱返回 null
   */
  private static String resolveFirstEmail(ScimUser scimUser) {
    if (scimUser.getEmails() == null || scimUser.getEmails().isEmpty()) {
      return null;
    }
    return scimUser.getEmails().get(0).getValue();
  }
}
