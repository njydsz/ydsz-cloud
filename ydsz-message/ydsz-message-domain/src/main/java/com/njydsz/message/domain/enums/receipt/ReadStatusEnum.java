package com.njydsz.message.domain.enums.receipt;

import com.njydsz.common.domain.enums.BaseStatusEnum;

/**
 * 站内通知已读状态枚举。
 *
 * <p>对应 SQL {@code ydsz_msg_notification.read_status} 的 CHECK 约束取值（0 / 1）。
 * 实现 {@link BaseStatusEnum} 契约，提供 {@link #canTransitTo} 状态流转校验。
 *
 * <p><b>状态说明：</b>
 *
 * <ul>
 *   <li>{@link #UNREAD} — 未读（默认值，read_status = 0）
 *   <li>{@link #READ} — 已读（终态，read_status = 1）
 * </ul>
 *
 * <p><b>持久化映射：</b>枚举名转为字符串存储（{@code "UNREAD"} / {@code "READ"}）， 读取时通过 {@link #parse(String)} 还原。 数据库列使用
 * {@code VARCHAR(16)} 类型，列名 {@code read_status}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum ReadStatusEnum implements BaseStatusEnum<ReadStatusEnum> {

  /** 未读 */
  UNREAD,
  /** 已读 */
  READ;

  /**
   * 解析字符串为枚举（兼容旧数据 0/1）。
   *
   * @param value 数据库中的原始值
   * @return 对应枚举，无法匹配返回 {@link #UNREAD}
   */
  public static ReadStatusEnum parse(String value) {
    if (value == null || value.isBlank()) {
      return UNREAD;
    }
    // 兼容旧数据: "0"=UNREAD, "1"=READ
    if ("0".equals(value)) {
      return UNREAD;
    }
    if ("1".equals(value)) {
      return READ;
    }
    // 枚举名匹配
    for (ReadStatusEnum status : values()) {
      if (status.name().equalsIgnoreCase(value)) {
        return status;
      }
    }
    return UNREAD;
  }

  /**
   * 转为数据库存储值（整数字符串形式以兼容历史 0/1 数据）。
   *
   * @return "0" 表示未读, "1" 表示已读
   */
  public String toDbValue() {
    return this == READ ? "1" : "0";
  }

  /** {@inheritDoc} */
  @Override
  public boolean isTerminal() {
    return this == READ;
  }

  /** {@inheritDoc} */
  @Override
  public boolean canTransitTo(ReadStatusEnum target) {
    if (this == target) {
      return true;
    }
    return switch (this) {
      case UNREAD -> target == READ;
      case READ -> false;
    };
  }
}
