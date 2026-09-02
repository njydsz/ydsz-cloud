package com.njydsz.common.notify.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通知消息类型枚举
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@AllArgsConstructor
public enum NotifyType {
  /** 纯文本消息 */
  TEXT(1, "纯文本"),
  /** 富文本消息 */
  HTML(2, "富文本"),
  /** 模板消息 */
  TEMPLATE(3, "模板消息"),
  /** Markdown 消息 */
  MARKDOWN(4, "Markdown"),
  /** 卡片消息 */
  CARD(5, "卡片消息");

  private final int code;

  private final String name;
}
