package com.njydsz.userinfo.domain.enums;

/**
 * 账号封禁类型枚举。
 *
 * <p>定义账号运营侧封禁的类型：临时封禁（到期自动解除）与永久封禁（仅能由管理员手动解除）。
 *
 * <p><b>DB 存储：</b>使用枚举名字符串（{@code TEMPORARY} / {@code PERMANENT}）存储，为 null 时表示未封禁。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum BanType {

  /**
   * 临时封禁。
   *
   * <p>到期后自动解除（懒检查：下次调用 {@code isBanned()} 时判断）。
   */
  TEMPORARY,

  /**
   * 永久封禁。
   *
   * <p>仅能由管理员手动解除。
   */
  PERMANENT;

  /**
   * 判断是否为永久封禁。
   *
   * @return true 表示永久封禁
   */
  public boolean isPermanent() {
    return this == PERMANENT;
  }
}
