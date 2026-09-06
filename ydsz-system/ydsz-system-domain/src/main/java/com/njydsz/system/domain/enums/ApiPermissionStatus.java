package com.njydsz.system.domain.enums;

/**
 * 接口权限状态枚举
 *
 * <p>用于接口权限注册表（{@code ydsz_sys_api_permission}）的状态管理。 与 DDL 默认值对齐：{@code status VARCHAR(32) DEFAULT 'ENABLED'}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum ApiPermissionStatus {

  /** 启用状态（接口可被访问） */
  ENABLED("ENABLED"),

  /** 禁用状态（接口不可被访问） */
  DISABLED("DISABLED");

  /** 状态码字符串值 */
  private final String code;

  ApiPermissionStatus(String code) {
    this.code = code;
  }

  /**
   * 获取状态码字符串值。
   *
   * @return 状态码字符串
   */
  public String getCode() {
    return code;
  }

  /**
   * 根据状态码字符串获取枚举实例。
   *
   * @param code 状态码字符串（不区分大小写）
   * @return 对应枚举；未匹配返回 {@code null}
   */
  public static ApiPermissionStatus fromCode(String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    for (ApiPermissionStatus status : values()) {
      if (status.code.equalsIgnoreCase(code)) {
        return status;
      }
    }
    return null;
  }
}
