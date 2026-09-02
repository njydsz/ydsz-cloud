package com.njydsz.common.notify.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 默认的消息发送结果实现
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DefaultNotifyResult implements NotifySendResult {

  /** 是否发送成功 */
  private boolean success;

  /** 消息ID */
  private String messageId;

  /** 错误信息 */
  private String errorMessage;

  /** 发送渠道 */
  private String channel;

  /** 发送时间戳 */
  private long sendTime;

  @Override
  public boolean isSuccess() {
    return success;
  }
}
