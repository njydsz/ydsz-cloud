package com.njydsz.common.safe.permission;

import java.util.Collections;
import java.util.Set;

import org.springframework.http.HttpStatus;

import com.njydsz.common.exception.code.SecurityExceptionCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionLevel;

/**
 * 字段级权限拒绝异常。
 *
 * <p>当用户对指定字段无访问权限时抛出，对应 {@link SecurityExceptionCode#PERMISSION_DENIED_COLUMN}
 * （错误码 C01065，HTTP 403）。
 *
 * <p>携带字段级上下文信息：当前用户 ID、被拒绝的字段标识、已授权字段集合、访问的资源标识。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * throw new FieldPermissionDeniedException(
 *     "user-001",
 *     Set.of("name", "email"),
 *     Set.of("salary"),
 *     "UserController#getSalary");
 * }</pre>
 *
 * @author ydsz
 * @since 26.10.14
 * @see FieldPermission
 * @see FieldPermissionAspectImpl
 */
public class FieldPermissionDeniedException extends BusinessException {

  private static final long serialVersionUID = 1L;

  /** 触发拒绝的当前用户 ID */
  private final String userId;

  /** 用户已被授权的字段集合（调试用），永不为 {@code null} */
  private final transient Set<String> grantedFields;

  /** 本次校验缺失的字段集合，永不为 {@code null} */
  private final transient Set<String> requiredFields;

  /** 被访问的资源标识（类名#方法名） */
  private final String resource;

  /**
   * 构造字段权限拒绝异常。
   *
   * @param userId 当前用户 ID
   * @param grantedFields 用户已授权的字段集合（可为 null，视为空集）
   * @param requiredFields 本次校验缺失的字段集合（可为 null，视为空集）
   * @param resource 被访问的资源标识
   */
  public FieldPermissionDeniedException(
      String userId,
      Set<String> grantedFields,
      Set<String> requiredFields,
      String resource) {
    super();
    initFields(
        SecurityExceptionCode.PERMISSION_DENIED_COLUMN.getCode(),
        SecurityExceptionCode.PERMISSION_DENIED_COLUMN.getKey(),
        new Object[] {});
    setHttpStatus(HttpStatus.FORBIDDEN.value());
    setLevel(ExceptionLevel.WARN);
    setCategory(ExceptionCategory.SECURITY);
    this.userId = userId;
    this.grantedFields =
        grantedFields != null ? Collections.unmodifiableSet(grantedFields) : Collections.emptySet();
    this.requiredFields =
        requiredFields != null ? Collections.unmodifiableSet(requiredFields) : Collections.emptySet();
    this.resource = resource;
    setMessage(buildMessage());
  }

  /**
   * 构建异常消息（包含上下文以便排查）。
   *
   * @return 拼接后的消息文本
   */
  private String buildMessage() {
    StringBuilder sb = new StringBuilder();
    if (userId != null) {
      sb.append("字段权限拒绝 | 用户ID：").append(userId);
    } else {
      sb.append("字段权限拒绝 | 未登录用户");
    }
    if (requiredFields != null && !requiredFields.isEmpty()) {
      sb.append(" | 需要字段：").append(String.join(", ", requiredFields));
    }
    if (grantedFields != null && !grantedFields.isEmpty()) {
      sb.append(" | 已有字段：").append(String.join(", ", grantedFields));
    }
    if (resource != null) {
      sb.append(" | 资源：").append(resource);
    }
    return sb.toString();
  }

  public String getUserId() {
    return userId;
  }

  public Set<String> getGrantedFields() {
    return grantedFields;
  }

  public Set<String> getRequiredFields() {
    return requiredFields;
  }

  public String getResource() {
    return resource;
  }

  @Override
  public String toString() {
    return "FieldPermissionDeniedException{"
        + "userId='" + userId + '\''
        + ", grantedFields=" + grantedFields
        + ", requiredFields=" + requiredFields
        + ", resource='" + resource + '\''
        + ", code='" + getCode() + '\''
        + '}';
  }
}
