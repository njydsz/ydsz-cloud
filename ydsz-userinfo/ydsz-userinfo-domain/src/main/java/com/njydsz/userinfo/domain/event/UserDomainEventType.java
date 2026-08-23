package com.njydsz.userinfo.domain.event;

import lombok.Getter;

/**
 * 用户中心模块领域事件类型枚举。
 *
 * <p>替代 {@code DomainEventTypes} 中的硬编码字符串常量，提供类型安全的事件类型引用。
 * 每个枚举值携带 {@code code} 字符串（与 common-event 的 {@code DomainEvent.eventType} 兼容）。
 *
 * <h3>事件类型清单</h3>
 *
 * <ul>
 *   <li>用户聚合根事件：USER_CREATED / USER_UPDATED / USER_DELETED / USER_LOGIN
 *   <li>角色聚合根事件：ROLE_CHANGED
 *   <li>组织聚合根事件：ORG_STRUCTURE_CHANGED
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
public enum UserDomainEventType {

  // ==================== 用户聚合根事件 ====================
  /** 用户创建 */
  USER_CREATED("USER_CREATED", "用户创建"),
  /** 用户信息更新 */
  USER_UPDATED("USER_UPDATED", "用户信息更新"),
  /** 用户删除 */
  USER_DELETED("USER_DELETED", "用户删除"),
  /** 用户登录成功 */
  USER_LOGIN("USER_LOGIN", "用户登录"),

  // ==================== 角色/组织聚合根事件 ====================
  /** 角色变更（分配/撤销/权限修改） */
  ROLE_CHANGED("ROLE_CHANGED", "角色变更"),
  /** 组织架构变更（部门/公司增删改） */
  ORG_STRUCTURE_CHANGED("ORG_STRUCTURE_CHANGED", "组织架构变更");

  /** 事件类型编码（与 common-event DomainEvent.eventType 兼容） */
  private final String code;

  /** 事件描述（中文，用于日志与文档） */
  private final String description;

  UserDomainEventType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /**
   * 从编码解析枚举值。
   *
   * @param code 事件类型编码
   * @return 枚举值，无法识别时返回 null
   */
  public static UserDomainEventType fromCode(String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    for (UserDomainEventType type : values()) {
      if (type.code.equals(code)) {
        return type;
      }
    }
    return null;
  }
}
