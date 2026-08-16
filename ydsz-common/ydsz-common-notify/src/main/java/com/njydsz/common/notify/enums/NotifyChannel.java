package com.njydsz.common.notify.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息通知渠道枚举
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum NotifyChannel {
  EMAIL(1, "邮件"),

  /** 短信渠道。当前版本暂未实现短信渠道，将在后续版本补充。 */
  SMS(2, "短信"),
  WECOM(3, "企业微信"),
  DINGTALK(4, "钉钉"),
  FEISHU(5, "飞书"),
  /** 站内信渠道。当前版本暂未实现，将在后续版本补充。 */
  INSITE(6, "站内信");

  private final int code;

  private final String name;

  /**
   * 根据渠道编码解析对应的通知渠道。
   *
   * <p>遍历全部枚举项匹配 {@code code}；无匹配时抛出 {@link IllegalArgumentException}， 不做静默回退，调用方需自行兜底处理未知渠道。
   *
   * @param code 渠道编码（对应各枚举项的 code 字段）
   * @return 匹配的通知渠道
   * @throws IllegalArgumentException 当编码不匹配任何已定义渠道时抛出
   */
  public static NotifyChannel fromCode(int code) {
    for (NotifyChannel channel : values()) {
      if (channel.code == code) {
        return channel;
      }
    }
    throw new IllegalArgumentException("未知通知渠道: " + code);
  }
}
