package com.njydsz.system.domain.enums;

import com.njydsz.common.domain.enums.BaseStatusEnum;

import java.util.List;

/**
 * 接口权限状态枚举
 *
 * <p>用于接口权限注册表（{@code ydsz_sys_api_permission}）的状态管理。 与 DDL 默认值对齐：{@code status VARCHAR(32) DEFAULT 'ENABLED'}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum ApiPermissionStatus implements BaseStatusEnum<ApiPermissionStatus> {

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

  /**
   * 校验状态流转合法性。
   *
   * <p>启用/禁用双向均可流转，无终态。
   *
   * @param target 目标状态
   * @return 始终返回 true（二态可逆）
   */
  @Override
  public boolean canTransitTo(ApiPermissionStatus target) {
    return target != null;
  }

  /**
   * 返回所有枚举值。
   *
   * @return 全量状态列表
   */
  @Override
  public List<ApiPermissionStatus> allStates() {
    return List.of(values());
  }
}
